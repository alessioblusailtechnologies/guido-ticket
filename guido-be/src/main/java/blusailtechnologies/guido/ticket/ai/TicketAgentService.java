package blusailtechnologies.guido.ticket.ai;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import blusailtechnologies.guido.ticket.ai.progress.ToolEvent;
import blusailtechnologies.guido.ticket.ai.progress.ToolProgressBus;
import blusailtechnologies.guido.ticket.attachment.AttachmentContent;
import blusailtechnologies.guido.ticket.attachment.AttachmentExtractor;
import blusailtechnologies.guido.ticket.persistence.ChatHistoryService;
import blusailtechnologies.guido.ticket.persistence.ChatSessionRow;
import blusailtechnologies.guido.ticket.pricing.PricingService;

@Service
public class TicketAgentService {

	private static final Logger log = LoggerFactory.getLogger(TicketAgentService.class);

	private final ChatClient chatClient;
	private final AttachmentExtractor attachmentExtractor;
	private final ToolProgressBus bus;
	private final ChatHistoryService history;
	private final PricingService pricing;
	private final String model;

	public TicketAgentService(
			ChatClient chatClient,
			AttachmentExtractor attachmentExtractor,
			ToolProgressBus bus,
			ChatHistoryService history,
			PricingService pricing,
			@Value("${spring.ai.anthropic.chat.model:claude-opus-4-6}") String model) {
		this.chatClient = chatClient;
		this.attachmentExtractor = attachmentExtractor;
		this.bus = bus;
		this.history = history;
		this.pricing = pricing;
		this.model = model;
	}

	public SseEmitter chatStream(String sessionId, String message,
	                             List<MultipartFile> attachments, String queryResultsJson) {
		SseEmitter emitter = bus.register(sessionId);
		bus.publish(sessionId, ToolEvent.sessionStarted(sessionId));

		Thread.startVirtualThread(() -> {
			try {
				String reply = runConversation(sessionId, message, attachments, queryResultsJson);
				bus.publish(sessionId, ToolEvent.complete(reply));
				bus.complete(sessionId);
			} catch (Exception e) {
				log.error("Chat stream failed for session {}", sessionId, e);
				bus.publish(sessionId, ToolEvent.fatal(rootMessage(e)));
				bus.completeWithError(sessionId, e);
			}
		});

		return emitter;
	}

	private String runConversation(String sessionId, String message,
	                               List<MultipartFile> attachments, String queryResultsJson)
			throws IOException {
		StringBuilder userText = new StringBuilder();
		if (message != null && !message.isBlank()) {
			userText.append(message);
		}

		List<Media> mediaList = new ArrayList<>();
		List<Map<String, Object>> attachmentMeta = new ArrayList<>();
		if (attachments != null) {
			for (MultipartFile file : attachments) {
				if (file == null || file.isEmpty()) {
					continue;
				}
				AttachmentContent content = attachmentExtractor.extract(file);
				attachmentMeta.add(Map.of(
						"name", String.valueOf(content.filename()),
						"size", file.getSize(),
						"type", content.mimeType().toString(),
						"truncated", content.truncated()
				));
				if (content.isImage()) {
					mediaList.add(Media.builder()
							.mimeType(content.mimeType())
							.data(new ByteArrayResource(content.imageBytes()))
							.build());
				} else if (content.hasText()) {
					userText.append("\n\n--- Allegato: ").append(content.filename());
					if (content.truncated()) {
						userText.append(" (TESTO TRONCATO: il documento eccedeva il limite di estrazione)");
					}
					userText.append(" ---\n");
					userText.append(content.extractedText());
					if (content.truncated()) {
						userText.append("\n\n[…l'estrazione è stata troncata. Se serve il resto del documento, l'utente può alzare guido.attachment.max-text-chars o ridurre l'allegato.]");
					}
				} else {
					userText.append("\n\n[Allegato ").append(content.filename())
							.append(" ricevuto ma senza testo estraibile]");
				}
			}
		}

		String userPayload = userText.toString();
		history.ensureSession(sessionId, model, userPayload);
		history.appendUserMessage(sessionId, userPayload,
				attachmentMeta.isEmpty() ? null : attachmentMeta,
				queryResultsJson);

		ChatResponse response = chatClient.prompt()
				.user(u -> {
					u.text(userPayload);
					mediaList.forEach(u::media);
				})
				.toolContext(Map.of("sessionId", sessionId))
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
				.call()
				.chatResponse();

		String reply = response != null && response.getResult() != null
				&& response.getResult().getOutput() != null
				? response.getResult().getOutput().getText()
				: "";

		recordUsageAndPersist(sessionId, reply, response);
		return reply;
	}

	private void recordUsageAndPersist(String sessionId, String reply, ChatResponse response) {
		Integer in = null, out = null, cacheCreate = null, cacheRead = null;
		if (response != null && response.getMetadata() != null) {
			Usage usage = response.getMetadata().getUsage();
			if (usage != null) {
				in = toInt(usage.getPromptTokens());
				out = toInt(usage.getCompletionTokens());
				Map<String, Object> native_ = readNativeUsage(usage);
				cacheCreate = pickInt(native_, "cache_creation_input_tokens", "cacheCreationInputTokens");
				cacheRead = pickInt(native_, "cache_read_input_tokens", "cacheReadInputTokens");
			}
		}

		BigDecimal cost = pricing.cost(model,
				nz(in), nz(out), nz(cacheCreate), nz(cacheRead));

		history.appendAssistantMessage(sessionId, reply, null,
				in, out, cacheCreate, cacheRead, cost);

		ChatSessionRow row = history.findSession(sessionId).orElse(null);
		if (row != null) {
			bus.publish(sessionId, ToolEvent.usage(new ToolEvent.Usage(
					model, in, out, cacheCreate, cacheRead, cost,
					row.totalInputTokens(),
					row.totalOutputTokens(),
					row.totalCacheCreationTokens(),
					row.totalCacheReadTokens(),
					row.totalCostUsd()
			)));
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> readNativeUsage(Usage usage) {
		try {
			Object n = usage.getNativeUsage();
			if (n instanceof Map<?, ?> m) {
				Map<String, Object> result = new HashMap<>();
				m.forEach((k, v) -> result.put(String.valueOf(k), v));
				return result;
			}
		} catch (Exception ignored) {
		}
		return Map.of();
	}

	private static Integer pickInt(Map<String, Object> map, String... keys) {
		for (String k : keys) {
			Object v = map.get(k);
			if (v instanceof Number n) return n.intValue();
		}
		return null;
	}

	private static Integer toInt(Object v) {
		if (v instanceof Number n) return n.intValue();
		return null;
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String rootMessage(Throwable t) {
		Throwable cur = t;
		while (cur.getCause() != null && cur.getCause() != cur) {
			cur = cur.getCause();
		}
		return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
	}
}

package blusailtechnologies.guido.ticket.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import blusailtechnologies.guido.ticket.ai.progress.ToolEvent;
import blusailtechnologies.guido.ticket.ai.progress.ToolProgressBus;
import blusailtechnologies.guido.ticket.attachment.AttachmentContent;
import blusailtechnologies.guido.ticket.attachment.AttachmentExtractor;

@Service
public class TicketAgentService {

	private static final Logger log = LoggerFactory.getLogger(TicketAgentService.class);

	private final ChatClient chatClient;
	private final AttachmentExtractor attachmentExtractor;
	private final ToolProgressBus bus;

	public TicketAgentService(
			ChatClient chatClient,
			AttachmentExtractor attachmentExtractor,
			ToolProgressBus bus) {
		this.chatClient = chatClient;
		this.attachmentExtractor = attachmentExtractor;
		this.bus = bus;
	}

	public SseEmitter chatStream(String sessionId, String message, List<MultipartFile> attachments) {
		SseEmitter emitter = bus.register(sessionId);
		bus.publish(sessionId, ToolEvent.sessionStarted(sessionId));

		Thread.startVirtualThread(() -> {
			try {
				String reply = runConversation(sessionId, message, attachments);
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

	private String runConversation(String sessionId, String message, List<MultipartFile> attachments)
			throws IOException {
		StringBuilder userText = new StringBuilder();
		if (message != null && !message.isBlank()) {
			userText.append(message);
		}

		List<Media> mediaList = new ArrayList<>();
		if (attachments != null) {
			for (MultipartFile file : attachments) {
				if (file == null || file.isEmpty()) {
					continue;
				}
				AttachmentContent content = attachmentExtractor.extract(file);
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

		return chatClient.prompt()
				.user(u -> {
					u.text(userText.toString());
					mediaList.forEach(u::media);
				})
				.toolContext(Map.of("sessionId", sessionId))
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
				.call()
				.content();
	}

	private static String rootMessage(Throwable t) {
		Throwable cur = t;
		while (cur.getCause() != null && cur.getCause() != cur) {
			cur = cur.getCause();
		}
		return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
	}
}

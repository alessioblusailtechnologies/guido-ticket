package blusailtechnologies.guido.ticket.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ChatHistoryService {

	private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
	private static final int TITLE_MAX = 80;

	private final ChatSessionRepository sessions;
	private final ChatMessageRepository messages;
	private final ObjectMapper mapper;

	public ChatHistoryService(ChatSessionRepository sessions,
	                         ChatMessageRepository messages,
	                         ObjectMapper mapper) {
		this.sessions = sessions;
		this.messages = messages;
		this.mapper = mapper;
	}

	public ChatSessionRow ensureSession(String sessionId, String model, String firstUserMessage) {
		Optional<ChatSessionRow> existing = sessions.findById(sessionId);
		if (existing.isPresent()) {
			return existing.get();
		}
		String title = buildTitle(firstUserMessage);
		sessions.insert(sessionId, title, model);
		log.info("Nuova sessione persistente {} · {}", sessionId, title);
		return sessions.findById(sessionId).orElseThrow();
	}

	public long appendUserMessage(String sessionId, String content, List<?> attachments, String queryResultsJson) {
		int ord = messages.nextOrdinal(sessionId);
		String attachmentsJson = toJsonOrNull(attachments);
		long id = messages.insert(sessionId, ord, "user", content,
				attachmentsJson, queryResultsJson, null,
				null, null, null, null, null);
		sessions.touch(sessionId);
		return id;
	}

	public long appendAssistantMessage(String sessionId, String content,
	                                   List<?> steps,
	                                   Integer inputTok, Integer outputTok,
	                                   Integer cacheCreate, Integer cacheRead,
	                                   BigDecimal cost) {
		int ord = messages.nextOrdinal(sessionId);
		String stepsJson = toJsonOrNull(steps);
		long id = messages.insert(sessionId, ord, "assistant", content,
				null, null, stepsJson,
				inputTok, outputTok, cacheCreate, cacheRead, cost);
		if (cost != null) {
			sessions.addUsage(sessionId,
					nz(inputTok), nz(outputTok),
					nz(cacheCreate), nz(cacheRead),
					cost);
		} else {
			sessions.touch(sessionId);
		}
		return id;
	}

	public Optional<ChatSessionRow> findSession(String sessionId) {
		return sessions.findById(sessionId);
	}

	public List<ChatSessionRow> listRecent(int limit) {
		return sessions.listRecent(limit);
	}

	public List<ChatMessageRow> listMessages(String sessionId) {
		return messages.listBySession(sessionId);
	}

	public int deleteSession(String sessionId) {
		return sessions.delete(sessionId);
	}

	public int renameSession(String sessionId, String newTitle) {
		String clean = newTitle == null ? "" : newTitle.trim();
		if (clean.isBlank()) {
			clean = "Nuova conversazione";
		}
		if (clean.length() > 255) {
			clean = clean.substring(0, 255);
		}
		return sessions.updateTitle(sessionId, clean);
	}

	private String toJsonOrNull(Object value) {
		if (value == null) return null;
		try {
			return mapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			log.warn("JSON serialization failed: {}", e.getMessage());
			return null;
		}
	}

	private static String buildTitle(String firstUserMessage) {
		if (firstUserMessage == null || firstUserMessage.isBlank()) {
			return "Nuova conversazione";
		}
		String clean = firstUserMessage.replaceAll("\\s+", " ").trim();
		return clean.length() <= TITLE_MAX ? clean : clean.substring(0, TITLE_MAX) + "…";
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}

package blusailtechnologies.guido.ticket.ai;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import blusailtechnologies.guido.ticket.persistence.ChatHistoryService;
import blusailtechnologies.guido.ticket.persistence.ChatMessageRow;
import blusailtechnologies.guido.ticket.persistence.ChatSessionRow;

@RestController
@RequestMapping(value = "/api/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
public class SessionsController {

	private static final Logger log = LoggerFactory.getLogger(SessionsController.class);

	private final ChatHistoryService history;
	private final ObjectMapper mapper;

	public SessionsController(ChatHistoryService history, ObjectMapper mapper) {
		this.history = history;
		this.mapper = mapper;
	}

	@GetMapping
	public List<SessionSummary> list(@RequestParam(value = "limit", defaultValue = "50") int limit) {
		return history.listRecent(Math.min(limit, 200)).stream()
				.map(SessionsController::toSummary)
				.toList();
	}

	@GetMapping("/{id}")
	public ResponseEntity<SessionDetail> load(@PathVariable("id") String id) {
		Optional<ChatSessionRow> opt = history.findSession(id);
		if (opt.isEmpty()) {
			return ResponseEntity.notFound().build();
		}
		List<ChatMessageRow> msgs = history.listMessages(id);
		List<SessionMessage> dtos = new ArrayList<>(msgs.size());
		for (ChatMessageRow m : msgs) {
			dtos.add(new SessionMessage(
					m.role(),
					m.content(),
					parseList(m.attachmentsJson()),
					parseList(m.queryResultsJson()),
					parseList(m.stepsJson()),
					m.inputTokens(), m.outputTokens(),
					m.cacheCreationTokens(), m.cacheReadTokens(),
					m.costUsd(), m.createdAt()
			));
		}
		return ResponseEntity.ok(new SessionDetail(toSummary(opt.get()), dtos));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") String id) {
		int deleted = history.deleteSession(id);
		return deleted > 0 ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}

	private static SessionSummary toSummary(ChatSessionRow r) {
		return new SessionSummary(
				r.id(), r.title(), r.model(),
				r.createdAt(), r.updatedAt(),
				r.totalInputTokens(), r.totalOutputTokens(),
				r.totalCacheCreationTokens(), r.totalCacheReadTokens(),
				r.totalCostUsd()
		);
	}

	private List<Map<String, Object>> parseList(String json) {
		if (json == null || json.isBlank()) return null;
		try {
			return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
		} catch (IOException e) {
			log.warn("JSON parse failed: {}", e.getMessage());
			return null;
		}
	}

	public record SessionSummary(
			String id, String title, String model,
			Instant createdAt, Instant updatedAt,
			long totalInputTokens, long totalOutputTokens,
			long totalCacheCreationTokens, long totalCacheReadTokens,
			BigDecimal totalCostUsd) {}

	public record SessionMessage(
			String role,
			String content,
			List<Map<String, Object>> attachments,
			List<Map<String, Object>> queryResults,
			List<Map<String, Object>> steps,
			Integer inputTokens, Integer outputTokens,
			Integer cacheCreationTokens, Integer cacheReadTokens,
			BigDecimal costUsd,
			Instant createdAt) {}

	public record SessionDetail(SessionSummary session, List<SessionMessage> messages) {}
}

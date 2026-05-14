package blusailtechnologies.guido.ticket.ai.progress;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolEvent(
		String type,
		String tool,
		String summary,
		String detail,
		Instant timestamp,
		Usage usage
) {

	public ToolEvent(String type, String tool, String summary, String detail, Instant timestamp) {
		this(type, tool, summary, detail, timestamp, null);
	}

	public static ToolEvent sessionStarted(String sessionId) {
		return new ToolEvent("session_started", null, sessionId, null, Instant.now());
	}

	public static ToolEvent started(String tool, String summary) {
		return new ToolEvent("tool_started", tool, summary, null, Instant.now());
	}

	public static ToolEvent finished(String tool, String summary) {
		return new ToolEvent("tool_finished", tool, summary, null, Instant.now());
	}

	public static ToolEvent error(String tool, String message) {
		return new ToolEvent("tool_error", tool, message, null, Instant.now());
	}

	public static ToolEvent complete(String reply) {
		return new ToolEvent("complete", null, null, reply, Instant.now());
	}

	public static ToolEvent fatal(String message) {
		return new ToolEvent("error", null, message, null, Instant.now());
	}

	public static ToolEvent usage(Usage usage) {
		return new ToolEvent("usage", null, null, null, Instant.now(), usage);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Usage(
			String model,
			Integer inputTokens,
			Integer outputTokens,
			Integer cacheCreationTokens,
			Integer cacheReadTokens,
			BigDecimal costUsd,
			Long sessionTotalInputTokens,
			Long sessionTotalOutputTokens,
			Long sessionTotalCacheCreationTokens,
			Long sessionTotalCacheReadTokens,
			BigDecimal sessionTotalCostUsd
	) {}
}

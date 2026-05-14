package blusailtechnologies.guido.ticket.ai.progress;

import java.time.Instant;

public record ToolEvent(
		String type,
		String tool,
		String summary,
		String detail,
		Instant timestamp
) {

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
}

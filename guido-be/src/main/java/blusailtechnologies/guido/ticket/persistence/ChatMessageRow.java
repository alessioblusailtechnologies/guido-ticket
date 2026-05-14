package blusailtechnologies.guido.ticket.persistence;

import java.math.BigDecimal;
import java.time.Instant;

public record ChatMessageRow(
		Long id,
		String sessionId,
		int ordinal,
		String role,
		String content,
		String attachmentsJson,
		String queryResultsJson,
		String stepsJson,
		Integer inputTokens,
		Integer outputTokens,
		Integer cacheCreationTokens,
		Integer cacheReadTokens,
		BigDecimal costUsd,
		Instant createdAt
) {
}

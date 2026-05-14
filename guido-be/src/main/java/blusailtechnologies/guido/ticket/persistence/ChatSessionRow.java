package blusailtechnologies.guido.ticket.persistence;

import java.math.BigDecimal;
import java.time.Instant;

public record ChatSessionRow(
		String id,
		String title,
		String model,
		Instant createdAt,
		Instant updatedAt,
		long totalInputTokens,
		long totalOutputTokens,
		long totalCacheCreationTokens,
		long totalCacheReadTokens,
		BigDecimal totalCostUsd
) {
}

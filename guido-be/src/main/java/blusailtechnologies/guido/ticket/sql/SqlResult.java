package blusailtechnologies.guido.ticket.sql;

import java.util.List;
import java.util.Map;

public record SqlResult(
		List<String> columns,
		List<Map<String, Object>> rows,
		int rowCount,
		int appliedLimit,
		boolean truncated,
		long elapsedMillis
) {
}

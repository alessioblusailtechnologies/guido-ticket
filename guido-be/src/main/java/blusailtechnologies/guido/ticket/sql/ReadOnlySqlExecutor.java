package blusailtechnologies.guido.ticket.sql;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import blusailtechnologies.guido.ticket.config.SqlToolProperties;

@Component
public class ReadOnlySqlExecutor {

	private final JdbcTemplate jdbc;
	private final SqlToolProperties props;
	private final ReadOnlySqlValidator validator;

	public ReadOnlySqlExecutor(JdbcTemplate jdbc, SqlToolProperties props, ReadOnlySqlValidator validator) {
		this.jdbc = jdbc;
		this.props = props;
		this.validator = validator;
	}

	public SqlResult execute(String sql, Integer requestedLimit) {
		validator.validate(sql);
		int effectiveLimit = clampLimit(requestedLimit);
		String wrapped = wrapWithLimit(sql, effectiveLimit);

		jdbc.setQueryTimeout(props.queryTimeoutSeconds());

		long start = System.currentTimeMillis();
		List<String> columns = new ArrayList<>();
		List<Map<String, Object>> rows = new ArrayList<>();

		jdbc.query(wrapped, rs -> {
			if (columns.isEmpty()) {
				ResultSetMetaData md = rs.getMetaData();
				int cols = md.getColumnCount();
				for (int i = 1; i <= cols; i++) {
					columns.add(md.getColumnLabel(i));
				}
			}
			Map<String, Object> row = new LinkedHashMap<>();
			for (int i = 0; i < columns.size(); i++) {
				row.put(columns.get(i), rs.getObject(i + 1));
			}
			rows.add(row);
		});

		long elapsed = System.currentTimeMillis() - start;
		boolean truncated = rows.size() >= effectiveLimit;
		return new SqlResult(columns, rows, rows.size(), effectiveLimit, truncated, elapsed);
	}

	private int clampLimit(Integer requestedLimit) {
		if (requestedLimit == null || requestedLimit <= 0) {
			return props.defaultLimit();
		}
		return Math.min(requestedLimit, props.maxLimit());
	}

	private String wrapWithLimit(String originalSql, int limit) {
		String trimmed = originalSql.trim();
		if (trimmed.endsWith(";")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
		}
		return "SELECT * FROM (" + trimmed + ") guido_q WHERE ROWNUM <= " + limit;
	}
}

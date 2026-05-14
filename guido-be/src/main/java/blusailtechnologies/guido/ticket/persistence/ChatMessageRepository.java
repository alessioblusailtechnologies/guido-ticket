package blusailtechnologies.guido.ticket.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ChatMessageRepository {

	private final JdbcTemplate jdbc;

	public ChatMessageRepository(@Qualifier("appJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	private static final RowMapper<ChatMessageRow> MAPPER = (ResultSet rs, int n) -> new ChatMessageRow(
			rs.getLong("id"),
			rs.getString("session_id"),
			rs.getInt("ordinal"),
			rs.getString("role"),
			rs.getString("content"),
			rs.getString("attachments_json"),
			rs.getString("query_results_json"),
			rs.getString("steps_json"),
			(Integer) rs.getObject("input_tokens"),
			(Integer) rs.getObject("output_tokens"),
			(Integer) rs.getObject("cache_creation_tokens"),
			(Integer) rs.getObject("cache_read_tokens"),
			rs.getBigDecimal("cost_usd"),
			toInstant(rs.getTimestamp("created_at"))
	);

	public int nextOrdinal(String sessionId) {
		Integer max = jdbc.queryForObject(
				"SELECT COALESCE(MAX(ordinal), -1) FROM chat_message WHERE session_id = ?",
				Integer.class, sessionId);
		return (max == null ? -1 : max) + 1;
	}

	public long insert(
			String sessionId, int ordinal, String role, String content,
			String attachmentsJson, String queryResultsJson, String stepsJson,
			Integer inputTok, Integer outputTok, Integer cacheCreate, Integer cacheRead,
			BigDecimal cost) {
		jdbc.update("""
				INSERT INTO chat_message (
				  session_id, ordinal, role, content,
				  attachments_json, query_results_json, steps_json,
				  input_tokens, output_tokens, cache_creation_tokens, cache_read_tokens, cost_usd
				) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				sessionId, ordinal, role, content,
				attachmentsJson, queryResultsJson, stepsJson,
				inputTok, outputTok, cacheCreate, cacheRead, cost);
		Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
		return id == null ? -1L : id;
	}

	public List<ChatMessageRow> listBySession(String sessionId) {
		return jdbc.query(
				"SELECT * FROM chat_message WHERE session_id = ? ORDER BY ordinal ASC",
				MAPPER, sessionId);
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}
}

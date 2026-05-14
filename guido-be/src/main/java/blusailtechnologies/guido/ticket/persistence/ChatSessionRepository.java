package blusailtechnologies.guido.ticket.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ChatSessionRepository {

	private final JdbcTemplate jdbc;

	public ChatSessionRepository(@Qualifier("appJdbcTemplate") JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	private static final RowMapper<ChatSessionRow> MAPPER = (ResultSet rs, int n) -> new ChatSessionRow(
			rs.getString("id"),
			rs.getString("title"),
			rs.getString("model"),
			toInstant(rs.getTimestamp("created_at")),
			toInstant(rs.getTimestamp("updated_at")),
			rs.getLong("total_input_tokens"),
			rs.getLong("total_output_tokens"),
			rs.getLong("total_cache_creation_tokens"),
			rs.getLong("total_cache_read_tokens"),
			rs.getBigDecimal("total_cost_usd")
	);

	public Optional<ChatSessionRow> findById(String id) {
		List<ChatSessionRow> rows = jdbc.query(
				"SELECT * FROM chat_session WHERE id = ?", MAPPER, id);
		return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
	}

	public List<ChatSessionRow> listRecent(int limit) {
		return jdbc.query(
				"SELECT * FROM chat_session ORDER BY updated_at DESC LIMIT ?",
				MAPPER, limit);
	}

	public void insert(String id, String title, String model) {
		jdbc.update(
				"INSERT INTO chat_session (id, title, model) VALUES (?, ?, ?)",
				id, title, model);
	}

	public void touch(String id) {
		jdbc.update("UPDATE chat_session SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", id);
	}

	public void addUsage(String id, int inputTok, int outputTok, int cacheCreate, int cacheRead, BigDecimal cost) {
		jdbc.update("""
				UPDATE chat_session SET
				  total_input_tokens          = total_input_tokens + ?,
				  total_output_tokens         = total_output_tokens + ?,
				  total_cache_creation_tokens = total_cache_creation_tokens + ?,
				  total_cache_read_tokens     = total_cache_read_tokens + ?,
				  total_cost_usd              = total_cost_usd + ?,
				  updated_at                  = CURRENT_TIMESTAMP
				WHERE id = ?
				""", inputTok, outputTok, cacheCreate, cacheRead, cost, id);
	}

	public int updateTitle(String id, String title) {
		return jdbc.update(
				"UPDATE chat_session SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
				title, id);
	}

	public int delete(String id) {
		return jdbc.update("DELETE FROM chat_session WHERE id = ?", id);
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}
}

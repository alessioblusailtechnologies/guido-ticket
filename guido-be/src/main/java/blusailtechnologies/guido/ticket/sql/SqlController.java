package blusailtechnologies.guido.ticket.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql")
public class SqlController {

	private static final Logger log = LoggerFactory.getLogger(SqlController.class);

	private final ReadOnlySqlExecutor executor;

	public SqlController(ReadOnlySqlExecutor executor) {
		this.executor = executor;
	}

	@PostMapping(
			value = "/execute",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public SqlExecuteResponse execute(@RequestBody ExecuteRequest req) {
		log.info("SQL execute · session={} · sql={}",
				req.sessionId(),
				abbreviate(req.sql(), 300));
		try {
			SqlResult result = executor.execute(req.sql(), req.limit());
			log.info("SQL done · session={} · rows={} · truncated={} · {}ms",
					req.sessionId(), result.rowCount(), result.truncated(), result.elapsedMillis());
			return SqlExecuteResponse.ok(result);
		} catch (RuntimeException e) {
			String message = rootMessage(e);
			log.warn("SQL execute failed · session={} · error={}", req.sessionId(), message);
			return SqlExecuteResponse.failed(message);
		}
	}

	private static String rootMessage(Throwable t) {
		Throwable cur = t;
		while (cur.getCause() != null && cur.getCause() != cur) {
			cur = cur.getCause();
		}
		String msg = cur.getMessage();
		if (msg == null || msg.isBlank()) {
			msg = cur.getClass().getSimpleName();
		}
		return msg;
	}

	private static String abbreviate(String s, int max) {
		if (s == null) return "";
		String single = s.replaceAll("\\s+", " ").trim();
		return single.length() <= max ? single : single.substring(0, max) + "…";
	}

	public record ExecuteRequest(String sessionId, String sql, Integer limit) {}
}

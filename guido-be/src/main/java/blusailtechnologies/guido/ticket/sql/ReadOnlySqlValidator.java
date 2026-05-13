package blusailtechnologies.guido.ticket.sql;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ReadOnlySqlValidator {

	private static final Pattern STARTS_WITH_SELECT_OR_WITH =
			Pattern.compile("^\\s*(SELECT|WITH)\\b", Pattern.CASE_INSENSITIVE);

	private static final List<Pattern> FORBIDDEN_KEYWORDS = List.of(
			Pattern.compile("(?i)\\bINSERT\\b"),
			Pattern.compile("(?i)\\bUPDATE\\b"),
			Pattern.compile("(?i)\\bDELETE\\b"),
			Pattern.compile("(?i)\\bMERGE\\b"),
			Pattern.compile("(?i)\\bTRUNCATE\\b"),
			Pattern.compile("(?i)\\bDROP\\b"),
			Pattern.compile("(?i)\\bALTER\\b"),
			Pattern.compile("(?i)\\bCREATE\\b"),
			Pattern.compile("(?i)\\bGRANT\\b"),
			Pattern.compile("(?i)\\bREVOKE\\b"),
			Pattern.compile("(?i)\\bEXEC(UTE)?\\b"),
			Pattern.compile("(?i)\\bCALL\\b"),
			Pattern.compile("(?i)\\bBEGIN\\b"),
			Pattern.compile("(?i)\\bCOMMIT\\b"),
			Pattern.compile("(?i)\\bROLLBACK\\b"),
			Pattern.compile("(?i)\\bSAVEPOINT\\b"),
			Pattern.compile("(?i)\\bFOR\\s+UPDATE\\b"),
			Pattern.compile("(?i)\\bINTO\\b")
	);

	public void validate(String sql) {
		if (sql == null || sql.isBlank()) {
			throw new IllegalArgumentException("Query SQL vuota.");
		}
		String stripped = stripCommentsAndLiterals(sql).trim();
		if (stripped.endsWith(";")) {
			stripped = stripped.substring(0, stripped.length() - 1).trim();
		}
		if (stripped.contains(";")) {
			throw new IllegalArgumentException("Sono permesse solo query a singolo statement: niente ';' interni.");
		}
		if (!STARTS_WITH_SELECT_OR_WITH.matcher(stripped).find()) {
			throw new IllegalArgumentException("La query deve iniziare con SELECT o WITH.");
		}
		for (Pattern forbidden : FORBIDDEN_KEYWORDS) {
			if (forbidden.matcher(stripped).find()) {
				throw new IllegalArgumentException(
						"Query rifiutata: contiene la parola chiave non consentita "
								+ forbidden.pattern().replace("(?i)", "").replace("\\b", ""));
			}
		}
	}

	private String stripCommentsAndLiterals(String sql) {
		String noBlockComments = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
		String noLineComments = noBlockComments.replaceAll("--[^\\n]*", " ");
		return noLineComments.replaceAll("'([^']|'')*'", "''");
	}
}

package blusailtechnologies.guido.ticket.tools;

import org.springframework.ai.chat.model.ToolContext;

final class Tools {

	private Tools() {}

	static String sessionId(ToolContext ctx) {
		if (ctx == null || ctx.getContext() == null) {
			return null;
		}
		Object v = ctx.getContext().get("sessionId");
		return v == null ? null : v.toString();
	}

	static String brief(String s, int max) {
		if (s == null) return "";
		String single = s.replaceAll("\\s+", " ").trim();
		return single.length() > max ? single.substring(0, max) + "…" : single;
	}

	static String capContent(String content, int maxChars, String label) {
		if (content == null || content.length() <= maxChars) {
			return content;
		}
		int kept = maxChars;
		int total = content.length();
		String note = "\n\n... [TRONCATO: mostrati primi " + kept + " caratteri su " + total
				+ ". Se serve il resto " + (label == null ? "" : "di " + label + " ")
				+ "raffina la ricerca o chiedi la parte mancante esplicitamente.]";
		return content.substring(0, maxChars) + note;
	}
}

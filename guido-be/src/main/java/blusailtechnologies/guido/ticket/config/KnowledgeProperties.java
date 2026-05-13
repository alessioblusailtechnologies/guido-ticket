package blusailtechnologies.guido.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "guido.knowledge")
public record KnowledgeProperties(
		String tablesDir,
		String proceduresDir,
		String vectorStoreDir,
		int topK
) {
}

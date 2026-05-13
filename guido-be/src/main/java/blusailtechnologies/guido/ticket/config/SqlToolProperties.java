package blusailtechnologies.guido.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "guido.sql")
public record SqlToolProperties(
		int defaultLimit,
		int maxLimit,
		int queryTimeoutSeconds
) {
}

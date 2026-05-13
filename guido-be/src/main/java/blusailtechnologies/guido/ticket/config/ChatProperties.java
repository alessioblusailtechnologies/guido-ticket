package blusailtechnologies.guido.ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "guido.chat")
public record ChatProperties(
		int historyWindow
) {
}

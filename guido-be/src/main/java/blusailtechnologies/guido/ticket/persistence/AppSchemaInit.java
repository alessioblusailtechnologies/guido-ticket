package blusailtechnologies.guido.ticket.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AppSchemaInit {

	private static final Logger log = LoggerFactory.getLogger(AppSchemaInit.class);

	@Bean
	ApplicationRunner initAppSchema(
			@Qualifier("appJdbcTemplate") JdbcTemplate appJdbc,
			@org.springframework.beans.factory.annotation.Value("classpath:schema-app.sql") Resource schema) {
		return args -> {
			String sql;
			try {
				sql = schema.getContentAsString(StandardCharsets.UTF_8);
			} catch (IOException e) {
				log.error("Impossibile leggere schema-app.sql", e);
				return;
			}
			for (String stmt : sql.split(";\\s*\\n")) {
				String trimmed = stmt.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("--")) {
					continue;
				}
				try {
					appJdbc.execute(trimmed);
				} catch (Exception e) {
					log.warn("Statement schema fallito (forse già applicato): {}", e.getMessage());
				}
			}
			log.info("Schema app DB inizializzato");
		};
	}
}

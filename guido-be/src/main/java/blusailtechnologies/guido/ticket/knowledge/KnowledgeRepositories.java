package blusailtechnologies.guido.ticket.knowledge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import blusailtechnologies.guido.ticket.config.KnowledgeProperties;

@Configuration
public class KnowledgeRepositories {

	@Bean(name = "tablesRepository")
	SqlFileRepository tablesRepository(KnowledgeProperties props) {
		return new SqlFileRepository(props.tablesDir());
	}

	@Bean(name = "proceduresRepository")
	SqlFileRepository proceduresRepository(KnowledgeProperties props) {
		return new SqlFileRepository(props.proceduresDir());
	}
}

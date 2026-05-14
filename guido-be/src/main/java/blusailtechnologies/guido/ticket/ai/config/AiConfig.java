package blusailtechnologies.guido.ticket.ai.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import blusailtechnologies.guido.ticket.config.ChatProperties;
import blusailtechnologies.guido.ticket.config.KnowledgeProperties;
import blusailtechnologies.guido.ticket.tools.ProcedureTools;
import blusailtechnologies.guido.ticket.tools.SchemaTools;
import blusailtechnologies.guido.ticket.tools.SourceTools;

@Configuration
public class AiConfig {

	private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

	@Bean
	ChatMemoryRepository chatMemoryRepository() {
		return new InMemoryChatMemoryRepository();
	}

	@Bean
	ChatMemory chatMemory(ChatMemoryRepository repository, ChatProperties props) {
		return MessageWindowChatMemory.builder()
				.chatMemoryRepository(repository)
				.maxMessages(props.historyWindow())
				.build();
	}

	@Bean
	ChatClient chatClient(
			ChatClient.Builder builder,
			ChatMemory chatMemory,
			@Value("classpath:prompts/system.md") Resource systemPromptTemplate,
			KnowledgeProperties knowledgeProps,
			SchemaTools schemaTools,
			ProcedureTools procedureTools,
			SourceTools sourceTools) throws IOException {

		String template = systemPromptTemplate.getContentAsString(StandardCharsets.UTF_8);
		String projectMap = loadProjectMap(knowledgeProps);
		String systemPrompt = template.replace("{{PROJECT_MAP}}", projectMap);

		return builder
				.defaultSystem(systemPrompt)
				.defaultTools(schemaTools, procedureTools, sourceTools)
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
	}

	private String loadProjectMap(KnowledgeProperties props) {
		String filePath = props.projectMapFile();
		if (filePath == null || filePath.isBlank()) {
			return "(mappa progetti non configurata)";
		}
		Path p = Paths.get(filePath);
		if (!Files.isRegularFile(p)) {
			log.warn("Project map file non trovato: {}", p);
			return "(mappa progetti non trovata sul filesystem)";
		}
		try {
			String content = Files.readString(p, StandardCharsets.UTF_8);
			log.info("Caricata mappa progetti da {} ({} char)", p, content.length());
			return content;
		} catch (IOException e) {
			log.warn("Errore caricando project map {}: {}", p, e.getMessage());
			return "(errore lettura mappa progetti)";
		}
	}
}

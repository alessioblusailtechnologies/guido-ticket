package blusailtechnologies.guido.ticket.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import blusailtechnologies.guido.ticket.config.ChatProperties;
import blusailtechnologies.guido.ticket.tools.ProcedureTools;
import blusailtechnologies.guido.ticket.tools.SchemaTools;
import blusailtechnologies.guido.ticket.tools.SqlQueryTools;

@Configuration
public class AiConfig {

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
			@org.springframework.beans.factory.annotation.Value("classpath:prompts/system.md")
			Resource systemPrompt,
			SqlQueryTools sqlQueryTools,
			SchemaTools schemaTools,
			ProcedureTools procedureTools) {
		return builder
				.defaultSystem(systemPrompt)
				.defaultTools(sqlQueryTools, schemaTools, procedureTools)
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
	}
}

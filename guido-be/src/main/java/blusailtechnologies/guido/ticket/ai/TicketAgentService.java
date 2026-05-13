package blusailtechnologies.guido.ticket.ai;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import blusailtechnologies.guido.ticket.attachment.AttachmentContent;
import blusailtechnologies.guido.ticket.attachment.AttachmentExtractor;

@Service
public class TicketAgentService {

	private final ChatClient chatClient;
	private final AttachmentExtractor attachmentExtractor;

	public TicketAgentService(ChatClient chatClient, AttachmentExtractor attachmentExtractor) {
		this.chatClient = chatClient;
		this.attachmentExtractor = attachmentExtractor;
	}

	public String chat(String sessionId, String message, List<MultipartFile> attachments) throws IOException {
		StringBuilder userText = new StringBuilder();
		if (message != null && !message.isBlank()) {
			userText.append(message);
		}

		List<Media> mediaList = new ArrayList<>();
		if (attachments != null) {
			for (MultipartFile file : attachments) {
				if (file == null || file.isEmpty()) {
					continue;
				}
				AttachmentContent content = attachmentExtractor.extract(file);
				if (content.isImage()) {
					mediaList.add(Media.builder()
							.mimeType(content.mimeType())
							.data(new ByteArrayResource(content.imageBytes()))
							.build());
				} else if (content.hasText()) {
					userText.append("\n\n--- Allegato: ").append(content.filename()).append(" ---\n");
					userText.append(content.extractedText());
				} else {
					userText.append("\n\n[Allegato ").append(content.filename())
							.append(" ricevuto ma senza testo estraibile]");
				}
			}
		}

		return chatClient.prompt()
				.user(u -> {
					u.text(userText.toString());
					mediaList.forEach(u::media);
				})
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
				.call()
				.content();
	}
}

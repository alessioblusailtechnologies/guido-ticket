package blusailtechnologies.guido.ticket.ai;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final TicketAgentService agent;

	public ChatController(TicketAgentService agent) {
		this.agent = agent;
	}

	@PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE })
	public ChatResponse chat(
			@RequestParam(value = "sessionId", required = false) String sessionId,
			@RequestParam("message") String message,
			@RequestParam(value = "attachments", required = false) List<MultipartFile> attachments)
			throws IOException {
		String effectiveSession = (sessionId == null || sessionId.isBlank())
				? UUID.randomUUID().toString()
				: sessionId;
		String reply = agent.chat(effectiveSession, message, attachments);
		return new ChatResponse(effectiveSession, reply);
	}

	public record ChatResponse(String sessionId, String reply) {
	}
}

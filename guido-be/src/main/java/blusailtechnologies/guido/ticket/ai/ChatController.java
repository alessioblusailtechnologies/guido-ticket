package blusailtechnologies.guido.ticket.ai;

import java.util.List;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final TicketAgentService agent;

	public ChatController(TicketAgentService agent) {
		this.agent = agent;
	}

	@PostMapping(
			consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE },
			produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter chat(
			@RequestParam(value = "sessionId", required = false) String sessionId,
			@RequestParam("message") String message,
			@RequestParam(value = "attachments", required = false) List<MultipartFile> attachments,
			@RequestParam(value = "queryResults", required = false) String queryResultsJson) {
		String effectiveSession = (sessionId == null || sessionId.isBlank())
				? UUID.randomUUID().toString()
				: sessionId;
		return agent.chatStream(effectiveSession, message, attachments, queryResultsJson);
	}
}

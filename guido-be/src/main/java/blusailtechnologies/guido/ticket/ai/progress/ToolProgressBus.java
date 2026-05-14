package blusailtechnologies.guido.ticket.ai.progress;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ToolProgressBus {

	private static final Logger log = LoggerFactory.getLogger(ToolProgressBus.class);
	private static final long DEFAULT_TIMEOUT_MS = 15L * 60_000L;

	private final ConcurrentMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

	public SseEmitter register(String sessionId) {
		SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
		emitters.put(sessionId, emitter);
		emitter.onCompletion(() -> emitters.remove(sessionId));
		emitter.onTimeout(() -> {
			emitters.remove(sessionId);
			emitter.complete();
		});
		emitter.onError(t -> emitters.remove(sessionId));
		return emitter;
	}

	public void publish(String sessionId, ToolEvent event) {
		if (sessionId == null) {
			return;
		}
		SseEmitter emitter = emitters.get(sessionId);
		if (emitter == null) {
			return;
		}
		try {
			emitter.send(SseEmitter.event().name(event.type()).data(event));
		} catch (IOException | IllegalStateException e) {
			log.warn("SSE publish failed for session {} (likely client disconnect or emitter timeout): {}",
					sessionId, e.getMessage());
			emitters.remove(sessionId);
		}
	}

	public void complete(String sessionId) {
		SseEmitter emitter = emitters.remove(sessionId);
		if (emitter != null) {
			emitter.complete();
		}
	}

	public void completeWithError(String sessionId, Throwable t) {
		SseEmitter emitter = emitters.remove(sessionId);
		if (emitter != null) {
			emitter.completeWithError(t);
		}
	}
}

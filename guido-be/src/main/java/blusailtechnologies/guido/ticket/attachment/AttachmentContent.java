package blusailtechnologies.guido.ticket.attachment;

import org.springframework.util.MimeType;

public record AttachmentContent(
		String filename,
		MimeType mimeType,
		String extractedText,
		boolean truncated,
		byte[] imageBytes
) {

	public boolean isImage() {
		return imageBytes != null;
	}

	public boolean hasText() {
		return extractedText != null && !extractedText.isBlank();
	}
}

package blusailtechnologies.guido.ticket.attachment;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

@Component
public class AttachmentExtractor {

	private final int maxTextChars;

	public AttachmentExtractor(@Value("${guido.attachment.max-text-chars:500000}") int maxTextChars) {
		this.maxTextChars = maxTextChars;
	}

	public AttachmentContent extract(MultipartFile file) throws IOException {
		String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
		MimeType mime;
		try {
			mime = MimeTypeUtils.parseMimeType(contentType);
		} catch (Exception e) {
			mime = MimeTypeUtils.APPLICATION_OCTET_STREAM;
		}

		if ("image".equalsIgnoreCase(mime.getType())) {
			return new AttachmentContent(file.getOriginalFilename(), mime, null, false, file.getBytes());
		}

		WriteOutContentHandler tikaHandler = new WriteOutContentHandler(maxTextChars);
		boolean truncated = false;

		try (InputStream in = file.getInputStream()) {
			Metadata metadata = new Metadata();
			new AutoDetectParser().parse(in, new BodyContentHandler(tikaHandler), metadata, new ParseContext());
		} catch (SAXException e) {
			if (isWriteLimitCause(e)) {
				truncated = true;
			} else {
				throw new IOException("Tika extraction failed for " + file.getOriginalFilename(), e);
			}
		} catch (TikaException e) {
			throw new IOException("Tika extraction failed for " + file.getOriginalFilename(), e);
		}

		return new AttachmentContent(file.getOriginalFilename(), mime, tikaHandler.toString(), truncated, null);
	}

	private static boolean isWriteLimitCause(Throwable t) {
		Throwable cur = t;
		while (cur != null) {
			if (cur instanceof WriteLimitReachedException) {
				return true;
			}
			if (cur.getMessage() != null && cur.getMessage().toLowerCase().contains("write limit")) {
				return true;
			}
			cur = cur.getCause();
		}
		return false;
	}
}

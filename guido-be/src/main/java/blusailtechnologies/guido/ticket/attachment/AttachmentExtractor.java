package blusailtechnologies.guido.ticket.attachment;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

@Component
public class AttachmentExtractor {

	private static final int MAX_TEXT_CHARS = 200_000;

	public AttachmentContent extract(MultipartFile file) throws IOException {
		String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
		MimeType mime;
		try {
			mime = MimeTypeUtils.parseMimeType(contentType);
		} catch (Exception e) {
			mime = MimeTypeUtils.APPLICATION_OCTET_STREAM;
		}

		if ("image".equalsIgnoreCase(mime.getType())) {
			return new AttachmentContent(file.getOriginalFilename(), mime, null, file.getBytes());
		}

		String text;
		try (InputStream in = file.getInputStream()) {
			BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_CHARS);
			Metadata metadata = new Metadata();
			new AutoDetectParser().parse(in, handler, metadata, new ParseContext());
			text = handler.toString();
		} catch (TikaException | SAXException e) {
			throw new IOException("Tika extraction failed for " + file.getOriginalFilename(), e);
		}
		return new AttachmentContent(file.getOriginalFilename(), mime, text, null);
	}
}

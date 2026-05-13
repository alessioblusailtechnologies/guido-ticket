package blusailtechnologies.guido.ticket.knowledge;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import blusailtechnologies.guido.ticket.config.KnowledgeProperties;

@Configuration
public class KnowledgeBootstrap {

	private static final Logger log = LoggerFactory.getLogger(KnowledgeBootstrap.class);

	@Bean
	ApplicationRunner indexKnowledge(
			KnowledgeProperties props,
			@Qualifier("tablesRepository") SqlFileRepository tables,
			@Qualifier("proceduresRepository") SqlFileRepository procedures,
			@Qualifier("tablesVectorStore") VectorStore tablesVectorStore,
			@Qualifier("proceduresVectorStore") VectorStore proceduresVectorStore) {
		return args -> {
			Path baseDir = Paths.get(props.vectorStoreDir());
			Files.createDirectories(baseDir);
			loadOrBuild("tables", tables, tablesVectorStore, baseDir.resolve("tables.json").toFile());
			loadOrBuild("procedures", procedures, proceduresVectorStore, baseDir.resolve("procedures.json").toFile());
		};
	}

	private void loadOrBuild(String label, SqlFileRepository repo, VectorStore store, File persistFile)
			throws IOException {
		if (!(store instanceof SimpleVectorStore simple)) {
			log.warn("[{}] VectorStore non è un SimpleVectorStore, niente persistenza", label);
			return;
		}
		if (persistFile.isFile()) {
			log.info("[{}] Carico vector store da {}", label, persistFile);
			simple.load(persistFile);
			return;
		}
		log.info("[{}] Indicizzo i file da {}", label, repo.baseDir());
		List<SqlFile> all = repo.readAll();
		List<Document> docs = new ArrayList<>(all.size());
		for (SqlFile f : all) {
			Map<String, Object> metadata = new HashMap<>();
			metadata.put("name", f.name());
			metadata.put("source", f.path().toString());
			docs.add(new Document(f.content(), metadata));
		}
		TokenTextSplitter splitter = TokenTextSplitter.builder()
				.withChunkSize(7000)
				.withMinChunkSizeChars(400)
				.withMaxNumChunks(50000)
				.withKeepSeparator(true)
				.build();
		List<Document> chunks = docs.isEmpty() ? List.of() : splitter.apply(docs);
		if (!chunks.isEmpty()) {
			simple.add(chunks);
		}
		simple.save(persistFile);
		log.info("[{}] Indicizzati {} file in {} chunk, salvati su {}",
				label, docs.size(), chunks.size(), persistFile);
	}
}

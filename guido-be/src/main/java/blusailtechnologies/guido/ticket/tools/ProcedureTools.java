package blusailtechnologies.guido.ticket.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import blusailtechnologies.guido.ticket.config.KnowledgeProperties;
import blusailtechnologies.guido.ticket.knowledge.SqlFile;
import blusailtechnologies.guido.ticket.knowledge.SqlFileRepository;

@Component
public class ProcedureTools {

	private final SqlFileRepository procedures;
	private final VectorStore proceduresVectorStore;
	private final KnowledgeProperties props;

	public ProcedureTools(
			@Qualifier("proceduresRepository") SqlFileRepository procedures,
			@Qualifier("proceduresVectorStore") VectorStore proceduresVectorStore,
			KnowledgeProperties props) {
		this.procedures = procedures;
		this.proceduresVectorStore = proceduresVectorStore;
		this.props = props;
	}

	@Tool(
			name = "findProcedures",
			description = """
					Ricerca semantica sulle stored procedure / package PL/SQL Veolia. Passa
					una query in linguaggio naturale (es. 'come si calcola lo sbilanciamento
					mensile gas') e ottieni i nomi delle procedure più rilevanti con un
					estratto del codice. Per il codice completo usa getProcedureSource.
					""")
	public List<ProcedureHit> findProcedures(
			@ToolParam(description = "Query in linguaggio naturale che descrive cosa fa la procedura cercata.")
			String query) {
		SearchRequest request = SearchRequest.builder()
				.query(query)
				.topK(props.topK() * 3)
				.build();
		List<Document> docs = proceduresVectorStore.similaritySearch(request);
		Map<String, ProcedureHit> byName = new LinkedHashMap<>();
		for (Document d : docs) {
			String name = String.valueOf(d.getMetadata().getOrDefault("name", "?"));
			byName.putIfAbsent(name, new ProcedureHit(name, trim(d.getText(), 400)));
			if (byName.size() >= props.topK()) {
				break;
			}
		}
		return List.copyOf(byName.values());
	}

	@Tool(
			name = "getProcedureSource",
			description = """
					Restituisce il codice sorgente completo (CREATE OR REPLACE ...) della
					procedura/package specificato. Usa il nome esatto (case-insensitive).
					Se non trovata ritorna messaggio di errore.
					""")
	public String getProcedureSource(
			@ToolParam(description = "Nome esatto della procedura (case-insensitive). Es: 'CALCOLO_PUN', 'AGGIORNA_FORNITORI'.")
			String name) {
		Optional<SqlFile> file = procedures.read(name);
		return file.map(SqlFile::content)
				.orElseGet(() -> "Procedura '" + name + "' non trovata. "
						+ "Prova findProcedures con una query in linguaggio naturale.");
	}

	private static String trim(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max) + "…";
	}

	public record ProcedureHit(String name, String excerpt) {
	}
}

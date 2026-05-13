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
public class SchemaTools {

	private final SqlFileRepository tables;
	private final VectorStore tablesVectorStore;
	private final KnowledgeProperties props;

	public SchemaTools(
			@Qualifier("tablesRepository") SqlFileRepository tables,
			@Qualifier("tablesVectorStore") VectorStore tablesVectorStore,
			KnowledgeProperties props) {
		this.tables = tables;
		this.tablesVectorStore = tablesVectorStore;
		this.props = props;
	}

	@Tool(
			name = "listTables",
			description = """
					Elenca i nomi delle tabelle disponibili (DDL esportati). Accetta un filtro
					opzionale che fa match parziale case-insensitive sul nome (utile per pattern
					tipo 'FATTURA' o 'T1_'). Usa describeTable per ottenere il DDL completo di
					una specifica tabella.
					""")
	public List<String> listTables(
			@ToolParam(required = false,
					description = "Sottostringa per filtrare i nomi tabella. Lascia vuoto per elencarle tutte.")
			String nameFilter) {
		return tables.listNames(nameFilter);
	}

	@Tool(
			name = "describeTable",
			description = """
					Restituisce il DDL completo (CREATE TABLE + constraint + index + commenti)
					della tabella specificata, leggendo il file esportato. Usa il nome esatto
					(case-insensitive). Se la tabella non è trovata ritorna una stringa con
					messaggio di errore.
					""")
	public String describeTable(
			@ToolParam(description = "Nome esatto della tabella (case-insensitive). Es: 'FATTURA', 'T1_PRE_FATTURA_HEADER'.")
			String name) {
		Optional<SqlFile> file = tables.read(name);
		return file.map(SqlFile::content)
				.orElseGet(() -> "Tabella '" + name + "' non trovata nella knowledge base. "
						+ "Prova listTables o findTables per individuare il nome corretto.");
	}

	@Tool(
			name = "findTables",
			description = """
					Ricerca semantica sulle tabelle. Passa una query in linguaggio naturale
					(es. 'dove sono memorizzate le fatture di vendita gas') e ottieni i nomi
					delle tabelle più rilevanti con un breve estratto del DDL. Usa describeTable
					per ottenere poi il DDL completo della tabella più promettente.
					""")
	public List<TableHit> findTables(
			@ToolParam(description = "Query in linguaggio naturale che descrive il dato cercato.")
			String query) {
		SearchRequest request = SearchRequest.builder()
				.query(query)
				.topK(props.topK() * 3)
				.build();
		List<Document> docs = tablesVectorStore.similaritySearch(request);
		Map<String, TableHit> byName = new LinkedHashMap<>();
		for (Document d : docs) {
			String name = String.valueOf(d.getMetadata().getOrDefault("name", "?"));
			byName.putIfAbsent(name, new TableHit(name, trim(d.getText(), 400)));
			if (byName.size() >= props.topK()) {
				break;
			}
		}
		return List.copyOf(byName.values());
	}

	private static String trim(String s, int max) {
		if (s == null) {
			return "";
		}
		return s.length() <= max ? s : s.substring(0, max) + "…";
	}

	public record TableHit(String name, String excerpt) {
	}
}

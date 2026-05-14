package blusailtechnologies.guido.ticket.tools;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import blusailtechnologies.guido.ticket.ai.progress.ToolEvent;
import blusailtechnologies.guido.ticket.ai.progress.ToolProgressBus;
import blusailtechnologies.guido.ticket.config.KnowledgeProperties;
import blusailtechnologies.guido.ticket.knowledge.SqlFile;
import blusailtechnologies.guido.ticket.knowledge.SqlFileRepository;

@Component
public class SchemaTools {

	private final SqlFileRepository tables;
	private final KnowledgeProperties props;
	private final ToolProgressBus bus;

	public SchemaTools(
			@Qualifier("tablesRepository") SqlFileRepository tables,
			KnowledgeProperties props,
			ToolProgressBus bus) {
		this.tables = tables;
		this.props = props;
		this.bus = bus;
	}

	@Tool(
			name = "listTables",
			description = """
					Elenca i nomi delle tabelle (e viste) disponibili: i DDL sono esportati da DataGrip
					e contengono CREATE TABLE/VIEW + constraint + indici + commenti. Accetta un filtro
					opzionale che fa match parziale CASE-INSENSITIVE sul nome (es. 'FATTURA', 'T1_', 'ECM').
					Usa describeTable per leggere il DDL completo di una tabella/vista.
					""")
	public List<String> listTables(
			@ToolParam(required = false,
					description = "Sottostringa per filtrare i nomi tabella/vista. Lascia vuoto per elencarle tutte.")
			String nameFilter,
			ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("listTables",
				nameFilter == null || nameFilter.isBlank()
						? "Elenco tutte le tabelle/viste"
						: "Cerco tabelle/viste che contengono \"" + nameFilter + "\""));
		try {
			List<String> result = tables.listNames(nameFilter);
			bus.publish(sid, ToolEvent.finished("listTables", "Trovate " + result.size() + " tabelle/viste"));
			return result;
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("listTables", e.getMessage()));
			throw e;
		}
	}

	@Tool(
			name = "describeTable",
			description = """
					Restituisce il DDL completo (CREATE TABLE/VIEW + constraint + index + commenti)
					dell'oggetto specificato, leggendo il file esportato. Usa il nome esatto
					(case-insensitive). Se non è trovato ritorna messaggio di errore.
					""")
	public String describeTable(
			@ToolParam(description = "Nome esatto della tabella/vista (case-insensitive). Es: 'FATTURA', 'T1_PRE_FATTURA_HEADER'.")
			String name,
			ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("describeTable", "Leggo il DDL di " + name));
		try {
			Optional<SqlFile> file = tables.read(name);
			if (file.isPresent()) {
				String content = Tools.capContent(file.get().content(), props.maxContentChars(), file.get().name());
				bus.publish(sid, ToolEvent.finished("describeTable",
						"DDL recuperato per " + file.get().name() + " · " + content.length() + " char"));
				return content;
			}
			bus.publish(sid, ToolEvent.finished("describeTable", name + " non trovata"));
			return "Tabella/vista '" + name + "' non trovata nella knowledge base. "
					+ "Prova listTables o searchInTables per individuare il nome corretto.";
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("describeTable", e.getMessage()));
			throw e;
		}
	}

	@Tool(
			name = "searchInTables",
			description = """
					Cerca un pattern (case-insensitive) DENTRO i DDL di TUTTE le tabelle/viste:
					utile per trovare le tabelle che contengono una colonna, un commento, una FK
					o un riferimento specifico. Ritorna fino a N risultati con nome tabella + snippet
					di contesto attorno al match. Esempi di pattern utili: 'CODICE_POD',
					'COD_FORNITORE', 'CHECK_MATERIA_PRIMA', 'EDISON'.
					Per il DDL completo poi usa describeTable.
					""")
	public List<SqlFileRepository.ContentHit> searchInTables(
			@ToolParam(description = "Pattern testuale da cercare nel contenuto dei DDL (case-insensitive).")
			String pattern,
			ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("searchInTables",
				"Cerco \"" + Tools.brief(pattern, 60) + "\" nei DDL"));
		try {
			List<SqlFileRepository.ContentHit> hits = tables.searchByContent(
					pattern, props.searchMaxHits(), props.searchSnippetChars());
			bus.publish(sid, ToolEvent.finished("searchInTables",
					hits.size() + " match"
							+ (hits.size() == props.searchMaxHits() ? " (limite raggiunto)" : "")));
			return hits;
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("searchInTables", e.getMessage()));
			throw e;
		}
	}
}

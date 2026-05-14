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
public class ProcedureTools {

	private final SqlFileRepository procedures;
	private final KnowledgeProperties props;
	private final ToolProgressBus bus;

	public ProcedureTools(
			@Qualifier("proceduresRepository") SqlFileRepository procedures,
			KnowledgeProperties props,
			ToolProgressBus bus) {
		this.procedures = procedures;
		this.props = props;
		this.bus = bus;
	}

	@Tool(
			name = "listProcedures",
			description = """
					Elenca i nomi delle procedure / package PL/SQL Oracle disponibili. Accetta un
					filtro opzionale che fa match parziale CASE-INSENSITIVE (es. 'CALCOLO', 'AGGIORNA',
					'ECM_AUTH'). Usa getProcedureSource per leggere il codice completo.
					""")
	public List<String> listProcedures(
			@ToolParam(required = false,
					description = "Sottostringa per filtrare i nomi delle procedure. Lascia vuoto per elencarle tutte.")
			String nameFilter,
			ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("listProcedures",
				nameFilter == null || nameFilter.isBlank()
						? "Elenco tutte le procedure"
						: "Cerco procedure che contengono \"" + nameFilter + "\""));
		try {
			List<String> result = procedures.listNames(nameFilter);
			bus.publish(sid, ToolEvent.finished("listProcedures",
					"Trovate " + result.size() + " procedure"));
			return result;
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("listProcedures", e.getMessage()));
			throw e;
		}
	}

	@Tool(
			name = "searchInProcedures",
			description = """
					Cerca un pattern (case-insensitive) DENTRO il codice di TUTTE le procedure PL/SQL.
					Utile per trovare le procedure che leggono/scrivono una tabella, che invocano
					una funzione, o che gestiscono un caso specifico. Ritorna fino a N hit con
					nome procedura + snippet di contesto. Pattern utili: 'FATTURA_TESTATA', 'NVL(',
					'BULK COLLECT', 'EXCEPTION WHEN'. Per il sorgente completo usa getProcedureSource.
					""")
	public List<SqlFileRepository.ContentHit> searchInProcedures(
			@ToolParam(description = "Pattern testuale (case-insensitive) da cercare nel codice delle procedure.")
			String pattern,
			ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("searchInProcedures",
				"Cerco \"" + Tools.brief(pattern, 60) + "\" nelle procedure"));
		try {
			List<SqlFileRepository.ContentHit> hits = procedures.searchByContent(
					pattern, props.searchMaxHits(), props.searchSnippetChars());
			bus.publish(sid, ToolEvent.finished("searchInProcedures",
					hits.size() + " match"
							+ (hits.size() == props.searchMaxHits() ? " (limite raggiunto)" : "")));
			return hits;
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("searchInProcedures", e.getMessage()));
			throw e;
		}
	}

	@Tool(
			name = "getProcedureSource",
			description = """
					Restituisce il codice sorgente completo (CREATE OR REPLACE ...) della procedura
					o package specificato. Usa il nome esatto (case-insensitive). Se non trovata
					ritorna messaggio di errore.
					""")
	public String getProcedureSource(
			@ToolParam(description = "Nome esatto della procedura (case-insensitive). Es: 'CALCOLO_PUN', 'AGGIORNA_FORNITORI'.")
			String name,
			ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("getProcedureSource", "Leggo " + name));
		try {
			Optional<SqlFile> file = procedures.read(name);
			if (file.isPresent()) {
				String content = Tools.capContent(file.get().content(), props.maxContentChars(), file.get().name());
				bus.publish(sid, ToolEvent.finished("getProcedureSource",
						"Sorgente recuperato per " + file.get().name() + " · " + content.length() + " char"));
				return content;
			}
			bus.publish(sid, ToolEvent.finished("getProcedureSource",
					name + " non trovata"));
			return "Procedura '" + name + "' non trovata. "
					+ "Prova listProcedures o searchInProcedures.";
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("getProcedureSource", e.getMessage()));
			throw e;
		}
	}
}

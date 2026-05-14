package blusailtechnologies.guido.ticket.tools;

import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import blusailtechnologies.guido.ticket.ai.progress.ToolEvent;
import blusailtechnologies.guido.ticket.ai.progress.ToolProgressBus;
import blusailtechnologies.guido.ticket.config.KnowledgeProperties;
import blusailtechnologies.guido.ticket.knowledge.SourceFile;
import blusailtechnologies.guido.ticket.knowledge.SourceFileRepository;

@Component
public class SourceTools {

	private final SourceFileRepository sources;
	private final KnowledgeProperties props;
	private final ToolProgressBus bus;

	public SourceTools(SourceFileRepository sources, KnowledgeProperties props, ToolProgressBus bus) {
		this.sources = sources;
		this.props = props;
		this.bus = bus;
	}

	@Tool(
			name = "listSourceProjects",
			description = """
					Elenca i progetti applicativi Veolia di cui hai accesso ai sorgenti
					(C#, TypeScript, HTML, config). Esempi tipici: JobRunner, dataloader,
					ecm (frontend Angular), prefattura, ssetl, wcf (servizi WCF), wcfauth.
					Usalo come prima esplorazione quando il ticket cita un modulo applicativo.
					""")
	public List<String> listSourceProjects(ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("listSourceProjects", "Elenco i progetti disponibili"));
		try {
			List<String> result = sources.listProjects();
			bus.publish(sid, ToolEvent.finished("listSourceProjects",
					result.size() + " progetti: " + result));
			return result;
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("listSourceProjects", e.getMessage()));
			throw e;
		}
	}

	@Tool(
			name = "listSourceFiles",
			description = """
					Elenca i file di un progetto applicativo (path relativi al root del progetto).
					Accetta filtro opzionale CASE-INSENSITIVE che fa match parziale sul path
					(es. 'Fattura', '/services/', '.config'). Usa getSourceFile per leggere
					il contenuto di un file specifico.
					""")
	public List<String> listSourceFiles(
			@ToolParam(description = "Nome esatto del progetto. Usa listSourceProjects per vedere i disponibili.")
			String project,
			@ToolParam(required = false, description = "Filtro sul path (sottostringa case-insensitive).")
			String pathFilter,
			ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("listSourceFiles",
				"Elenco file di " + project
						+ (pathFilter != null && !pathFilter.isBlank() ? " · filtro: " + pathFilter : "")));
		try {
			List<String> result = sources.listFiles(project, pathFilter);
			bus.publish(sid, ToolEvent.finished("listSourceFiles", result.size() + " file"));
			return result;
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("listSourceFiles", e.getMessage()));
			throw e;
		}
	}

	@Tool(
			name = "getSourceFile",
			description = """
					Restituisce il contenuto di un file sorgente. Path relativo al root del progetto.
					Es: project='wcf', path='Services/FatturaService.cs'. Se non trovato ritorna errore.
					""")
	public String getSourceFile(
			@ToolParam(description = "Nome del progetto.")
			String project,
			@ToolParam(description = "Path del file relativo al root del progetto (case-insensitive). Es: 'Services/FatturaService.cs'.")
			String path,
			ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("getSourceFile", project + " · " + path));
		try {
			Optional<SourceFile> f = sources.read(project, path);
			if (f.isPresent()) {
				String content = Tools.capContent(f.get().content(), props.maxContentChars(), f.get().relativePath());
				bus.publish(sid, ToolEvent.finished("getSourceFile",
						"Letto " + f.get().relativePath() + " · " + content.length() + " char"));
				return content;
			}
			bus.publish(sid, ToolEvent.finished("getSourceFile", path + " non trovato"));
			return "File '" + path + "' non trovato nel progetto '" + project
					+ "'. Verifica con listSourceFiles.";
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("getSourceFile", e.getMessage()));
			throw e;
		}
	}

	@Tool(
			name = "searchInSources",
			description = """
					Cerca un pattern (case-insensitive) DENTRO il codice di TUTTI i sorgenti
					applicativi Veolia. Utile per trovare riferimenti a una classe, una procedura
					SQL, una rotta API, una colonna DB, una stringa di errore. Puoi limitare
					la ricerca a un singolo progetto passando 'project'. Ritorna fino a N hit
					con (project, path, snippet). Usa poi getSourceFile per leggere il file completo.
					""")
	public List<SourceFileRepository.SourceHit> searchInSources(
			@ToolParam(description = "Pattern testuale (case-insensitive). Es: 'CALCOLO_PUN', 'OracleConnection', '/api/fatture'.")
			String pattern,
			@ToolParam(required = false,
					description = "Limita la ricerca a un solo progetto. Lascia vuoto per cercare ovunque.")
			String project,
			ToolContext context) {
		String sid = Tools.sessionId(context);
		bus.publish(sid, ToolEvent.started("searchInSources",
				"Cerco \"" + Tools.brief(pattern, 60) + "\""
						+ (project != null && !project.isBlank() ? " in " + project : " in tutti i progetti")));
		try {
			List<SourceFileRepository.SourceHit> hits = sources.search(
					pattern, project, props.searchMaxHits(), props.searchSnippetChars());
			bus.publish(sid, ToolEvent.finished("searchInSources",
					hits.size() + " match"
							+ (hits.size() == props.searchMaxHits() ? " (limite raggiunto, raffina il pattern)" : "")));
			return hits;
		} catch (RuntimeException e) {
			bus.publish(sid, ToolEvent.error("searchInSources", e.getMessage()));
			throw e;
		}
	}
}

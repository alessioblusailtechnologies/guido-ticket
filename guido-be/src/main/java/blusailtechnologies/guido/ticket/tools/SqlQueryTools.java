package blusailtechnologies.guido.ticket.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import blusailtechnologies.guido.ticket.sql.ReadOnlySqlExecutor;
import blusailtechnologies.guido.ticket.sql.SqlResult;

@Component
public class SqlQueryTools {

	private final ReadOnlySqlExecutor executor;

	public SqlQueryTools(ReadOnlySqlExecutor executor) {
		this.executor = executor;
	}

	@Tool(
			name = "executeReadOnlySql",
			description = """
					Esegue una query SQL Oracle in modalità STRICT READ-ONLY sul database Veolia.
					VINCOLI:
					 - Sono accettate solo query che iniziano con SELECT o WITH.
					 - Non sono permessi statement multipli (niente ; interni), né DML/DDL,
					   né CALL/EXEC/BEGIN/COMMIT, né FOR UPDATE, né INTO.
					 - La query viene avvolta automaticamente con un filtro ROWNUM per limitare le righe.
					   Default limit = 200, massimo = 1000.
					 - Statement timeout = 30 secondi.
					Usa questo tool ogni volta che ti serve leggere dati dal DB.
					Se non sei sicuro della struttura di una tabella, prima chiama describeTable
					oppure findTables per individuare quella giusta.
					Ritorna columns + rows (max 'limit') + flag 'truncated' se sono state tagliate.
					""")
	public SqlResult executeReadOnlySql(
			@ToolParam(description = "Query SQL Oracle. Solo SELECT o WITH. Singolo statement, niente ';' a parte fine.")
			String sql,
			@ToolParam(required = false,
					description = "Numero massimo di righe da restituire. Default 200, massimo 1000. Omettilo se va bene il default.")
			Integer limit) {
		return executor.execute(sql, limit);
	}
}

package blusailtechnologies.guido.ticket.sql;

public record SqlExecuteResponse(SqlResult result, String error) {

	public static SqlExecuteResponse ok(SqlResult result) {
		return new SqlExecuteResponse(result, null);
	}

	public static SqlExecuteResponse failed(String error) {
		return new SqlExecuteResponse(null, error);
	}
}

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface SqlResult {
  columns: string[];
  rows: Record<string, unknown>[];
  rowCount: number;
  appliedLimit: number;
  truncated: boolean;
  elapsedMillis: number;
}

export interface SqlExecuteResponse {
  result: SqlResult | null;
  error: string | null;
}

export interface ExecuteRequest {
  sessionId: string | null;
  sql: string;
  limit?: number;
}

@Injectable({ providedIn: 'root' })
export class SqlService {
  private readonly http = inject(HttpClient);

  execute(req: ExecuteRequest): Promise<SqlExecuteResponse> {
    return firstValueFrom(this.http.post<SqlExecuteResponse>('/api/sql/execute', req));
  }
}

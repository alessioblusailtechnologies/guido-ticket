import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { SqlResult } from '../sql/sql.service';

export interface SessionSummary {
  id: string;
  title: string;
  model: string | null;
  createdAt: string;
  updatedAt: string;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalCacheCreationTokens: number;
  totalCacheReadTokens: number;
  totalCostUsd: number;
}

export interface SessionMessage {
  role: 'user' | 'assistant';
  content: string;
  attachments?: { name: string; size: number; type: string; truncated?: boolean }[] | null;
  queryResults?: {
    label: string;
    sql: string;
    status: 'ok' | 'error';
    result?: SqlResult;
    error?: string;
  }[] | null;
  steps?: { tool: string; summary: string; state: 'running' | 'done' | 'error' }[] | null;
  inputTokens?: number | null;
  outputTokens?: number | null;
  cacheCreationTokens?: number | null;
  cacheReadTokens?: number | null;
  costUsd?: number | null;
  createdAt?: string;
}

export interface SessionDetail {
  session: SessionSummary;
  messages: SessionMessage[];
}

@Injectable({ providedIn: 'root' })
export class SessionsService {
  private readonly http = inject(HttpClient);

  list(limit = 50): Promise<SessionSummary[]> {
    return firstValueFrom(this.http.get<SessionSummary[]>('/api/sessions', { params: { limit } }));
  }

  load(id: string): Promise<SessionDetail> {
    return firstValueFrom(this.http.get<SessionDetail>(`/api/sessions/${id}`));
  }

  delete(id: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/sessions/${id}`));
  }
}

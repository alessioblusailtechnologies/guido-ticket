import { Injectable, inject, signal } from '@angular/core';

import { SqlResult } from '../sql/sql.service';
import { SessionsService, SessionMessage } from '../sessions/sessions.service';

export interface ToolStep {
  tool: string;
  summary: string;
  state: 'running' | 'done' | 'error';
}

export interface QueryResultAttachment {
  label: string;
  sql: string;
  status: 'ok' | 'error';
  result?: SqlResult;
  error?: string;
}

export interface ChatTurn {
  role: 'user' | 'assistant';
  text: string;
  attachments?: { name: string; size: number; type: string }[];
  queryResults?: QueryResultAttachment[];
  steps?: ToolStep[];
  pending?: boolean;
  error?: string;
}

export interface UsageInfo {
  model: string | null;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalCacheCreationTokens: number;
  totalCacheReadTokens: number;
  totalCostUsd: number;
}

interface ServerEvent {
  type: 'session_started' | 'tool_started' | 'tool_finished' | 'tool_error' | 'complete' | 'error' | 'usage';
  tool?: string;
  summary?: string;
  detail?: string;
  timestamp?: string;
  usage?: {
    model?: string;
    inputTokens?: number;
    outputTokens?: number;
    cacheCreationTokens?: number;
    cacheReadTokens?: number;
    costUsd?: number;
    sessionTotalInputTokens?: number;
    sessionTotalOutputTokens?: number;
    sessionTotalCacheCreationTokens?: number;
    sessionTotalCacheReadTokens?: number;
    sessionTotalCostUsd?: number;
  };
}

const EMPTY_USAGE: UsageInfo = {
  model: null,
  totalInputTokens: 0,
  totalOutputTokens: 0,
  totalCacheCreationTokens: 0,
  totalCacheReadTokens: 0,
  totalCostUsd: 0,
};

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly sessionsService = inject(SessionsService);

  readonly history = signal<ChatTurn[]>([]);
  readonly busy = signal(false);
  readonly sessionId = signal<string | null>(null);
  readonly usage = signal<UsageInfo>(EMPTY_USAGE);

  async send(message: string, attachments: File[], queryResults?: QueryResultAttachment[]): Promise<void> {
    const userTurn: ChatTurn = {
      role: 'user',
      text: message,
      attachments: attachments.map(f => ({ name: f.name, size: f.size, type: f.type })),
      queryResults,
    };
    const assistantTurn: ChatTurn = { role: 'assistant', text: '', pending: true, steps: [] };
    this.history.update(h => [...h, userTurn, assistantTurn]);
    this.busy.set(true);

    const formData = new FormData();
    formData.append('message', message);
    const currentSession = this.sessionId();
    if (currentSession) {
      formData.append('sessionId', currentSession);
    }
    for (const file of attachments) {
      formData.append('attachments', file, file.name);
    }
    if (queryResults && queryResults.length > 0) {
      formData.append('queryResults', JSON.stringify(queryResults));
    }

    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        body: formData,
        headers: { Accept: 'text/event-stream' },
      });

      if (!response.ok || !response.body) {
        const text = await response.text().catch(() => '');
        throw new Error(`HTTP ${response.status} ${response.statusText}${text ? ' · ' + text : ''}`);
      }

      await this.consumeSse(response.body);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Errore di comunicazione col backend';
      this.applyToLastAssistantTurn(turn => ({ ...turn, pending: false, error: msg }));
    } finally {
      this.busy.set(false);
    }
  }

  reset(): void {
    this.sessionId.set(null);
    this.history.set([]);
    this.usage.set(EMPTY_USAGE);
  }

  async loadSession(id: string): Promise<void> {
    const detail = await this.sessionsService.load(id);
    this.sessionId.set(detail.session.id);
    this.usage.set({
      model: detail.session.model,
      totalInputTokens: detail.session.totalInputTokens,
      totalOutputTokens: detail.session.totalOutputTokens,
      totalCacheCreationTokens: detail.session.totalCacheCreationTokens,
      totalCacheReadTokens: detail.session.totalCacheReadTokens,
      totalCostUsd: detail.session.totalCostUsd,
    });
    this.history.set(detail.messages.map(m => this.toTurn(m)));
  }

  private toTurn(m: SessionMessage): ChatTurn {
    const role: 'user' | 'assistant' = m.role === 'assistant' ? 'assistant' : 'user';
    return {
      role,
      text: m.content ?? '',
      attachments: this.coerceAttachments(m.attachments),
      queryResults: this.coerceQueryResults(m.queryResults),
      steps: undefined,
    };
  }

  private coerceAttachments(raw: unknown): ChatTurn['attachments'] {
    if (!Array.isArray(raw)) return undefined;
    return raw.map(r => ({
      name: String((r as { name?: unknown }).name ?? ''),
      size: Number((r as { size?: unknown }).size ?? 0),
      type: String((r as { type?: unknown }).type ?? ''),
    }));
  }

  private coerceQueryResults(raw: unknown): QueryResultAttachment[] | undefined {
    if (!Array.isArray(raw)) return undefined;
    return raw.map(r => r as QueryResultAttachment);
  }

  private async consumeSse(body: ReadableStream<Uint8Array>): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      let sepIdx: number;
      while ((sepIdx = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, sepIdx);
        buffer = buffer.slice(sepIdx + 2);
        this.handleRawEvent(rawEvent);
      }
    }

    if (buffer.trim().length > 0) {
      this.handleRawEvent(buffer);
    }
  }

  private handleRawEvent(raw: string): void {
    const dataLines: string[] = [];
    for (const line of raw.split('\n')) {
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart());
      }
    }
    if (dataLines.length === 0) return;

    const dataStr = dataLines.join('\n');
    let payload: ServerEvent;
    try {
      payload = JSON.parse(dataStr);
    } catch {
      return;
    }
    this.applyEvent(payload);
  }

  private applyEvent(ev: ServerEvent): void {
    switch (ev.type) {
      case 'session_started':
        if (ev.summary) {
          this.sessionId.set(ev.summary);
        }
        return;
      case 'tool_started':
        this.applyToLastAssistantTurn(turn => ({
          ...turn,
          steps: [...(turn.steps ?? []), {
            tool: ev.tool ?? 'tool',
            summary: ev.summary ?? '',
            state: 'running',
          }],
        }));
        return;
      case 'tool_finished':
        this.markLastMatchingStep(ev.tool, 'done', ev.summary);
        return;
      case 'tool_error':
        this.markLastMatchingStep(ev.tool, 'error', ev.summary);
        return;
      case 'usage':
        if (ev.usage) {
          const u = ev.usage;
          this.usage.set({
            model: u.model ?? this.usage().model,
            totalInputTokens: u.sessionTotalInputTokens ?? 0,
            totalOutputTokens: u.sessionTotalOutputTokens ?? 0,
            totalCacheCreationTokens: u.sessionTotalCacheCreationTokens ?? 0,
            totalCacheReadTokens: u.sessionTotalCacheReadTokens ?? 0,
            totalCostUsd: u.sessionTotalCostUsd ?? 0,
          });
        }
        return;
      case 'complete':
        this.applyToLastAssistantTurn(turn => ({
          ...turn,
          text: ev.detail ?? '',
          pending: false,
        }));
        return;
      case 'error':
        this.applyToLastAssistantTurn(turn => ({
          ...turn,
          pending: false,
          error: ev.summary ?? 'Errore lato server',
        }));
        return;
    }
  }

  private applyToLastAssistantTurn(mutator: (t: ChatTurn) => ChatTurn): void {
    this.history.update(h => {
      if (h.length === 0) return h;
      const copy = [...h];
      for (let i = copy.length - 1; i >= 0; i--) {
        if (copy[i].role === 'assistant') {
          copy[i] = mutator(copy[i]);
          break;
        }
      }
      return copy;
    });
  }

  private markLastMatchingStep(tool: string | undefined, state: 'done' | 'error', summary?: string): void {
    this.applyToLastAssistantTurn(turn => {
      const steps = [...(turn.steps ?? [])];
      for (let i = steps.length - 1; i >= 0; i--) {
        if ((tool == null || steps[i].tool === tool) && steps[i].state === 'running') {
          steps[i] = { ...steps[i], state, summary: summary ?? steps[i].summary };
          break;
        }
      }
      return { ...turn, steps };
    });
  }
}

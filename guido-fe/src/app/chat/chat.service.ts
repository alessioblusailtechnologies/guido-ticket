import { Injectable, signal } from '@angular/core';

import { SqlResult } from '../sql/sql.service';

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

interface ServerEvent {
  type: 'session_started' | 'tool_started' | 'tool_finished' | 'tool_error' | 'complete' | 'error';
  tool?: string;
  summary?: string;
  detail?: string;
  timestamp?: string;
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  readonly history = signal<ChatTurn[]>([]);
  readonly busy = signal(false);
  readonly sessionId = signal<string | null>(null);

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

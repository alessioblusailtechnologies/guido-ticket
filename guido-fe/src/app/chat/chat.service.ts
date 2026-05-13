import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface ChatTurn {
  role: 'user' | 'assistant';
  text: string;
  attachments?: { name: string; size: number; type: string }[];
  pending?: boolean;
  error?: string;
}

export interface ChatResponse {
  sessionId: string;
  reply: string;
}

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  readonly history = signal<ChatTurn[]>([]);
  readonly busy = signal(false);
  readonly sessionId = signal<string | null>(null);

  async send(message: string, attachments: File[]): Promise<void> {
    const userTurn: ChatTurn = {
      role: 'user',
      text: message,
      attachments: attachments.map(f => ({ name: f.name, size: f.size, type: f.type })),
    };
    const assistantTurn: ChatTurn = { role: 'assistant', text: '', pending: true };
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
      const res = await firstValueFrom(
        this.http.post<ChatResponse>('/api/chat', formData)
      );
      this.sessionId.set(res.sessionId);
      this.history.update(h => {
        const copy = [...h];
        const last = copy[copy.length - 1];
        copy[copy.length - 1] = { ...last, text: res.reply, pending: false };
        return copy;
      });
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Errore di comunicazione col backend';
      this.history.update(h => {
        const copy = [...h];
        const last = copy[copy.length - 1];
        copy[copy.length - 1] = { ...last, pending: false, error: msg };
        return copy;
      });
    } finally {
      this.busy.set(false);
    }
  }

  reset(): void {
    this.sessionId.set(null);
    this.history.set([]);
  }
}

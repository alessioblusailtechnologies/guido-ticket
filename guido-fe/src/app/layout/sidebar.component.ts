import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  QueryList,
  ViewChildren,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { SessionsService, SessionSummary } from '../sessions/sessions.service';
import { ChatService } from '../chat/chat.service';

export type NavKey = 'assistant';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarComponent {
  private readonly sessionsService = inject(SessionsService);
  private readonly chat = inject(ChatService);

  @Input() active: NavKey = 'assistant';
  @Input() busy = false;
  @Output() newChat = new EventEmitter<void>();
  @Output() navigate = new EventEmitter<NavKey>();
  @Output() openSession = new EventEmitter<string>();
  @Output() sessionDeleted = new EventEmitter<string>();

  @ViewChildren('titleInput') private titleInputs?: QueryList<ElementRef<HTMLInputElement>>;

  readonly sessions = signal<SessionSummary[]>([]);
  readonly loading = signal(false);
  readonly currentSessionId = this.chat.sessionId;
  readonly editingId = signal<string | null>(null);
  readonly editingTitle = signal<string>('');

  constructor() {
    effect(() => {
      this.chat.sessionsRefreshTrigger();
      untracked(() => void this.refresh());
    });
  }

  onNewChat(): void {
    this.newChat.emit();
  }

  onNavigate(key: NavKey): void {
    this.navigate.emit(key);
  }

  onSelectSession(id: string): void {
    if (this.busy || this.editingId() === id) return;
    this.openSession.emit(id);
  }

  onRefreshClick(): void {
    void this.refresh();
  }

  startRename(event: MouseEvent, session: SessionSummary): void {
    event.stopPropagation();
    this.editingId.set(session.id);
    this.editingTitle.set(session.title);
    setTimeout(() => {
      const input = this.titleInputs?.first?.nativeElement;
      if (input) {
        input.focus();
        input.select();
      }
    });
  }

  cancelRename(): void {
    this.editingId.set(null);
    this.editingTitle.set('');
  }

  async confirmRename(session: SessionSummary): Promise<void> {
    const newTitle = this.editingTitle().trim();
    this.editingId.set(null);
    if (!newTitle || newTitle === session.title) {
      return;
    }
    this.sessions.update(list =>
      list.map(s => (s.id === session.id ? { ...s, title: newTitle } : s)),
    );
    try {
      await this.sessionsService.rename(session.id, newTitle);
    } catch {
      void this.refresh();
    }
  }

  onRenameKey(event: KeyboardEvent, session: SessionSummary): void {
    if (event.key === 'Enter') {
      event.preventDefault();
      void this.confirmRename(session);
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.cancelRename();
    }
  }

  async onDelete(event: MouseEvent, session: SessionSummary): Promise<void> {
    event.stopPropagation();
    const ok = window.confirm(`Eliminare la conversazione "${session.title}"?`);
    if (!ok) return;
    this.sessions.update(list => list.filter(s => s.id !== session.id));
    try {
      await this.sessionsService.delete(session.id);
      if (this.currentSessionId() === session.id) {
        this.sessionDeleted.emit(session.id);
      }
    } catch {
      void this.refresh();
    }
  }

  formatCost(cost: number): string {
    if (!cost) return '—';
    if (cost < 0.01) return '<$0.01';
    return '$' + cost.toFixed(cost < 1 ? 3 : 2);
  }

  formatRelative(updatedAt: string): string {
    const then = new Date(updatedAt).getTime();
    if (isNaN(then)) return '';
    const diffMs = Date.now() - then;
    const min = Math.floor(diffMs / 60000);
    if (min < 1) return 'ora';
    if (min < 60) return `${min} min fa`;
    const hr = Math.floor(min / 60);
    if (hr < 24) return `${hr} h fa`;
    const days = Math.floor(hr / 24);
    if (days < 7) return `${days} g fa`;
    return new Date(updatedAt).toLocaleDateString();
  }

  private async refresh(): Promise<void> {
    if (this.loading()) return;
    this.loading.set(true);
    try {
      const list = await this.sessionsService.list(50);
      this.sessions.set(list);
    } catch {
      this.sessions.set([]);
    } finally {
      this.loading.set(false);
    }
  }
}

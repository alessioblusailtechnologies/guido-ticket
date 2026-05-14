import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';

import { SessionsService, SessionSummary } from '../sessions/sessions.service';
import { ChatService } from '../chat/chat.service';

export type NavKey = 'assistant';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarComponent implements OnInit {
  private readonly sessionsService = inject(SessionsService);
  private readonly chat = inject(ChatService);

  @Input() active: NavKey = 'assistant';
  @Input() busy = false;
  @Output() newChat = new EventEmitter<void>();
  @Output() navigate = new EventEmitter<NavKey>();
  @Output() openSession = new EventEmitter<string>();

  readonly sessions = signal<SessionSummary[]>([]);
  readonly loading = signal(false);
  readonly currentSessionId = this.chat.sessionId;

  ngOnInit(): void {
    void this.refresh();
  }

  onNewChat(): void {
    this.newChat.emit();
  }

  onNavigate(key: NavKey): void {
    this.navigate.emit(key);
  }

  onSelectSession(id: string): void {
    if (this.busy) return;
    this.openSession.emit(id);
  }

  onRefreshClick(): void {
    void this.refresh();
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

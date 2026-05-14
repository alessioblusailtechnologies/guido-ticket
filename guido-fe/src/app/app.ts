import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { SidebarComponent, NavKey } from './layout/sidebar.component';
import { TopbarComponent } from './layout/topbar.component';
import { ChatPageComponent } from './chat/chat-page.component';
import { ChatService } from './chat/chat.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, SidebarComponent, TopbarComponent, ChatPageComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly chat = inject(ChatService);

  readonly busy = this.chat.busy;
  readonly sessionId = this.chat.sessionId;
  readonly usage = this.chat.usage;

  active: NavKey = 'assistant';

  onNewChat(): void {
    this.chat.reset();
  }

  onNavigate(key: NavKey): void {
    this.active = key;
  }

  async onOpenSession(id: string): Promise<void> {
    try {
      await this.chat.loadSession(id);
    } catch (err) {
      console.error('Caricamento sessione fallito', err);
    }
  }

  onSessionDeleted(_id: string): void {
    this.chat.reset();
  }
}

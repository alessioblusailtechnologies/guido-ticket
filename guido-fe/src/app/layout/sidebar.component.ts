import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

export type NavKey = 'assistant';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarComponent {
  @Input() active: NavKey = 'assistant';
  @Input() busy = false;
  @Output() newChat = new EventEmitter<void>();
  @Output() navigate = new EventEmitter<NavKey>();

  onNewChat(): void {
    this.newChat.emit();
  }

  onNavigate(key: NavKey): void {
    this.navigate.emit(key);
  }
}

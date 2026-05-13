import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopbarComponent {
  @Input() title = 'Assistant';
  @Input() subtitle = 'Troubleshooting ticket Veolia';
  @Input() sessionId: string | null = null;
  @Input() busy = false;
  @Output() newChat = new EventEmitter<void>();

  shortId(id: string | null): string {
    return id ? id.slice(0, 8) : '';
  }
}

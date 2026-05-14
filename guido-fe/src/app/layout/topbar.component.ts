import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

import { HugeiconsIconComponent } from '@hugeicons/angular';
import { Settings02Icon } from '@hugeicons/core-free-icons';

import { UsageInfo } from '../chat/chat.service';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [CommonModule, HugeiconsIconComponent],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopbarComponent {
  @Input() title = 'Assistant';
  @Input() subtitle = 'Troubleshooting ticket Veolia';
  @Input() sessionId: string | null = null;
  @Input() busy = false;
  @Input() usage: UsageInfo | null = null;

  readonly icons = {
    settings: Settings02Icon,
  } as const;

  shortId(id: string | null): string {
    return id ? id.slice(0, 8) : '';
  }

  modelLabel(model: string | null | undefined): string {
    if (!model) return '—';
    return model
      .replace(/^claude-/, '')
      .replace(/-\d{8}$/, '')
      .replace(/-/g, ' ');
  }

  formatTokens(n: number | null | undefined): string {
    const value = n ?? 0;
    if (value < 1000) return value.toString();
    if (value < 1_000_000) return (value / 1000).toFixed(1).replace(/\.0$/, '') + 'K';
    return (value / 1_000_000).toFixed(2).replace(/\.00$/, '') + 'M';
  }

  totalTokens(u: UsageInfo | null): number {
    if (!u) return 0;
    return (u.totalInputTokens ?? 0)
      + (u.totalOutputTokens ?? 0)
      + (u.totalCacheCreationTokens ?? 0)
      + (u.totalCacheReadTokens ?? 0);
  }

  formatCost(cost: number | null | undefined): string {
    const value = cost ?? 0;
    if (value === 0) return '$0.00';
    if (value < 0.01) return '<$0.01';
    return '$' + value.toFixed(value < 1 ? 3 : 2);
  }
}

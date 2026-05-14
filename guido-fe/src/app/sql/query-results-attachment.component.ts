import { ChangeDetectionStrategy, Component, Input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

import { QueryResultAttachment } from '../chat/chat.service';

@Component({
  selector: 'app-query-results-attachment',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './query-results-attachment.component.html',
  styleUrl: './query-results-attachment.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueryResultsAttachmentComponent {
  @Input({ required: true }) items: QueryResultAttachment[] = [];

  readonly expanded = signal<Set<number>>(new Set());

  toggle(index: number): void {
    this.expanded.update(set => {
      const next = new Set(set);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
  }

  isOpen(index: number): boolean {
    return this.expanded().has(index);
  }

  metaLabel(item: QueryResultAttachment): string {
    if (item.status === 'error') return 'errore';
    if (!item.result) return '—';
    const r = item.result;
    const rows = r.rowCount === 1 ? '1 riga' : `${r.rowCount} righe`;
    return r.truncated ? `${rows} · troncato` : rows;
  }

  visibleRows(item: QueryResultAttachment, max = 30): Record<string, unknown>[] {
    if (!item.result) return [];
    return item.result.rows.slice(0, max);
  }

  formatCell(value: unknown): string {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'object') return JSON.stringify(value);
    const s = String(value);
    return s.length > 200 ? s.slice(0, 200) + '…' : s;
  }
}

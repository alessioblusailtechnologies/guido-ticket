import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';

import { HugeiconsIconComponent } from '@hugeicons/angular';
import { PlayIcon, Tick02Icon } from '@hugeicons/core-free-icons';

import { SqlResult, SqlService } from './sql.service';

export type SqlCardStatus = 'proposed' | 'executing' | 'done' | 'error';

export interface SqlCardStateEvent {
  cardId: string;
  status: SqlCardStatus;
  feedback?: string;
  sql: string;
  result?: SqlResult;
  error?: string;
}

@Component({
  selector: 'app-sql-proposal-card',
  standalone: true,
  imports: [CommonModule, HugeiconsIconComponent],
  templateUrl: './sql-proposal-card.component.html',
  styleUrl: './sql-proposal-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SqlProposalCardComponent {
  private readonly sqlService = inject(SqlService);

  @Input({ required: true }) sql = '';
  @Input({ required: true }) cardId = '';
  @Input() sessionId: string | null = null;

  @Output() stateChanged = new EventEmitter<SqlCardStateEvent>();

  readonly state = signal<SqlCardStatus>('proposed');
  readonly result = signal<SqlResult | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly icons = {
    play: PlayIcon,
    tick: Tick02Icon,
  } as const;

  async runQuery(): Promise<void> {
    if (this.state() === 'executing' || this.state() === 'done') {
      return;
    }
    this.state.set('executing');
    this.errorMessage.set(null);
    this.emit('executing');

    try {
      const res = await this.sqlService.execute({
        sessionId: this.sessionId,
        sql: this.sql,
      });

      if (res.error) {
        this.errorMessage.set(res.error);
        this.state.set('error');
        this.emit('error', this.buildErrorFeedback(res.error), { error: res.error });
        return;
      }

      if (!res.result) {
        const msg = 'Risposta vuota dal server';
        this.errorMessage.set(msg);
        this.state.set('error');
        this.emit('error', this.buildErrorFeedback(msg), { error: msg });
        return;
      }

      this.result.set(res.result);
      this.state.set('done');
      this.emit('done', this.buildFeedback(res.result), { result: res.result });
    } catch (err: unknown) {
      const msg = this.extractMessage(err);
      this.errorMessage.set(msg);
      this.state.set('error');
      this.emit('error', this.buildErrorFeedback(msg), { error: msg });
    }
  }

  visibleRows(res: SqlResult, max = 30): Record<string, unknown>[] {
    return res.rows.slice(0, max);
  }

  formatCell(value: unknown): string {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
  }

  private emit(status: SqlCardStatus, feedback?: string, extras?: { result?: SqlResult; error?: string }): void {
    this.stateChanged.emit({
      cardId: this.cardId,
      status,
      feedback,
      sql: this.sql,
      result: extras?.result,
      error: extras?.error,
    });
  }

  private buildFeedback(res: SqlResult): string {
    const maxRowsInline = 50;
    const rowsForLlm = res.rows.slice(0, maxRowsInline);
    const lines: string[] = [];
    lines.push('```sql');
    lines.push(this.sql);
    lines.push('```');
    lines.push(`Righe restituite: ${res.rowCount} · limit applicato ${res.appliedLimit} · truncated=${res.truncated} · ${res.elapsedMillis} ms`);
    if (res.rowCount === 0) {
      lines.push('');
      lines.push('Nessuna riga.');
    } else {
      lines.push('');
      lines.push('| ' + res.columns.join(' | ') + ' |');
      lines.push('|' + res.columns.map(() => '---').join('|') + '|');
      for (const row of rowsForLlm) {
        const cells = res.columns.map(c => this.formatCell(row[c]).replace(/\|/g, '\\|'));
        lines.push('| ' + cells.join(' | ') + ' |');
      }
      if (res.rowCount > rowsForLlm.length) {
        lines.push('');
        lines.push(`(Mostrate prime ${rowsForLlm.length} righe sul totale di ${res.rowCount}.)`);
      }
    }
    return lines.join('\n');
  }

  private buildErrorFeedback(msg: string): string {
    return [
      '```sql',
      this.sql,
      '```',
      'Errore esecuzione: ' + msg,
    ].join('\n');
  }

  private extractMessage(err: unknown): string {
    if (err instanceof Error) return err.message;
    if (typeof err === 'object' && err !== null && 'message' in err) {
      return String((err as { message: unknown }).message);
    }
    return 'Errore sconosciuto';
  }
}

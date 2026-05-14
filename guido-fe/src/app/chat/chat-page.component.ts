import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';

import { HugeiconsIconComponent } from '@hugeicons/angular';
import {
  Attachment01Icon,
  Cancel01Icon,
  SentIcon,
  Tick02Icon,
} from '@hugeicons/core-free-icons';

import { ChatService, ChatTurn, QueryResultAttachment } from './chat.service';
import { SqlProposalCardComponent, SqlCardStateEvent, SqlCardStatus } from '../sql/sql-proposal-card.component';
import { SqlResult } from '../sql/sql.service';
import { QueryResultsAttachmentComponent } from '../sql/query-results-attachment.component';

interface MarkdownSegment {
  kind: 'markdown';
  html: SafeHtml;
}
interface SqlSegment {
  kind: 'sql';
  sql: string;
  cardId: string;
}
type Segment = MarkdownSegment | SqlSegment;

interface RenderedTurn extends ChatTurn {
  segments?: Segment[];
  html: SafeHtml;
  turnIndex: number;
}

interface CardState {
  status: SqlCardStatus;
  feedback?: string;
  sql?: string;
  result?: SqlResult;
  error?: string;
}

@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [CommonModule, FormsModule, SqlProposalCardComponent, QueryResultsAttachmentComponent, HugeiconsIconComponent],
  templateUrl: './chat-page.component.html',
  styleUrl: './chat-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatPageComponent implements AfterViewChecked {
  private readonly chat = inject(ChatService);
  private readonly sanitizer = inject(DomSanitizer);

  @ViewChild('scroller') private scroller?: ElementRef<HTMLDivElement>;
  @ViewChild('textArea') private textArea?: ElementRef<HTMLTextAreaElement>;
  @ViewChild('fileInput') private fileInput?: ElementRef<HTMLInputElement>;

  readonly busy = this.chat.busy;
  readonly sessionId = this.chat.sessionId;

  readonly draft = signal('');
  readonly attachments = signal<File[]>([]);
  readonly dragOver = signal(false);

  readonly cardStates = signal<Map<string, CardState>>(new Map());
  readonly turnsSent = signal<Set<number>>(new Set());

  readonly icons = {
    tick: Tick02Icon,
    cancel: Cancel01Icon,
    attachment: Attachment01Icon,
    send: SentIcon,
  } as const;

  readonly turns = computed<RenderedTurn[]>(() =>
    this.chat.history().map((turn, idx) => ({
      ...turn,
      html: this.renderMarkdown(turn.text),
      segments: turn.role === 'assistant' ? this.splitSegments(turn.text, idx) : undefined,
      turnIndex: idx,
    })),
  );

  readonly canSend = computed(
    () => !this.busy() && (this.draft().trim().length > 0 || this.attachments().length > 0),
  );

  ngAfterViewChecked(): void {
    if (this.scroller) {
      this.scroller.nativeElement.scrollTop = this.scroller.nativeElement.scrollHeight;
    }
  }

  onAttachClick(): void {
    this.fileInput?.nativeElement.click();
  }

  onFilesPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files) {
      return;
    }
    this.addFiles(Array.from(input.files));
    input.value = '';
  }

  removeAttachment(index: number): void {
    this.attachments.update(files => files.filter((_, i) => i !== index));
  }

  onTextareaKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  @HostListener('dragover', ['$event'])
  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  @HostListener('dragleave', ['$event'])
  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
  }

  @HostListener('drop', ['$event'])
  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    if (event.dataTransfer?.files) {
      this.addFiles(Array.from(event.dataTransfer.files));
    }
  }

  send(): void {
    if (!this.canSend()) {
      return;
    }
    const text = this.draft().trim();
    const files = this.attachments();
    this.draft.set('');
    this.attachments.set([]);
    void this.chat.send(text, files);
  }

  newSession(): void {
    this.chat.reset();
    this.draft.set('');
    this.attachments.set([]);
    this.cardStates.set(new Map());
    this.turnsSent.set(new Set());
  }

  formatBytes(size: number): string {
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }

  onCardStateChanged(event: SqlCardStateEvent): void {
    this.cardStates.update(map => {
      const next = new Map(map);
      next.set(event.cardId, {
        status: event.status,
        feedback: event.feedback,
        sql: event.sql,
        result: event.result,
        error: event.error,
      });
      return next;
    });
  }

  pendingFeedbacksForTurn(turn: RenderedTurn): { count: number; executing: number; total: number } {
    if (this.turnsSent().has(turn.turnIndex)) {
      return { count: 0, executing: 0, total: 0 };
    }
    const sqlSegs = (turn.segments ?? []).filter(s => s.kind === 'sql') as SqlSegment[];
    let count = 0;
    let executing = 0;
    for (const s of sqlSegs) {
      const st = this.cardStates().get(s.cardId);
      if (!st) continue;
      if (st.status === 'executing') executing++;
      if ((st.status === 'done' || st.status === 'error') && st.feedback) count++;
    }
    return { count, executing, total: sqlSegs.length };
  }

  canSubmitBatch(turn: RenderedTurn): boolean {
    const p = this.pendingFeedbacksForTurn(turn);
    return !this.busy() && p.count > 0 && p.executing === 0;
  }

  submitBatch(turn: RenderedTurn): void {
    if (!this.canSubmitBatch(turn)) return;
    const sqlSegs = (turn.segments ?? []).filter(s => s.kind === 'sql') as SqlSegment[];
    const feedbacks: string[] = [];
    const items: QueryResultAttachment[] = [];
    let counter = 1;
    for (const s of sqlSegs) {
      const st = this.cardStates().get(s.cardId);
      if (!st || (st.status !== 'done' && st.status !== 'error') || !st.feedback) {
        continue;
      }
      feedbacks.push(st.feedback);
      items.push({
        label: `query_${counter++}.sql`,
        sql: st.sql ?? s.sql,
        status: st.status === 'done' ? 'ok' : 'error',
        result: st.result,
        error: st.error,
      });
    }
    if (feedbacks.length === 0) return;

    const header = feedbacks.length === 1
      ? '[Risultato della query]'
      : `[Risultati di ${feedbacks.length} query]`;
    const message = [header, '', feedbacks.join('\n\n---\n\n')].join('\n');

    this.turnsSent.update(set => {
      const next = new Set(set);
      next.add(turn.turnIndex);
      return next;
    });
    void this.chat.send(message, [], items);
  }

  isTurnSent(turn: RenderedTurn): boolean {
    return this.turnsSent().has(turn.turnIndex);
  }

  private addFiles(files: File[]): void {
    if (files.length === 0) return;
    this.attachments.update(curr => [...curr, ...files]);
  }

  private splitSegments(text: string, turnIndex: number): Segment[] {
    if (!text) return [];
    const segments: Segment[] = [];
    const regex = /```sql\s*\n([\s\S]*?)```/gi;
    let lastIndex = 0;
    let segIdx = 0;
    let match: RegExpExecArray | null;
    while ((match = regex.exec(text)) !== null) {
      const before = text.slice(lastIndex, match.index);
      if (before.trim().length > 0) {
        segments.push({ kind: 'markdown', html: this.renderMarkdown(before) });
      }
      segments.push({ kind: 'sql', sql: match[1].trim(), cardId: `t${turnIndex}-s${segIdx++}` });
      lastIndex = match.index + match[0].length;
    }
    const tail = text.slice(lastIndex);
    if (tail.trim().length > 0) {
      segments.push({ kind: 'markdown', html: this.renderMarkdown(tail) });
    }
    return segments;
  }

  private renderMarkdown(text: string): SafeHtml {
    if (!text) {
      return this.sanitizer.bypassSecurityTrustHtml('');
    }
    const html = marked.parse(text, { async: false }) as string;
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }
}

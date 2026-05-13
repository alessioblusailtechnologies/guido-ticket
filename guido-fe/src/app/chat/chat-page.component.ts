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

import { ChatService, ChatTurn } from './chat.service';

interface RenderedTurn extends ChatTurn {
  html: SafeHtml;
}

@Component({
  selector: 'app-chat-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
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

  readonly turns = computed<RenderedTurn[]>(() =>
    this.chat.history().map(turn => ({
      ...turn,
      html: this.renderMarkdown(turn.text),
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
  }

  formatBytes(size: number): string {
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }

  private addFiles(files: File[]): void {
    if (files.length === 0) return;
    this.attachments.update(curr => [...curr, ...files]);
  }

  private renderMarkdown(text: string): SafeHtml {
    if (!text) {
      return this.sanitizer.bypassSecurityTrustHtml('');
    }
    const html = marked.parse(text, { async: false }) as string;
    return this.sanitizer.bypassSecurityTrustHtml(html);
  }
}

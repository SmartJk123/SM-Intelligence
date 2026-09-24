import {
  afterNextRender,
  Component,
  ElementRef,
  OnDestroy,
  output,
  signal,
  ViewChild,
} from '@angular/core';

@Component({
  selector: 'app-invoice-camera',
  template: `
    <dialog
      #dialog
      aria-labelledby="camera-title"
      aria-describedby="camera-help"
      (cancel)="$event.preventDefault(); dismiss()"
    >
      <header>
        <h2 id="camera-title">{{ photo() ? 'Review your photo' : 'Photograph your invoice' }}</h2>
        <button
          type="button"
          class="button secondary"
          (click)="dismiss()"
          aria-label="Close camera"
        >
          Close
        </button>
      </header>
      <p id="camera-help">
        {{
          photo()
            ? 'Check that the whole invoice is clear and readable.'
            : 'Place the whole invoice in view and hold the camera steady.'
        }}
      </p>
      @if (error()) {
        <p class="camera-error" role="alert">{{ error() }}</p>
      }
      @if (starting()) {
        <p role="status">Waiting for camera access… Allow camera permission in your browser.</p>
      }
      <video
        #video
        autoplay
        muted
        playsinline
        [hidden]="!!photo() || !!error()"
        (loadeddata)="videoReady()"
        aria-label="Live camera preview"
      ></video>
      @if (photo()) {
        <img [src]="photo()" alt="Captured invoice, ready to review" />
      }
      <div class="actions">
        @if (photo()) {
          <button type="button" class="button primary" (click)="usePhoto()">Use photo</button>
          <button type="button" class="button secondary" (click)="start()">Retake</button>
        } @else {
          <button
            type="button"
            class="button primary"
            (click)="capture()"
            [disabled]="!ready() || capturing()"
          >
            {{ capturing() ? 'Capturing…' : 'Capture photo' }}
          </button>
          <button
            type="button"
            class="button secondary"
            (click)="switchCamera()"
            [disabled]="!ready() || capturing()"
          >
            Switch camera
          </button>
          @if (error()) {
            <button type="button" class="button secondary" (click)="start()">Try again</button>
          }
        }
      </div>
      <small
        >Your camera stops when you capture a photo or close this window. Nothing is uploaded until
        you save the invoice.</small
      >
    </dialog>
  `,
  styles: `
    dialog {
      box-sizing: border-box;
      width: min(680px, calc(100vw - 24px));
      max-height: calc(100dvh - 24px);
      overflow: auto;
      border: 1px solid var(--ws-line);
      border-radius: 18px;
      padding: 24px;
      background: var(--ws-card);
      color: var(--ws-ink);
    }
    dialog::backdrop {
      background: rgba(5, 18, 35, 0.75);
    }
    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
    }
    h2 {
      font-size: 24px;
      margin: 0;
    }
    p,
    small {
      color: var(--ws-muted);
      line-height: 1.6;
    }
    p {
      font-size: 14px;
      margin: 16px 0;
    }
    video,
    img {
      display: block;
      width: 100%;
      max-height: 55dvh;
      object-fit: contain;
      background: #081420;
      border-radius: 12px;
    }
    video {
      min-height: 200px;
    }
    video[hidden] {
      display: none;
    }
    .actions {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin: 20px 0;
    }
    .camera-error {
      border-left: 3px solid #db5151;
      padding: 12px;
      background: var(--ws-soft);
    }
    small {
      display: block;
      font-size: 12px;
    }
    @media (max-width: 480px) {
      dialog {
        padding: 16px;
      }
      h2 {
        font-size: 20px;
      }
      .actions .button {
        flex: 1;
        padding: 12px;
        font-size: 13px;
      }
    }
  `,
})
export class InvoiceCamera implements OnDestroy {
  readonly captured = output<File>();
  readonly closed = output<void>();
  @ViewChild('dialog', { static: true }) dialog!: ElementRef<HTMLDialogElement>;
  @ViewChild('video', { static: true }) video!: ElementRef<HTMLVideoElement>;
  readonly starting = signal(false);
  readonly ready = signal(false);
  readonly capturing = signal(false);
  readonly error = signal('');
  readonly photo = signal('');
  private blob: Blob | null = null;
  private stream: MediaStream | null = null;
  private facing: 'environment' | 'user' = 'environment';
  private generation = 0;
  private disposed = false;

  constructor() {
    afterNextRender(() => {
      this.dialog.nativeElement.showModal();
      void this.start();
    });
  }

  async start() {
    if (this.disposed) return;
    const generation = ++this.generation;
    this.stopStream();
    this.clearPhoto();
    this.error.set('');
    this.capturing.set(false);
    if (!globalThis.isSecureContext) {
      this.error.set(
        'Live camera needs HTTPS on your phone, or localhost on this computer. Open a trusted HTTPS address, or close this window and use Upload a file.',
      );
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      this.error.set(
        'This browser cannot open a live camera. Try a current browser or use Upload a file.',
      );
      return;
    }
    this.starting.set(true);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: {
          facingMode: { ideal: this.facing },
          width: { ideal: 1920 },
          height: { ideal: 1440 },
        },
      });
      // Permission may resolve after the dialog has been closed or another camera requested.
      if (this.disposed || generation !== this.generation) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      this.stream = stream;
      const video = this.video.nativeElement;
      video.muted = true;
      video.srcObject = stream;
      for (const track of stream.getVideoTracks())
        track.addEventListener('ended', () => {
          if (this.stream !== stream) return;
          this.stopStream();
          this.error.set('The camera disconnected. Reconnect it and try again.');
        });
      await video.play();
      if (generation === this.generation) this.videoReady();
    } catch (e) {
      if (this.disposed || generation !== this.generation) return;
      this.stopStream();
      const name = e instanceof Error ? e.name : '';
      this.error.set(
        name === 'NotAllowedError' || name === 'SecurityError'
          ? 'Camera permission was denied. Allow camera access in your browser’s site settings, then try again.'
          : name === 'NotFoundError'
            ? 'No camera was found. Connect a camera or use Upload a file.'
            : 'The camera could not start. Close other apps using it and try again, or upload a file.',
      );
    } finally {
      if (generation === this.generation) this.starting.set(false);
    }
  }
  videoReady() {
    const video = this.video.nativeElement;
    this.ready.set(
      !!this.stream && video.videoWidth > 0 && video.videoHeight > 0 && video.readyState >= 2,
    );
  }
  switchCamera() {
    if (!this.ready() || this.capturing()) return;
    this.facing = this.facing === 'environment' ? 'user' : 'environment';
    void this.start();
  }
  async capture() {
    if (!this.ready() || this.capturing()) return;
    this.capturing.set(true);
    this.error.set('');
    const generation = this.generation;
    try {
      const video = this.video.nativeElement;
      const scale = Math.min(1, 2400 / Math.max(video.videoWidth, video.videoHeight));
      const canvas = document.createElement('canvas');
      canvas.width = Math.round(video.videoWidth * scale);
      canvas.height = Math.round(video.videoHeight * scale);
      const context = canvas.getContext('2d');
      if (!context) throw new Error('Canvas unavailable');
      context.drawImage(video, 0, 0, canvas.width, canvas.height);
      const blob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, 'image/jpeg', 0.92),
      );
      canvas.width = canvas.height = 0;
      if (this.disposed || generation !== this.generation) return;
      if (!blob || !blob.size || blob.size > 10 * 1024 * 1024) throw new Error('Invalid capture');
      this.blob = blob;
      this.photo.set(URL.createObjectURL(blob));
      this.stopStream();
    } catch {
      if (!this.disposed && generation === this.generation)
        this.error.set('Could not capture the photo. Please try again.');
    } finally {
      if (generation === this.generation) this.capturing.set(false);
    }
  }
  usePhoto() {
    if (!this.blob || this.disposed) return;
    this.captured.emit(new File([this.blob], `invoice-${Date.now()}.jpg`, { type: 'image/jpeg' }));
    this.dismiss();
  }
  dismiss() {
    this.release();
    this.dialog.nativeElement.close();
    this.closed.emit();
  }
  private stopStream() {
    const stream = this.stream;
    this.stream = null;
    stream?.getTracks().forEach((track) => track.stop());
    if (this.video) this.video.nativeElement.srcObject = null;
    this.ready.set(false);
  }
  private clearPhoto() {
    if (this.photo()) URL.revokeObjectURL(this.photo());
    this.photo.set('');
    this.blob = null;
  }
  private release() {
    this.disposed = true;
    ++this.generation;
    this.stopStream();
    this.clearPhoto();
  }
  ngOnDestroy() {
    this.release();
  }
}

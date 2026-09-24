import { TestBed } from '@angular/core/testing';
import { InvoiceCamera } from './invoice-camera';

describe('Live invoice camera', () => {
  let getUserMedia: ReturnType<typeof vi.fn>;
  let stop: ReturnType<typeof vi.fn>;
  let stream: MediaStream;
  beforeEach(() => {
    TestBed.configureTestingModule({});
    stop = vi.fn();
    const track = { stop, addEventListener: vi.fn() };
    stream = { getTracks: () => [track], getVideoTracks: () => [track] } as unknown as MediaStream;
    getUserMedia = vi.fn().mockResolvedValue(stream);
    vi.stubGlobal('isSecureContext', true);
    vi.stubGlobal('navigator', new Proxy(navigator, {
      get(target, key) { return key === 'mediaDevices' ? { getUserMedia } : Reflect.get(target, key); },
    }));
    vi.spyOn(HTMLDialogElement.prototype, 'showModal').mockImplementation(() => {});
    vi.spyOn(HTMLDialogElement.prototype, 'close').mockImplementation(() => {});
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    vi.stubGlobal('URL', class extends URL {
      static override createObjectURL = vi.fn().mockReturnValue('blob:camera-test');
      static override revokeObjectURL = vi.fn();
    });
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });
  async function open() {
    const fixture = TestBed.createComponent(InvoiceCamera);
    await fixture.whenStable();
    return { fixture, camera: fixture.componentInstance };
  }
  it('requests video only, prefers the rear camera and releases it on close', async () => {
    const { camera } = await open();
    expect(getUserMedia).toHaveBeenCalledWith(
      expect.objectContaining({
        audio: false,
        video: expect.objectContaining({ facingMode: { ideal: 'environment' } }),
      }),
    );
    const closed = vi.fn();
    camera.closed.subscribe(closed);
    camera.dismiss();
    expect(stop).toHaveBeenCalledOnce();
    expect(closed).toHaveBeenCalledOnce();
  });
  it('stops a late stream when permission resolves after the dialog was destroyed', async () => {
    let resolve!: (value: MediaStream) => void;
    getUserMedia.mockReturnValue(
      new Promise<MediaStream>((r) => {
        resolve = r;
      }),
    );
    const { fixture } = await open();
    fixture.destroy();
    resolve(stream);
    await Promise.resolve();
    expect(stop).toHaveBeenCalledOnce();
  });
  it('explains denied permission and insecure origins without a file-picker fallback', async () => {
    getUserMedia.mockRejectedValue(new DOMException('Denied', 'NotAllowedError'));
    const { camera } = await open();
    expect(camera.error()).toContain('permission was denied');
    vi.stubGlobal('isSecureContext', false);
    getUserMedia.mockClear();
    await camera.start();
    expect(camera.error()).toContain('HTTPS');
    expect(getUserMedia).not.toHaveBeenCalled();
  });
  it('captures a still, stops the stream, and emits JPEG only after Use photo', async () => {
    const { camera } = await open();
    const video = camera.video.nativeElement;
    Object.defineProperties(video, {
      videoWidth: { value: 1920 },
      videoHeight: { value: 1080 },
      readyState: { value: 2 },
    });
    camera.videoReady();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      drawImage: vi.fn(),
    } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((callback) =>
      callback(new Blob(['photo'], { type: 'image/jpeg' })),
    );
    const captured = vi.fn();
    camera.captured.subscribe(captured);
    await camera.capture();
    expect(stop).toHaveBeenCalledOnce();
    expect(camera.photo()).toBe('blob:camera-test');
    expect(captured).not.toHaveBeenCalled();
    camera.usePhoto();
    expect(captured).toHaveBeenCalledWith(expect.any(File));
    expect(captured.mock.calls[0][0].type).toBe('image/jpeg');
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:camera-test');
  });
  it('releases the old camera before switching or retaking', async () => {
    const { camera } = await open();
    camera.ready.set(true);
    camera.switchCamera();
    expect(stop).toHaveBeenCalledOnce();
    await Promise.resolve();
    expect(getUserMedia).toHaveBeenLastCalledWith(
      expect.objectContaining({
        video: expect.objectContaining({ facingMode: { ideal: 'user' } }),
      }),
    );
    await camera.start();
    expect(stop).toHaveBeenCalledTimes(2);
  });
});

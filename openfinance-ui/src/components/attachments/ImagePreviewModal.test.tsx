import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { ImagePreviewModal } from './ImagePreviewModal';
import type { Attachment } from '@/types/attachment';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------
const mockDownloadAttachment = vi.fn();
const mockFetchBlobMutate = vi.fn();

vi.mock('@/hooks/useAttachments', () => ({
  useDownloadAttachment: () => ({ downloadAttachment: mockDownloadAttachment }),
  useFetchAttachmentBlob: () => ({ mutate: mockFetchBlobMutate }),
}));

// Mock Radix Dialog to render inline (no portal / aria-hidden issues in jsdom)
vi.mock('@/components/ui/Dialog', () => ({
  Dialog: ({ children, open }: any) => (open ? <div data-testid="dialog">{children}</div> : null),
  DialogOverlay: () => null,
  DialogPortal: ({ children }: any) => <>{children}</>,
}));

vi.mock('@radix-ui/react-dialog', () => ({
  __esModule: true,
  default: {},
  Content: ({ children, ...props }: any) => <div {...props}>{children}</div>,
}));

// Mock URL.createObjectURL / revokeObjectURL
const mockCreateObjectURL = vi.fn(() => 'blob:http://localhost/fake-url');
const mockRevokeObjectURL = vi.fn();
globalThis.URL.createObjectURL = mockCreateObjectURL;
globalThis.URL.revokeObjectURL = mockRevokeObjectURL;

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------
function makeImage(overrides: Partial<Attachment> = {}): Attachment {
  return {
    id: 1,
    fileName: 'photo.png',
    contentType: 'image/png',
    fileSize: 1024,
    entityType: 'TRANSACTION',
    entityId: 10,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  } as Attachment;
}

const images: Attachment[] = [
  makeImage({ id: 1, fileName: 'photo1.png' }),
  makeImage({ id: 2, fileName: 'photo2.png' }),
  makeImage({ id: 3, fileName: 'photo3.png' }),
];

function simulateBlobSuccess() {
  mockFetchBlobMutate.mockImplementation((_id: number, opts: any) => {
    opts?.onSuccess?.(new Blob(['img'], { type: 'image/png' }));
  });
}

function simulateBlobError() {
  mockFetchBlobMutate.mockImplementation((_id: number, opts: any) => {
    opts?.onError?.(new Error('Network error'));
  });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe('ImagePreviewModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    simulateBlobSuccess();
  });

  it('returns null when images array is empty', () => {
    const { container } = renderWithProviders(
      <ImagePreviewModal images={[]} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    expect(container.innerHTML).toBe('');
  });

  it('renders the current image filename', () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    expect(screen.getByText('photo1.png')).toBeInTheDocument();
  });

  it('shows image counter "1 of 3"', () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    expect(screen.getByText('1 of 3')).toBeInTheDocument();
  });

  it('loads image blob on open', () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    expect(mockFetchBlobMutate).toHaveBeenCalledWith(1, expect.any(Object));
  });

  it('displays loaded image with correct alt text', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img).toBeTruthy();
      expect(img?.alt).toBe('photo1.png');
      expect(img?.src).toBe('blob:http://localhost/fake-url');
    });
  });

  it('shows loading spinner while fetching', () => {
    mockFetchBlobMutate.mockImplementation(() => {
      // Don't call onSuccess - stays loading
    });
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    expect(screen.getByText('Loading image...')).toBeInTheDocument();
  });

  it('shows error message on fetch failure', () => {
    simulateBlobError();
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    expect(screen.getByText('Failed to load image. Please try again.')).toBeInTheDocument();
  });

  it('shows Retry button on error', () => {
    simulateBlobError();
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('retries loading when Retry is clicked', () => {
    simulateBlobError();
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    mockFetchBlobMutate.mockClear();
    simulateBlobSuccess();
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(mockFetchBlobMutate).toHaveBeenCalled();
  });

  it('navigates to next image on Next click', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.click(screen.getByTitle('Next (→)'));
    await waitFor(() => {
      expect(screen.getByText('photo2.png')).toBeInTheDocument();
      expect(screen.getByText('2 of 3')).toBeInTheDocument();
    });
  });

  it('navigates to previous image on Previous click', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={1} open={true} onClose={vi.fn()} />
    );
    fireEvent.click(screen.getByTitle('Previous (←)'));
    await waitFor(() => {
      expect(screen.getByText('photo1.png')).toBeInTheDocument();
    });
  });

  it('wraps around to last image when pressing Previous on first', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.click(screen.getByTitle('Previous (←)'));
    await waitFor(() => {
      expect(screen.getByText('photo3.png')).toBeInTheDocument();
      expect(screen.getByText('3 of 3')).toBeInTheDocument();
    });
  });

  it('wraps around to first image when pressing Next on last', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={2} open={true} onClose={vi.fn()} />
    );
    fireEvent.click(screen.getByTitle('Next (→)'));
    await waitFor(() => {
      expect(screen.getByText('photo1.png')).toBeInTheDocument();
    });
  });

  it('does not show navigation buttons for single image', () => {
    renderWithProviders(
      <ImagePreviewModal images={[images[0]]} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    expect(screen.queryByTitle('Previous (←)')).not.toBeInTheDocument();
    expect(screen.queryByTitle('Next (→)')).not.toBeInTheDocument();
  });

  it('calls onClose when close button is clicked', () => {
    const onClose = vi.fn();
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={onClose} />
    );
    fireEvent.click(screen.getByTitle('Close (Esc)'));
    expect(onClose).toHaveBeenCalled();
  });

  it('handles Escape key to close', () => {
    const onClose = vi.fn();
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={onClose} />
    );
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('handles ArrowLeft key for navigation', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={1} open={true} onClose={vi.fn()} />
    );
    fireEvent.keyDown(window, { key: 'ArrowLeft' });
    await waitFor(() => {
      expect(screen.getByText('photo1.png')).toBeInTheDocument();
    });
  });

  it('handles ArrowRight key for navigation', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.keyDown(window, { key: 'ArrowRight' });
    await waitFor(() => {
      expect(screen.getByText('photo2.png')).toBeInTheDocument();
    });
  });

  it('handles + key for zoom in', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.keyDown(window, { key: '+' });
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(1.25)');
    });
  });

  it('handles - key for zoom out', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.keyDown(window, { key: '-' });
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(0.75)');
    });
  });

  it('handles R key for rotation', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.keyDown(window, { key: 'R' });
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('rotate(90deg)');
    });
  });

  it('zooms in with button click', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.click(screen.getByTitle('Zoom in (+)'));
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(1.25)');
    });
  });

  it('zooms out with button click', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.click(screen.getByTitle('Zoom out (-)'));
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(0.75)');
    });
  });

  it('rotates with button click', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.click(screen.getByTitle('Rotate (R)'));
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('rotate(90deg)');
    });
  });

  it('calls downloadAttachment on download click', () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.click(screen.getByTitle('Download'));
    expect(mockDownloadAttachment).toHaveBeenCalledWith(1, 'photo1.png');
  });

  it('resets zoom and rotation when navigating', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    // Zoom in and rotate first
    fireEvent.keyDown(window, { key: '+' });
    fireEvent.keyDown(window, { key: 'R' });
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(1.25)');
      expect(img?.style.transform).toContain('rotate(90deg)');
    });
    // Navigate to next
    fireEvent.click(screen.getByTitle('Next (→)'));
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(1)');
      expect(img?.style.transform).toContain('rotate(0deg)');
    });
  });

  it('does not render when open is false', () => {
    const { container } = renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={false} onClose={vi.fn()} />
    );
    expect(screen.queryByText('photo1.png')).not.toBeInTheDocument();
  });

  it('shows keyboard shortcuts hint', () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    expect(screen.getByText(/Use ← → to navigate/)).toBeInTheDocument();
  });

  it('handles = key as zoom in alias', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.keyDown(window, { key: '=' });
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(1.25)');
    });
  });

  it('handles _ key as zoom out alias', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    fireEvent.keyDown(window, { key: '_' });
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(0.75)');
    });
  });

  it('clamps zoom to maximum of 3', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    // Zoom in many times (1 → 1.25 → 1.5 → ... → 3)
    for (let i = 0; i < 20; i++) {
      fireEvent.keyDown(window, { key: '+' });
    }
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(3)');
    });
  });

  it('clamps zoom to minimum of 0.5', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    // Zoom out many times (1 → 0.75 → 0.5)
    for (let i = 0; i < 20; i++) {
      fireEvent.keyDown(window, { key: '-' });
    }
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('scale(0.5)');
    });
  });

  it('rotation wraps at 360', async () => {
    renderWithProviders(
      <ImagePreviewModal images={images} initialIndex={0} open={true} onClose={vi.fn()} />
    );
    // Rotate 4 times: 90 → 180 → 270 → 0
    for (let i = 0; i < 4; i++) {
      fireEvent.keyDown(window, { key: 'r' });
    }
    await waitFor(() => {
      const img = document.querySelector('img');
      expect(img?.style.transform).toContain('rotate(0deg)');
    });
  });
});

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import { PropertyGallery } from './PropertyGallery';
import { usePropertyImages, useAttachmentImageUrl } from '@/hooks/usePropertyImages';

vi.mock('@/hooks/usePropertyImages', () => ({
  usePropertyImages: vi.fn(),
  useAttachmentImageUrl: vi.fn(() => 'blob:mock'),
}));

const mockUsePropertyImages = vi.mocked(usePropertyImages);

function renderGallery(propertyId = 5) {
  return render(
    <I18nextProvider i18n={i18n}>
      <PropertyGallery propertyId={propertyId} />
    </I18nextProvider>
  );
}

describe('PropertyGallery', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders a thumbnail per image attachment', () => {
    mockUsePropertyImages.mockReturnValue({
      images: [
        { id: 1, image: true, fileName: 'front.png' } as any,
        { id: 2, image: true, fileName: 'back.png' } as any,
      ],
      isLoading: false,
    });
    renderGallery();
    const imgs = screen.getAllByRole('img');
    expect(imgs).toHaveLength(2);
    expect(imgs[0]).toHaveAttribute('alt', 'front.png');
  });

  it('shows the empty state pointing at the attachments tab', () => {
    mockUsePropertyImages.mockReturnValue({ images: [], isLoading: false });
    renderGallery();
    expect(screen.getByText(/No images yet/)).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
});

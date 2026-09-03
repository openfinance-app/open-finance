import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';
import { AssetGallery } from './AssetGallery';
import { useEntityImages, useAttachmentImageUrl } from '@/hooks/useEntityImages';

vi.mock('@/hooks/useEntityImages', () => ({
  useEntityImages: vi.fn(),
  useAttachmentImageUrl: vi.fn(() => 'blob:mock'),
}));

const mockUseEntityImages = vi.mocked(useEntityImages);

function renderGallery(assetId = 5) {
  return render(
    <I18nextProvider i18n={i18n}>
      <AssetGallery assetId={assetId} />
    </I18nextProvider>
  );
}

describe('AssetGallery', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('queries images for the ASSET entity type', () => {
    mockUseEntityImages.mockReturnValue({ images: [], isLoading: false });
    renderGallery();
    expect(mockUseEntityImages).toHaveBeenCalledWith('ASSET', 5);
  });

  it('renders a thumbnail per image attachment', () => {
    mockUseEntityImages.mockReturnValue({
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
    mockUseEntityImages.mockReturnValue({ images: [], isLoading: false });
    renderGallery();
    expect(screen.getByText(/No images yet/)).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
});

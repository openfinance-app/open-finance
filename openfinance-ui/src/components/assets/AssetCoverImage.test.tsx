import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { AssetCoverImage } from './AssetCoverImage';
import { useEntityImages, useAttachmentImageUrl } from '@/hooks/useEntityImages';
import { AttachmentEntityType } from '@/types/attachment';
import type { Asset } from '@/types/asset';

vi.mock('@/hooks/useEntityImages');
const mockedUseEntityImages = useEntityImages as unknown as ReturnType<typeof vi.fn>;
const mockedUseAttachmentImageUrl = useAttachmentImageUrl as unknown as ReturnType<typeof vi.fn>;

const asset = (overrides = {}): Asset =>
  ({ id: 7, name: 'Vintage Watch', photoPath: null, ...overrides }) as Asset;

describe('AssetCoverImage', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
    mockedUseEntityImages.mockReturnValue({ images: [] });
    mockedUseAttachmentImageUrl.mockReturnValue(null);
  });

  it('requests images for the asset entity and this asset id', () => {
    renderWithProviders(<AssetCoverImage asset={asset()} />);
    expect(mockedUseEntityImages).toHaveBeenCalledWith(AttachmentEntityType.ASSET, 7);
    expect(mockedUseAttachmentImageUrl).toHaveBeenCalledWith(null);
  });

  it('prefers the first attachment image over the legacy photoPath', () => {
    mockedUseEntityImages.mockReturnValue({ images: [{ id: 11 }, { id: 12 }] });
    mockedUseAttachmentImageUrl.mockReturnValue('blob:attachment-11');
    renderWithProviders(<AssetCoverImage asset={asset({ photoPath: '/legacy.jpg' })} />);

    expect(mockedUseAttachmentImageUrl).toHaveBeenCalledWith(11);
    const img = screen.getByRole('img', { name: 'Vintage Watch' });
    expect(img).toHaveAttribute('src', 'blob:attachment-11');
  });

  it('falls back to the legacy photoPath when there are no attachments', () => {
    renderWithProviders(<AssetCoverImage asset={asset({ photoPath: '/legacy.jpg' })} />);
    expect(screen.getByRole('img', { name: 'Vintage Watch' })).toHaveAttribute(
      'src',
      '/legacy.jpg'
    );
  });

  it('uses the legacy photoPath while the attachment URL is still loading', () => {
    mockedUseEntityImages.mockReturnValue({ images: [{ id: 11 }] });
    mockedUseAttachmentImageUrl.mockReturnValue(null);
    renderWithProviders(<AssetCoverImage asset={asset({ photoPath: '/legacy.jpg' })} />);
    expect(screen.getByRole('img', { name: 'Vintage Watch' })).toHaveAttribute(
      'src',
      '/legacy.jpg'
    );
  });

  it('shows the no-photo placeholder when neither source exists', () => {
    renderWithProviders(<AssetCoverImage asset={asset()} />);
    expect(screen.queryByRole('img')).toBeNull();
    expect(screen.getByText(/no photo/i)).toBeInTheDocument();
  });
});

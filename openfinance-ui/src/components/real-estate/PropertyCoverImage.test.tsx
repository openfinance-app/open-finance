import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { PropertyCoverImage } from './PropertyCoverImage';
import { usePropertyImages, useAttachmentImageUrl } from '@/hooks/usePropertyImages';

vi.mock('@/hooks/usePropertyImages');
const mockedUsePropertyImages = usePropertyImages as unknown as ReturnType<typeof vi.fn>;
const mockedUseAttachmentImageUrl = useAttachmentImageUrl as unknown as ReturnType<typeof vi.fn>;

describe('PropertyCoverImage', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.clearAllMocks();
    mockedUsePropertyImages.mockReturnValue({ images: [] });
    mockedUseAttachmentImageUrl.mockReturnValue(null);
  });

  it('shows the placeholder icon when the property has no images', () => {
    renderWithProviders(
      <PropertyCoverImage propertyId={5} placeholderIcon={<span>house-icon</span>} />
    );
    expect(mockedUsePropertyImages).toHaveBeenCalledWith(5);
    expect(screen.getByText('house-icon')).toBeInTheDocument();
    expect(screen.queryByRole('img')).toBeNull();
  });

  it('shows the first image attachment as a decorative cover', () => {
    mockedUsePropertyImages.mockReturnValue({ images: [{ id: 31 }, { id: 32 }] });
    mockedUseAttachmentImageUrl.mockReturnValue('blob:property-31');
    const { container } = renderWithProviders(
      <PropertyCoverImage propertyId={5} placeholderIcon={<span>house-icon</span>} />
    );

    expect(mockedUseAttachmentImageUrl).toHaveBeenCalledWith(31);
    const img = container.querySelector('img');
    expect(img).toHaveAttribute('src', 'blob:property-31');
    expect(img).toHaveAttribute('alt', ''); // decorative: card already labels the property
    expect(screen.queryByText('house-icon')).toBeNull();
  });

  it('keeps the placeholder while the attachment URL is loading', () => {
    mockedUsePropertyImages.mockReturnValue({ images: [{ id: 31 }] });
    mockedUseAttachmentImageUrl.mockReturnValue(null);
    renderWithProviders(
      <PropertyCoverImage propertyId={5} placeholderIcon={<span>house-icon</span>} />
    );
    expect(screen.getByText('house-icon')).toBeInTheDocument();
    expect(screen.queryByRole('img')).toBeNull();
  });
});

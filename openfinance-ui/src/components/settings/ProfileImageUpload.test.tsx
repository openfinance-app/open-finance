import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/test/i18n-test';

const mockUploadMutate = vi.fn();
const mockDeleteMutate = vi.fn();

vi.mock('@/hooks/useAuth', () => ({
  useUploadProfileImage: () => ({
    mutate: mockUploadMutate,
    isPending: false,
  }),
  useDeleteProfileImage: () => ({
    mutate: mockDeleteMutate,
    isPending: false,
  }),
}));

import { ProfileImageUpload } from './ProfileImageUpload';

function Wrapper({ children }: { children: React.ReactNode }) {
  return <I18nextProvider i18n={i18n}>{children}</I18nextProvider>;
}

describe('ProfileImageUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders initials when no image', () => {
    render(<ProfileImageUpload username="John Doe" />, { wrapper: Wrapper });
    expect(screen.getByText('JD')).toBeInTheDocument();
  });

  it('renders single-word username initials', () => {
    render(<ProfileImageUpload username="Alice" />, { wrapper: Wrapper });
    expect(screen.getByText('AL')).toBeInTheDocument();
  });

  it('renders profile image when provided', () => {
    render(
      <ProfileImageUpload username="John" currentImage="data:image/png;base64,abc" />,
      { wrapper: Wrapper }
    );
    const img = screen.getByAltText(/profile/i);
    expect(img).toHaveAttribute('src', 'data:image/png;base64,abc');
  });

  it('renders file input for upload', () => {
    render(<ProfileImageUpload username="John" />, { wrapper: Wrapper });
    const fileInput = document.querySelector('input[type="file"]');
    expect(fileInput).toBeInTheDocument();
    expect(fileInput).toHaveAttribute('accept');
  });

  it('shows delete button when image exists', () => {
    render(
      <ProfileImageUpload username="John" currentImage="data:image/png;base64,abc" />,
      { wrapper: Wrapper }
    );
    // Look for delete/remove button
    const deleteButton = screen.getByRole('button', { name: /delete|remove/i });
    expect(deleteButton).toBeInTheDocument();
  });

  it('validates file type on upload', () => {
    render(<ProfileImageUpload username="John" />, { wrapper: Wrapper });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const invalidFile = new File(['test'], 'test.txt', { type: 'text/plain' });
    Object.defineProperty(fileInput, 'files', { value: [invalidFile], writable: false });
    fireEvent.change(fileInput);
    // Should show validation error about unsupported file type
    waitFor(() => {
      expect(screen.getByText(/unsupported|file type/i)).toBeInTheDocument();
    });
  });

  it('validates file size on upload', () => {
    render(<ProfileImageUpload username="John" />, { wrapper: Wrapper });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    // Create a file larger than 2MB
    const bigContent = new Uint8Array(3 * 1024 * 1024);
    const bigFile = new File([bigContent], 'big.jpg', { type: 'image/jpeg' });
    Object.defineProperty(fileInput, 'files', { value: [bigFile], writable: false });
    fireEvent.change(fileInput);
    waitFor(() => {
      expect(screen.getByText(/too large|size/i)).toBeInTheDocument();
    });
  });

  it('calls upload mutation with valid file', () => {
    render(<ProfileImageUpload username="John" />, { wrapper: Wrapper });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const validFile = new File(['test'], 'photo.jpg', { type: 'image/jpeg' });
    Object.defineProperty(fileInput, 'files', { value: [validFile], writable: false });
    fireEvent.change(fileInput);
    expect(mockUploadMutate).toHaveBeenCalledWith(validFile, expect.any(Object));
  });

  it('calls delete mutation on delete click', () => {
    render(
      <ProfileImageUpload username="John" currentImage="data:image/png;base64,abc" />,
      { wrapper: Wrapper }
    );
    const deleteButton = screen.getByRole('button', { name: /delete|remove/i });
    fireEvent.click(deleteButton);
    expect(mockDeleteMutate).toHaveBeenCalled();
  });

  it('renders question mark for empty username', () => {
    render(<ProfileImageUpload username="" />, { wrapper: Wrapper });
    expect(screen.getByText('?')).toBeInTheDocument();
  });
});

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { ProfileImageUpload } from '@/components/settings/ProfileImageUpload';
import i18n from '@/test/i18n-test';
const state = vi.hoisted(() => ({
  upload: vi.fn(),
  remove: vi.fn(),
  uploading: false,
  removing: false,
}));
vi.mock('@/hooks/useAuth', async original => ({
  ...(await original<typeof import('@/hooks/useAuth')>()),
  useUploadProfileImage: () => ({ mutate: state.upload, isPending: state.uploading }),
  useDeleteProfileImage: () => ({ mutate: state.remove, isPending: state.removing }),
}));
const tr = (key: string) => i18n.t(`settings:profile.${key}`);
const oldImage = 'data:image/png;base64,b2xk';
beforeEach(() => {
  mockAuthentication();
  state.upload.mockReset();
  state.remove.mockReset();
  state.uploading = false;
  state.removing = false;
});
describe('Profile image request lifecycle', () => {
  it.each(['upload', 'remove'] as const)('locks controls while %s is pending', operation => {
    state.uploading = operation === 'upload';
    state.removing = operation === 'remove';
    renderWithProviders(<ProfileImageUpload username="User" currentImage={oldImage} />);
    expect(screen.getByLabelText(tr('chooseProfilePhoto'))).toBeDisabled();
    expect(screen.getByRole('button')).toBeDisabled();
  });
  it('cancelling the chooser does not replace the current image', () => {
    renderWithProviders(<ProfileImageUpload username="User" currentImage={oldImage} />);
    fireEvent.change(screen.getByLabelText(tr('chooseProfilePhoto')), { target: { files: [] } });
    expect(state.upload).not.toHaveBeenCalled();
    expect(screen.getByRole('img')).toHaveAttribute('src', oldImage);
  });
  it.each(['upload', 'remove'] as const)('shows success after %s completes', async operation => {
    renderWithProviders(<ProfileImageUpload username="User" currentImage={oldImage} />);
    if (operation === 'upload') {
      fireEvent.change(screen.getByLabelText(tr('chooseProfilePhoto')), {
        target: { files: [new File(['photo'], 'photo.png', { type: 'image/png' })] },
      });
      await waitFor(() => expect(screen.getByRole('img').getAttribute('src')).not.toBe(oldImage));
    } else fireEvent.click(screen.getByRole('button'));
    act(() => state[operation === 'upload' ? 'upload' : 'remove'].mock.calls[0][1].onSuccess());
    expect(
      screen.getByText(tr(operation === 'upload' ? 'uploadSuccess' : 'removeSuccess'))
    ).toBeVisible();
  });
  it.each([
    [
      'upload',
      { response: { data: { message: 'Invalid image encoding' } } },
      'Invalid image encoding',
    ],
    ['upload', { response: { data: null } }, undefined],
    ['upload', {}, undefined],
    ['remove', { response: { data: { message: 'Image is locked' } } }, 'Image is locked'],
    ['remove', { response: { data: null } }, undefined],
    ['remove', {}, undefined],
  ] as const)(
    'restores the saved image and exposes a safe %s error',
    async (operation, error, expected) => {
      renderWithProviders(<ProfileImageUpload username="User" currentImage={oldImage} />);
      if (operation === 'upload') {
        fireEvent.change(screen.getByLabelText(tr('chooseProfilePhoto')), {
          target: { files: [new File(['photo'], 'photo.png', { type: 'image/png' })] },
        });
        await waitFor(() => expect(screen.getByRole('img').getAttribute('src')).not.toBe(oldImage));
      } else fireEvent.click(screen.getByRole('button'));
      act(() =>
        state[operation === 'upload' ? 'upload' : 'remove'].mock.calls[0][1].onError(error)
      );
      expect(screen.getByRole('alert')).toHaveTextContent(
        expected ?? tr(operation === 'upload' ? 'uploadError' : 'removeError')
      );
      expect(screen.getByRole('img')).toHaveAttribute('src', oldImage);
    }
  );
});

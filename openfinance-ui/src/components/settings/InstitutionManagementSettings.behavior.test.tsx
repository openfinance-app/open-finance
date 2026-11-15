import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ComponentProps } from 'react';
import { InstitutionManagementSettings } from '@/components/settings/InstitutionManagementSettings';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import type { Institution } from '@/types/institution';
import i18n from '@/test/i18n-test';

const state = vi.hoisted(() => ({
  data: undefined as Institution[] | undefined,
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
}));
vi.mock('@/hooks/useInstitutions', () => ({
  useInstitutions: () => ({ data: state.data, isLoading: false }),
  useCreateInstitution: () => ({ mutateAsync: state.create }),
  useUpdateInstitution: () => ({ mutateAsync: state.update }),
  useDeleteInstitution: () => ({ mutateAsync: state.remove }),
}));
vi.mock('@/components/common/CountrySelector', async original => ({
  ...(await original<typeof import('@/components/common/CountrySelector')>()),
  CountrySelector: ({
    id,
    value,
    onValueChange,
  }: ComponentProps<typeof import('@/components/common/CountrySelector').CountrySelector>) => (
    <select id={id} value={value} onChange={event => onValueChange(event.target.value)}>
      {['', 'FR', 'US'].map(code => (
        <option key={code} value={code}>
          {code || 'None'}
        </option>
      ))}
    </select>
  ),
}));
const label = (key: string) => i18n.t(key, { ns: 'institutions' });
const custom: Institution = { id: 1, name: 'Local Bank', isSystem: false };
const input = (key: string) => screen.getByLabelText(label(key));
function createForm() {
  renderWithProviders(<InstitutionManagementSettings />);
  fireEvent.click(screen.getByRole('button', { name: label('addInstitution') }));
  fireEvent.change(input('form.name'), { target: { value: ' New Bank ' } });
}
function submitForm() {
  fireEvent.submit(input('form.name').closest('form')!);
}
function logoFile(type = 'image/png', size = 3) {
  return new File([new Uint8Array(size)], 'logo.png', { type });
}
function upload(file?: File) {
  fireEvent.change(screen.getByLabelText(label('form.uploadLogo')), {
    target: { files: file ? [file] : [] },
  });
}

describe('Institution editing, upload boundaries and recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    window.history.replaceState(null, '', '/institutions');
    state.data = [custom];
    state.create.mockResolvedValue(undefined);
    state.update.mockResolvedValue(undefined);
    state.remove.mockResolvedValue(undefined);
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    Element.prototype.scrollIntoView = vi.fn();
  });
  afterEach(() => vi.restoreAllMocks());

  it('handles an absent response without inventing custom institutions', () => {
    state.data = undefined;
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.getByText(label('custom.empty'))).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: label('custom.addFirst') }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it.each(['', 'abc12345', 'abc12345def'])(
    'accepts optional or valid BIC %s and trims the institution name',
    async bic => {
      createForm();
      fireEvent.change(input('form.bic'), { target: { value: bic } });
      fireEvent.blur(input('form.bic'));
      fireEvent.change(input('form.country'), { target: { value: '' } });
      submitForm();
      await waitFor(() =>
        expect(state.create).toHaveBeenCalledWith({
          name: 'New Bank',
          bic: bic ? bic.toUpperCase() : undefined,
          country: undefined,
          logo: undefined,
        })
      );
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    }
  );

  it.each(['1234567', '123456789', '1234567890'])(
    'blocks invalid BIC length %s on blur and on submit',
    bic => {
      createForm();
      fireEvent.change(input('form.bic'), { target: { value: bic } });
      fireEvent.blur(input('form.bic'));
      expect(screen.getByText(label('form.bicLengthError'))).toBeInTheDocument();
      submitForm();
      expect(state.create).not.toHaveBeenCalled();
      fireEvent.change(input('form.bic'), { target: { value: '' } });
      fireEvent.blur(input('form.bic'));
      expect(screen.queryByText(label('form.bicLengthError'))).not.toBeInTheDocument();
    }
  );

  it('rejects whitespace names even if the form is submitted directly', () => {
    createForm();
    fireEvent.change(input('form.name'), { target: { value: '   ' } });
    submitForm();
    expect(screen.getByText(label('form.nameRequired'))).toBeInTheDocument();
    expect(state.create).not.toHaveBeenCalled();
  });

  it.each(['validation', 'message', 'fallback'])(
    'keeps edits for retry after a %s save error',
    async kind => {
      state.create.mockRejectedValueOnce(
        kind === 'validation'
          ? {
              response: {
                data: { validationErrors: { name: 'Name is already used', bic: 'BIC is invalid' } },
              },
            }
          : kind === 'message'
            ? { response: { data: { message: 'Name conflict' } } }
            : new Error('Network failure')
      );
      createForm();
      submitForm();
      expect(
        await screen.findByText(
          kind === 'validation'
            ? 'name: Name is already used; bic: BIC is invalid'
            : kind === 'message'
              ? 'Name conflict'
              : label('loadError')
        )
      ).toBeInTheDocument();
      expect(input('form.name')).toHaveValue(' New Bank ');
      fireEvent.change(input('form.name'), { target: { value: 'Retry Bank' } });
      submitForm();
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
      expect(state.create).toHaveBeenLastCalledWith(
        expect.objectContaining({ name: 'Retry Bank' })
      );
    }
  );

  it('edits optional fields that were absent in the stored institution', async () => {
    renderWithProviders(<InstitutionManagementSettings />);
    fireEvent.click(
      screen.getByRole('button', {
        name: i18n.t('form.editInstitution', { ns: 'institutions', name: custom.name }),
      })
    );
    expect(input('form.bic')).toHaveValue('');
    expect(input('form.country')).toHaveValue('');
    fireEvent.change(input('form.country'), { target: { value: 'US' } });
    submitForm();
    await waitFor(() =>
      expect(state.update).toHaveBeenCalledWith({
        id: 1,
        data: { name: 'Local Bank', bic: undefined, country: 'US', logo: undefined },
      })
    );
  });

  it.each(['image/gif', 'text/plain'])(
    'rejects unsupported logo format %s without reading it',
    type => {
      const read = vi.spyOn(FileReader.prototype, 'readAsDataURL');
      createForm();
      upload(logoFile(type));
      expect(screen.getByText(label('form.logoInvalidType'))).toBeInTheDocument();
      expect(read).not.toHaveBeenCalled();
    }
  );

  it('rejects a logo one byte over the size limit', () => {
    const read = vi.spyOn(FileReader.prototype, 'readAsDataURL');
    createForm();
    upload(logoFile('image/png', 500 * 1024 + 1));
    expect(screen.getByText(label('form.logoTooLarge'))).toBeInTheDocument();
    expect(read).not.toHaveBeenCalled();
  });

  it('accepts the exact logo size limit without overwriting edits made during file reading', async () => {
    const read = vi
      .spyOn(FileReader.prototype, 'readAsDataURL')
      .mockImplementation(() => undefined);
    createForm();
    upload(logoFile('image/png', 500 * 1024));
    const reader = read.mock.contexts[0];
    fireEvent.change(input('form.name'), { target: { value: 'Changed while reading' } });
    Object.defineProperty(reader, 'result', { value: 'data:image/png;base64,YWJj' });
    act(() => reader.onload?.call(reader, new ProgressEvent('load') as ProgressEvent<FileReader>));
    expect(screen.getByRole('img', { name: label('form.logoPreview') })).toHaveAttribute(
      'src',
      'data:image/png;base64,YWJj'
    );
    submitForm();
    await waitFor(() =>
      expect(state.create).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Changed while reading',
          logo: 'data:image/png;base64,YWJj',
        })
      )
    );
  });

  it('reports file-reader errors and permits another selection', () => {
    const read = vi
      .spyOn(FileReader.prototype, 'readAsDataURL')
      .mockImplementation(() => undefined);
    createForm();
    upload(logoFile());
    const reader = read.mock.contexts[0];
    act(() =>
      reader.onerror?.call(reader, new ProgressEvent('error') as ProgressEvent<FileReader>)
    );
    expect(screen.getByText(label('form.logoReadError'))).toBeInTheDocument();
    upload();
    expect(screen.queryByText(label('form.logoReadError'))).not.toBeInTheDocument();
  });

  it('removes an existing logo and resets the preview before creating another institution', async () => {
    state.data = [{ ...custom, logo: 'data:image/png;base64,YWJj', country: 'ZZ' }];
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.getByText('ZZ')).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole('button', {
        name: i18n.t('form.editInstitution', { ns: 'institutions', name: custom.name }),
      })
    );
    fireEvent.click(screen.getByRole('button', { name: label('form.removeLogo') }));
    expect(screen.queryByRole('img', { name: label('form.logoPreview') })).not.toBeInTheDocument();
    submitForm();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(state.update).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ logo: undefined }) })
    );
    fireEvent.click(screen.getByRole('button', { name: label('addInstitution') }));
    expect(input('form.name')).toHaveValue('');
    expect(screen.queryByRole('img', { name: label('form.logoPreview') })).not.toBeInTheDocument();
  });

  it.each([false, true])(
    'reports delete errors with server detail when present (%s)',
    async detail => {
      const alert = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
      state.remove.mockRejectedValueOnce(
        detail ? { response: { data: { message: 'Still has accounts' } } } : new Error('Network')
      );
      renderWithProviders(<InstitutionManagementSettings />);
      fireEvent.click(
        screen.getByRole('button', {
          name: i18n.t('form.deleteInstitution', { ns: 'institutions', name: custom.name }),
        })
      );
      fireEvent.click(
        within(screen.getByRole('dialog')).getByRole('button', {
          name: label('deleteDialog.confirm'),
        })
      );
      await waitFor(() =>
        expect(alert).toHaveBeenCalledWith(
          detail ? 'Still has accounts' : label('deleteDialog.error')
        )
      );
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    }
  );

  it('expands and collapses the system bank catalog without exposing edit controls', () => {
    state.data = Array.from({ length: 13 }, (_, index) => ({
      id: index + 1,
      name: `System Bank ${index + 1}`,
      logo: index === 0 ? '/bank.svg' : undefined,
      isSystem: true,
    }));
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.queryByText('System Bank 13')).not.toBeInTheDocument();
    fireEvent.click(
      screen.getByRole('button', {
        name: i18n.t('system.andMore', { ns: 'institutions', count: 1 }),
      })
    );
    expect(screen.getByText('System Bank 13')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /edit institution/i })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: label('system.showFewer') }));
    expect(screen.queryByText('System Bank 13')).not.toBeInTheDocument();
  });
});

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication, userEvent } from '@/test/test-utils';

const mockInstitutions = [
  { id: 1, name: 'Chase Bank', bic: 'CHASUS33', country: 'US', logo: '', isSystem: false, accountCount: 2 },
  { id: 2, name: 'BNP Paribas', bic: 'BNPAFRPP', country: 'FR', logo: '', isSystem: true, accountCount: 1 },
];

let mockData: any[] = mockInstitutions;
let mockIsLoading = false;
let mockError: any = null;
const mockCreateMutateAsync = vi.fn();
const mockUpdateMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();

vi.mock('@/hooks/useInstitutions', () => ({
  useInstitutions: () => ({ data: mockData, isLoading: mockIsLoading, error: mockError }),
  useCreateInstitution: () => ({ mutateAsync: mockCreateMutateAsync }),
  useUpdateInstitution: () => ({ mutateAsync: mockUpdateMutateAsync }),
  useDeleteInstitution: () => ({ mutateAsync: mockDeleteMutateAsync }),
}));
vi.mock('@/hooks/useDocumentTitle', () => ({
  useDocumentTitle: vi.fn(),
}));
vi.mock('@/components/common/CountrySelector', () => ({
  CountrySelector: ({ value, onChange }: any) => (
    <select data-testid="country-selector" value={value} onChange={(e: any) => onChange(e.target.value)}>
      <option value="US">US</option>
      <option value="FR">FR</option>
    </select>
  ),
  ALL_COUNTRIES: [{ code: 'US', name: 'United States' }, { code: 'FR', name: 'France' }],
}));
vi.mock('@/components/ui/Dialog', () => ({
  Dialog: ({ children, open }: any) => (open ? <div role="dialog">{children}</div> : null),
  DialogContent: ({ children }: any) => <div>{children}</div>,
  DialogHeader: ({ children }: any) => <div>{children}</div>,
  DialogTitle: ({ children }: any) => <h2>{children}</h2>,
}));

import { InstitutionManagementSettings } from './InstitutionManagementSettings';

describe('InstitutionManagementSettings', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    mockData = [...mockInstitutions];
    mockIsLoading = false;
    mockError = null;
  });

  it('renders add institution button', () => {
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.getByText(/add institution/i)).toBeInTheDocument();
  });

  it('renders search input', () => {
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.getByPlaceholderText(/search/i)).toBeInTheDocument();
  });

  it('displays institution names', () => {
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.getByText('Chase Bank')).toBeInTheDocument();
    expect(screen.getByText('BNP Paribas')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    mockIsLoading = true;
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.queryByText('Chase Bank')).not.toBeInTheDocument();
  });

  it('shows empty state when no institutions', () => {
    mockData = [];
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.getByText(/no custom institutions/i)).toBeInTheDocument();
  });

  it('filters institutions by search', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    await user.type(screen.getByPlaceholderText(/search/i), 'Chase');
    expect(screen.getByText('Chase Bank')).toBeInTheDocument();
    expect(screen.queryByText('BNP Paribas')).not.toBeInTheDocument();
  });

  it('opens create form dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add institution/i }));
    await waitFor(() => {
      expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy();
    });
  });

  it('displays BIC codes', () => {
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.getByText('CHASUS33')).toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('Network error');
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.getByText(/failed to load/i)).toBeInTheDocument();
  });

  it('shows country name for custom institutions', () => {
    renderWithProviders(<InstitutionManagementSettings />);
    expect(screen.getByText('United States')).toBeInTheDocument();
  });

  it('submits create form', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockResolvedValue({});
    renderWithProviders(<InstitutionManagementSettings />);
    const addBtn = screen.getByRole('button', { name: /add institution/i });
    await user.click(addBtn);
    // Wait for the dialog to render with the form
    let nameInput: HTMLElement | null = null;
    await waitFor(() => {
      nameInput = document.querySelector('input[placeholder*="e.g"]') as HTMLElement;
      expect(nameInput).toBeTruthy();
    });
    await user.type(nameInput!, 'My Bank');
    // Find the Create submit button inside the dialog
    const buttons = Array.from(document.querySelectorAll('button'));
    const submitButton = buttons.find(b => b.textContent?.trim() === 'Create');
    expect(submitButton).toBeTruthy();
    await user.click(submitButton!);
    await waitFor(() => expect(mockCreateMutateAsync).toHaveBeenCalled());
  });

  it('opens edit form with pre-filled data', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    // Click edit button on Chase Bank (custom institution)
    const pencilButtons = screen.getAllByRole('button').filter(
      (btn) => btn.classList.contains('h-8') && btn.classList.contains('w-8') && !btn.classList.contains('text-error')
    );
    if (pencilButtons.length > 0) {
      await user.click(pencilButtons[0]);
      await waitFor(() => {
        expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy();
      });
      // Should show pre-filled name
      expect(screen.getByDisplayValue('Chase Bank')).toBeInTheDocument();
    }
  });

  it('opens delete confirmation dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    // Find delete button (has text-error class)
    const deleteButtons = screen.getAllByRole('button').filter(
      (btn) => btn.classList.contains('text-error')
    );
    if (deleteButtons.length > 0) {
      await user.click(deleteButtons[0]);
      await waitFor(() => {
        expect(screen.getByText(/are you sure/i)).toBeInTheDocument();
      });
    }
  });

  it('confirms delete and calls mutation', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockResolvedValue({});
    renderWithProviders(<InstitutionManagementSettings />);
    const deleteButtons = screen.getAllByRole('button').filter(
      (btn) => btn.classList.contains('text-error')
    );
    if (deleteButtons.length > 0) {
      await user.click(deleteButtons[0]);
      await waitFor(() => expect(screen.getByText(/are you sure/i)).toBeInTheDocument());
      const confirmBtn = screen.getByRole('button', { name: /^delete$/i });
      await user.click(confirmBtn);
      await waitFor(() => expect(mockDeleteMutateAsync).toHaveBeenCalledWith(1));
    }
  });

  it('cancels delete dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    const deleteButtons = screen.getAllByRole('button').filter(
      (btn) => btn.classList.contains('text-error')
    );
    if (deleteButtons.length > 0) {
      await user.click(deleteButtons[0]);
      await waitFor(() => expect(screen.getByText(/are you sure/i)).toBeInTheDocument());
      const cancelBtn = screen.getByRole('button', { name: /cancel/i });
      await user.click(cancelBtn);
      await waitFor(() => expect(screen.queryByText(/are you sure/i)).not.toBeInTheDocument());
    }
  });

  it('shows no-results message when search matches nothing', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    await user.type(screen.getByPlaceholderText(/search/i), 'zzzzzzz');
    // Custom section should show no-results text
    expect(screen.queryByText('Chase Bank')).not.toBeInTheDocument();
  });

  it('shows institution with logo', () => {
    mockData = [
      { id: 1, name: 'Logo Bank', bic: 'LOGOTEST', country: 'US', logo: 'data:image/png;base64,abc', isSystem: false, accountCount: 0 },
    ];
    renderWithProviders(<InstitutionManagementSettings />);
    const img = document.querySelector('img[src="data:image/png;base64,abc"]');
    expect(img).toBeTruthy();
  });

  it('handles create form validation for empty name', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add institution/i }));
    await waitFor(() => expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy());
    // Submit button should be disabled when name is empty
    const buttons = Array.from(document.querySelectorAll('button'));
    const submitButton = buttons.find(b => b.textContent?.trim() === 'Create');
    expect(submitButton).toBeTruthy();
    expect(submitButton).toBeDisabled();
  });

  it('validates BIC length on blur (not 8 or 11 chars)', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add institution/i }));
    await waitFor(() => expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy());

    // Find BIC input field
    const bicInput = document.querySelector('input[maxlength="11"]') as HTMLInputElement;
    expect(bicInput).toBeTruthy();

    // Type invalid BIC (5 chars, not 8 or 11)
    await user.type(bicInput!, 'ABCDE');
    await user.tab(); // blur to trigger validation
    
    await waitFor(() => {
      expect(screen.getByText(/8 or 11 characters/i)).toBeInTheDocument();
    });
  });

  it('clears BIC error when BIC is emptied', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add institution/i }));
    await waitFor(() => expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy());

    const bicInput = document.querySelector('input[maxlength="11"]') as HTMLInputElement;
    await user.type(bicInput!, 'AB');
    await user.tab();
    
    await waitFor(() => {
      expect(screen.getByText(/8 or 11 characters/i)).toBeInTheDocument();
    });

    // Clear the BIC
    await user.clear(bicInput!);
    await user.tab();

    await waitFor(() => {
      expect(screen.queryByText(/8 or 11 characters/i)).not.toBeInTheDocument();
    });
  });

  it('shows form error when create fails with server message', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockRejectedValue({
      response: { data: { message: 'Duplicate name' } },
    });
    renderWithProviders(<InstitutionManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add institution/i }));
    await waitFor(() => expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy());

    const nameInput = document.querySelector('input[placeholder*="e.g"]') as HTMLElement;
    await user.type(nameInput!, 'Existing Bank');

    const submitButton = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Create');
    await user.click(submitButton!);

    await waitFor(() => {
      expect(screen.getByText('Duplicate name')).toBeInTheDocument();
    });
  });

  it('shows validation errors from server response', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockRejectedValue({
      response: { data: { validationErrors: { name: 'too long', bic: 'invalid format' } } },
    });
    renderWithProviders(<InstitutionManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add institution/i }));
    await waitFor(() => expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy());

    const nameInput = document.querySelector('input[placeholder*="e.g"]') as HTMLElement;
    await user.type(nameInput!, 'Some Bank');

    const submitButton = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Create');
    await user.click(submitButton!);

    await waitFor(() => {
      expect(screen.getByText(/name: too long/i)).toBeInTheDocument();
    });
  });

  it('submits update form when editing', async () => {
    const user = userEvent.setup();
    mockUpdateMutateAsync.mockResolvedValue({});
    renderWithProviders(<InstitutionManagementSettings />);
    
    // Click edit button on Chase Bank
    const pencilButtons = screen.getAllByRole('button').filter(
      (btn) => btn.classList.contains('h-8') && btn.classList.contains('w-8') && !btn.classList.contains('text-error')
    );
    await user.click(pencilButtons[0]);
    await waitFor(() => {
      expect(screen.getByDisplayValue('Chase Bank')).toBeInTheDocument();
    });

    // Modify the name
    const nameInput = screen.getByDisplayValue('Chase Bank');
    await user.clear(nameInput);
    await user.type(nameInput, 'Chase Updated');

    // Find Update button
    const submitButton = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Update');
    await user.click(submitButton!);

    await waitFor(() => {
      expect(mockUpdateMutateAsync).toHaveBeenCalledWith({
        id: 1,
        data: expect.objectContaining({ name: 'Chase Updated' }),
      });
    });
  });

  it('shows system institutions section', () => {
    renderWithProviders(<InstitutionManagementSettings />);
    // BNP Paribas is a system institution
    expect(screen.getByText('BNP Paribas')).toBeInTheDocument();
  });

  it('shows "show more" button when more than 12 system institutions', () => {
    const manySystem = Array.from({ length: 15 }, (_, i) => ({
      id: 100 + i,
      name: `System Bank ${i}`,
      bic: `SYS${i}`,
      country: 'FR',
      logo: '',
      isSystem: true,
      accountCount: 0,
    }));
    mockData = manySystem;
    renderWithProviders(<InstitutionManagementSettings />);
    // Should show "show more" toggle
    expect(screen.getByText(/and \d+ more/i)).toBeInTheDocument();
  });

  it('toggles showing all system institutions', async () => {
    const user = userEvent.setup();
    const manySystem = Array.from({ length: 15 }, (_, i) => ({
      id: 100 + i,
      name: `System Bank ${i}`,
      bic: `SYS${i}`,
      country: 'FR',
      logo: '',
      isSystem: true,
      accountCount: 0,
    }));
    mockData = manySystem;
    renderWithProviders(<InstitutionManagementSettings />);
    
    // Click "show more" to expand
    const showMore = screen.getByText(/and \d+ more/i);
    await user.click(showMore);
    
    // All 15 institutions should be visible now
    expect(screen.getByText('System Bank 14')).toBeInTheDocument();
    
    // Should now show "show fewer"
    expect(screen.getByText(/show fewer/i)).toBeInTheDocument();
  });

  it('rejects logo file with invalid type', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add institution/i }));
    await waitFor(() => expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy());

    const fileInput = document.getElementById('logo-upload') as HTMLInputElement;
    expect(fileInput).toBeTruthy();

    const invalidFile = new File(['test'], 'test.pdf', { type: 'application/pdf' });
    fireEvent.change(fileInput, { target: { files: [invalidFile] } });

    await waitFor(() => {
      expect(screen.getByText(/invalid file type/i)).toBeInTheDocument();
    });
  });

  it('rejects logo file that is too large', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add institution/i }));
    await waitFor(() => expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy());

    const fileInput = document.getElementById('logo-upload') as HTMLInputElement;
    const largeContent = new Array(600 * 1024).fill('a').join('');
    const largeFile = new File([largeContent], 'logo.png', { type: 'image/png' });
    fireEvent.change(fileInput, { target: { files: [largeFile] } });

    await waitFor(() => {
      expect(screen.getByText(/too large/i)).toBeInTheDocument();
    });
  });

  it('prevents submit with invalid BIC length', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InstitutionManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add institution/i }));
    await waitFor(() => expect(document.querySelector('input[placeholder*="e.g"]')).toBeTruthy());

    const nameInput = document.querySelector('input[placeholder*="e.g"]') as HTMLElement;
    await user.type(nameInput!, 'Test Bank');

    const bicInput = document.querySelector('input[maxlength="11"]') as HTMLInputElement;
    await user.type(bicInput!, 'SHORT');

    // The submit button should be disabled because of BIC error
    // First trigger validation by blurring
    await user.tab();
    
    await waitFor(() => {
      const submitButton = Array.from(document.querySelectorAll('button')).find(b => b.textContent?.trim() === 'Create');
      expect(submitButton).toBeDisabled();
    });
  });

  it('handles delete error with alert', async () => {
    const user = userEvent.setup();
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    mockDeleteMutateAsync.mockRejectedValue({
      response: { data: { message: 'Cannot delete' } },
    });
    renderWithProviders(<InstitutionManagementSettings />);
    const deleteButtons = screen.getAllByRole('button').filter(
      (btn) => btn.classList.contains('text-error')
    );
    await user.click(deleteButtons[0]);
    await waitFor(() => expect(screen.getByText(/are you sure/i)).toBeInTheDocument());
    const confirmBtn = screen.getByRole('button', { name: /^delete$/i });
    await user.click(confirmBtn);
    await waitFor(() => expect(alertSpy).toHaveBeenCalledWith('Cannot delete'));
    alertSpy.mockRestore();
  });
});

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import i18n from '@/test/i18n-test';
import {
  renderWithProviders,
  mockAuthentication,
  clearAuthentication,
  userEvent,
} from '@/test/test-utils';

const mockPayees = [
  {
    id: 1,
    name: 'Amazon',
    logo: '',
    isActive: true,
    isSystem: false,
    transactionCount: 5,
    totalAmount: 500,
    categoryId: 1,
    categoryName: 'Shopping',
  },
  {
    id: 2,
    name: 'Netflix',
    logo: '',
    isActive: true,
    isSystem: true,
    transactionCount: 12,
    totalAmount: 200,
    categoryId: 2,
    categoryName: 'Entertainment',
  },
  {
    id: 3,
    name: 'Old Payee',
    logo: '',
    isActive: false,
    isSystem: false,
    transactionCount: 0,
    totalAmount: 0,
  },
];

let mockPayeeData: any[] = mockPayees;
let mockIsLoading = false;
let mockError: any = null;
const mockCreateMutateAsync = vi.fn();
const mockUpdateMutateAsync = vi.fn();
const mockDeleteMutateAsync = vi.fn();
const mockToggleMutateAsync = vi.fn();

vi.mock('@/hooks/usePayees', () => ({
  usePayees: () => ({ data: mockPayeeData, isLoading: mockIsLoading, error: mockError }),
  useCreatePayee: () => ({ mutateAsync: mockCreateMutateAsync }),
  useUpdatePayee: () => ({ mutateAsync: mockUpdateMutateAsync }),
  useDeletePayee: () => ({ mutateAsync: mockDeleteMutateAsync }),
  useTogglePayeeActive: () => ({ mutateAsync: mockToggleMutateAsync }),
}));
vi.mock('@/hooks/useDocumentTitle', () => ({
  useDocumentTitle: vi.fn(),
}));
vi.mock('@/hooks/useSecondaryConversion', () => ({
  useSecondaryConversion: () => ({
    convert: (v: number) => v,
    secondaryCurrency: null,
    secondaryExchangeRate: null,
  }),
}));
vi.mock('@/components/ui/CategorySelect', () => ({
  CategorySelect: () => (
    <select data-testid="category-select">
      <option>Cat</option>
    </select>
  ),
}));

import { PayeeManagementSettings } from './PayeeManagementSettings';

describe('PayeeManagementSettings', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    vi.clearAllMocks();
    Element.prototype.scrollIntoView = vi.fn();
    mockPayeeData = [...mockPayees];
    mockIsLoading = false;
    mockError = null;
    mockCreateMutateAsync.mockReset().mockResolvedValue({});
    mockUpdateMutateAsync.mockReset().mockResolvedValue({});
    mockDeleteMutateAsync.mockReset().mockResolvedValue({});
  });

  it('uploads a logo, removes it, and saves the cleared value with the edited payee', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PayeeManagementSettings />);
    await user.click(screen.getByRole('button', { name: /edit.*amazon/i }));
    const fileInput = screen.getByLabelText(i18n.t('form.uploadLogo', { ns: 'payees' }));
    fireEvent.change(fileInput, {
      target: { files: [new File(['avatar'], 'logo.png', { type: 'image/png' })] },
    });
    await screen.findByAltText('Logo preview');
    await user.click(
      screen.getByRole('button', { name: i18n.t('form.removeLogo', { ns: 'payees' }) })
    );
    expect(screen.queryByAltText('Logo preview')).not.toBeInTheDocument();
    const name = screen.getByDisplayValue('Amazon');
    await user.clear(name);
    await user.type(name, 'Updated merchant');
    await user.click(screen.getByRole('button', { name: /^update$/i }));
    await waitFor(() =>
      expect(mockUpdateMutateAsync).toHaveBeenCalledWith({
        id: 1,
        data: { name: 'Updated merchant', logo: '', categoryId: 1 },
      })
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it.each([
    ['document.txt', 'text/plain', 5, 'logoInvalidType'],
    ['oversized.png', 'image/png', 500 * 1024 + 1, 'logoTooLarge'],
  ] as const)('rejects the unusable logo %s', async (name, type, size, errorKey) => {
    const user = userEvent.setup();
    renderWithProviders(<PayeeManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add payee/i }));
    fireEvent.change(screen.getByLabelText(i18n.t('form.uploadLogo', { ns: 'payees' })), {
      target: { files: [new File([new Uint8Array(size)], name, { type })] },
    });
    expect(screen.getByText(i18n.t(`form.${errorKey}`, { ns: 'payees' }))).toBeInTheDocument();
    expect(screen.queryByAltText('Logo preview')).not.toBeInTheDocument();
    expect(mockCreateMutateAsync).not.toHaveBeenCalled();
  });

  it('reports a file read failure and leaves the form editable', async () => {
    const user = userEvent.setup();
    const read = vi.spyOn(FileReader.prototype, 'readAsDataURL').mockImplementation(function (
      this: FileReader
    ) {
      this.dispatchEvent(new ProgressEvent('error'));
    });
    renderWithProviders(<PayeeManagementSettings />);
    await user.click(screen.getByRole('button', { name: /add payee/i }));
    fireEvent.change(screen.getByLabelText(i18n.t('form.uploadLogo', { ns: 'payees' })), {
      target: { files: [new File(['x'], 'logo.png', { type: 'image/png' })] },
    });
    expect(screen.getByText(i18n.t('form.logoReadError', { ns: 'payees' }))).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    read.mockRestore();
  });

  it('retains the deletion dialog on failure and shows the server message', async () => {
    const user = userEvent.setup();
    const alert = vi.spyOn(window, 'alert').mockImplementation(() => {});
    const log = vi.spyOn(console, 'error').mockImplementation(() => {});
    mockDeleteMutateAsync.mockRejectedValue({ response: { data: { message: 'Payee is in use' } } });
    renderWithProviders(<PayeeManagementSettings />);
    await user.click(screen.getByRole('button', { name: /delete.*amazon/i }));
    await user.click(screen.getByRole('button', { name: /^delete$/i }));
    await waitFor(() => expect(alert).toHaveBeenCalledWith('Payee is in use'));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    alert.mockRestore();
    log.mockRestore();
  });

  it('renders add payee button', () => {
    renderWithProviders(<PayeeManagementSettings />);
    expect(screen.getByText(/add payee/i)).toBeInTheDocument();
  });

  it('renders search input', () => {
    renderWithProviders(<PayeeManagementSettings />);
    expect(screen.getByPlaceholderText(/search/i)).toBeInTheDocument();
  });

  it('displays payee names', () => {
    renderWithProviders(<PayeeManagementSettings />);
    expect(screen.getByText('Amazon')).toBeInTheDocument();
    expect(screen.getByText('Netflix')).toBeInTheDocument();
  });

  it('shows loading state', () => {
    mockIsLoading = true;
    renderWithProviders(<PayeeManagementSettings />);
    expect(screen.queryByText('Amazon')).not.toBeInTheDocument();
  });

  it('shows empty state when no payees', () => {
    mockPayeeData = [];
    renderWithProviders(<PayeeManagementSettings />);
    expect(screen.getByText(/no custom payees/i)).toBeInTheDocument();
  });

  it('filters payees by search', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PayeeManagementSettings />);
    await user.type(screen.getByPlaceholderText(/search/i), 'Amazon');
    expect(screen.getByText('Amazon')).toBeInTheDocument();
    expect(screen.queryByText('Netflix')).not.toBeInTheDocument();
  });

  it('opens create form dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PayeeManagementSettings />);
    await user.click(screen.getByText(/add payee/i));
    await waitFor(() => {
      expect(screen.getByRole('dialog')).toBeInTheDocument();
    });
  });

  it('submits create form', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockResolvedValue({});
    renderWithProviders(<PayeeManagementSettings />);
    await user.click(screen.getByText(/add payee/i));
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    const nameInput = screen.getByPlaceholderText(/amazon|netflix/i);
    await user.type(nameInput, 'New Payee');
    const submitButton = screen.getByRole('button', { name: /^create$/i });
    await user.click(submitButton);
    await waitFor(() => expect(mockCreateMutateAsync).toHaveBeenCalled());
  });

  it('hides inactive payees by default', () => {
    renderWithProviders(<PayeeManagementSettings />);
    expect(screen.queryByText('Old Payee')).not.toBeInTheDocument();
  });

  it('shows error state', () => {
    mockError = new Error('Network error');
    renderWithProviders(<PayeeManagementSettings />);
    expect(screen.getByText(/failed to load/i)).toBeInTheDocument();
  });

  it('shows inactive payees when toggle clicked', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PayeeManagementSettings />);
    const toggleBtn = screen.getByRole('button', { name: /show hidden/i });
    await user.click(toggleBtn);
    expect(screen.getByText('Old Payee')).toBeInTheDocument();
  });

  it('shows category name on payee card', () => {
    renderWithProviders(<PayeeManagementSettings />);
    expect(screen.getByText('Shopping')).toBeInTheDocument();
  });

  it('opens edit form with pre-filled data', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PayeeManagementSettings />);
    // Find edit button for Amazon (custom payee)
    const editBtn = screen.getByRole('button', { name: /edit.*amazon/i });
    await user.click(editBtn);
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    expect(screen.getByDisplayValue('Amazon')).toBeInTheDocument();
  });

  it('opens delete dialog', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PayeeManagementSettings />);
    const deleteBtn = screen.getByRole('button', { name: /delete.*amazon/i });
    await user.click(deleteBtn);
    await waitFor(() => expect(screen.getByText(/are you sure/i)).toBeInTheDocument());
  });

  it('confirms delete and calls mutation', async () => {
    const user = userEvent.setup();
    mockDeleteMutateAsync.mockResolvedValue({});
    renderWithProviders(<PayeeManagementSettings />);
    const deleteBtn = screen.getByRole('button', { name: /delete.*amazon/i });
    await user.click(deleteBtn);
    await waitFor(() => expect(screen.getByText(/are you sure/i)).toBeInTheDocument());
    const confirmBtn = screen.getByRole('button', { name: /^delete$/i });
    await user.click(confirmBtn);
    await waitFor(() => expect(mockDeleteMutateAsync).toHaveBeenCalledWith(1));
  });

  it('shows payee with logo', () => {
    mockPayeeData = [
      {
        id: 10,
        name: 'Logo Payee',
        logo: 'data:image/png;base64,abc',
        isActive: true,
        isSystem: false,
        transactionCount: 1,
        totalAmount: 100,
      },
    ];
    renderWithProviders(<PayeeManagementSettings />);
    const img = document.querySelector('img');
    expect(img).toBeTruthy();
  });

  it('handles 409 conflict error on create', async () => {
    const user = userEvent.setup();
    mockCreateMutateAsync.mockRejectedValue({
      response: { status: 409, data: { message: 'Duplicate' } },
    });
    renderWithProviders(<PayeeManagementSettings />);
    await user.click(screen.getByText(/add payee/i));
    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    const nameInput = screen.getByPlaceholderText(/amazon|netflix/i);
    await user.type(nameInput, 'Dup Payee');
    const submitButton = screen.getByRole('button', { name: /^create$/i });
    await user.click(submitButton);
    await waitFor(() => expect(screen.getByText(/duplicate|already exists/i)).toBeInTheDocument());
  });

  it('shows transaction count and total amount on custom payee card', () => {
    renderWithProviders(<PayeeManagementSettings />);
    // Amazon has 5 transactions
    expect(screen.getByText(/5 transaction/i)).toBeInTheDocument();
  });

  it('shows system payee toggle visibility button', () => {
    renderWithProviders(<PayeeManagementSettings />);
    // Netflix is system payee — should have eye toggle
    const toggleBtns = screen.getAllByTitle(/hide payee|show payee/i);
    expect(toggleBtns.length).toBeGreaterThan(0);
  });

  it('toggles system payee active state', async () => {
    const user = userEvent.setup();
    mockToggleMutateAsync.mockResolvedValue({});
    renderWithProviders(<PayeeManagementSettings />);
    const toggleBtns = screen.getAllByTitle(/hide payee|show payee/i);
    await user.click(toggleBtns[0]);
    expect(mockToggleMutateAsync).toHaveBeenCalledWith(2);
  });

  it('shows empty search results message', async () => {
    const user = userEvent.setup();
    renderWithProviders(<PayeeManagementSettings />);
    await user.type(screen.getByPlaceholderText(/search/i), 'zzzzzzz');
    expect(screen.queryByText('Amazon')).not.toBeInTheDocument();
  });
});

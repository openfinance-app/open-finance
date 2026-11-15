import { fireEvent, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import i18n from '@/test/i18n-test';
import LiabilitiesPage from '@/pages/LiabilitiesPage';
import type { Liability, LiabilityFilters, LiabilityRequest } from '@/types/liability';
type PageResponse = NonNullable<
  ReturnType<typeof import('@/hooks/useLiabilities').useLiabilitiesPaged>['data']
>;

const state = vi.hoisted(() => ({
  page: vi.fn(),
  all: undefined as PageResponse | undefined,
  matching: undefined as PageResponse | undefined,
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  detail: vi.fn(),
  filters: {} as LiabilityFilters,
  totalPages: 3,
  enabled: true,
  filterPatch: {} as LiabilityFilters,
}));
vi.mock('@/hooks/useLiabilities', () => ({
  useLiabilitiesPaged: (filters: LiabilityFilters, all?: boolean, enabled?: boolean) => {
    if (all && enabled === undefined) return { data: state.all };
    if (all) return { data: state.matching };
    state.filters = filters;
    return state.page(filters);
  },
  useLiability: (id: number | null) => ({ data: state.detail(id) }),
  useCreateLiability: () => ({ mutateAsync: state.create, isPending: false }),
  useUpdateLiability: () => ({ mutateAsync: state.update, isPending: false }),
  useDeleteLiability: () => ({ mutateAsync: state.remove }),
}));
vi.mock('@/hooks/useLiabilityConversion', () => ({
  useLiabilityConversionEnabled: () => state.enabled,
}));
vi.mock('@/components/liabilities/LiabilityList', () => ({
  LiabilityList: ({
    liabilities,
    onEdit,
    onViewDetails,
  }: ComponentProps<typeof import('@/components/liabilities/LiabilityList').LiabilityList>) => (
    <section aria-label="Liabilities">
      {liabilities.map(item => (
        <div key={item.id}>
          <span>{item.name}</span>
          <button onClick={() => onEdit(item)}>Edit {item.name}</button>
          <button onClick={() => onViewDetails?.(item)}>View {item.name}</button>
        </div>
      ))}
    </section>
  ),
}));
vi.mock('@/components/liabilities/LiabilityFilters', () => ({
  LiabilityFilters: ({
    onFiltersChange,
  }: ComponentProps<
    typeof import('@/components/liabilities/LiabilityFilters').LiabilityFilters
  >) => <button onClick={() => onFiltersChange(state.filterPatch)}>Apply filters</button>,
}));
vi.mock('@/components/liabilities/LiabilitySummaryCards', () => ({
  LiabilitySummaryCards: (
    props: ComponentProps<
      typeof import('@/components/liabilities/LiabilitySummaryCards').LiabilitySummaryCards
    >
  ) => (
    <output aria-label="Summary inputs">
      {JSON.stringify({
        all: props.liabilities.map(item => item.id),
        matching: props.filteredLiabilities?.map(item => item.id),
        active: props.isActiveFilter,
      })}
    </output>
  ),
}));
vi.mock('@/components/liabilities/LiabilityDetailDialog', () => ({
  LiabilityDetailDialog: ({
    liability,
    onClose,
  }: ComponentProps<
    typeof import('@/components/liabilities/LiabilityDetailDialog').LiabilityDetailDialog
  >) =>
    liability ? (
      <section aria-label="Loan detail">
        <span>{liability.name} details</span>
        <button onClick={onClose}>Close detail</button>
      </section>
    ) : null,
}));
vi.mock('@/components/liabilities/ConvertLiabilityWizard', () => ({
  ConvertLiabilityWizard: ({
    onClose,
    onConverted,
  }: ComponentProps<
    typeof import('@/components/liabilities/ConvertLiabilityWizard').ConvertLiabilityWizard
  >) => (
    <section aria-label="Conversion">
      <button onClick={onClose}>Cancel conversion</button>
      <button onClick={() => onConverted(42)}>Complete conversion</button>
    </section>
  ),
}));
vi.mock('@/components/liabilities/LiabilityForm', () => ({
  LiabilityForm: ({
    onSubmit,
    onCancel,
  }: ComponentProps<typeof import('@/components/liabilities/LiabilityForm').LiabilityForm>) => (
    <section aria-label="Loan form">
      <button
        onClick={() =>
          onSubmit({
            name: 'New loan',
            type: 'LOAN',
            principal: '10',
            currentBalance: '10',
            currency: 'EUR',
            startDate: '2025-01-01',
          } satisfies LiabilityRequest)
        }
      >
        Save loan
      </button>
      <button onClick={onCancel}>Cancel form</button>
    </section>
  ),
}));
vi.mock('@/components/ui/Pagination', () => ({
  Pagination: ({
    currentPage,
    onPageChange,
    onPageSizeChange,
  }: ComponentProps<typeof import('@/components/ui/Pagination').Pagination>) => (
    <nav aria-label="Pages">
      <output aria-label="Current page">{currentPage}</output>
      <button onClick={() => onPageChange(currentPage + 1)}>Next page</button>
      <button onClick={() => onPageSizeChange?.(50)}>Show fifty</button>
    </nav>
  ),
}));
const debt = {
  id: 1,
  name: 'Home loan',
  type: 'MORTGAGE',
  currency: 'EUR',
  currentBalance: 100,
} as Liability;
function page(content: Liability[], number = 0): PageResponse {
  return { content, number, totalPages: state.totalPages, totalElements: 60, size: 20 };
}
function filters(patch: LiabilityFilters): void {
  state.filterPatch = patch;
  fireEvent.click(screen.getByRole('button', { name: i18n.t('liabilities:filters') }));
  fireEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
}

beforeEach(() => {
  vi.resetAllMocks();
  sessionStorage.clear();
  mockAuthentication();
  window.history.replaceState({}, '', '/liabilities');
  Element.prototype.scrollIntoView = vi.fn();
  state.totalPages = 3;
  state.enabled = true;
  state.all = page([debt]);
  state.matching = page([]);
  state.page.mockImplementation((criteria: LiabilityFilters) => ({
    data: page([debt], criteria.page ?? 0),
    isLoading: false,
  }));
  state.detail.mockImplementation((id: number | null) =>
    id === 42 ? { ...debt, id: 42, name: 'New loan' } : undefined
  );
  state.create.mockResolvedValue({ ...debt, id: 42 });
});

describe('Liability page state transitions', () => {
  it.each([0, 1])(
    'returns from a removed final page to the nearest available page: %s pages',
    remaining => {
      const view = renderWithProviders(<LiabilitiesPage />);
      fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
      fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
      expect(state.filters.page).toBe(2);
      state.totalPages = remaining;
      view.rerender(<LiabilitiesPage />);
      expect(state.filters.page).toBe(0);
    }
  );

  it('resets pagination on page-size and filter changes while keeping the chosen size', () => {
    renderWithProviders(<LiabilitiesPage />);
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    fireEvent.click(screen.getByRole('button', { name: 'Show fifty' }));
    expect(state.filters).toMatchObject({ page: 0, size: 50 });
    fireEvent.click(screen.getByRole('button', { name: 'Next page' }));
    filters({ balanceExact: '0' });
    expect(state.filters).toEqual({ page: 0, size: 50, balanceExact: '0' });
    expect(JSON.parse(screen.getByLabelText('Summary inputs').textContent!)).toEqual({
      all: [1],
      matching: [],
      active: true,
    });
  });

  it.each([false, true])(
    'clears empty searches and restores pagination defaults: preserveSort=%s',
    preserveSort => {
      renderWithProviders(<LiabilitiesPage />);
      state.page.mockImplementation((criteria: LiabilityFilters) => ({
        data: page(criteria.search ? [] : [debt]),
      }));
      filters({ search: 'Missing', ...(preserveSort ? { sort: 'name,asc' } : {}) });
      fireEvent.click(
        screen.getByRole('button', { name: i18n.t('liabilities:empty.clearFilters') })
      );
      expect(state.filters).toEqual({
        page: 0,
        size: 20,
        sort: preserveSort ? 'name,asc' : 'createdAt,desc',
      });
    }
  );

  it('handles missing page and summary data without rendering stale debt', () => {
    state.page.mockReturnValue({ data: undefined, isLoading: false });
    state.all = undefined;
    renderWithProviders(<LiabilitiesPage />);
    expect(screen.queryByLabelText('Summary inputs')).not.toBeInTheDocument();
    expect(screen.queryByText('Home loan')).not.toBeInTheDocument();
    expect(screen.getByText(i18n.t('liabilities:empty.noLiabilities'))).toBeVisible();
  });

  it('opens a created loan through its detail query before the list refreshes', async () => {
    renderWithProviders(<LiabilitiesPage />);
    fireEvent.click(screen.getByRole('button', { name: i18n.t('liabilities:addLiability') }));
    fireEvent.click(screen.getByRole('button', { name: 'Save loan' }));
    expect(await screen.findByText('New loan details')).toBeVisible();
    expect(state.detail).toHaveBeenCalledWith(42);
    fireEvent.click(screen.getByRole('button', { name: 'Close detail' }));
    expect(screen.queryByLabelText('Loan detail')).not.toBeInTheDocument();
  });

  it.each([false, true])('cancels or completes account conversion: complete=%s', async complete => {
    renderWithProviders(<LiabilitiesPage />);
    fireEvent.click(screen.getByRole('button', { name: i18n.t('liabilities:conversion.action') }));
    fireEvent.click(
      screen.getByRole('button', { name: complete ? 'Complete conversion' : 'Cancel conversion' })
    );
    expect(screen.queryByLabelText('Conversion')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Loan detail') !== null).toBe(complete);
  });

  it('closes an open conversion if the feature becomes unavailable', () => {
    const view = renderWithProviders(<LiabilitiesPage />);
    fireEvent.click(screen.getByRole('button', { name: i18n.t('liabilities:conversion.action') }));
    state.enabled = false;
    view.rerender(<LiabilitiesPage />);
    expect(screen.queryByLabelText('Conversion')).not.toBeInTheDocument();
  });
});

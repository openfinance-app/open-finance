import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { LiabilityFilters } from '../LiabilityFilters';

describe('LiabilityFilters', () => {
  const defaultProps = {
    filters: {},
    onFiltersChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders search input', () => {
    renderWithProviders(<LiabilityFilters {...defaultProps} />);
    expect(screen.getByLabelText(/search/i)).toBeInTheDocument();
  });

  it('renders type filter dropdown', () => {
    renderWithProviders(<LiabilityFilters {...defaultProps} />);
    expect(screen.getByLabelText(/type/i)).toBeInTheDocument();
  });

  it('renders sort dropdown', () => {
    renderWithProviders(<LiabilityFilters {...defaultProps} />);
    expect(screen.getByLabelText(/sort/i)).toBeInTheDocument();
  });

  it('renders liability type options', () => {
    renderWithProviders(<LiabilityFilters {...defaultProps} />);
    const typeSelect = screen.getByLabelText(/type/i) as HTMLSelectElement;
    expect(typeSelect.options.length).toBeGreaterThan(1); // "All" + types
  });

  it('calls onFiltersChange when search changes', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<LiabilityFilters filters={{}} onFiltersChange={onFiltersChange} />);
    fireEvent.change(screen.getByLabelText(/search/i), { target: { value: 'mortgage' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ search: 'mortgage' }));
  });

  it('calls onFiltersChange when type changes', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<LiabilityFilters filters={{}} onFiltersChange={onFiltersChange} />);
    fireEvent.change(screen.getByLabelText(/type/i), { target: { value: 'MORTGAGE' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ type: 'MORTGAGE' }));
  });

  it('calls onFiltersChange when sort changes', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(<LiabilityFilters filters={{}} onFiltersChange={onFiltersChange} />);
    fireEvent.change(screen.getByLabelText(/sort/i), { target: { value: 'name,asc' } });
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ sort: 'name,asc' }));
  });

  it('clears filters on clear click', () => {
    const onFiltersChange = vi.fn();
    renderWithProviders(
      <LiabilityFilters
        filters={{ search: 'test', type: 'LOAN' as any, size: 20, sort: 'createdAt,desc' }}
        onFiltersChange={onFiltersChange}
      />
    );
    fireEvent.click(screen.getByText(/clear/i));
    expect(onFiltersChange).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 20, sort: 'createdAt,desc' })
    );
  });

  it('disables clear when no active filters', () => {
    renderWithProviders(<LiabilityFilters {...defaultProps} />);
    const clearBtn = screen.getByText(/clear/i);
    expect(clearBtn).toBeDisabled();
  });

  it('enables clear when filters are active', () => {
    renderWithProviders(
      <LiabilityFilters filters={{ search: 'test' }} onFiltersChange={vi.fn()} />
    );
    const clearBtn = screen.getByText(/clear/i);
    expect(clearBtn).not.toBeDisabled();
  });
});

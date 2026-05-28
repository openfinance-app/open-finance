import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SavedSearchesDropdown } from './SavedSearchesDropdown';
import type { SavedSearch } from '@/types/search';

// Mock ConfirmationDialog to render inline for easier testing
vi.mock('@/components/ConfirmationDialog', () => ({
  ConfirmationDialog: ({ open, onConfirm, title, description, confirmText, cancelText, onOpenChange }: any) => {
    if (!open) return null;
    return (
      <div data-testid="confirm-dialog">
        <p>{title}</p>
        <p>{description}</p>
        <button onClick={() => onOpenChange(false)}>{cancelText}</button>
        <button onClick={onConfirm}>{confirmText}</button>
      </div>
    );
  },
}));

describe('SavedSearchesDropdown', () => {
  const mockSearches: SavedSearch[] = [
    {
      id: '1',
      name: 'My Search',
      filters: { query: 'test' },
      createdAt: '2024-01-01T00:00:00Z',
      lastUsed: '2024-01-15T00:00:00Z',
    },
    {
      id: '2',
      name: 'Recent Search',
      filters: { query: 'groceries' },
      createdAt: '2024-02-01T00:00:00Z',
      lastUsed: null as any,
    },
  ];

  let onLoad: ReturnType<typeof vi.fn>;
  let onDelete: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    onLoad = vi.fn();
    onDelete = vi.fn();
  });

  it('renders nothing when no saved searches', () => {
    const { container } = render(
      <SavedSearchesDropdown savedSearches={[]} onLoad={onLoad} onDelete={onDelete} />
    );
    expect(container.firstChild).toBeNull();
  });

  it('renders dropdown button when searches exist', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    expect(screen.getByText('Saved Searches')).toBeInTheDocument();
  });

  it('shows badge with count', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    expect(screen.getByText('2')).toBeInTheDocument();
  });

  it('shows saved searches on button click', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('My Search')).toBeInTheDocument();
    expect(screen.getByText('Recent Search')).toBeInTheDocument();
  });

  it('shows query text for searches with query filter', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText(/Query: "test"/)).toBeInTheDocument();
  });

  it('shows "Used" time for searches with lastUsed', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText(/Used/)).toBeInTheDocument();
  });

  it('shows "Created" time for searches without lastUsed', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText(/Created/)).toBeInTheDocument();
  });

  it('calls onLoad and closes dropdown when search is clicked', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    fireEvent.click(screen.getByText('My Search'));
    expect(onLoad).toHaveBeenCalledWith(mockSearches[0]);
    // Dropdown should close — search names should not be visible
    expect(screen.queryByText('Recent Search')).not.toBeInTheDocument();
  });

  it('opens confirmation dialog when delete button is clicked', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    // Delete buttons are in sorted order: Recent Search (index 0), My Search (index 1)
    const deleteButtons = screen.getAllByTitle('Delete saved search');
    fireEvent.click(deleteButtons[0]);
    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
    expect(screen.getByText(/Are you sure you want to delete "Recent Search"/)).toBeInTheDocument();
  });

  it('calls onDelete when confirmation is confirmed', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    const deleteButtons = screen.getAllByTitle('Delete saved search');
    fireEvent.click(deleteButtons[0]);
    fireEvent.click(screen.getByText('Delete'));
    expect(onDelete).toHaveBeenCalledWith('2'); // Recent Search id
  });

  it('closes confirmation dialog on cancel', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    const deleteButtons = screen.getAllByTitle('Delete saved search');
    fireEvent.click(deleteButtons[0]);
    fireEvent.click(screen.getByText('Cancel'));
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
    expect(onDelete).not.toHaveBeenCalled();
  });

  it('sorts searches by lastUsed/createdAt (most recent first)', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    // Recent Search (created 2024-02-01) should appear before My Search (lastUsed 2024-01-15)
    const names = screen.getAllByText(/Search/).filter(el => el.classList.contains('truncate'));
    // "Recent Search" has later date so should be first
    expect(names[0].textContent).toBe('Recent Search');
    expect(names[1].textContent).toBe('My Search');
  });

  it('closes dropdown when backdrop is clicked', () => {
    render(
      <SavedSearchesDropdown savedSearches={mockSearches} onLoad={onLoad} onDelete={onDelete} />
    );
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('My Search')).toBeInTheDocument();
    // Click the backdrop (fixed inset-0 div)
    const backdrop = document.querySelector('.fixed.inset-0');
    fireEvent.click(backdrop!);
    expect(screen.queryByText('My Search')).not.toBeInTheDocument();
  });
});

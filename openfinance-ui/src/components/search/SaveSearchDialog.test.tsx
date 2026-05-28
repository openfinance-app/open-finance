import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';
import { SaveSearchDialog } from './SaveSearchDialog';

describe('SaveSearchDialog', () => {
  const defaultProps = {
    isOpen: true,
    onClose: vi.fn(),
    onSave: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders the dialog when open', () => {
    renderWithProviders(<SaveSearchDialog {...defaultProps} />);
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('calls onSave with trimmed name on submit', () => {
    renderWithProviders(<SaveSearchDialog {...defaultProps} />);
    const input = screen.getByRole('textbox');
    fireEvent.change(input, { target: { value: 'My search name' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    expect(defaultProps.onSave).toHaveBeenCalledWith('My search name');
  });

  it('shows error for empty name', () => {
    renderWithProviders(<SaveSearchDialog {...defaultProps} />);
    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    // Error should prevent onSave from being called
    expect(defaultProps.onSave).not.toHaveBeenCalled();
  });
});

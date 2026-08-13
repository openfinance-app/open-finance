import { describe, it, expect, vi } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import { renderWithProviders } from '@/test/test-utils';
import { RegexToggle } from './RegexToggle';

describe('RegexToggle', () => {
  it('reflects disabled state via aria-pressed', () => {
    renderWithProviders(<RegexToggle enabled={false} onChange={vi.fn()} />);
    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'false');
  });

  it('reflects enabled state via aria-pressed', () => {
    renderWithProviders(<RegexToggle enabled={true} onChange={vi.fn()} />);
    expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'true');
  });

  it('calls onChange with the toggled value when clicked', () => {
    const onChange = vi.fn();
    renderWithProviders(<RegexToggle enabled={false} onChange={onChange} />);
    fireEvent.click(screen.getByRole('button'));
    expect(onChange).toHaveBeenCalledWith(true);
  });

  it('calls onChange(false) when currently enabled', () => {
    const onChange = vi.fn();
    renderWithProviders(<RegexToggle enabled={true} onChange={onChange} />);
    fireEvent.click(screen.getByRole('button'));
    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('applies a highlighted style when enabled', () => {
    renderWithProviders(<RegexToggle enabled={true} onChange={vi.fn()} />);
    expect(screen.getByRole('button').className).toContain('bg-primary/20');
  });
});

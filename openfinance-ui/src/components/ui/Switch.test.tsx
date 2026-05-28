import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Switch } from './Switch';

describe('Switch', () => {
  it('renders as a switch role', () => {
    render(<Switch />);
    expect(screen.getByRole('switch')).toBeInTheDocument();
  });

  it('reflects checked state via aria-checked', () => {
    render(<Switch checked={true} />);
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true');
  });

  it('reflects unchecked state via aria-checked', () => {
    render(<Switch checked={false} />);
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'false');
  });

  it('calls onCheckedChange with toggled value on click', () => {
    const onCheckedChange = vi.fn();
    render(<Switch checked={false} onCheckedChange={onCheckedChange} />);
    fireEvent.click(screen.getByRole('switch'));
    expect(onCheckedChange).toHaveBeenCalledWith(true);
  });

  it('toggles from true to false', () => {
    const onCheckedChange = vi.fn();
    render(<Switch checked={true} onCheckedChange={onCheckedChange} />);
    fireEvent.click(screen.getByRole('switch'));
    expect(onCheckedChange).toHaveBeenCalledWith(false);
  });

  it('applies primary bg when checked', () => {
    render(<Switch checked={true} />);
    const sw = screen.getByRole('switch');
    expect(sw.className).toContain('bg-primary');
  });

  it('applies input bg when unchecked', () => {
    render(<Switch checked={false} />);
    const sw = screen.getByRole('switch');
    expect(sw.className).toContain('bg-input');
  });

  it('is disabled when disabled prop set', () => {
    render(<Switch disabled />);
    expect(screen.getByRole('switch')).toBeDisabled();
  });
});

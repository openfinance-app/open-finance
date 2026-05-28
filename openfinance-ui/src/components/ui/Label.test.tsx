import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Label } from './Label';

describe('Label', () => {
  it('renders text content', () => {
    render(<Label>Username</Label>);
    expect(screen.getByText('Username')).toBeInTheDocument();
  });

  it('is rendered as a label element', () => {
    const { container } = render(<Label>Email</Label>);
    expect(container.querySelector('label')).toBeInTheDocument();
  });

  it('applies htmlFor attribute', () => {
    render(<Label htmlFor="email-input">Email</Label>);
    expect(screen.getByText('Email')).toHaveAttribute('for', 'email-input');
  });

  it('passes through className', () => {
    const { container } = render(<Label className="custom-label">L</Label>);
    const label = container.querySelector('label') as HTMLElement;
    expect(label.className).toContain('custom-label');
  });

  it('forwards ref', () => {
    const ref = { current: null as HTMLLabelElement | null };
    render(<Label ref={ref}>Ref Label</Label>);
    expect(ref.current).toBeInstanceOf(HTMLLabelElement);
  });
});

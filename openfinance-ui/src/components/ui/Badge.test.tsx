import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Badge } from './Badge';

describe('Badge', () => {
  it('renders children text', () => {
    render(<Badge>Active</Badge>);
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('applies default variant styles', () => {
    const { container } = render(<Badge>Default</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('bg-surface-elevated');
  });

  it('applies success variant styles', () => {
    const { container } = render(<Badge variant="success">OK</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('text-success');
  });

  it('applies error variant styles', () => {
    const { container } = render(<Badge variant="error">Fail</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('text-error');
  });

  it('applies warning variant', () => {
    const { container } = render(<Badge variant="warning">Warn</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('text-warning');
  });

  it('applies info variant', () => {
    const { container } = render(<Badge variant="info">Info</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('text-accent-blue');
  });

  it('applies destructive variant', () => {
    const { container } = render(<Badge variant="destructive">Delete</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('text-destructive');
  });

  it('applies outline variant', () => {
    const { container } = render(<Badge variant="outline">Outlined</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('bg-transparent');
  });

  it('applies sm size', () => {
    const { container } = render(<Badge size="sm">Small</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('text-xs');
  });

  it('applies lg size', () => {
    const { container } = render(<Badge size="lg">Large</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('text-base');
  });

  it('passes through extra className', () => {
    const { container } = render(<Badge className="custom-class">Custom</Badge>);
    const badge = container.firstChild as HTMLElement;
    expect(badge.className).toContain('custom-class');
  });

  it('forwards ref', () => {
    const ref = { current: null as HTMLDivElement | null };
    render(<Badge ref={ref}>Ref</Badge>);
    expect(ref.current).toBeInstanceOf(HTMLDivElement);
  });
});

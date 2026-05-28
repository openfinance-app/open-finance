import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Card, CardHeader } from './Card';

describe('Card', () => {
  it('renders children', () => {
    render(<Card>Card Content</Card>);
    expect(screen.getByText('Card Content')).toBeInTheDocument();
  });

  it('applies default padding (md)', () => {
    const { container } = render(<Card>Content</Card>);
    const card = container.firstChild as HTMLElement;
    expect(card.className).toContain('p-6');
  });

  it('applies sm padding', () => {
    const { container } = render(<Card padding="sm">Content</Card>);
    const card = container.firstChild as HTMLElement;
    expect(card.className).toContain('p-4');
  });

  it('applies lg padding', () => {
    const { container } = render(<Card padding="lg">Content</Card>);
    const card = container.firstChild as HTMLElement;
    expect(card.className).toContain('p-8');
  });

  it('applies no padding', () => {
    const { container } = render(<Card padding="none">Content</Card>);
    const card = container.firstChild as HTMLElement;
    // Should not contain any p- class (except those in the base styles)
    expect(card.className).not.toContain('p-4');
    expect(card.className).not.toContain('p-6');
    expect(card.className).not.toContain('p-8');
  });

  it('adds hover styles when hover=true', () => {
    const { container } = render(<Card hover>Hoverable</Card>);
    const card = container.firstChild as HTMLElement;
    expect(card.className).toContain('hover:bg-surface-elevated');
  });

  it('does not add hover styles by default', () => {
    const { container } = render(<Card>No hover</Card>);
    const card = container.firstChild as HTMLElement;
    expect(card.className).not.toContain('hover:bg-surface-elevated');
  });

  it('passes through extra className', () => {
    const { container } = render(<Card className="my-custom">Content</Card>);
    const card = container.firstChild as HTMLElement;
    expect(card.className).toContain('my-custom');
  });

  it('forwards ref', () => {
    const ref = { current: null as HTMLDivElement | null };
    render(<Card ref={ref}>Ref</Card>);
    expect(ref.current).toBeInstanceOf(HTMLDivElement);
  });
});

describe('CardHeader', () => {
  it('renders children', () => {
    render(<CardHeader>Header Content</CardHeader>);
    expect(screen.getByText('Header Content')).toBeInTheDocument();
  });

  it('passes through className', () => {
    const { container } = render(<CardHeader className="extra-class">H</CardHeader>);
    const header = container.firstChild as HTMLElement;
    expect(header.className).toContain('extra-class');
  });
});

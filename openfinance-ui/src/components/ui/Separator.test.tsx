import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { Separator } from './Separator';

describe('Separator', () => {
  it('renders horizontal separator by default', () => {
    const { container } = render(<Separator />);
    const el = container.firstChild as HTMLElement;
    expect(el.className).toContain('h-[1px]');
    expect(el.className).toContain('w-full');
  });

  it('renders vertical separator', () => {
    const { container } = render(<Separator orientation="vertical" />);
    const el = container.firstChild as HTMLElement;
    expect(el.className).toContain('h-full');
    expect(el.className).toContain('w-[1px]');
  });

  it('uses role=none when decorative (default)', () => {
    const { container } = render(<Separator />);
    const el = container.firstChild as HTMLElement;
    expect(el).toHaveAttribute('role', 'none');
  });

  it('uses role=separator when not decorative', () => {
    const { container } = render(<Separator decorative={false} />);
    const el = container.firstChild as HTMLElement;
    expect(el).toHaveAttribute('role', 'separator');
  });

  it('passes through className', () => {
    const { container } = render(<Separator className="my-sep" />);
    const el = container.firstChild as HTMLElement;
    expect(el.className).toContain('my-sep');
  });

  it('forwards ref', () => {
    const ref = { current: null as HTMLDivElement | null };
    render(<Separator ref={ref} />);
    expect(ref.current).toBeInstanceOf(HTMLDivElement);
  });
});

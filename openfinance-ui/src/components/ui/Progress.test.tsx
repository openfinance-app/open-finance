import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { Progress } from './Progress';

describe('Progress', () => {
  it('renders a progress bar', () => {
    const { container } = render(<Progress value={50} />);
    const outer = container.firstChild as HTMLElement;
    expect(outer.className).toContain('overflow-hidden');
    expect(outer.className).toContain('rounded-full');
  });

  it('applies the correct transform for the value', () => {
    const { container } = render(<Progress value={75} />);
    const inner = (container.firstChild as HTMLElement).firstChild as HTMLElement;
    expect(inner.style.transform).toBe('scaleX(0.75)');
  });

  it('shows 0% progress when value is 0', () => {
    const { container } = render(<Progress value={0} />);
    const inner = (container.firstChild as HTMLElement).firstChild as HTMLElement;
    expect(inner.style.transform).toBe('scaleX(0)');
  });

  it('shows 100% progress when value is 100', () => {
    const { container } = render(<Progress value={100} />);
    const inner = (container.firstChild as HTMLElement).firstChild as HTMLElement;
    expect(inner.style.transform).toBe('scaleX(1)');
  });

  it('clamps values above 100', () => {
    const { container } = render(<Progress value={150} />);
    const inner = (container.firstChild as HTMLElement).firstChild as HTMLElement;
    expect(inner.style.transform).toBe('scaleX(1)');
  });

  it('defaults to 0 when no value', () => {
    const { container } = render(<Progress />);
    const inner = (container.firstChild as HTMLElement).firstChild as HTMLElement;
    expect(inner.style.transform).toBe('scaleX(0)');
  });

  it('exposes progressbar semantics', () => {
    const { container } = render(<Progress value={40} />);
    const outer = container.firstChild as HTMLElement;
    expect(outer.getAttribute('role')).toBe('progressbar');
    expect(outer.getAttribute('aria-valuenow')).toBe('40');
  });

  it('passes through extra className', () => {
    const { container } = render(<Progress className="my-progress" value={50} />);
    const outer = container.firstChild as HTMLElement;
    expect(outer.className).toContain('my-progress');
  });

  it('forwards ref', () => {
    const ref = { current: null as HTMLDivElement | null };
    render(<Progress ref={ref} value={50} />);
    expect(ref.current).toBeInstanceOf(HTMLDivElement);
  });
});

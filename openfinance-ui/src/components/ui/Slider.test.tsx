import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Slider } from './Slider';

describe('Slider', () => {
  it('renders a range input', () => {
    render(<Slider value={[50]} onValueChange={vi.fn()} />);
    const input = screen.getByRole('slider');
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('type', 'range');
  });

  it('sets min, max, step attributes', () => {
    render(<Slider value={[5]} onValueChange={vi.fn()} min={0} max={10} step={0.5} />);
    const input = screen.getByRole('slider');
    expect(input).toHaveAttribute('min', '0');
    expect(input).toHaveAttribute('max', '10');
    expect(input).toHaveAttribute('step', '0.5');
  });

  it('uses default min=0, max=100, step=1', () => {
    render(<Slider value={[50]} onValueChange={vi.fn()} />);
    const input = screen.getByRole('slider');
    expect(input).toHaveAttribute('min', '0');
    expect(input).toHaveAttribute('max', '100');
    expect(input).toHaveAttribute('step', '1');
  });

  it('calls onValueChange with array on change', () => {
    const onValueChange = vi.fn();
    render(<Slider value={[50]} onValueChange={onValueChange} />);
    fireEvent.change(screen.getByRole('slider'), { target: { value: '75' } });
    expect(onValueChange).toHaveBeenCalledWith([75]);
  });

  it('passes className', () => {
    render(<Slider value={[50]} onValueChange={vi.fn()} className="my-slider" />);
    const input = screen.getByRole('slider');
    expect(input.className).toContain('my-slider');
  });
});

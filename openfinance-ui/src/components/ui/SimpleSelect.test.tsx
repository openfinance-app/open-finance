import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { SimpleSelect } from './SimpleSelect';

describe('SimpleSelect', () => {
  it('renders a select element', () => {
    render(
      <SimpleSelect>
        <option value="a">Option A</option>
        <option value="b">Option B</option>
      </SimpleSelect>
    );
    expect(screen.getByRole('combobox')).toBeInTheDocument();
  });

  it('renders options', () => {
    render(
      <SimpleSelect>
        <option value="a">Option A</option>
        <option value="b">Option B</option>
      </SimpleSelect>
    );
    expect(screen.getByText('Option A')).toBeInTheDocument();
    expect(screen.getByText('Option B')).toBeInTheDocument();
  });

  it('calls onChange on selection', () => {
    const onChange = vi.fn();
    render(
      <SimpleSelect onChange={onChange}>
        <option value="a">Option A</option>
        <option value="b">Option B</option>
      </SimpleSelect>
    );
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'b' } });
    expect(onChange).toHaveBeenCalled();
  });

  it('applies custom className', () => {
    render(
      <SimpleSelect className="custom-select">
        <option>X</option>
      </SimpleSelect>
    );
    expect(screen.getByRole('combobox').className).toContain('custom-select');
  });

  it('forwards ref', () => {
    const ref = { current: null as HTMLSelectElement | null };
    render(
      <SimpleSelect ref={ref}>
        <option>X</option>
      </SimpleSelect>
    );
    expect(ref.current).toBeInstanceOf(HTMLSelectElement);
  });

  it('can be disabled', () => {
    render(
      <SimpleSelect disabled>
        <option>X</option>
      </SimpleSelect>
    );
    expect(screen.getByRole('combobox')).toBeDisabled();
  });
});

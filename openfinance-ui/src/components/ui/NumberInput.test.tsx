import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NumberInput } from './NumberInput';

const { useNumberFormatMock } = vi.hoisted(() => ({
  useNumberFormatMock: vi.fn(),
}));

vi.mock('@/context/NumberFormatContext', () => ({
  useNumberFormat: useNumberFormatMock,
}));

describe('NumberInput', () => {
  beforeEach(() => {
    useNumberFormatMock.mockReturnValue({
      numberFormat: '1.234,56',
      isLoading: false,
      setNumberFormat: vi.fn(),
    });
  });

  it('formats the canonical value using the user number format', () => {
    render(<NumberInput value="1234567.89" onChange={vi.fn()} aria-label="Amount" />);

    expect(screen.getByRole('textbox', { name: 'Amount' })).toHaveValue('1.234.567,89');
  });

  it('converts localized edits back to the canonical value', () => {
    const onChange = vi.fn();
    render(<NumberInput value="1234.56" onChange={onChange} aria-label="Amount" />);

    fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), {
      target: { value: '1.234,57' },
    });

    expect(onChange).toHaveBeenCalledWith('1234.57');
  });

  it('emits an empty canonical value when cleared', () => {
    const onChange = vi.fn();
    render(<NumberInput value="1234.56" onChange={onChange} aria-label="Amount" />);

    fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), {
      target: { value: '' },
    });

    expect(onChange).toHaveBeenCalledWith('');
  });
});

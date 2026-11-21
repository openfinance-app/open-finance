import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderWithProviders as render, mockAuthentication } from '@/test/test-utils';
import { NumberInput } from './NumberInput';

const { useNumberFormatMock } = vi.hoisted(() => ({
  useNumberFormatMock: vi.fn(),
}));

vi.mock('@/context/NumberFormatContext', async importOriginal => ({
  ...(await importOriginal<typeof import('@/context/NumberFormatContext')>()),
  useNumberFormat: useNumberFormatMock,
}));

describe('NumberInput', () => {
  beforeEach(() => {
    mockAuthentication();
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

  it('uses the first Enter to calculate and the next Enter to submit the result', async () => {
    const user = userEvent.setup();
    const save = vi.fn();
    function AmountForm() {
      const [amount, setAmount] = useState('');
      return (
        <form
          onSubmit={event => {
            event.preventDefault();
            save(amount);
          }}
        >
          <NumberInput calculable value={amount} onChange={setAmount} aria-label="Amount" />
          <button>Save</button>
        </form>
      );
    }
    render(<AmountForm />);
    const input = screen.getByRole('textbox', { name: 'Amount' });
    await user.type(input, '9+5{Enter}');
    expect(input).toHaveValue('14');
    expect(input).toHaveFocus();
    expect(save).not.toHaveBeenCalled();
    await user.keyboard('{Enter}');
    expect(save).toHaveBeenCalledWith('14');
  });

  it.each([
    ['1.234,50 + 0,51', '1235.01', '1.235,01'],
    ['(3 + 4) * -2 + 18 / 3', '-8', '-8'],
    ['9 - 3 * 2 - 1', '2', '2'],
    ['0,1 + 0,2', '0.3', '0,3'],
    ['1 / 3', '0.333333333333333333', '0,333333333333333333'],
    ['9007199254740993 + 0,01', '9007199254740993.01', '9.007.199.254.740.993,01'],
  ])('calculates %s using the field number format', (draft, canonical, display) => {
    const change = vi.fn();
    render(<NumberInput calculable value="50" onChange={change} aria-label="Amount" />);
    const input = screen.getByRole('textbox', { name: 'Amount' });
    fireEvent.change(input, { target: { value: draft } });
    expect(change).not.toHaveBeenCalled();
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(change).toHaveBeenCalledExactlyOnceWith(canonical);
    expect(input).toHaveValue(display);
  });

  it.each(['9+', '9/0', '2*(3+4', '12x+1', '1..2+3', '1 2+3', '110-1'])(
    'keeps %s editable and blocks submission of the previous value',
    draft => {
      const save = vi.fn(event => event.preventDefault());
      const change = vi.fn();
      render(
        <form noValidate onSubmit={save}>
          <NumberInput calculable max="100" value="50" onChange={change} aria-label="Amount" />
        </form>
      );
      const input = screen.getByRole('textbox', { name: 'Amount' });
      fireEvent.change(input, { target: { value: draft } });
      fireEvent.keyDown(input, { key: 'Enter' });
      expect(input).toHaveAttribute('aria-invalid', 'true');
      expect(input).toHaveValue(draft);
      fireEvent.submit(input.closest('form')!);
      expect(save).not.toHaveBeenCalled();
      expect(change).not.toHaveBeenCalled();
      fireEvent.change(input, { target: { value: '8+2' } });
      fireEvent.keyDown(input, { key: 'Enter' });
      expect(change).toHaveBeenLastCalledWith('10');
      expect(input).toBeValid();
    }
  );

  it('converts localized edits back to the canonical value', () => {
    const onChange = vi.fn();
    render(<NumberInput value="1234.56" onChange={onChange} aria-label="Amount" />);

    fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), {
      target: { value: '1.234,57' },
    });

    expect(onChange).toHaveBeenCalledWith('1234.57');
  });

  it('enforces a field’s decimal-place limit for calculated amounts', () => {
    const change = vi.fn();
    render(
      <NumberInput calculable maxFractionDigits={2} onChange={change} aria-label="Principal" />
    );
    const input = screen.getByRole('textbox', { name: 'Principal' });
    fireEvent.change(input, { target: { value: '1 / 3' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(input).toHaveAccessibleDescription('Use at most 2 decimal places.');
    expect(change).not.toHaveBeenCalled();
  });

  it.each(['1,234.56', '1 234,56'])(
    'accepts a well-formed pasted amount in another notation: %s',
    display => {
      const onChange = vi.fn();
      render(<NumberInput value="0" onChange={onChange} aria-label="Amount" />);
      fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), {
        target: { value: display },
      });
      expect(onChange).toHaveBeenLastCalledWith('1234.56');
    }
  );

  it('emits an empty canonical value when cleared', () => {
    const onChange = vi.fn();
    render(<NumberInput value="1234.56" onChange={onChange} aria-label="Amount" />);

    fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), {
      target: { value: '' },
    });

    expect(onChange).toHaveBeenCalledWith('');
  });
  it.each([
    ['1.200', '1200'],
    ['1.200,00', '1200.00'],
    ['1,2', '1.2'],
    ['1.200.000', '1200000'],
  ])('parses European display %s as %s', (display, canonical) => {
    const onChange = vi.fn();
    render(<NumberInput value="0" onChange={onChange} aria-label="Amount" />);
    fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), {
      target: { value: display },
    });
    expect(onChange).toHaveBeenLastCalledWith(canonical);
  });
});

describe('NumberInput invalid drafts', () => {
  beforeEach(() => {
    mockAuthentication();
    useNumberFormatMock.mockReturnValue({ numberFormat: '1,234.56' });
  });

  it.each(['abc', '12-3', '1..2', '50.'])(
    'retains and rejects the draft %s instead of submitting the old amount',
    draft => {
      const save = vi.fn(event => event.preventDefault());
      const change = vi.fn();
      render(
        <form noValidate onSubmit={save}>
          <NumberInput value="50" onChange={change} aria-label="Amount" />
          <button>Save</button>
        </form>
      );
      const input = screen.getByRole('textbox', { name: 'Amount' });
      fireEvent.change(input, { target: { value: draft } });
      fireEvent.blur(input);
      fireEvent.submit(input.closest('form')!);
      expect(input).toHaveValue(draft);
      expect(save).not.toHaveBeenCalled();
      expect(change).not.toHaveBeenCalled();
      expect(input).toHaveAccessibleDescription('Enter a valid number.');
      expect(input).toHaveFocus();
      fireEvent.change(input, { target: { value: '60.98' } });
      fireEvent.submit(input.closest('form')!);
      expect(save).toHaveBeenCalledOnce();
      expect(change).toHaveBeenLastCalledWith('60.98');
    }
  );

  it.each(['-1', '101'])('enforces numeric bounds for text input %s', draft => {
    const save = vi.fn();
    render(
      <form noValidate onSubmit={save}>
        <NumberInput value="50" min="0" max="100" onChange={vi.fn()} aria-label="Amount" />
      </form>
    );
    const input = screen.getByRole('textbox', { name: 'Amount' });
    fireEvent.change(input, { target: { value: draft } });
    fireEvent.submit(input.closest('form')!);
    expect(save).not.toHaveBeenCalled();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription();
  });

  it('shows an associated error when a required number is cleared', () => {
    render(
      <form>
        <NumberInput required value="" onChange={vi.fn()} aria-label="Amount" />
      </form>
    );
    const input = screen.getByRole('textbox', { name: 'Amount' });
    fireEvent.invalid(input);
    expect(input).toHaveAccessibleDescription('This field is required');
  });
});

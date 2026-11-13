import { fireEvent, screen } from '@testing-library/react';
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

  it('converts localized edits back to the canonical value', () => {
    const onChange = vi.fn();
    render(<NumberInput value="1234.56" onChange={onChange} aria-label="Amount" />);

    fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), {
      target: { value: '1.234,57' },
    });

    expect(onChange).toHaveBeenCalledWith('1234.57');
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

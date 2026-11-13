import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DateInput } from '@/components/ui/DateInput';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

vi.mock('@/hooks/useUserSettings', async importOriginal => ({
  ...(await importOriginal<typeof import('@/hooks/useUserSettings')>()),
  useUserSettings: () => ({ data: { dateFormat: 'DD/MM/YYYY' } }),
}));

function EditableDate() {
  const [value, setValue] = useState('2026-10-18');
  return (
    <>
      <label htmlFor="date">Date</label>
      <DateInput id="date" value={value} onChange={setValue} />
      <output>{value}</output>
      <button onClick={() => setValue('2025-02-03')}>Reset</button>
    </>
  );
}

describe('DateInput editing', () => {
  beforeEach(mockAuthentication);

  it('keeps the draft and caret when deleting and replacing a date segment', async () => {
    const user = userEvent.setup();
    renderWithProviders(<EditableDate />);
    const input = screen.getByRole('textbox', { name: 'Date' }) as HTMLInputElement;
    await user.click(input);
    input.setSelectionRange(0, 1);
    await user.keyboard('{Backspace}');
    expect(input).toHaveValue('8/10/2026');
    expect(input.selectionStart).toBe(0);
    await user.keyboard('2');
    expect(input).toHaveValue('28/10/2026');
    expect(input.selectionStart).toBe(1);
    expect(screen.getByRole('status')).toHaveTextContent('2026-10-28');
  });

  it('normalizes on blur and accepts external resets', async () => {
    const user = userEvent.setup();
    renderWithProviders(<EditableDate />);
    const input = screen.getByRole('textbox', { name: 'Date' });
    fireEvent.change(input, { target: { value: '4.2.2026' } });
    expect(input).toHaveValue('4.2.2026');
    fireEvent.blur(input);
    expect(input).toHaveValue('04/02/2026');
    await user.click(screen.getByRole('button', { name: 'Reset' }));
    expect(input).toHaveValue('03/02/2025');
  });
});

describe('DateInput invalid drafts', () => {
  beforeEach(mockAuthentication);
  it.each(['31/02/2026', 'tomorrow', '01/01/2027'])(
    'retains and blocks invalid or out-of-range draft %s',
    draft => {
      const save = vi.fn(event => event.preventDefault());
      const change = vi.fn();
      renderWithProviders(
        <form noValidate onSubmit={save}>
          <label htmlFor="date">Date</label>
          <DateInput id="date" value="2026-02-01" max="2026-12-31" onChange={change} />
        </form>
      );
      const input = screen.getByRole('textbox', { name: 'Date' });
      fireEvent.change(input, { target: { value: draft } });
      fireEvent.blur(input);
      fireEvent.submit(input.closest('form')!);
      expect(input).toHaveValue(draft);
      expect(input).toHaveAttribute('aria-invalid', 'true');
      expect(input).toHaveAccessibleDescription();
      expect(save).not.toHaveBeenCalled();
      expect(change).not.toHaveBeenCalled();
      fireEvent.change(input, { target: { value: '28/02/2026' } });
      fireEvent.submit(input.closest('form')!);
      expect(save).toHaveBeenCalledOnce();
      expect(change).toHaveBeenLastCalledWith('2026-02-28');
    }
  );
});

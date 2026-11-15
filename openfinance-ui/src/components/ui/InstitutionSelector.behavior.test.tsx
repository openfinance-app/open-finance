import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { InstitutionSelector } from '@/components/ui/InstitutionSelector';

const state = vi.hoisted(() => ({
  institutions: [
    { id: 1, name: 'Alpine Bank', country: 'CH', logo: '/alpine.png', bic: 'ALPINE' },
    { id: 2, name: 'French Bank', country: 'FR', logo: undefined, bic: 'FRBANK' },
    { id: 3, name: 'Local Credit', country: undefined, logo: undefined, bic: undefined },
  ],
}));
vi.mock('@/hooks/useInstitutions', () => ({
  useInstitutions: () => ({ data: state.institutions, isLoading: false, isError: false }),
}));

describe('Institution selection and clearing', () => {
  beforeEach(() => {
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    Element.prototype.hasPointerCapture = vi.fn(() => false);
    Element.prototype.setPointerCapture = vi.fn();
    Element.prototype.releasePointerCapture = vi.fn();
  });
  it.each([
    ['1', 'Alpine Bank', '(CH)', '/alpine.png'],
    ['2', 'French Bank', '(France)', undefined],
    ['3', 'Local Credit', undefined, undefined],
  ])(
    'displays selected institution %s with its own country and logo',
    (value, name, country, logo) => {
      renderWithProviders(<InstitutionSelector value={value} onValueChange={vi.fn()} />);
      const trigger = screen.getByRole('combobox');
      expect(trigger).toHaveTextContent(name!);
      if (country) expect(trigger).toHaveTextContent(country);
      else expect(trigger).not.toHaveTextContent('(');
      if (logo) expect(trigger.querySelector('img')).toHaveAttribute('src', logo);
      else expect(trigger.querySelector('img')).toBeNull();
    }
  );
  it('displays a placeholder when a previously selected institution is unavailable', () => {
    renderWithProviders(
      <InstitutionSelector value="999" placeholder="Choose a bank" onValueChange={vi.fn()} />
    );
    expect(screen.getByRole('combobox')).toHaveTextContent('Choose a bank');
  });
  it('clears the current institution with an undefined value', async () => {
    const changed = vi.fn();
    renderWithProviders(<InstitutionSelector value="1" onValueChange={changed} />);
    await userEvent.click(screen.getByRole('combobox'));
    await userEvent.click(screen.getByRole('option', { name: 'None' }));
    expect(changed).toHaveBeenCalledExactlyOnceWith(undefined);
  });
  it('searches BIC, selects a matching bank and clears the search when reopened', async () => {
    const changed = vi.fn();
    renderWithProviders(<InstitutionSelector onValueChange={changed} />);
    await userEvent.click(screen.getByRole('combobox'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '  frbank  ' } });
    expect(screen.queryByRole('option', { name: /Alpine/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('option', { name: /French Bank/ }));
    expect(changed).toHaveBeenCalledWith('2');
    await userEvent.click(screen.getByRole('combobox'));
    expect(screen.getByRole('textbox')).toHaveValue('');
    expect(screen.getByRole('option', { name: /Alpine/ })).toBeVisible();
    expect(within(screen.getByRole('listbox')).getByText('OTHER')).toBeVisible();
  });
});

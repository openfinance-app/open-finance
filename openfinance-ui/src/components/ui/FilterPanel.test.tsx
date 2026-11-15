import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { FilterPanel } from '@/components/ui/FilterPanel';
import { countActiveFilters } from '@/utils/filter-utils';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

describe('FilterPanel', () => {
  beforeEach(() => mockAuthentication());

  it('counts zero as a monetary criterion, excluding unset values', () => {
    expect(countActiveFilters([0, '0', false, '', undefined, null])).toBe(2);
  });

  it('keeps active secondary criteria discoverable and clearable while collapsed', async () => {
    const user = userEvent.setup();
    const onClear = vi.fn();
    renderWithProviders(
      <FilterPanel
        activeCount={2}
        additionalCount={1}
        onClear={onClear}
        additional={<input aria-label="Minimum amount" defaultValue="0" />}
      >
        <input aria-label="Search" defaultValue="Rent" />
      </FilterPanel>
    );
    expect(screen.getByLabelText('Minimum amount')).not.toBeVisible();
    expect(screen.getByText('1 active')).toBeVisible();
    await user.click(screen.getByText('More filters'));
    expect(screen.getByLabelText('Minimum amount')).toBeVisible();
    await user.click(screen.getByText('More filters'));
    await user.click(screen.getByRole('button', { name: 'Clear' }));
    expect(onClear).toHaveBeenCalledOnce();
  });
});

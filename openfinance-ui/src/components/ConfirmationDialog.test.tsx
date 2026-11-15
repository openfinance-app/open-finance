import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
beforeEach(() => mockAuthentication());
describe('Confirmation while an operation is pending', () => {
  it.each([true, false])('only permits dismissing an idle operation (loading %s)', loading => {
    const changed = vi.fn();
    const confirm = vi.fn();
    renderWithProviders(
      <ConfirmationDialog
        open
        loading={loading}
        onOpenChange={changed}
        onConfirm={confirm}
        title="Delete item?"
        description="Remove this item"
      />
    );
    fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' });
    if (loading) expect(changed).not.toHaveBeenCalled();
    else expect(changed).toHaveBeenCalledWith(false);
    expect(confirm).not.toHaveBeenCalled();
  });
});

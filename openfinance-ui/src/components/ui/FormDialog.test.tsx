import { useState } from 'react';
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { FormDialog } from '@/components/ui/FormDialog';
import { useFormDraft } from '@/hooks/useFormDraft';
import { DialogContent, DialogTitle } from '@/components/ui/Dialog';
import { NumberInput } from '@/components/ui/NumberInput';

function Editor({ busy, close }: { busy: boolean; close: () => void }) {
  const [text, setText] = useState('');
  const cancel = useFormDraft(!!text, busy, close);
  return (
    <>
      <label htmlFor="name">Name</label>
      <input id="name" value={text} onChange={e => setText(e.target.value)} />
      <NumberInput value="50" onChange={() => {}} aria-label="Amount" />
      <button onClick={cancel}>Cancel</button>
      <button onClick={close}>Successful save</button>
    </>
  );
}
function Harness({ busy = false }: { busy?: boolean }) {
  const [open, setOpen] = useState(true);
  return (
    <FormDialog open={open} onOpenChange={setOpen}>
      <DialogContent>
        <DialogTitle>Edit</DialogTitle>
        <Editor busy={busy} close={() => setOpen(false)} />
      </DialogContent>
    </FormDialog>
  );
}

describe('FormDialog draft protection', () => {
  beforeEach(() => {
    mockAuthentication();
    vi.spyOn(window, 'confirm').mockReturnValue(false);
  });
  afterEach(() => vi.restoreAllMocks());

  it('closes an untouched form without confirmation', async () => {
    renderWithProviders(<Harness />);
    await userEvent.setup().keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(window.confirm).not.toHaveBeenCalled();
  });

  it.each(['escape', 'close', 'cancel'])(
    'requires confirmation for dirty %s dismissal',
    async mode => {
      const user = userEvent.setup();
      renderWithProviders(<Harness />);
      await user.type(screen.getByRole('textbox', { name: 'Name' }), 'New name');
      const dismiss = async () => {
        if (mode === 'escape') await user.keyboard('{Escape}');
        else
          await user.click(
            screen.getByRole('button', { name: mode === 'close' ? 'Close' : 'Cancel' })
          );
      };
      await dismiss();
      expect(window.confirm).toHaveBeenCalledOnce();
      expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('New name');
      vi.mocked(window.confirm).mockReturnValue(true);
      await dismiss();
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    }
  );

  it('protects an invalid numeric draft even when its canonical value has not changed', async () => {
    renderWithProviders(<Harness />);
    fireEvent.change(screen.getByRole('textbox', { name: 'Amount' }), {
      target: { value: 'invalid' },
    });
    await userEvent.setup().keyboard('{Escape}');
    expect(window.confirm).toHaveBeenCalledOnce();
    expect(screen.getByRole('textbox', { name: 'Amount' })).toHaveValue('invalid');
  });

  it('blocks dismissal while saving without prompting', async () => {
    renderWithProviders(<Harness busy />);
    await userEvent.setup().keyboard('{Escape}');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(window.confirm).not.toHaveBeenCalled();
  });

  it('allows successful saves to close without a discard prompt', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Harness />);
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'Edited');
    await user.click(screen.getByRole('button', { name: 'Successful save' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(window.confirm).not.toHaveBeenCalled();
  });
});

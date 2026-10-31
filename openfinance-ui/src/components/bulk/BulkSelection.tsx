import { useContext, useRef, useState, type ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Pencil, Trash2, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { ConfirmationDialog } from '@/components/ConfirmationDialog';
import { BulkEditDialog } from '@/components/bulk/BulkEditDialog';
import { BulkContext } from '@/components/bulk/bulk-context';
import {
  BULK_FIELDS,
  type BulkItem,
  type BulkResource,
  type BulkResult,
  type MetadataPatch,
} from '@/types/bulk';
import { runBulkAction } from '@/utils/bulk-actions';
import { cn } from '@/lib/utils';
import '@/components/bulk/bulk-selection.css';

export function BulkSelection({
  resource,
  items,
  children,
  scopeKey = '',
  className,
}: {
  resource: BulkResource;
  items: BulkItem[];
  children: ReactNode;
  scopeKey?: string;
  className?: string;
}) {
  const { t } = useTranslation('common');
  const queryClient = useQueryClient();
  const [selection, setSelection] = useState<{ scope: string; ids: Set<number> }>({
    scope: scopeKey,
    ids: new Set(),
  });
  const [dialog, setDialog] = useState<'edit' | 'delete' | null>(null);
  const [result, setResult] = useState<BulkResult | null>(null);
  const [progress, setProgress] = useState(0);
  const inFlight = useRef(false);
  const failedIds = new Set(result?.failed.map(failure => failure.item.id));
  if (selection.scope !== scopeKey) {
    // Keep failed records available if deletion moves them onto the preceding page.
    setSelection({ scope: scopeKey, ids: failedIds });
  } else if (
    [...selection.ids].some(id => !failedIds.has(id) && !items.some(item => item.id === id))
  ) {
    setSelection({
      scope: scopeKey,
      ids: new Set(
        [...selection.ids].filter(id => failedIds.has(id) || items.some(item => item.id === id))
      ),
    });
  }
  // Selection is scoped to the current view; hidden/filter-excluded rows are never acted upon.
  const selected = new Set(
    items
      .filter(item => selection.scope === scopeKey && selection.ids.has(item.id))
      .map(item => item.id)
  );
  const selectedItems = items.filter(item => selected.has(item.id));
  const mutation = useMutation({
    mutationFn: (patch: MetadataPatch | undefined) =>
      runBulkAction(resource, selectedItems, patch, setProgress, t('bulk.requestFailed')),
    onSuccess: async value => {
      setResult(value);
      setSelection({ scope: scopeKey, ids: new Set(value.failed.map(failure => failure.item.id)) });
      setDialog(null);
      // Bulk deletes/edits can affect linked cards, summaries, search, budgets and undo history.
      // Invalidate once after the whole batch, including partial failures.
      await queryClient.invalidateQueries();
    },
  });
  const execute = async (patch?: MetadataPatch) => {
    if (inFlight.current || dialog === null || selectedItems.length === 0) return;
    inFlight.current = true;
    setProgress(0);
    setResult(null);
    try {
      await mutation.mutateAsync(patch);
    } finally {
      inFlight.current = false;
    }
  };
  const changeSelection = (ids: Set<number>) => {
    setSelection({ scope: scopeKey, ids });
    setResult(null);
  };
  const locked = mutation.isPending || dialog !== null;
  return (
    <BulkContext.Provider
      value={{
        items,
        selected,
        busy: mutation.isPending,
        locked,
        progress,
        result,
        canEdit: !!BULK_FIELDS[resource],
        toggle: id => {
          if (locked) return;
          const next = new Set(selected);
          if (next.has(id)) next.delete(id);
          else next.add(id);
          changeSelection(next);
        },
        toggleAll: () => {
          if (!locked)
            changeSelection(
              selected.size === items.length ? new Set() : new Set(items.map(item => item.id))
            );
        },
        clear: () => {
          if (!locked) changeSelection(new Set());
        },
        edit: () => setDialog('edit'),
        remove: () => setDialog('delete'),
      }}
    >
      <div className={className}>{children}</div>
      <ConfirmationDialog
        open={dialog === 'delete'}
        onOpenChange={open => {
          if (!open && !mutation.isPending) setDialog(null);
        }}
        onConfirm={() => {
          void execute();
        }}
        title={t('bulk.deleteTitle', { count: selected.size })}
        description={t(`bulk.deleteDescription.${resource}`)}
        confirmText={t('bulk.delete', { count: selected.size })}
        confirmDisabled={selected.size === 0}
        loading={mutation.isPending}
      >
        <ul className="max-h-40 overflow-y-auto space-y-1 text-sm text-text-secondary">
          {selectedItems.map(item => (
            <li key={item.id} className="break-words">
              {item.label}
            </li>
          ))}
        </ul>
        {mutation.isPending && (
          <p role="status">
            {t('bulk.progress', { completed: progress, count: selectedItems.length })}
          </p>
        )}
      </ConfirmationDialog>
      {dialog === 'edit' && (
        <BulkEditDialog
          resource={resource}
          items={selectedItems}
          busy={mutation.isPending}
          progress={progress}
          onClose={() => {
            if (!mutation.isPending) setDialog(null);
          }}
          onSubmit={execute}
        />
      )}
    </BulkContext.Provider>
  );
}

export function SelectionCheckbox({ id, className }: { id: number; className?: string }) {
  const context = useContext(BulkContext);
  const { t } = useTranslation('common');
  const item = context?.items.find(value => value.id === id);
  if (!context || !item) return null;
  return (
    <label
      className={cn(
        'selection-checkbox inline-flex h-8 w-8 shrink-0 cursor-pointer items-center justify-center rounded-md hover:bg-surface-elevated focus-within:bg-surface-elevated',
        className
      )}
      data-selected={context.selected.has(id)}
      onClick={event => event.stopPropagation()}
      onKeyDown={event => event.stopPropagation()}
    >
      <input
        type="checkbox"
        className="selection-input h-4 w-4 accent-primary focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary"
        aria-label={t('bulk.selectItem', { name: item.label })}
        checked={context.selected.has(id)}
        disabled={context.locked}
        onChange={() => context.toggle(id)}
      />
    </label>
  );
}

export function BulkToolbar({ embedded = false }: { embedded?: boolean }) {
  const context = useContext(BulkContext);
  const { t } = useTranslation('common');
  if (!context || (context.selected.size === 0 && !context.result)) return null;
  const { items, selected, result, locked } = context;
  return (
    <div className={embedded ? undefined : 'space-y-3 mb-4'}>
      {selected.size > 0 && (
        <div
          className={cn(
            'flex flex-wrap items-center gap-x-4 gap-y-2 border-border py-2',
            embedded ? 'border-b px-3 lg:px-4' : 'border-y'
          )}
        >
          <label className="flex min-h-11 cursor-pointer items-center gap-2 text-sm text-text-secondary">
            <input
              type="checkbox"
              className="selection-input h-4 w-4 accent-primary focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary"
              checked={items.length > 0 && selected.size === items.length}
              ref={element => {
                if (element)
                  element.indeterminate = selected.size > 0 && selected.size < items.length;
              }}
              onChange={context.toggleAll}
              disabled={locked || items.length === 0}
            />
            {t('bulk.selectVisible')}
          </label>
          <span className="text-sm tabular-nums text-text-secondary" role="status">
            {t('bulk.selected', { count: selected.size })}
          </span>
          <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
            {context.canEdit && (
              <Button
                variant="outline"
                onClick={context.edit}
                disabled={locked}
                aria-label={t('bulk.edit')}
                className="px-3 text-sm sm:px-4 sm:text-base"
              >
                <Pencil size={16} className="hidden sm:block mr-2" />
                <span className="sm:hidden">{t('bulk.editShort')}</span>
                <span className="hidden sm:inline">{t('bulk.edit')}</span>
              </Button>
            )}
            <Button
              variant="danger"
              onClick={context.remove}
              disabled={locked}
              aria-label={t('bulk.delete', { count: selected.size })}
              className="px-3 text-sm sm:px-4 sm:text-base"
            >
              <Trash2 size={16} className="hidden sm:block mr-2" />
              <span className="sm:hidden">{t('bulk.deleteShort', { count: selected.size })}</span>
              <span className="hidden sm:inline">{t('bulk.delete', { count: selected.size })}</span>
            </Button>
            <Button
              variant="ghost"
              onClick={context.clear}
              disabled={locked}
              aria-label={t('bulk.clearSelection')}
              className="w-11 px-0"
            >
              <X size={16} />
            </Button>
          </div>
        </div>
      )}
      {result && (
        <div
          role={result.failed.length ? 'alert' : 'status'}
          className={cn(
            'text-sm space-y-2',
            embedded && 'border-b border-border px-3 py-3 lg:px-4'
          )}
        >
          <p className={result.failed.length ? 'text-error' : 'text-success'}>
            {t('bulk.result', { succeeded: result.succeeded.length, failed: result.failed.length })}
          </p>
          {result.failed.length > 0 && (
            <>
              <p className="text-text-secondary">{t('bulk.retryHint')}</p>
              <ul className="max-h-48 overflow-y-auto space-y-1 text-text-secondary">
                {result.failed.map(({ item, message }) => (
                  <li key={item.id} className="break-words">
                    <span className="font-medium text-text-primary">{item.label}:</span> {message}
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </div>
  );
}

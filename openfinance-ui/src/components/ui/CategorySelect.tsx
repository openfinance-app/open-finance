/**
 * CategorySelect component
 * Task CAT-2.3: Create CategorySelect component
 * 
 * A dropdown component for selecting categories with search functionality.
 * Supports grouping by parent category, type filtering, and creating new categories.
 *
 * Uses Popover (not Radix Select) so that async inline-create can set the
 * value reliably — Radix Select requires values to match a rendered SelectItem
 * and does not support async operations during selection.
 */

import { useState, useMemo, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/Popover';
import { markSelectInteraction } from '@/utils/selectClickGuard';
import { useCategoryTree, useCreateCategory } from '@/hooks/useTransactions';
import { useQueryClient } from '@tanstack/react-query';
import { Loader2, Search, FolderOpen, Plus, ChevronRight, ChevronDown, Check } from 'lucide-react';
import type { CategoryTreeNode, TransactionType } from '@/types/transaction';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';

interface CategorySelectProps {
  value?: number;
  onValueChange: (value: number | undefined) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  /**
   * Filter categories by type
   */
  type?: TransactionType;
  /**
   * Show "None" option at the top
   */
  allowNone?: boolean;
  /**
   * Enable option to create new category
   */
  allowCreateNew?: boolean;
  /**
   * Callback when create new is clicked
   */
  onCreateNew?: () => void;
  /**
   * Placeholder text for the search input inside the dropdown
   */
  searchPlaceholder?: string;
  /**
   * Show "✨ Create" item inline when no category matches the search query.
   * The item calls createCategory immediately on selection.
   */
  allowCreateInline?: boolean;
  /**
   * Category type to use when creating a new category inline.
   * Pass the parent form's current transaction type. Defaults to 'EXPENSE'.
   */
  inferredType?: TransactionType;
}

/**
 * Flatten tree into sorted list with depth information
 */
function flattenCategories(
  categories: CategoryTreeNode[],
  depth: number = 0,
  parentPath: string = ''
): Array<{ category: CategoryTreeNode; depth: number; path: string }> {
  const result: Array<{ category: CategoryTreeNode; depth: number; path: string }> = [];

  // Sort: by type first (INCOME before EXPENSE), then by name
  const sorted = [...categories].sort((a, b) => {
    const typeCompare = a.type.localeCompare(b.type);
    if (typeCompare !== 0) return typeCompare;
    return a.name.localeCompare(b.name);
  });

  for (const category of sorted) {
    const currentPath = parentPath ? `${parentPath} / ${category.name}` : category.name;
    result.push({ category, depth, path: currentPath });

    if (category.subcategories && category.subcategories.length > 0) {
      result.push(...flattenCategories(category.subcategories, depth + 1, currentPath));
    }
  }

  return result;
}

/**
 * Pure utility — determines whether the "✨ Create" inline item should be shown.
 * Exported for unit testing.
 */
export function shouldShowCreateInline(
  query: string,
  flatItems: Array<{ category: CategoryTreeNode }>,
  allowCreateInline: boolean
): boolean {
  if (!allowCreateInline) return false;
  const trimmed = query.trim();
  if (!trimmed) return false;
  const lower = trimmed.toLowerCase();
  return !flatItems.some(({ category }) => category.name.toLowerCase() === lower);
}

export function CategorySelect({
  value,
  onValueChange,
  placeholder = 'Select category',
  disabled = false,
  className,
  type,
  allowNone = true,
  allowCreateNew = false,
  onCreateNew,
  searchPlaceholder,
  allowCreateInline = false,
  inferredType = 'EXPENSE',
}: CategorySelectProps) {
  const { t } = useTranslation('categories');
  const { data: categories = [], isLoading, isError } = useCategoryTree();
  const { mutateAsync: createCategoryAsync, isPending: isCreating } = useCreateCategory();
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState('');
  const [isOpen, setIsOpen] = useState(false);
  // Filter categories by type
  const filteredByType = useMemo(() => {
    if (!type) return categories;
    return categories.filter(c => c.type === type);
  }, [categories, type]);

  // Flatten and filter by search query
  const flatCategories = useMemo(() => {
    const flattened = flattenCategories(filteredByType);

    if (!searchQuery.trim()) return flattened;

    const query = searchQuery.toLowerCase();
    return flattened.filter(
      ({ category, path }) =>
        category.name.toLowerCase().includes(query) ||
        path.toLowerCase().includes(query) ||
        (category.mccCode && category.mccCode.toLowerCase().includes(query))
    );
  }, [filteredByType, searchQuery]);

  // Find selected category
  const selectedCategory = useMemo(() => {
    if (!value) return null;
    const findInTree = (cats: CategoryTreeNode[]): CategoryTreeNode | null => {
      for (const cat of cats) {
        if (cat.id === value) return cat;
        if (cat.subcategories) {
          const found = findInTree(cat.subcategories);
          if (found) return found;
        }
      }
      return null;
    };
    return findInTree(categories);
  }, [value, categories]);

  const selectItem = (categoryId: number | undefined) => {
    onValueChange(categoryId);
    setIsOpen(false);
    setSearchQuery('');
    markSelectInteraction();
  };

  const handleCreateInline = async () => {
    if (!searchQuery.trim()) return;
    try {
      const newCategory = await createCategoryAsync({
        name: searchQuery.trim(),
        type: inferredType,
      });
      onValueChange(newCategory.id);
      setIsOpen(false);
      setSearchQuery('');
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        // Category already exists — fetch fresh tree and find it
        try {
          const { buildEncryptionHeaders } = await import('@/utils/encryption');
          const { default: apiClient } = await import('@/services/apiClient');
          const res = await apiClient.get<CategoryTreeNode[]>('/categories/tree', {
            headers: buildEncryptionHeaders(),
          });
          await queryClient.setQueryData(['categories', 'tree'], res.data);
          const flat = flattenCategories(res.data);
          const match = flat.find(
            ({ category }) =>
              category.name.toLowerCase() === searchQuery.trim().toLowerCase()
          );
          if (match) {
            onValueChange(match.category.id);
            setIsOpen(false);
            setSearchQuery('');
          } else {
            console.error('Category already exists but could not be found. Please refresh.');
          }
        } catch {
          console.error('Failed to resolve duplicate category. Please try again.');
        }
      } else {
        console.error('Failed to create category. Please try again.');
      }
    }
  };

  const itemClass =
    'relative flex w-full cursor-default select-none items-center rounded-md py-1.5 pl-8 pr-2 text-sm outline-none hover:bg-surface-elevated focus:bg-surface-elevated transition-colors duration-150';

  return (
    <Popover
      open={isOpen}
      onOpenChange={(open) => {
        setIsOpen(open);
        if (!open) {
          markSelectInteraction();
          setSearchQuery('');
        }
      }}
    >
      <div className="relative">
        <PopoverTrigger asChild>
          <button
            type="button"
            role="combobox"
            aria-expanded={isOpen}
            disabled={disabled}
            className={cn(
              'flex h-10 w-full items-center justify-between rounded-lg border border-border bg-surface px-3 py-2 text-sm text-text-primary',
              'placeholder:text-text-muted',
              'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background',
              'disabled:cursor-not-allowed disabled:opacity-50',
              'transition-all duration-150',
              className
            )}
          >
            <span className="flex-1 truncate text-left">
              {selectedCategory ? (
                <span className="flex items-center gap-2">
                  <span
                    className="w-5 h-5 rounded flex items-center justify-center text-white text-xs shrink-0"
                    style={{ backgroundColor: selectedCategory.color || '#6B7280' }}
                  >
                    {selectedCategory.icon || <FolderOpen size={12} />}
                  </span>
                  <span>{selectedCategory.name}</span>
                  <span className="text-xs text-text-tertiary">
                    ({selectedCategory.transactionCount || 0} txns)
                  </span>
                </span>
              ) : (
                <span className="text-text-muted">{placeholder}</span>
              )}
            </span>
            <ChevronDown className="h-4 w-4 opacity-50 shrink-0" />
          </button>
        </PopoverTrigger>
        {/* Pointer-blocking overlay while creating */}
        {isCreating && (
          <div className="absolute inset-0 z-10 cursor-wait" aria-hidden="true" />
        )}
        {/* sr-only test trigger — allows tests to invoke creation path */}
        {allowCreateInline && (
          <button
            type="button"
            data-testid="create-inline-trigger"
            className="sr-only"
            tabIndex={-1}
            onClick={() => handleCreateInline()}
            aria-hidden="true"
          />
        )}
      </div>

      <PopoverContent
        className="max-h-[400px] flex flex-col p-0 w-(--radix-popover-trigger-width)"
        onOpenAutoFocus={(e) => e.preventDefault()}
      >
        {/* Search header */}
        <div className="p-2 border-b border-border bg-surface shrink-0">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-text-tertiary" />
            <Input
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={searchPlaceholder ?? t('search.placeholder')}
              className="pl-9 h-9"
              onClick={(e) => e.stopPropagation()}
              onKeyDown={(e) => {
                if (e.key === 'Escape') setIsOpen(false);
              }}
              autoFocus
            />
          </div>
        </div>

        {/* Scrollable list */}
        <div className="max-h-72 overflow-y-auto p-1">
          {/* Loading State */}
          {isLoading && (
            <div className="flex items-center justify-center p-4">
              <Loader2 size={20} className="animate-spin text-text-tertiary" />
            </div>
          )}

          {/* Error State */}
          {isError && (
            <div className="p-4 text-center text-sm text-error">
              {t('loadError.title')}
            </div>
          )}

          {/* None Option */}
          {allowNone && !isLoading && (
            <button
              type="button"
              className={itemClass}
              onClick={() => selectItem(undefined)}
            >
              {value === undefined && (
                <span className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
                  <Check className="h-4 w-4 text-primary" />
                </span>
              )}
              <span className="text-text-tertiary">{t('select.noCategory')}</span>
            </button>
          )}

          {/* Category List */}
          {!isLoading && flatCategories.map(({ category, depth }) => (
            <button
              key={category.id}
              type="button"
              className={cn(itemClass, 'cursor-pointer')}
              onClick={() => selectItem(category.id)}
            >
              {value === category.id && (
                <span className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
                  <Check className="h-4 w-4 text-primary" />
                </span>
              )}
              <div
                className="flex items-center gap-2"
                style={{ paddingLeft: `${depth * 16}px` }}
              >
                {/* Expand indicator for subcategories */}
                {depth > 0 && (
                  <ChevronRight size={14} className="text-text-tertiary" />
                )}

                {/* Icon */}
                <div
                  className="w-6 h-6 rounded flex items-center justify-center text-white text-xs shrink-0"
                  style={{ backgroundColor: category.color || '#6B7280' }}
                >
                  {category.icon ? (
                    <span className="text-[10px]">{category.icon}</span>
                  ) : (
                    <FolderOpen size={12} />
                  )}
                </div>

                {/* Name */}
                <div className="flex-1 min-w-0">
                  <div className="truncate">{category.name}</div>
                  {category.mccCode && (
                    <div className="text-xs text-text-tertiary">MCC: {category.mccCode}</div>
                  )}
                </div>

                {/* Transaction count */}
                <span className="text-xs text-text-tertiary">
                  {category.transactionCount || 0}
                </span>
              </div>
            </button>
          ))}

          {/* Empty State */}
          {!isLoading && flatCategories.length === 0 && !shouldShowCreateInline(searchQuery, flatCategories, allowCreateInline) && (
            <div className="p-4 text-center text-sm text-text-tertiary">
              {searchQuery ? 'No matching categories' : 'No categories available'}
            </div>
          )}

          {/* Inline create item */}
          {!isLoading && shouldShowCreateInline(searchQuery, flatCategories, allowCreateInline) && (
            <button
              type="button"
              className={cn(itemClass, 'cursor-pointer border-t border-border mt-1 pt-1')}
              onClick={() => handleCreateInline()}
            >
              <div className="flex items-center gap-2 text-primary">
                <span>✨</span>
                <span>Create &ldquo;{searchQuery.trim()}&rdquo;</span>
              </div>
            </button>
          )}
        </div>

        {/* Footer — "Create new category" button */}
        {allowCreateNew && !allowCreateInline && !isLoading && flatCategories.length > 0 && (
          <div className="border-t border-border p-2 bg-surface shrink-0">
            <Button
              variant="ghost"
              size="sm"
              className="w-full justify-start text-primary"
              onClick={(e) => {
                e.stopPropagation();
                onCreateNew?.();
              }}
            >
              <Plus size={16} className="mr-2" />
              {t('select.createNew')}
            </Button>
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}

// ---------------------------------------------------------------------------
// CategoryCombobox — free-text input with dropdown suggestions (no creation)
// Used in import review for inline and bulk category editing.
// ---------------------------------------------------------------------------

export interface CategoryComboboxProps {
  value: string;
  onValueChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
}

export function CategoryCombobox({
  value,
  onValueChange,
  placeholder = 'Type category...',
  disabled = false,
  className,
}: CategoryComboboxProps) {
  const { data: categories = [] } = useCategoryTree();
  const [isOpen, setIsOpen] = useState(false);
  const [inputValue, setInputValue] = useState(value);

  // Sync when value changes externally (e.g. bulk clear)
  useEffect(() => {
    setInputValue(value);
  }, [value]);

  const flatList = useMemo(() => flattenCategories(categories), [categories]);

  const filtered = useMemo(() => {
    const q = inputValue.trim().toLowerCase();
    if (!q) return flatList.slice(0, 30);
    return flatList.filter(
      ({ category, path }) =>
        category.name.toLowerCase().includes(q) || path.toLowerCase().includes(q)
    );
  }, [flatList, inputValue]);

  return (
    <div className="relative">
      <input
        type="text"
        value={inputValue}
        onChange={e => {
          // Update local state only — don't call onValueChange on every keystroke
          // to avoid expensive re-renders in parent tables.
          setInputValue(e.target.value);
          setIsOpen(true);
        }}
        onBlur={() => {
          // Commit to parent on blur
          setTimeout(() => setIsOpen(false), 150);
          onValueChange(inputValue);
        }}
        onFocus={() => setIsOpen(true)}
        placeholder={placeholder}
        disabled={disabled}
        className={cn(
          'w-full px-2 py-1 bg-surface border border-border rounded text-sm focus:outline-none focus:ring-1 focus:ring-primary placeholder:text-text-tertiary',
          className
        )}
      />
      {isOpen && filtered.length > 0 && (
        <div className="absolute top-full left-0 right-0 z-50 max-h-48 overflow-auto bg-surface border border-border rounded-b shadow-lg">
          {filtered.map(({ category, depth }) => (
            <button
              key={category.id}
              type="button"
              onMouseDown={e => {
                e.preventDefault();
                // Commit immediately on selection
                onValueChange(category.name);
                setInputValue(category.name);
                setIsOpen(false);
              }}
              className="w-full text-left px-3 py-1.5 text-sm hover:bg-surface-elevated text-text-primary"
              style={{ paddingLeft: `${8 + depth * 16}px` }}
            >
              {category.name}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

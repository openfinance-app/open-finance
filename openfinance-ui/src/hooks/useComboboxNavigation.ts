import { useEffect, useId, useRef, useState } from 'react';
import type {
  ButtonHTMLAttributes,
  HTMLAttributes,
  InputHTMLAttributes,
  KeyboardEvent,
  MouseEvent,
  RefObject,
} from 'react';

interface ComboboxNavigationOptions {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  options: readonly string[];
  onSelect: (value: string) => void;
  selectedValue?: string;
  searchQuery: string;
  disabled?: boolean;
}

type OptionProps = ButtonHTMLAttributes<HTMLButtonElement> & { 'data-highlighted'?: string };

interface ComboboxNavigation {
  onOpenChange: (open: boolean) => void;
  triggerProps: ButtonHTMLAttributes<HTMLButtonElement>;
  inputProps: InputHTMLAttributes<HTMLInputElement>;
  listProps: HTMLAttributes<HTMLDivElement> & { ref: RefObject<HTMLDivElement | null> };
  getOptionProps: (value: string) => OptionProps;
}

/** Keyboard navigation for searchable lists, keeping focus in the search input. */
export function useComboboxNavigation({
  open,
  onOpenChange,
  options,
  onSelect,
  selectedValue,
  searchQuery,
  disabled = false,
}: ComboboxNavigationOptions): ComboboxNavigation {
  const listId = useId();
  const listRef = useRef<HTMLDivElement>(null);
  const [highlight, setHighlight] = useState<{ value: string; query: string } | null>(null);
  const activeIndex =
    open && highlight?.query === searchQuery ? options.indexOf(highlight.value) : -1;
  const activeValue = activeIndex >= 0 ? options[activeIndex] : undefined;
  const optionId = (value: string): string => `${listId}-${encodeURIComponent(value)}`;
  const activeId = activeValue === undefined ? undefined : optionId(activeValue);

  useEffect(() => {
    if (activeId) {
      listRef.current?.ownerDocument.getElementById(activeId)?.scrollIntoView({ block: 'nearest' });
    }
  }, [activeId]);

  const handleOpenChange = (nextOpen: boolean): void => {
    setHighlight(null);
    onOpenChange(nextOpen);
  };

  const highlightOption = (value: string): void => {
    setHighlight({ value, query: searchQuery });
  };

  const onKeyDown = (event: KeyboardEvent<HTMLElement>): void => {
    if (event.nativeEvent.isComposing || event.altKey || event.ctrlKey || event.metaKey) return;

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      event.stopPropagation();
      if (disabled) return;

      onOpenChange(true);
      const direction = event.key === 'ArrowDown' ? 1 : -1;
      const initialIndex = selectedValue === undefined ? -1 : options.indexOf(selectedValue);
      const firstIndex = direction === 1 ? 0 : options.length - 1;
      const nextIndex =
        activeIndex < 0
          ? initialIndex >= 0
            ? initialIndex
            : firstIndex
          : Math.max(0, Math.min(options.length - 1, activeIndex + direction));

      if (options[nextIndex] !== undefined) highlightOption(options[nextIndex]);
    } else if (event.key === 'Enter' && open) {
      // An empty result list must not submit the surrounding form.
      event.preventDefault();
      event.stopPropagation();
      if (!disabled && activeValue !== undefined) onSelect(activeValue);
    } else if (event.key === 'Escape' && open) {
      event.preventDefault();
      event.stopPropagation();
      handleOpenChange(false);
    }
  };

  return {
    onOpenChange: handleOpenChange,
    triggerProps: {
      'aria-controls': open ? listId : undefined,
      'aria-haspopup': 'listbox',
      'aria-expanded': open,
      onKeyDown: (event: KeyboardEvent<HTMLButtonElement>): void => {
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') onKeyDown(event);
      },
    },
    inputProps: {
      role: 'combobox',
      'aria-autocomplete': 'list',
      'aria-controls': open ? listId : undefined,
      'aria-expanded': open,
      'aria-activedescendant': activeId,
      onKeyDown,
    },
    listProps: {
      id: listId,
      ref: listRef,
      role: 'listbox',
    },
    getOptionProps: (value: string): OptionProps => ({
      id: optionId(value),
      role: 'option',
      'aria-selected': value === selectedValue,
      'data-highlighted': value === activeValue ? '' : undefined,
      tabIndex: -1,
      // Preserve the search cursor when choosing or hovering over an option.
      onMouseDown: (event: MouseEvent<HTMLButtonElement>): void => event.preventDefault(),
      onMouseMove: (): void => highlightOption(value),
    }),
  };
}

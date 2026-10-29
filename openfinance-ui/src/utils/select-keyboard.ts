import type { KeyboardEvent } from 'react';

/** Let Radix handle navigation and dismissal without running typeahead while searching. */
export function handleSelectSearchKeyDown(event: KeyboardEvent<HTMLInputElement>): void {
  if (
    event.nativeEvent.isComposing ||
    event.altKey ||
    event.ctrlKey ||
    event.metaKey ||
    !['ArrowDown', 'ArrowUp', 'Escape', 'Tab'].includes(event.key)
  ) {
    event.stopPropagation();
    if (event.key === 'Enter' && !event.nativeEvent.isComposing) event.preventDefault();
  }
}

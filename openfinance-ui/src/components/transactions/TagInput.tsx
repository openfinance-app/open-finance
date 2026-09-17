/**
 * TagInput Component
 * Task 12.3.4: Create TagInput component with autocomplete and tag chips display
 *
 * Component for entering and managing transaction tags with autocomplete functionality
 */
import { useState, useRef, useEffect } from 'react';
import { X } from 'lucide-react';
import { Badge } from '@/components/ui/Badge';

interface TagInputProps {
  value: string[];
  onChange: (tags: string[]) => void;
  suggestions?: string[];
  placeholder?: string;
  maxTags?: number;
  disabled?: boolean;
}

/**
 * TagInput - Component for entering tags with autocomplete
 *
 * Features:
 * - Add tags by typing and pressing Enter, comma, or space
 * - Remove tags by clicking the X button
 * - Autocomplete suggestions from popular tags
 * - Tag chips display with visual styling
 * - Maximum tag limit enforcement
 * - Duplicate tag prevention
 *
 * @param value - Array of current tags
 * @param onChange - Callback when tags change
 * @param suggestions - Optional array of suggested tags for autocomplete
 * @param placeholder - Placeholder text for input
 * @param maxTags - Maximum number of tags allowed (default: 10)
 * @param disabled - Whether the input is disabled
 */
export function TagInput({
  value = [],
  onChange,
  suggestions = [],
  placeholder = 'Add tags (press Enter, comma, or space)',
  maxTags = 10,
  disabled = false,
}: TagInputProps) {
  const [inputValue, setInputValue] = useState('');
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [selectedSuggestionIndex, setSelectedSuggestionIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const suggestionsRef = useRef<HTMLDivElement>(null);

  // Filter suggestions based on input value and exclude already added tags
  const filteredSuggestions = suggestions
    .filter(tag => tag.toLowerCase().includes(inputValue.toLowerCase()) && !value.includes(tag))
    .slice(0, 5); // Show max 5 suggestions

  // Reset selected suggestion when filtered suggestions change
  useEffect(() => {
    setSelectedSuggestionIndex(0);
  }, [inputValue]);

  // Handle click outside to close suggestions
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        suggestionsRef.current &&
        !suggestionsRef.current.contains(event.target as Node) &&
        inputRef.current &&
        !inputRef.current.contains(event.target as Node)
      ) {
        setShowSuggestions(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const addTag = (tag: string) => {
    const trimmedTag = tag.trim();

    // Validation
    if (!trimmedTag) return;
    if (value.includes(trimmedTag)) return; // Duplicate
    if (value.length >= maxTags) return; // Max limit reached
    if (trimmedTag.length > 50) return; // Tag too long

    onChange([...value, trimmedTag]);
    setInputValue('');
    setShowSuggestions(false);
    inputRef.current?.focus();
  };

  const removeTag = (indexToRemove: number) => {
    onChange(value.filter((_, index) => index !== indexToRemove));
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setInputValue(newValue);
    setShowSuggestions(newValue.length > 0 && filteredSuggestions.length > 0);
  };

  const handleInputKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    // Enter: Add tag or select suggestion
    if (e.key === 'Enter') {
      e.preventDefault();
      if (showSuggestions && filteredSuggestions.length > 0) {
        addTag(filteredSuggestions[selectedSuggestionIndex]);
      } else if (inputValue.trim()) {
        addTag(inputValue);
      }
      return;
    }

    // Comma or Space: Add tag (delimiter)
    if (e.key === ',' || e.key === ' ') {
      e.preventDefault();
      if (inputValue.trim()) {
        addTag(inputValue);
      }
      return;
    }

    // Backspace: Remove last tag if input is empty
    if (e.key === 'Backspace' && !inputValue && value.length > 0) {
      removeTag(value.length - 1);
      return;
    }

    // Arrow Down: Navigate suggestions
    if (e.key === 'ArrowDown' && showSuggestions) {
      e.preventDefault();
      setSelectedSuggestionIndex(prev => (prev < filteredSuggestions.length - 1 ? prev + 1 : prev));
      return;
    }

    // Arrow Up: Navigate suggestions
    if (e.key === 'ArrowUp' && showSuggestions) {
      e.preventDefault();
      setSelectedSuggestionIndex(prev => (prev > 0 ? prev - 1 : 0));
      return;
    }

    // Escape: Close suggestions
    if (e.key === 'Escape') {
      setShowSuggestions(false);
      return;
    }
  };

  const handleSuggestionClick = (tag: string) => {
    addTag(tag);
  };

  return (
    <div className="relative w-full">
      {/* Tags Display + Input */}
      <div
        className={`
          flex flex-wrap gap-2 p-2 border border-border rounded-lg
          min-h-[42px] focus-within:ring-2 focus-within:ring-primary focus-within:border-primary
          ${disabled ? 'bg-surface-elevated cursor-not-allowed' : 'bg-background'}
        `}
        onClick={() => inputRef.current?.focus()}
      >
        {/* Tag Chips */}
        {value.map((tag, index) => (
          <Badge
            key={index}
            variant="default"
            className="flex items-center gap-1 px-2 py-1 text-xs"
          >
            <span>{tag}</span>
            {!disabled && (
              <button
                type="button"
                onClick={e => {
                  e.stopPropagation();
                  removeTag(index);
                }}
                className="hover:bg-surface-elevated rounded-full p-0.5 transition-colors"
                aria-label={`Remove tag ${tag}`}
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </Badge>
        ))}

        {/* Input Field */}
        {!disabled && value.length < maxTags && (
          <input
            ref={inputRef}
            type="text"
            value={inputValue}
            onChange={handleInputChange}
            onKeyDown={handleInputKeyDown}
            onFocus={() => {
              if (inputValue && filteredSuggestions.length > 0) {
                setShowSuggestions(true);
              }
            }}
            placeholder={value.length === 0 ? placeholder : ''}
            className="flex-1 min-w-[120px] bg-transparent border-none outline-none text-sm placeholder:text-muted-foreground"
            disabled={disabled}
          />
        )}
      </div>

      {/* Max Tags Warning */}
      {value.length >= maxTags && (
        <p className="mt-1 text-xs text-warning">Maximum {maxTags} tags reached</p>
      )}

      {/* Autocomplete Suggestions Dropdown */}
      {showSuggestions && filteredSuggestions.length > 0 && (
        <div
          ref={suggestionsRef}
          className="absolute z-10 w-full mt-1 bg-background border border-border rounded-lg shadow-lg overflow-hidden"
        >
          {filteredSuggestions.map((tag, index) => (
            <button
              key={tag}
              type="button"
              onClick={() => handleSuggestionClick(tag)}
              onMouseEnter={() => setSelectedSuggestionIndex(index)}
              className={`
                w-full text-left px-3 py-2 text-sm transition-colors
                ${
                  index === selectedSuggestionIndex
                    ? 'bg-surface text-text-primary'
                    : 'bg-background text-text-secondary hover:bg-surface-elevated'
                }
              `}
            >
              {tag}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

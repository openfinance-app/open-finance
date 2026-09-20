/**
 * Chat Input Component
 * Task 11.3.3: Create ChatInput component
 *
 * Multi-line text input for chatting with AI assistant:
 * - Auto-resizing textarea
 * - Send button
 * - Keyboard shortcuts (Enter to send, Shift+Enter for new line)
 * - Character limit indicator
 * - Loading state
 *
 * @since Sprint 11 - AI Assistant Integration
 */
import React, { useRef, useEffect } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Send, Loader2 } from 'lucide-react';

interface ChatInputProps {
  /** Current input value */
  value: string;

  /** Input change handler */
  onChange: (value: string) => void;

  /** Submit handler */
  onSubmit: () => void;

  /** Whether a message is currently being sent */
  isLoading?: boolean;

  /** Whether input is disabled */
  disabled?: boolean;

  /** Placeholder text */
  placeholder?: string;

  /** Maximum character length */
  maxLength?: number;
}

/**
 * ChatInput component for sending messages to AI assistant
 */
export const ChatInput: React.FC<ChatInputProps> = ({
  value,
  onChange,
  onSubmit,
  isLoading = false,
  disabled = false,
  placeholder,
  maxLength = 2000,
}) => {
  const { t } = useTranslation('ai');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [isFocused, setIsFocused] = React.useState(false);

  // Auto-resize textarea
  useEffect(() => {
    const textarea = textareaRef.current;
    if (textarea) {
      // Reset height to recalculate
      textarea.style.height = 'auto';

      // Set to scroll height (capped at 200px max)
      const newHeight = Math.min(textarea.scrollHeight, 200);
      textarea.style.height = `${newHeight}px`;
    }
  }, [value]);

  // Focus textarea on mount
  useEffect(() => {
    textareaRef.current?.focus();
  }, []);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter to send, Shift+Enter for new line
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (value.trim() && !isLoading && !disabled) {
        onSubmit();
      }
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (value.trim() && !isLoading && !disabled) {
      onSubmit();
    }
  };

  const isNearLimit = value.length > maxLength * 0.9;
  const isOverLimit = value.length > maxLength;

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      {/* Input container */}
      <div
        className={`relative flex items-end gap-2 rounded-2xl border-2 transition-all ${
          isFocused ? 'border-primary bg-background' : 'border-border bg-surface'
        } ${disabled || isLoading ? 'opacity-60' : ''}`}
      >
        {/* Textarea */}
        <textarea
          ref={textareaRef}
          value={value}
          onChange={e => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setIsFocused(false)}
          disabled={disabled || isLoading}
          placeholder={placeholder ?? t('inputPlaceholder')}
          maxLength={maxLength}
          rows={1}
          className="flex-1 resize-none bg-transparent px-4 py-3 text-text-primary placeholder:text-text-muted focus:outline-none min-h-[48px] max-h-[200px]"
          style={{ scrollbarWidth: 'thin' }}
        />

        {/* Send button */}
        <button
          type="submit"
          disabled={!value.trim() || isLoading || disabled || isOverLimit}
          className="flex-shrink-0 mr-2 mb-2 p-2 rounded-lg bg-blue-600 text-white hover:bg-blue-700 disabled:bg-surface-elevated disabled:cursor-not-allowed transition-colors"
          title={t(isOverLimit ? 'input.tooLong' : 'input.send')}
          aria-label={t('input.send')}
        >
          {isLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Send className="w-5 h-5" />}
        </button>
      </div>

      {/* Footer with character count and help text */}
      <div className="flex items-center justify-between text-xs text-text-secondary px-1">
        <span>
          <Trans
            t={t}
            i18nKey="input.help"
            components={{ key: <kbd className="px-1.5 py-0.5 bg-surface-elevated rounded" /> }}
          />
        </span>

        {/* Character count */}
        {isNearLimit && (
          <span className={`font-medium ${isOverLimit ? 'text-red-600' : 'text-amber-600'}`}>
            {value.length}/{maxLength}
          </span>
        )}
      </div>
    </form>
  );
};

export default ChatInput;

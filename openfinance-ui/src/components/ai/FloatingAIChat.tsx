/**
 * Floating AI Chat Widget
 *
 * A floating action button (FAB) that opens an overlay chat panel.
 * Injected globally in AppLayout so it appears on every authenticated page.
 *
 * Features:
 * - Expandable chat panel anchored to bottom-right
 * - Reuses existing ChatMessage, ChatInput, and SuggestedPrompts components
 * - Conversation state persists while navigating between pages
 * - Health-check aware: hides FAB when AI service is unavailable
 * - Responsive: adapts to mobile viewports
 *
 * @since Sprint 11+ — Floating AI Chat Widget
 */
import React, { useRef, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import axios from 'axios';
import { MessageCircle, X, Minimize2, Trash2, AlertCircle, Sparkles } from 'lucide-react';
import { useAIChat, useSendMessage } from '@/hooks/useAIChat';
import ChatMessage from '@/components/ai/ChatMessage';
import ChatInput from '@/components/ai/ChatInput';
import { cn } from '@/lib/utils';
import type { Message } from '@/types/ai';

/**
 * FloatingAIChat renders a circular FAB in the bottom-right corner.
 * Clicking it opens a chat panel overlay.
 */
export const FloatingAIChat: React.FC = () => {
  const { t } = useTranslation('ai');

  const [isOpen, setIsOpen] = React.useState(false);
  const [inputValue, setInputValue] = React.useState('');
  const [messages, setMessages] = React.useState<Message[]>([]);
  const [conversationId, setConversationId] = React.useState<string | null>(null);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  const { isOllamaAvailable, isCheckingHealth } = useAIChat(conversationId);

  const sendMessage = useSendMessage();

  // Scroll to bottom when new messages arrive
  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages, isOpen]);

  // Close on Escape key
  useEffect(() => {
    const handleEsc = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        setIsOpen(false);
      }
    };
    document.addEventListener('keydown', handleEsc);
    return () => document.removeEventListener('keydown', handleEsc);
  }, [isOpen]);

  const handleSendMessage = useCallback(async () => {
    if (!inputValue.trim() || sendMessage.isPending) return;

    const userMessage: Message = {
      role: 'user',
      content: inputValue.trim(),
      timestamp: new Date().toISOString(),
    };

    setMessages(prev => [...prev, userMessage]);
    setInputValue('');

    try {
      const response = await sendMessage.mutateAsync({
        question: userMessage.content,
        conversation_id: conversationId,
        include_full_context: true,
      });

      const aiMessage: Message = {
        role: 'assistant',
        content: response.response,
        timestamp: response.timestamp,
      };

      setMessages(prev => [...prev, aiMessage]);
      setConversationId(response.conversation_id);
    } catch (error) {
      console.error('Failed to send message:', error);

      let errorContent: string;
      if (axios.isAxiosError(error) && (error.code === 'ERR_NETWORK' || !error.response)) {
        // Network failure or Ollama unreachable — no HTTP response received
        errorContent = t(
          'networkError',
          'The AI service is temporarily unreachable. Please check your connection and try again.'
        );
      } else if (axios.isAxiosError(error) && error.response && error.response.status >= 500) {
        // Backend/server-side error
        errorContent = t(
          'serverError',
          'The AI service encountered an internal error. Please try again in a moment.'
        );
      } else {
        // Generic processing error (e.g. 4xx or unknown)
        errorContent = t(
          'processingError',
          'Sorry, I encountered an error processing your request. Please try again.'
        );
      }

      const errorMessage: Message = {
        role: 'assistant',
        content: errorContent,
        timestamp: new Date().toISOString(),
      };

      setMessages(prev => [...prev, errorMessage]);
    }
  }, [inputValue, sendMessage, conversationId, t]);

  const handleSelectPrompt = useCallback((question: string) => {
    setInputValue(question);
  }, []);

  const handleNewConversation = useCallback(() => {
    setMessages([]);
    setConversationId(null);
    setInputValue('');
  }, []);

  const toggleOpen = useCallback(() => {
    setIsOpen(prev => !prev);
  }, []);

  // Don't render if AI service is unavailable and we've checked
  if (!isCheckingHealth && !isOllamaAvailable) {
    return null;
  }

  return (
    <>
      {/* Chat Panel */}
      {isOpen && (
        <div
          ref={panelRef}
          className={cn(
            'fixed z-[60] flex flex-col',
            'bg-background border border-border rounded-2xl shadow-2xl',
            'transition-all duration-200 ease-out',
            // Desktop: anchored bottom-right, fixed size
            'bottom-24 right-6 w-[420px] h-[600px]',
            // Mobile: nearly full-screen
            'max-sm:inset-x-3 max-sm:top-16 max-sm:bottom-20 max-sm:w-auto max-sm:h-auto max-sm:right-3'
          )}
          role="dialog"
          aria-label={t('title', 'AI Financial Assistant')}
        >
          {/* Header */}
          <div className="flex-shrink-0 flex items-center justify-between px-4 py-3 border-b border-border rounded-t-2xl bg-surface shadow-slot">
            <div className="flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-primary" />
              <span className="text-sm font-semibold text-text-primary">
                {t('title', 'AI Financial Assistant')}
              </span>
            </div>

            <div className="flex items-center gap-1">
              {messages.length > 0 && (
                <button
                  onClick={handleNewConversation}
                  className="p-1.5 rounded-lg hover:bg-surface-elevated transition-colors"
                  title={t('newConversation', 'New conversation')}
                  aria-label={t('newConversation', 'New conversation')}
                >
                  <Trash2 className="w-4 h-4 text-text-secondary" />
                </button>
              )}
              <button
                onClick={toggleOpen}
                className="p-1.5 rounded-lg hover:bg-surface-elevated transition-colors"
                title={t('minimize', 'Minimize')}
                aria-label={t('minimize', 'Minimize chat')}
              >
                <Minimize2 className="w-4 h-4 text-white" />
              </button>
            </div>
          </div>

          {/* Messages Area */}
          <div className="flex-1 overflow-y-auto px-4 py-4 space-y-4">
            {messages.length === 0 ? (
              /* Empty state — welcome + prompts */
              <div className="flex flex-col items-center justify-center h-full text-center px-2">
                <div className="w-14 h-14 mb-4 rounded-full bg-surface border border-border-strong shadow-slot flex items-center justify-center">
                  <Sparkles className="w-7 h-7 text-primary" />
                </div>
                <h3 className="plate-label  mb-1">{t('welcome.title', 'How can I help?')}</h3>
                <p className="text-xs text-text-secondary mb-4 max-w-xs">
                  {t('welcome.description', 'Ask me anything about your finances.')}
                </p>

                {/* Compact suggested prompts for widget */}
                <div className="w-full space-y-2">
                  {[
                    {
                      label: t('prompt.spending', 'Analyze my spending'),
                      question: t('prompts.analyzeSpending.question'),
                    },
                    {
                      label: t('prompt.budget', 'Budget advice'),
                      question: t('prompts.budgetRecommendations.question'),
                    },
                    {
                      label: t('prompt.summary', 'Financial summary'),
                      question: t('prompts.financialSummary.question'),
                    },
                    {
                      label: t('prompt.savings', 'Savings tips'),
                      question: t('prompts.moneySavingTips.question'),
                    },
                  ].map((p, i) => (
                    <button
                      key={i}
                      onClick={() => handleSelectPrompt(p.question)}
                      disabled={sendMessage.isPending}
                      className="w-full text-left px-3 py-2 text-sm bg-surface border border-border rounded-lg hover:border-blue-500 hover:bg-surface-elevated transition-all disabled:opacity-50"
                    >
                      {p.label}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              /* Messages */
              <>
                {messages.map((message, index) => (
                  <ChatMessage key={index} message={message} isStreaming={false} />
                ))}
                <div ref={messagesEndRef} />
              </>
            )}
          </div>

          {/* Input Area */}
          <div className="flex-shrink-0 px-4 py-3 border-t border-border rounded-b-2xl">
            <ChatInput
              value={inputValue}
              onChange={setInputValue}
              onSubmit={handleSendMessage}
              isLoading={sendMessage.isPending}
              disabled={!isOllamaAvailable}
              placeholder={t('inputPlaceholder', 'Ask about your finances...')}
            />

            {sendMessage.error && (
              <div className="mt-1.5 flex items-center gap-1.5 text-xs text-red-600">
                <AlertCircle className="w-3 h-3" />
                <span>{t('sendError', 'Failed to send message')}</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Floating Action Button */}
      <button
        onClick={toggleOpen}
        className={cn(
          'fixed z-[60] bottom-6 right-6',
          'w-14 h-14 rounded-full shadow-lg',
          'flex items-center justify-center',
          'transition-all duration-200',
          'focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2',
          isOpen
            ? 'bg-surface-elevated text-text-primary hover:bg-surface border border-border'
            : 'bg-gradient-to-b from-brass-bright to-primary text-primary-foreground shadow-plate-lift hover:brightness-[1.07] hover:scale-105'
        )}
        aria-label={isOpen ? t('closeChat', 'Close chat') : t('openChat', 'Open AI Assistant')}
        aria-expanded={isOpen}
      >
        {isOpen ? <X className="w-6 h-6" /> : <MessageCircle className="w-6 h-6" />}
      </button>
    </>
  );
};

export default FloatingAIChat;

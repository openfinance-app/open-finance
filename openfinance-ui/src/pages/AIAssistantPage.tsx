/**
 * AI Assistant Page
 * Task 11.3.1: Create AIAssistantPage component
 *
 * Main chat interface for interacting with the AI financial advisor
 *
 * @since Sprint 11 - AI Assistant Integration
 */
import React, { useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Bot, AlertCircle, RefreshCw, Sparkles } from 'lucide-react';
import { useAIChat, useSendMessage } from '@/hooks/useAIChat';
import { useDocumentTitle } from '@/hooks/useDocumentTitle';
import ChatMessage from '@/components/ai/ChatMessage';
import ChatInput from '@/components/ai/ChatInput';
import SuggestedPrompts from '@/components/ai/SuggestedPrompts';
import { FullPageSpinner } from '@/components/LoadingComponents';
import type { Message } from '@/types/ai';

export const AIAssistantPage: React.FC = () => {
  const { t } = useTranslation('ai');

  useDocumentTitle(t('title'));

  const [inputValue, setInputValue] = React.useState('');
  const [messages, setMessages] = React.useState<Message[]>([]);
  const [conversationId, setConversationId] = React.useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const { isOllamaAvailable, isCheckingHealth, healthError, refetchHealth } =
    useAIChat(conversationId);

  const sendMessage = useSendMessage();

  // Scroll to bottom when new messages arrive
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async () => {
    if (!inputValue.trim() || sendMessage.isPending) return;

    const userMessage: Message = {
      role: 'user',
      content: inputValue.trim(),
      timestamp: new Date().toISOString(),
    };

    // Add user message immediately
    setMessages(prev => [...prev, userMessage]);
    setInputValue('');

    try {
      // Send to backend
      const response = await sendMessage.mutateAsync({
        question: userMessage.content,
        conversation_id: conversationId,
        include_full_context: true,
      });

      // Add AI response
      const aiMessage: Message = {
        role: 'assistant',
        content: response.response,
        timestamp: response.timestamp,
      };

      setMessages(prev => [...prev, aiMessage]);
      setConversationId(response.conversation_id);
    } catch (error) {
      console.error('Failed to send message:', error);

      // Add error message
      const errorMessage: Message = {
        role: 'assistant',
        content: t('processingError'),
        timestamp: new Date().toISOString(),
      };

      setMessages(prev => [...prev, errorMessage]);
    }
  };

  const handleSelectPrompt = (question: string) => {
    setInputValue(question);
  };

  const handleNewConversation = () => {
    setMessages([]);
    setConversationId(null);
    setInputValue('');
  };

  // Loading state
  if (isCheckingHealth) {
    return <FullPageSpinner />;
  }

  // Ollama unavailable error
  if (!isOllamaAvailable || healthError) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background p-4">
        <div className="max-w-md w-full bg-surface rounded-lg shadow-lg p-8 text-center">
          <AlertCircle className="w-16 h-16 text-red-600 mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-text-primary mb-2">{t('unavailable.title')}</h2>
          <p className="text-text-secondary mb-6">{t('unavailable.description')}</p>
          <button
            onClick={() => refetchHealth()}
            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <RefreshCw className="w-4 h-4" />
            {t('unavailable.retry')}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-screen bg-background">
      {/* Header */}
      <div className="flex-shrink-0 bg-surface border-b border-border px-4 py-4 sm:px-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-gradient-to-br from-blue-600 to-emerald-600 flex items-center justify-center">
              <Bot className="w-6 h-6 text-white" />
            </div>
            <div>
              <h1 className="text-xl font-bold text-text-primary">{t('title')}</h1>
              <p className="text-sm text-text-secondary">{t('subtitle')}</p>
            </div>
          </div>

          {messages.length > 0 && (
            <button
              onClick={handleNewConversation}
              className="px-4 py-2 text-sm font-medium text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
            >
              {t('newConversation')}
            </button>
          )}
        </div>
      </div>

      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto px-4 py-6 sm:px-6">
        <div className="max-w-4xl mx-auto space-y-6">
          {messages.length === 0 ? (
            // Empty state
            <div className="text-center py-12">
              <div className="w-20 h-20 mx-auto mb-6 rounded-full bg-gradient-to-br from-blue-600 to-emerald-600 flex items-center justify-center">
                <Sparkles className="w-10 h-10 text-white" />
              </div>
              <h2 className="text-2xl font-bold text-text-primary mb-2">{t('welcome.title')}</h2>
              <p className="text-text-secondary mb-8 max-w-2xl mx-auto">
                {t('welcome.description')}
              </p>

              {/* Suggested Prompts */}
              <SuggestedPrompts
                onSelectPrompt={handleSelectPrompt}
                disabled={sendMessage.isPending}
              />
            </div>
          ) : (
            // Messages
            <>
              {messages.map((message, index) => (
                <ChatMessage key={index} message={message} isStreaming={false} />
              ))}

              {/* Scroll anchor */}
              <div ref={messagesEndRef} />
            </>
          )}
        </div>
      </div>

      {/* Input Area */}
      <div className="flex-shrink-0 bg-surface border-t border-border px-4 py-4 sm:px-6">
        <div className="max-w-4xl mx-auto">
          <ChatInput
            value={inputValue}
            onChange={setInputValue}
            onSubmit={handleSendMessage}
            isLoading={sendMessage.isPending}
            disabled={!isOllamaAvailable}
            placeholder={t('inputPlaceholder')}
          />

          {/* Error message */}
          {sendMessage.error && (
            <div className="mt-2 flex items-center gap-2 text-sm text-red-600">
              <AlertCircle className="w-4 h-4" />
              <span>{t('sendError')}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default AIAssistantPage;

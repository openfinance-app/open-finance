/**
 * Chat Message Component
 * Task 11.3.2: Create ChatMessage component
 * 
 * Displays individual chat messages with:
 * - Different styling for user vs AI messages
 * - Markdown rendering for AI responses
 * - Timestamps
 * - Copy to clipboard functionality
 * 
 * @since Sprint 11 - AI Assistant Integration
 */
import React from 'react';
import { COPY_FEEDBACK_RESET_MS } from '@/constants/timing';
import ReactMarkdown from 'react-markdown';
import { Bot, User, Copy, Check } from 'lucide-react';
import type { Message } from '@/types/ai';

interface ChatMessageProps {
  message: Message;
  isStreaming?: boolean;
}

/**
 * ChatMessage displays a single message in the conversation
 */
export const ChatMessage: React.FC<ChatMessageProps> = ({ message, isStreaming = false }) => {
  const [copied, setCopied] = React.useState(false);
  const isUser = message.role === 'user';

  const handleCopy = async () => {
    await navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), COPY_FEEDBACK_RESET_MS);
  };

  const formattedTime = new Date(message.timestamp).toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <div className={`flex gap-3 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      {/* Avatar */}
      <div
        className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${isUser ? 'bg-blue-600' : 'bg-emerald-600'
          }`}
      >
        {isUser ? (
          <User className="w-5 h-5 text-white" />
        ) : (
          <Bot className="w-5 h-5 text-white" />
        )}
      </div>

      {/* Message Content */}
      <div className={`flex-1 max-w-3xl ${isUser ? 'flex justify-end' : ''}`}>
        <div
          className={`rounded-2xl px-4 py-3 ${isUser
              ? 'bg-blue-600 text-white'
              : 'bg-surface-elevated text-text-primary'
            }`}
        >
          {/* Message text */}
          {isUser ? (
            <p className="whitespace-pre-wrap break-words">{message.content}</p>
          ) : (
            <div className="prose prose-sm dark:prose-invert max-w-none">
              <ReactMarkdown
                components={{
                  // Style code blocks
                  code: ({ className, children, ...props }: any) => {
                    const inline = !className;
                    return inline ? (
                      <code
                        className="bg-surface-elevated rounded px-1.5 py-0.5 text-sm font-mono"
                        {...props}
                      >
                        {children}
                      </code>
                    ) : (
                      <code
                        className="block bg-background text-text-secondary rounded-lg p-4 overflow-x-auto text-sm font-mono"
                        {...props}
                      >
                        {children}
                      </code>
                    );
                  },
                  // Style links
                  a: ({ node, children, ...props }) => (
                    <a
                      className="text-blue-600 hover:underline"
                      target="_blank"
                      rel="noopener noreferrer"
                      {...props}
                    >
                      {children}
                    </a>
                  ),
                  // Style lists
                  ul: ({ node, children, ...props }) => (
                    <ul className="list-disc pl-5 space-y-1" {...props}>
                      {children}
                    </ul>
                  ),
                  ol: ({ node, children, ...props }) => (
                    <ol className="list-decimal pl-5 space-y-1" {...props}>
                      {children}
                    </ol>
                  ),
                  // Style headings
                  h1: ({ node, children, ...props }) => (
                    <h1 className="text-2xl font-bold mt-4 mb-2" {...props}>
                      {children}
                    </h1>
                  ),
                  h2: ({ node, children, ...props }) => (
                    <h2 className="text-xl font-bold mt-3 mb-2" {...props}>
                      {children}
                    </h2>
                  ),
                  h3: ({ node, children, ...props }) => (
                    <h3 className="text-lg font-semibold mt-2 mb-1" {...props}>
                      {children}
                    </h3>
                  ),
                  // Style paragraphs
                  p: ({ node, children, ...props }) => (
                    <p className="mb-2 last:mb-0" {...props}>
                      {children}
                    </p>
                  ),
                }}
              >
                {message.content}
              </ReactMarkdown>

              {/* Streaming indicator */}
              {isStreaming && (
                <span className="inline-flex items-center gap-1 ml-1 animate-pulse">
                  <span className="w-1.5 h-1.5 bg-emerald-600 rounded-full"></span>
                  <span className="w-1.5 h-1.5 bg-emerald-600 rounded-full animation-delay-150"></span>
                  <span className="w-1.5 h-1.5 bg-emerald-600 rounded-full animation-delay-300"></span>
                </span>
              )}
            </div>
          )}

          {/* Timestamp and actions */}
          <div
            className={`flex items-center gap-2 mt-2 text-xs ${isUser ? 'text-blue-100' : 'text-text-secondary'
              }`}
          >
            <span>{formattedTime}</span>

            {!isUser && !isStreaming && (
              <>
                <span className="text-text-muted">•</span>
                <button
                  onClick={handleCopy}
                  className="hover:text-text-primary transition-colors flex items-center gap-1"
                  title="Copy to clipboard"
                >
                  {copied ? (
                    <>
                      <Check className="w-3 h-3" />
                      <span>Copied</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3 h-3" />
                      <span>Copy</span>
                    </>
                  )}
                </button>
              </>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ChatMessage;

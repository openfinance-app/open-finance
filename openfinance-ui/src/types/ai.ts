/**
 * AI Assistant Type Definitions
 *
 * Types for AI chat interactions, conversation history, and message handling.
 * Matches backend DTOs from org.openfinance.dto.AIDto
 *
 * @since Sprint 11 - AI Assistant Integration
 */

/**
 * Message in a conversation
 */
export interface Message {
  /** Message role: 'user' for user messages, 'assistant' for AI responses */
  role: 'user' | 'assistant';

  /** Message content text */
  content: string;

  /** Message timestamp (ISO 8601 format) */
  timestamp: string;
}

/**
 * Request to send a chat message
 */
export interface ChatRequest {
  /** User's question or message */
  question: string;

  /** Optional conversation ID to continue existing conversation */
  conversation_id?: string | null;

  /** Whether to include full financial context (default: true) */
  include_full_context?: boolean;
}

/**
 * Response from AI chat
 */
export interface ChatResponse {
  /** AI assistant's response text */
  response: string;

  /** Conversation ID (for follow-up messages) */
  conversation_id: string;

  /** Response timestamp (ISO 8601 format) */
  timestamp: string;

  /** Token count (approximate) for context and response */
  token_count?: number;

  /** Optional error message if processing failed */
  error?: string;
}

/**
 * Summary of a conversation (for list view)
 */
export interface ConversationSummary {
  /** Unique conversation ID */
  id: string;

  /** Optional conversation title (generated from first question) */
  title?: string | null;

  /** Number of messages in conversation */
  message_count: number;

  /** Preview of the last message */
  last_message_preview?: string | null;

  /** When conversation was created (ISO 8601 format) */
  created_at: string;

  /** When conversation was last updated (ISO 8601 format) */
  updated_at: string;
}

/**
 * Full conversation details with all messages
 */
export interface ConversationDetail {
  /** Unique conversation ID */
  id: string;

  /** User ID who owns this conversation */
  user_id: number;

  /** Optional conversation title */
  title?: string | null;

  /** All messages in chronological order */
  messages: Message[];

  /** When conversation was created (ISO 8601 format) */
  created_at: string;

  /** When conversation was last updated (ISO 8601 format) */
  updated_at: string;
}

/**
 * Health check response for Ollama service
 */
export interface OllamaHealthResponse {
  /** Whether Ollama is available and responding */
  available: boolean;

  /** Optional error message if unavailable */
  message?: string;
}

/**
 * Suggested prompt for quick actions
 */
export interface SuggestedPrompt {
  /** Display text for the button */
  label: string;

  /** The actual question to send to AI */
  question: string;

  /** Icon name (from lucide-react) */
  icon?: string;

  /** Category for grouping prompts */
  category?: 'spending' | 'budgeting' | 'investing' | 'debt' | 'general';
}

/**
 * Context settings for AI queries
 */
export interface AIContextSettings {
  /** Include account data in context */
  includeAccounts: boolean;

  /** Include transaction history in context */
  includeTransactions: boolean;

  /** Include asset portfolio in context */
  includeAssets: boolean;

  /** Include liability data in context */
  includeLiabilities: boolean;

  /** Include budget information in context */
  includeBudgets: boolean;
}

/**
 * AI Chat Hooks
 * Task 11.3.7: Create useAIChat hook
 * 
 * Provides React Query hooks for AI assistant interactions:
 * - Send chat messages
 * - Stream responses (SSE)
 * - Manage conversations
 * - Check Ollama health
 * 
 * @since Sprint 11 - AI Assistant Integration
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/services/apiClient';
import type {
  ChatRequest,
  ChatResponse,
  ConversationSummary,
  ConversationDetail,
  OllamaHealthResponse,
} from '@/types/ai';

/**
 * Check if Ollama service is available
 */
export function useOllamaHealth() {
  return useQuery<OllamaHealthResponse>({
    queryKey: ['ai', 'health'],
    queryFn: async () => {
      const response = await apiClient.get<OllamaHealthResponse>('/ai/health');
      return response.data;
    },
    refetchInterval: 30000, // Check every 30 seconds
    retry: 1, // Only retry once if service is down
  });
}

/**
 * Send a chat message to AI assistant
 */
export function useSendMessage() {
  const queryClient = useQueryClient();

  return useMutation<ChatResponse, Error, ChatRequest>({
    mutationFn: async (request: ChatRequest) => {
      const encryptionKey = sessionStorage.getItem('encryption_session');
      if (!encryptionKey) {
        throw new Error('Encryption key not found');
      }

      const response = await apiClient.post<ChatResponse>('/ai/chat', request, {
        headers: {
          'X-Encryption-Session': encryptionKey,
        },
      });
      return response.data;
    },
    onSuccess: (data) => {
      // Invalidate conversations list to show new/updated conversation
      queryClient.invalidateQueries({ queryKey: ['ai', 'conversations'] });

      // Update specific conversation cache if continuing existing conversation
      if (data.conversation_id) {
        queryClient.invalidateQueries({
          queryKey: ['ai', 'conversations', data.conversation_id]
        });
      }
    },
  });
}

/**
 * Stream AI response using Server-Sent Events (SSE)
 * 
 * Note: This is a manual implementation since React Query doesn't directly
 * support SSE streaming. Use this for real-time response display.
 * 
 * @param onChunk Callback for each response chunk
 * @param onComplete Callback when stream completes
 * @param onError Callback for errors
 * @returns Object with sendStreamingMessage function and cancel function
 */
export function useStreamingChat(
  onChunk: (chunk: string) => void,
  onComplete: () => void,
  onError: (error: Error) => void
) {
  const queryClient = useQueryClient();
  let eventSource: EventSource | null = null;

  const sendStreamingMessage = async (request: ChatRequest) => {
    const encryptionKey = sessionStorage.getItem('encryption_session');
    if (!encryptionKey) {
      onError(new Error('Encryption key not found'));
      return;
    }

    // Create query string for GET request (SSE doesn't support POST body easily)
    const params = new URLSearchParams({
      question: request.question,
      include_full_context: request.include_full_context !== false ? 'true' : 'false',
    });

    if (request.conversation_id) {
      params.append('conversation_id', request.conversation_id);
    }

    // const _token = sessionStorage.getItem('token');
    const baseURL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';
    const url = `${baseURL}/ai/chat/stream?${params.toString()}`;

    // Create EventSource for SSE
    eventSource = new EventSource(url, {
      withCredentials: false,
    });

    // Add headers via fetch polyfill approach (EventSource doesn't support custom headers directly)
    // For now, we'll use the standard chat endpoint and simulate streaming on the client
    // TODO: Implement proper SSE with authentication headers

    eventSource.onmessage = (event) => {
      onChunk(event.data);
    };

    eventSource.onerror = (error) => {
      console.error('SSE Error:', error);
      eventSource?.close();
      onError(new Error('Streaming connection failed'));
    };

    eventSource.addEventListener('complete', () => {
      eventSource?.close();
      onComplete();

      // Invalidate conversations cache
      queryClient.invalidateQueries({ queryKey: ['ai', 'conversations'] });
    });
  };

  const cancelStream = () => {
    if (eventSource) {
      eventSource.close();
      eventSource = null;
    }
  };

  return {
    sendStreamingMessage,
    cancelStream,
  };
}

/**
 * Fetch all conversations for the current user
 */
export function useConversations() {
  return useQuery<ConversationSummary[]>({
    queryKey: ['ai', 'conversations'],
    queryFn: async () => {
      const response = await apiClient.get<ConversationSummary[]>('/ai/conversations');
      return response.data;
    },
  });
}

/**
 * Fetch a specific conversation with all messages
 */
export function useConversation(conversationId: string | null) {
  return useQuery<ConversationDetail>({
    queryKey: ['ai', 'conversations', conversationId],
    queryFn: async () => {
      if (!conversationId) {
        throw new Error('Conversation ID is required');
      }

      const response = await apiClient.get<ConversationDetail>(
        `/ai/conversations/${conversationId}`
      );
      return response.data;
    },
    enabled: !!conversationId, // Only fetch if conversationId exists
  });
}

/**
 * Delete a conversation
 */
export function useDeleteConversation() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, string>({
    mutationFn: async (conversationId: string) => {
      await apiClient.delete(`/ai/conversations/${conversationId}`);
    },
    onSuccess: () => {
      // Invalidate conversations list
      queryClient.invalidateQueries({ queryKey: ['ai', 'conversations'] });
    },
  });
}

/**
 * Combined hook for chat workflow
 * 
 * Provides state management for the entire chat flow:
 * - Send messages
 * - Track conversation
 * - Handle errors
 */
export function useAIChat(conversationId?: string | null) {
  const sendMessage = useSendMessage();
  const conversation = useConversation(conversationId || null);
  const health = useOllamaHealth();

  const askQuestion = async (question: string, includeContext = true) => {
    return sendMessage.mutateAsync({
      question,
      conversation_id: conversationId || null,
      include_full_context: includeContext,
    });
  };

  return {
    // Send message
    askQuestion,
    isSending: sendMessage.isPending,
    sendError: sendMessage.error,

    // Conversation data
    conversation: conversation.data,
    isLoadingConversation: conversation.isLoading,
    conversationError: conversation.error,

    // Health status
    isOllamaAvailable: health.data?.available ?? false,
    isCheckingHealth: health.isLoading,
    healthError: health.error,

    // Refetch functions
    refetchConversation: conversation.refetch,
    refetchHealth: health.refetch,
  };
}

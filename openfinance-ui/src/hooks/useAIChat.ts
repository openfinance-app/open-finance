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
import { resolveEncryptionEnabled, useSecurityConfig } from '@/hooks/useSecurityConfig';
import apiClient from '@/services/apiClient';
import type {
  ChatRequest,
  ChatResponse,
  ConversationSummary,
  ConversationDetail,
  OllamaHealthResponse,
} from '@/types/ai';
import { buildEncryptionHeaders } from '@/utils/encryption';

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
  const securityConfig = useSecurityConfig();
  const encryptionEnabled = resolveEncryptionEnabled(securityConfig.data, securityConfig.isError);

  return useMutation<ChatResponse, Error, ChatRequest>({
    mutationFn: async (request: ChatRequest) => {
      const response = await apiClient.post<ChatResponse>('/ai/chat', request, {
        headers: buildEncryptionHeaders(encryptionEnabled),
      });
      return response.data;
    },
    onSuccess: data => {
      // Invalidate conversations list to show new/updated conversation
      queryClient.invalidateQueries({ queryKey: ['ai', 'conversations'] });

      // Update specific conversation cache if continuing existing conversation
      if (data.conversation_id) {
        queryClient.invalidateQueries({
          queryKey: ['ai', 'conversations', data.conversation_id],
        });
      }
    },
  });
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

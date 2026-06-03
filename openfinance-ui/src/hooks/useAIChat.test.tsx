import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import React from 'react';
import {
  useOllamaHealth,
  useSendMessage,
  useConversations,
  useConversation,
  useDeleteConversation,
  useStreamingChat,
  useAIChat,
} from './useAIChat';
import apiClient from '@/services/apiClient';

vi.mock('@/services/apiClient');
const mockedApiClient = apiClient as any;

const mockSessionStorage = {
  getItem: vi.fn(),
  setItem: vi.fn(),
  removeItem: vi.fn(),
  clear: vi.fn(),
  key: vi.fn(),
  length: 0,
};
Object.defineProperty(window, 'sessionStorage', { value: mockSessionStorage });

describe('useAIChat hooks', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    vi.clearAllMocks();
    mockSessionStorage.getItem.mockReturnValue('test-encryption-key');
  });

  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  // ── useOllamaHealth ─────────────────────────────────────────────────
  describe('useOllamaHealth', () => {
    it('should check ollama health status', async () => {
      const mockHealth = { available: true, model: 'llama3', version: '0.1.0' };
      mockedApiClient.get.mockResolvedValue({ data: mockHealth });

      const { result } = renderHook(() => useOllamaHealth(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockHealth);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/ai/health');
    });

    it('should handle ollama unavailable', async () => {
      // retry: 1 in source means it retries once, so we need the mock to fail consistently
      mockedApiClient.get.mockRejectedValue(new Error('Connection refused'));

      const { result } = renderHook(() => useOllamaHealth(), { wrapper });

      // retry:1 overrides test default retry:false, so needs longer timeout
      await waitFor(() => expect(result.current.isError).toBe(true), { timeout: 5000 });
    });
  });

  // ── useSendMessage ─────────────────────────────────────────────────
  describe('useSendMessage', () => {
    it('should send a chat message', async () => {
      const mockResponse = {
        response: 'Here is your financial analysis...',
        conversation_id: 'conv-123',
        model: 'llama3',
      };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useSendMessage(), { wrapper });

      await act(async () => {
        result.current.mutate({
          question: 'Analyze my spending',
          conversation_id: null,
          include_full_context: true,
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockResponse);
      expect(mockedApiClient.post).toHaveBeenCalledWith(
        '/ai/chat',
        {
          question: 'Analyze my spending',
          conversation_id: null,
          include_full_context: true,
        },
        { headers: { 'X-Encryption-Session': 'test-encryption-key' } },
      );
    });

    it('should throw error when encryption key is missing', async () => {
      mockSessionStorage.getItem.mockReturnValue(null);

      const { result } = renderHook(() => useSendMessage(), { wrapper });

      await act(async () => {
        result.current.mutate({
          question: 'Test',
          conversation_id: null,
          include_full_context: true,
        });
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
      expect(result.current.error?.message).toContain('Encryption key not found');
    });

    it('should invalidate conversations query on success', async () => {
      const mockResponse = { response: 'Answer', conversation_id: 'conv-456', model: 'llama3' };
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useSendMessage(), { wrapper });

      await act(async () => {
        result.current.mutate({
          question: 'Test',
          conversation_id: 'conv-456',
          include_full_context: true,
        });
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['ai', 'conversations'] });
    });
  });

  // ── useConversations ─────────────────────────────────────────────────
  describe('useConversations', () => {
    it('should fetch all conversations', async () => {
      const mockConversations = [
        { id: 'conv-1', title: 'Spending Analysis', createdAt: '2024-01-01', messageCount: 5 },
        { id: 'conv-2', title: 'Budget Help', createdAt: '2024-01-02', messageCount: 3 },
      ];
      mockedApiClient.get.mockResolvedValue({ data: mockConversations });

      const { result } = renderHook(() => useConversations(), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockConversations);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/ai/conversations');
    });
  });

  // ── useConversation ─────────────────────────────────────────────────
  describe('useConversation', () => {
    it('should fetch a specific conversation with messages', async () => {
      const mockConversation = {
        id: 'conv-1',
        title: 'Spending Analysis',
        messages: [
          { role: 'user', content: 'Analyze my spending' },
          { role: 'assistant', content: 'Here is your analysis...' },
        ],
      };
      mockedApiClient.get.mockResolvedValue({ data: mockConversation });

      const { result } = renderHook(() => useConversation('conv-1'), { wrapper });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(result.current.data).toEqual(mockConversation);
      expect(mockedApiClient.get).toHaveBeenCalledWith('/ai/conversations/conv-1');
    });

    it('should be disabled when conversation id is null', () => {
      const { result } = renderHook(() => useConversation(null), { wrapper });
      expect(result.current.fetchStatus).toBe('idle');
    });
  });

  // ── useDeleteConversation ─────────────────────────────────────────────────
  describe('useDeleteConversation', () => {
    it('should delete a conversation', async () => {
      mockedApiClient.delete.mockResolvedValue({});

      const { result } = renderHook(() => useDeleteConversation(), { wrapper });

      await act(async () => {
        result.current.mutate('conv-1');
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(mockedApiClient.delete).toHaveBeenCalledWith('/ai/conversations/conv-1');
    });

    it('should invalidate conversations query on success', async () => {
      mockedApiClient.delete.mockResolvedValue({});
      const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useDeleteConversation(), { wrapper });

      await act(async () => {
        result.current.mutate('conv-1');
      });

      await waitFor(() => expect(result.current.isSuccess).toBe(true));
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['ai', 'conversations'] });
    });
  });

  // ── useAIChat (combined hook) ─────────────────────────────────────────────────
  describe('useAIChat', () => {
    it('should expose all chat operations', () => {
      mockedApiClient.get.mockResolvedValue({ data: { available: true } });

      const { result } = renderHook(() => useAIChat(), { wrapper });

      expect(result.current.askQuestion).toBeDefined();
      expect(result.current.isSending).toBe(false);
      expect(result.current.sendError).toBeNull();
      expect(result.current.isLoadingConversation).toBe(false);
      expect(result.current.refetchConversation).toBeDefined();
      expect(result.current.refetchHealth).toBeDefined();
    });

    it('should send question via askQuestion', async () => {
      const mockResponse = { response: 'Analysis result', conversation_id: 'conv-new', model: 'llama3' };
      // First call for health check, subsequent for chat
      mockedApiClient.get.mockResolvedValue({ data: { available: true } });
      mockedApiClient.post.mockResolvedValue({ data: mockResponse });

      const { result } = renderHook(() => useAIChat(), { wrapper });

      let response;
      await act(async () => {
        response = await result.current.askQuestion('What is my net worth?');
      });

      expect(response).toEqual(mockResponse);
    });

    it('should load conversation when id is provided', async () => {
      const mockConversation = {
        id: 'conv-existing',
        title: 'Previous Chat',
        messages: [],
      };
      mockedApiClient.get.mockImplementation((url: string) => {
        if (url.includes('/ai/health')) return Promise.resolve({ data: { available: true } });
        if (url.includes('/ai/conversations/conv-existing')) return Promise.resolve({ data: mockConversation });
        return Promise.resolve({ data: {} });
      });

      const { result } = renderHook(() => useAIChat('conv-existing'), { wrapper });

      await waitFor(() => expect(result.current.conversation).toEqual(mockConversation));
    });
  });

  // ── useStreamingChat ─────────────────────────────────────────────────
  describe('useStreamingChat', () => {
    let mockEventSourceInstance: any;

    beforeEach(() => {
      mockEventSourceInstance = {
        onmessage: null as any,
        onerror: null as any,
        addEventListener: vi.fn(),
        close: vi.fn(),
      };

      // Use a class so `new EventSource(...)` works as a constructor
      const instance = mockEventSourceInstance;
      (globalThis as any).EventSource = class MockEventSource {
        addEventListener = instance.addEventListener;
        close = instance.close;
        onmessage: any = null;
        onerror: any = null;
        constructor() {
          // Proxy property sets back to the shared instance
          // eslint-disable-next-line @typescript-eslint/no-this-alias
          const self = this;
          Object.defineProperty(instance, 'onmessage', {
            get: () => self.onmessage,
            set: (v: any) => { self.onmessage = v; },
            configurable: true,
          });
          Object.defineProperty(instance, 'onerror', {
            get: () => self.onerror,
            set: (v: any) => { self.onerror = v; },
            configurable: true,
          });
        }
      };
    });

    it('should create EventSource with correct URL', async () => {
      let capturedUrl = '';
      const instance = mockEventSourceInstance;
      (globalThis as any).EventSource = class MockEventSource {
        addEventListener = instance.addEventListener;
        close = instance.close;
        onmessage: any = null;
        onerror: any = null;
        constructor(url: string) {
          capturedUrl = url;
        }
      };

      const onChunk = vi.fn();
      const onComplete = vi.fn();
      const onError = vi.fn();

      const { result } = renderHook(
        () => useStreamingChat(onChunk, onComplete, onError),
        { wrapper }
      );

      await act(async () => {
        await result.current.sendStreamingMessage({
          question: 'Test question',
          conversation_id: 'conv-1',
          include_full_context: true,
        });
      });

      expect(capturedUrl).toContain('/ai/chat/stream?');
      expect(capturedUrl).toContain('question=Test+question');
    });

    it('should call onChunk when receiving SSE message', async () => {
      const onChunk = vi.fn();
      const onComplete = vi.fn();
      const onError = vi.fn();

      const { result } = renderHook(
        () => useStreamingChat(onChunk, onComplete, onError),
        { wrapper }
      );

      await act(async () => {
        await result.current.sendStreamingMessage({
          question: 'Test',
          conversation_id: null,
          include_full_context: true,
        });
      });

      act(() => {
        mockEventSourceInstance.onmessage({ data: 'chunk1' });
      });

      expect(onChunk).toHaveBeenCalledWith('chunk1');
    });

    it('should call onError and close when SSE errors', async () => {
      const onChunk = vi.fn();
      const onComplete = vi.fn();
      const onError = vi.fn();

      const { result } = renderHook(
        () => useStreamingChat(onChunk, onComplete, onError),
        { wrapper }
      );

      await act(async () => {
        await result.current.sendStreamingMessage({
          question: 'Test',
          conversation_id: null,
          include_full_context: true,
        });
      });

      act(() => {
        mockEventSourceInstance.onerror(new Event('error'));
      });

      expect(onError).toHaveBeenCalledWith(expect.any(Error));
      expect(mockEventSourceInstance.close).toHaveBeenCalled();
    });

    it('should error when encryption key is missing', () => {
      mockSessionStorage.getItem.mockReturnValue(null);
      const onChunk = vi.fn();
      const onComplete = vi.fn();
      const onError = vi.fn();

      const { result } = renderHook(
        () => useStreamingChat(onChunk, onComplete, onError),
        { wrapper }
      );

      act(() => {
        result.current.sendStreamingMessage({
          question: 'Test',
          conversation_id: null,
          include_full_context: true,
        });
      });

      expect(onError).toHaveBeenCalledWith(expect.objectContaining({
        message: 'Encryption key not found',
      }));
    });

    it('should close EventSource when cancelStream is called', async () => {
      const onChunk = vi.fn();
      const onComplete = vi.fn();
      const onError = vi.fn();

      const { result } = renderHook(
        () => useStreamingChat(onChunk, onComplete, onError),
        { wrapper }
      );

      await act(async () => {
        await result.current.sendStreamingMessage({
          question: 'Test',
          conversation_id: null,
          include_full_context: true,
        });
      });

      act(() => {
        result.current.cancelStream();
      });

      expect(mockEventSourceInstance.close).toHaveBeenCalled();
    });

    it('should handle complete event', async () => {
      const onChunk = vi.fn();
      const onComplete = vi.fn();
      const onError = vi.fn();

      const { result } = renderHook(
        () => useStreamingChat(onChunk, onComplete, onError),
        { wrapper }
      );

      await act(async () => {
        await result.current.sendStreamingMessage({
          question: 'Test',
          conversation_id: null,
          include_full_context: true,
        });
      });

      // Get the 'complete' event listener
      const completeHandler = mockEventSourceInstance.addEventListener.mock.calls.find(
        (call: any[]) => call[0] === 'complete'
      );
      expect(completeHandler).toBeDefined();

      act(() => {
        completeHandler[1]();
      });

      expect(onComplete).toHaveBeenCalled();
      expect(mockEventSourceInstance.close).toHaveBeenCalled();
    });
  });
});

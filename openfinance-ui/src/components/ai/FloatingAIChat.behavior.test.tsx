import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import { AxiosError, AxiosHeaders } from 'axios';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';
import { FloatingAIChat } from '@/components/ai/FloatingAIChat';
import type {
  ChatRequest,
  ChatResponse,
  ConversationDetail,
  ConversationSummary,
  Message,
} from '@/types/ai';
const state = vi.hoisted(() => ({
  send: vi.fn<(request: ChatRequest) => Promise<ChatResponse>>(),
  reset: vi.fn(),
  remove: vi.fn<(id: string) => Promise<void>>(),
  pending: false,
  deleting: false,
  loading: false,
  visible: true,
  listError: null as Error | null,
  conversationError: null as Error | null,
  sendError: null as Error | null,
  list: [] as ConversationSummary[],
  conversations: {} as Record<string, ConversationDetail>,
}));
vi.mock('@/context/AuthContext', async original => ({
  ...(await original<typeof import('@/context/AuthContext')>()),
  useAuthContext: () => ({ user: { id: 7 } }),
}));
vi.mock('@/context/VisibilityContext', async original => ({
  ...(await original<typeof import('@/context/VisibilityContext')>()),
  useVisibility: () => ({ isAmountsVisible: state.visible }),
}));
vi.mock('@/hooks/useAIChat', () => ({
  useAIChat: (id: string | null) => ({
    isOllamaAvailable: true,
    isCheckingHealth: false,
    isLoadingConversation: state.loading,
    conversationError: state.conversationError,
    conversation: id ? state.conversations[id] : undefined,
  }),
  useConversations: () => ({ data: state.list, error: state.listError }),
  useSendMessage: () => ({
    mutateAsync: state.send,
    reset: state.reset,
    isPending: state.pending,
    error: state.sendError,
  }),
  useDeleteConversation: () => ({ mutateAsync: state.remove, isPending: state.deleting }),
}));
vi.mock('@/components/ai/ChatMessage', () => ({
  default: ({ message }: { message: Message }) => <p>{message.content}</p>,
}));
const response: ChatResponse = {
  response: 'Verified answer',
  conversation_id: 'a',
  timestamp: '2024-01-01T00:00:00Z',
};
function open() {
  renderWithProviders(<FloatingAIChat />);
  fireEvent.click(screen.getByRole('button', { name: 'Open AI Assistant' }));
}
function send() {
  fireEvent.change(screen.getByRole('textbox'), { target: { value: '  My balance?  ' } });
  fireEvent.click(screen.getByRole('button', { name: 'Send message (Enter)' }));
}
function select(value: string) {
  fireEvent.change(screen.getByRole('combobox', { name: 'Conversation history' }), {
    target: { value },
  });
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  mockAuthentication();
  Element.prototype.scrollIntoView = vi.fn();
  state.pending = false;
  state.deleting = false;
  state.loading = false;
  state.visible = true;
  state.listError = null;
  state.conversationError = null;
  state.sendError = null;
  state.conversations = {};
  state.list = [
    {
      id: 'a',
      title: 'Budget discussion',
      message_count: 1,
      created_at: '2024-01-01',
      updated_at: '2024-01-01',
    },
    { id: 'b', title: null, message_count: 0, created_at: '2024-01-02', updated_at: '2024-01-02' },
  ];
  state.send.mockResolvedValue(response);
  state.remove.mockResolvedValue(undefined);
});
describe('Floating chat response and history boundaries', () => {
  it.each(['network', 'no-response', 'server', 'client', 'unknown'])(
    'shows the appropriate recoverable message for %s failures',
    kind => {
      const error =
        kind === 'unknown'
          ? new Error('Unexpected')
          : new AxiosError(
              'Request failed',
              kind === 'network' ? 'ERR_NETWORK' : undefined,
              undefined,
              undefined,
              kind === 'server' || kind === 'client'
                ? {
                    data: {},
                    status: kind === 'server' ? 503 : 422,
                    statusText: '',
                    headers: {},
                    config: { headers: new AxiosHeaders() },
                  }
                : undefined
            );
      state.send.mockRejectedValue(error);
      open();
      send();
      return waitFor(() =>
        expect(
          screen.getByText(
            kind === 'network' || kind === 'no-response'
              ? /temporarily unreachable/
              : kind === 'server'
                ? /internal error/
                : /error processing your request/
          )
        ).toBeInTheDocument()
      );
    }
  );
  it('sends trimmed questions and persists the returned conversation identity', async () => {
    open();
    send();
    await screen.findByText('Verified answer');
    expect(state.send).toHaveBeenCalledWith({
      question: 'My balance?',
      conversation_id: null,
      include_full_context: true,
    });
    expect(sessionStorage.getItem('ai.conversation.7')).toBe('a');
    expect(screen.getByRole('textbox')).toHaveValue('');
  });
  it.each(['success', 'error'])(
    'ignores a stale send %s after starting a different conversation',
    async outcome => {
      const pending = deferred<ChatResponse>();
      state.send.mockReturnValue(pending.promise);
      open();
      send();
      select('b');
      await act(async () => {
        if (outcome === 'success') pending.resolve(response);
        else pending.reject(new Error('Old request'));
      });
      expect(screen.queryByText('Verified answer')).not.toBeInTheDocument();
      expect(screen.queryByText(/error processing/)).not.toBeInTheDocument();
      expect(screen.getByRole('combobox', { name: 'Conversation history' })).toHaveValue('b');
      expect(sessionStorage.getItem('ai.conversation.7')).toBe('b');
    }
  );
  it.each(['loading', 'error', 'pending'])(
    'blocks sending while conversation state is %s',
    condition => {
      state.loading = condition === 'loading';
      state.conversationError = condition === 'error' ? new Error('Unavailable') : null;
      state.pending = condition === 'pending';
      open();
      expect(screen.getByRole('button', { name: 'Send message (Enter)' })).toBeDisabled();
      expect(state.send).not.toHaveBeenCalled();
      if (condition === 'loading')
        expect(screen.getByRole('status')).toHaveTextContent('Loading conversation');
      if (condition === 'error')
        expect(screen.getByRole('alert')).toHaveTextContent('Could not load');
    }
  );
  it('cannot submit whitespace as a new question', () => {
    open();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '   ' } });
    expect(screen.getByRole('button', { name: 'Send message (Enter)' })).toBeDisabled();
    expect(state.send).not.toHaveBeenCalled();
  });
  it('deleting the selected conversation clears its messages and saved selection', async () => {
    sessionStorage.setItem('ai.conversation.7', 'a');
    state.conversations.a = {
      id: 'a',
      user_id: 7,
      created_at: '2024-01-01',
      updated_at: '2024-01-01',
      messages: [{ role: 'assistant', content: 'Old answer', timestamp: '2024-01-01' }],
    };
    open();
    expect(screen.getByText('Old answer')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Delete conversation' }));
    await waitFor(() => expect(screen.queryByText('Old answer')).not.toBeInTheDocument());
    expect(state.remove).toHaveBeenCalledWith('a');
    expect(sessionStorage.getItem('ai.conversation.7')).toBeNull();
  });
  it('failed deletion keeps the conversation selected and a new selection clears the error', async () => {
    state.remove.mockRejectedValue(new Error('Conflict'));
    sessionStorage.setItem('ai.conversation.7', 'a');
    open();
    fireEvent.click(screen.getByRole('button', { name: 'Delete conversation' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load');
    expect(sessionStorage.getItem('ai.conversation.7')).toBe('a');
    select('b');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
  it.each(['success', 'error'])(
    'a stale deletion %s cannot clear or mark another conversation',
    async outcome => {
      const pending = deferred<void>();
      state.remove.mockReturnValue(pending.promise);
      sessionStorage.setItem('ai.conversation.7', 'a');
      open();
      fireEvent.click(screen.getByRole('button', { name: 'Delete conversation' }));
      select('b');
      await act(async () => {
        if (outcome === 'success') pending.resolve();
        else pending.reject(new Error('Stale'));
      });
      expect(screen.getByRole('combobox', { name: 'Conversation history' })).toHaveValue('b');
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    }
  );
  it('clearing history selection removes persisted identity and resets mutation state', () => {
    sessionStorage.setItem('ai.conversation.7', 'a');
    open();
    select('');
    expect(state.reset).toHaveBeenCalled();
    expect(sessionStorage.getItem('ai.conversation.7')).toBeNull();
    expect(screen.getByRole('button', { name: 'Delete conversation' })).toBeDisabled();
  });
  it('privacy masks saved conversation titles and hides the chat input', () => {
    state.visible = false;
    open();
    expect(screen.getByRole('option', { name: 'Conversation a' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Budget discussion' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });
  it('untitled history and history-query errors are visible without disabling a new conversation', () => {
    state.listError = new Error('Unavailable');
    open();
    expect(screen.getByRole('option', { name: 'Untitled conversation' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Could not load');
    expect(screen.getByRole('textbox')).not.toBeDisabled();
  });
  it('a send mutation error remains visible independently of the conversation history', () => {
    state.sendError = new Error('Unavailable');
    open();
    expect(screen.getByText('Failed to send message. Please try again.')).toBeInTheDocument();
  });
  it('Escape only closes the open panel and other keys leave it open', () => {
    open();
    fireEvent.keyDown(document, { key: 'Enter' });
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.getByRole('button', { name: 'Open AI Assistant' })).toHaveAttribute(
      'aria-expanded',
      'false'
    );
  });
});

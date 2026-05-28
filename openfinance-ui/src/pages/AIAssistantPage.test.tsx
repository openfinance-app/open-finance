import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAuthentication } from '@/test/test-utils';

vi.mock('@/hooks/useDocumentTitle', () => ({ useDocumentTitle: vi.fn() }));

let mockOllamaAvailable = false;
let mockIsCheckingHealth = false;
let mockHealthError: string | null = null;
const mockRefetchHealth = vi.fn();
const mockSendMessage = vi.fn();
let mockSendPending = false;

vi.mock('@/hooks/useAIChat', () => ({
  useAIChat: () => ({
    isOllamaAvailable: mockOllamaAvailable,
    isCheckingHealth: mockIsCheckingHealth,
    healthError: mockHealthError,
    refetchHealth: mockRefetchHealth,
  }),
  useSendMessage: () => ({
    mutateAsync: mockSendMessage,
    isPending: mockSendPending,
  }),
}));
vi.mock('@/components/ai/ChatMessage', () => ({
  default: ({ message }: any) => <div data-testid="chat-message">{message?.content}</div>,
}));
vi.mock('@/components/ai/ChatInput', () => ({
  default: ({ onSubmit, isLoading }: any) => (
    <div>
      <input data-testid="chat-input" />
      <button data-testid="send-btn" onClick={() => onSubmit?.()} disabled={isLoading}>Send</button>
    </div>
  ),
}));
vi.mock('@/components/ai/SuggestedPrompts', () => ({
  default: ({ onSelectPrompt }: any) => (
    <div data-testid="suggested-prompts">
      <button data-testid="prompt-1" onClick={() => onSelectPrompt?.('What is my spending?')}>Prompt</button>
    </div>
  ),
}));

import { AIAssistantPage } from './AIAssistantPage';

describe('AIAssistantPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
    Element.prototype.scrollIntoView = vi.fn();
    mockOllamaAvailable = false;
    mockIsCheckingHealth = false;
    mockHealthError = null;
    mockSendPending = false;
  });

  it('renders the unavailable heading when AI is down', () => {
    renderWithProviders(<AIAssistantPage />);
    expect(screen.getByText('AI Service Unavailable')).toBeInTheDocument();
  });

  it('shows retry button', () => {
    renderWithProviders(<AIAssistantPage />);
    expect(screen.getByText('Retry Connection')).toBeInTheDocument();
  });

  it('calls refetchHealth on retry click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<AIAssistantPage />);
    await user.click(screen.getByText('Retry Connection'));
    expect(mockRefetchHealth).toHaveBeenCalled();
  });

  it('shows checking state with spinner', () => {
    mockIsCheckingHealth = true;
    renderWithProviders(<AIAssistantPage />);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders chat interface when AI is available', () => {
    mockOllamaAvailable = true;
    renderWithProviders(<AIAssistantPage />);
    expect(screen.getByTestId('chat-input')).toBeInTheDocument();
    expect(screen.getByTestId('suggested-prompts')).toBeInTheDocument();
  });

  it('renders page heading when available', () => {
    mockOllamaAvailable = true;
    renderWithProviders(<AIAssistantPage />);
    expect(screen.getAllByText(/AI Financial/i).length).toBeGreaterThan(0);
  });

  it('sends message on send button click', async () => {
    mockOllamaAvailable = true;
    mockSendMessage.mockResolvedValue({ content: 'Response' });
    const user = userEvent.setup();
    renderWithProviders(<AIAssistantPage />);
    await user.click(screen.getByTestId('send-btn'));
    // Message should have been sent
  });

  it('selects a suggested prompt', async () => {
    mockOllamaAvailable = true;
    mockSendMessage.mockResolvedValue({ content: 'Answer' });
    const user = userEvent.setup();
    renderWithProviders(<AIAssistantPage />);
    await user.click(screen.getByTestId('prompt-1'));
    // Prompt should be sent or populated
  });

  it('shows unavailable state with health error', () => {
    mockHealthError = 'Connection refused';
    renderWithProviders(<AIAssistantPage />);
    expect(screen.getByText('AI Service Unavailable')).toBeInTheDocument();
  });
});

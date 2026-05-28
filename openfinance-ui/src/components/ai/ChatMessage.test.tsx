import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ChatMessage } from './ChatMessage';
import type { Message } from '@/types/ai';

describe('ChatMessage', () => {
  const userMessage: Message = {
    role: 'user',
    content: 'What are my expenses?',
    timestamp: '2024-01-15T10:30:00Z',
  };

  const assistantMessage: Message = {
    role: 'assistant',
    content: 'Here is your expense breakdown.',
    timestamp: '2024-01-15T10:30:05Z',
  };

  beforeEach(() => {
    vi.clearAllMocks();
    Object.assign(navigator, {
      clipboard: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
  });

  it('renders user message content', () => {
    render(<ChatMessage message={userMessage} />);
    expect(screen.getByText('What are my expenses?')).toBeInTheDocument();
  });

  it('renders assistant message content', () => {
    render(<ChatMessage message={assistantMessage} />);
    expect(screen.getByText('Here is your expense breakdown.')).toBeInTheDocument();
  });

  it('shows copy button for assistant messages', () => {
    render(<ChatMessage message={assistantMessage} />);
    expect(screen.getByTitle('Copy to clipboard')).toBeInTheDocument();
  });

  it('does not show copy button for user messages', () => {
    render(<ChatMessage message={userMessage} />);
    expect(screen.queryByTitle('Copy to clipboard')).not.toBeInTheDocument();
  });

  it('displays formatted timestamp', () => {
    render(<ChatMessage message={userMessage} />);
    const timeText = screen.getByText(/\d{1,2}:\d{2}/);
    expect(timeText).toBeInTheDocument();
  });

  it('copies content to clipboard when copy button clicked', async () => {
    render(<ChatMessage message={assistantMessage} />);
    const copyBtn = screen.getByTitle('Copy to clipboard');
    fireEvent.click(copyBtn);
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith('Here is your expense breakdown.');
    });
    // Should show "Copied" text
    await waitFor(() => {
      expect(screen.getByText('Copied')).toBeInTheDocument();
    });
  });

  it('shows streaming indicator when isStreaming is true', () => {
    render(<ChatMessage message={assistantMessage} isStreaming={true} />);
    // Should NOT show copy button during streaming
    expect(screen.queryByTitle('Copy to clipboard')).not.toBeInTheDocument();
  });

  it('renders markdown in assistant messages', () => {
    const mdMessage: Message = {
      role: 'assistant',
      content: '**Bold text** and [a link](https://example.com)',
      timestamp: '2024-01-15T10:30:05Z',
    };
    render(<ChatMessage message={mdMessage} />);
    expect(screen.getByText('Bold text')).toBeInTheDocument();
    expect(screen.getByText('a link')).toBeInTheDocument();
  });

  it('renders code blocks in assistant messages', () => {
    const codeMessage: Message = {
      role: 'assistant',
      content: 'Here is code:\n```\nconsole.log("hello")\n```',
      timestamp: '2024-01-15T10:30:05Z',
    };
    render(<ChatMessage message={codeMessage} />);
    expect(screen.getByText(/console\.log/)).toBeInTheDocument();
  });

  it('renders inline code in assistant messages', () => {
    const msg: Message = {
      role: 'assistant',
      content: 'Use `npm install` to install',
      timestamp: '2024-01-15T10:30:05Z',
    };
    render(<ChatMessage message={msg} />);
    const codeEl = screen.getByText('npm install');
    expect(codeEl.tagName).toBe('CODE');
  });

  it('renders links with target blank', () => {
    const msg: Message = {
      role: 'assistant',
      content: 'Visit [OpenFinance](https://example.com) for details',
      timestamp: '2024-01-15T10:30:05Z',
    };
    render(<ChatMessage message={msg} />);
    const link = screen.getByText('OpenFinance');
    expect(link.closest('a')).toHaveAttribute('target', '_blank');
    expect(link.closest('a')).toHaveAttribute('rel', 'noopener noreferrer');
  });

  it('renders unordered lists', () => {
    const msg: Message = {
      role: 'assistant',
      content: '- Item one\n- Item two\n- Item three',
      timestamp: '2024-01-15T10:30:05Z',
    };
    render(<ChatMessage message={msg} />);
    expect(screen.getByText('Item one')).toBeInTheDocument();
    expect(screen.getByText('Item two')).toBeInTheDocument();
    const ul = screen.getByText('Item one').closest('ul');
    expect(ul).toBeInTheDocument();
  });

  it('renders ordered lists', () => {
    const msg: Message = {
      role: 'assistant',
      content: '1. First\n2. Second\n3. Third',
      timestamp: '2024-01-15T10:30:05Z',
    };
    render(<ChatMessage message={msg} />);
    expect(screen.getByText('First')).toBeInTheDocument();
    const ol = screen.getByText('First').closest('ol');
    expect(ol).toBeInTheDocument();
  });

  it('renders headings in markdown', () => {
    const msg: Message = {
      role: 'assistant',
      content: '# Heading 1\n## Heading 2\n### Heading 3',
      timestamp: '2024-01-15T10:30:05Z',
    };
    render(<ChatMessage message={msg} />);
    expect(screen.getByText('Heading 1').tagName).toBe('H1');
    expect(screen.getByText('Heading 2').tagName).toBe('H2');
    expect(screen.getByText('Heading 3').tagName).toBe('H3');
  });

  it('renders paragraphs in markdown', () => {
    const msg: Message = {
      role: 'assistant',
      content: 'Paragraph one\n\nParagraph two',
      timestamp: '2024-01-15T10:30:05Z',
    };
    render(<ChatMessage message={msg} />);
    expect(screen.getByText('Paragraph one').tagName).toBe('P');
    expect(screen.getByText('Paragraph two').tagName).toBe('P');
  });

  it('shows streaming dots when isStreaming', () => {
    render(<ChatMessage message={assistantMessage} isStreaming />);
    const dots = document.querySelectorAll('.animate-pulse .rounded-full');
    expect(dots.length).toBe(3);
  });
});

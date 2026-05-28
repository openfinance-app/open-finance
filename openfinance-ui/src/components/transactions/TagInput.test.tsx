import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TagInput } from './TagInput';
import { mockAuthentication, renderWithProviders } from '@/test/test-utils';

describe('TagInput', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('adds a tag by pressing Enter', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(<TagInput value={[]} onChange={onChange} />);

    const input = screen.getByPlaceholderText('Add tags (press Enter, comma, or space)');
    await user.type(input, 'groceries{enter}');

    expect(onChange).toHaveBeenCalledWith(['groceries']);
  });

  it('adds a tag by pressing comma', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(<TagInput value={[]} onChange={onChange} />);

    const input = screen.getByPlaceholderText('Add tags (press Enter, comma, or space)');
    await user.type(input, 'rent,');

    expect(onChange).toHaveBeenCalledWith(['rent']);
  });

  it('adds a tag by pressing space', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(<TagInput value={[]} onChange={onChange} />);

    const input = screen.getByPlaceholderText('Add tags (press Enter, comma, or space)');
    await user.type(input, 'food ');

    expect(onChange).toHaveBeenCalledWith(['food']);
  });

  it('does not add duplicate tags', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(<TagInput value={['groceries']} onChange={onChange} />);

    const input = screen.getByRole('textbox');
    await user.type(input, 'groceries{enter}');

    expect(onChange).not.toHaveBeenCalled();
  });

  it('does not add empty tags on Enter', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(<TagInput value={[]} onChange={onChange} />);

    const input = screen.getByPlaceholderText('Add tags (press Enter, comma, or space)');
    await user.type(input, '{enter}');

    expect(onChange).not.toHaveBeenCalled();
  });

  it('removes last tag with Backspace when input is empty', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(<TagInput value={['a', 'b']} onChange={onChange} />);

    const input = screen.getByRole('textbox');
    await user.click(input);
    await user.keyboard('{Backspace}');

    expect(onChange).toHaveBeenCalledWith(['a']);
  });

  it('closes suggestions on Escape', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(
      <TagInput value={[]} onChange={onChange} suggestions={['groceries', 'gas']} />
    );

    const input = screen.getByPlaceholderText('Add tags (press Enter, comma, or space)');
    await user.type(input, 'g');
    expect(screen.getByRole('button', { name: 'groceries' })).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('button', { name: 'groceries' })).not.toBeInTheDocument();
  });

  it('selects suggestion by clicking it', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(
      <TagInput value={[]} onChange={onChange} suggestions={['groceries', 'gas']} />
    );

    const input = screen.getByPlaceholderText('Add tags (press Enter, comma, or space)');
    await user.type(input, 'g');
    await user.click(screen.getByRole('button', { name: 'gas' }));

    expect(onChange).toHaveBeenCalledWith(['gas']);
  });

  it('does not add tags exceeding 50 characters', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(<TagInput value={[]} onChange={onChange} />);

    const input = screen.getByPlaceholderText('Add tags (press Enter, comma, or space)');
    const longTag = 'a'.repeat(51);
    await user.type(input, longTag + '{enter}');

    expect(onChange).not.toHaveBeenCalled();
  });

  it('hides input when max tags reached', () => {
    const onChange = vi.fn();

    renderWithProviders(<TagInput value={['a', 'b']} onChange={onChange} maxTags={2} />);

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByText('Maximum 2 tags reached')).toBeInTheDocument();
  });

  it('hides input and remove buttons when disabled', () => {
    const onChange = vi.fn();

    renderWithProviders(<TagInput value={['tag1', 'tag2']} onChange={onChange} disabled />);

    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /remove tag/i })).not.toBeInTheDocument();
    expect(screen.getByText('tag1')).toBeInTheDocument();
    expect(screen.getByText('tag2')).toBeInTheDocument();
  });

  it('navigates suggestions with ArrowUp', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(
      <TagInput value={[]} onChange={onChange} suggestions={['groceries', 'gas', 'gym']} />
    );

    const input = screen.getByPlaceholderText('Add tags (press Enter, comma, or space)');
    await user.type(input, 'g');
    await user.keyboard('{ArrowDown}{ArrowDown}{ArrowUp}{Enter}');
    expect(onChange).toHaveBeenCalledWith(['gas']);
  });

  it('filters out already-selected tags from suggestions', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(
      <TagInput value={['groceries']} onChange={onChange} suggestions={['groceries', 'gas']} />
    );

    const input = screen.getByRole('textbox');
    await user.type(input, 'g');
    expect(screen.queryByRole('button', { name: 'groceries' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'gas' })).toBeInTheDocument();
  });

  it('shows suggestions and selects one with keyboard navigation', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    renderWithProviders(
      <TagInput
        value={[]}
        onChange={onChange}
        suggestions={['groceries', 'gas', 'gym']}
      />
    );

    const input = screen.getByPlaceholderText('Add tags (press Enter, comma, or space)');
    await user.type(input, 'g');
    expect(screen.getByRole('button', { name: 'groceries' })).toBeInTheDocument();

    await user.keyboard('{ArrowDown}{Enter}');
    expect(onChange).toHaveBeenCalled();
  });

  it('removes a tag and shows max tag warning', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();

    const { rerender } = renderWithProviders(
      <TagInput value={['salary', 'bonus']} onChange={onChange} maxTags={2} />
    );

    expect(screen.getByText('Maximum 2 tags reached')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove tag bonus' }));
    expect(onChange).toHaveBeenCalledWith(['salary']);

    rerender(<TagInput value={['salary']} onChange={onChange} disabled />);
    expect(screen.queryByRole('button', { name: /remove tag/i })).not.toBeInTheDocument();
  });

  it('renders existing tags as badges', () => {
    renderWithProviders(<TagInput value={['rent', 'food']} onChange={vi.fn()} />);

    expect(screen.getByText('rent')).toBeInTheDocument();
    expect(screen.getByText('food')).toBeInTheDocument();
  });

  it('shows placeholder only when no tags exist', () => {
    const { rerender } = renderWithProviders(<TagInput value={[]} onChange={vi.fn()} />);
    expect(screen.getByPlaceholderText('Add tags (press Enter, comma, or space)')).toBeInTheDocument();

    rerender(<TagInput value={['tag1']} onChange={vi.fn()} />);
    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('placeholder', '');
  });
});
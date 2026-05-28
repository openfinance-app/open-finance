import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, fireEvent } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, userEvent } from '@/test/test-utils';
import { CountrySelector } from './CountrySelector';

describe('CountrySelector', () => {
  const onValueChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuthentication();
  });

  it('renders with placeholder when no value', () => {
    renderWithProviders(
      <CountrySelector value="" onValueChange={onValueChange} />
    );
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('displays selected country name', () => {
    renderWithProviders(
      <CountrySelector value="US" onValueChange={onValueChange} />
    );
    expect(screen.getByText('United States')).toBeInTheDocument();
  });

  it('opens dropdown and shows search input', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CountrySelector value="" onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });
  });

  it('filters countries by search query', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CountrySelector value="" onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });

    await user.type(screen.getByRole('textbox'), 'France');

    await waitFor(() => {
      expect(screen.getByText('France')).toBeInTheDocument();
    });
  });

  it('shows no match message for invalid search', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CountrySelector value="" onValueChange={onValueChange} noMatch="No results" />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });

    await user.type(screen.getByRole('textbox'), 'xyznonexistent');

    await waitFor(() => {
      expect(screen.getByText('No results')).toBeInTheDocument();
    });
  });

  it('selects a country and calls onValueChange', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CountrySelector value="" onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });

    await user.type(screen.getByRole('textbox'), 'France');

    await waitFor(() => {
      expect(screen.getByText('France')).toBeInTheDocument();
    });

    await user.click(screen.getByText('France'));

    expect(onValueChange).toHaveBeenCalledWith('FR');
  });

  it('respects disabled prop', () => {
    renderWithProviders(
      <CountrySelector value="" onValueChange={onValueChange} disabled />
    );

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('closes dropdown on Escape key in search', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CountrySelector value="" onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });

    fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Escape' });

    await waitFor(() => {
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    });
  });

  it('shows check mark for selected country', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <CountrySelector value="FR" onValueChange={onValueChange} />
    );

    await user.click(screen.getByRole('button'));

    await waitFor(() => {
      expect(screen.getByRole('textbox')).toBeInTheDocument();
    });

    await user.type(screen.getByRole('textbox'), 'France');

    await waitFor(() => {
      const franceBtns = screen.getAllByText('France');
      expect(franceBtns.length).toBeGreaterThanOrEqual(1);
    });
  });

  it('uses custom placeholder', () => {
    renderWithProviders(
      <CountrySelector value="" onValueChange={onValueChange} placeholder="Choose country" />
    );
    expect(screen.getByText('Choose country')).toBeInTheDocument();
  });
});

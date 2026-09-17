/**
 * Tests for CurrencyDisplayContext
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { CurrencyDisplayProvider, useCurrencyDisplay } from '@/context/CurrencyDisplayContext';

const mockLocalStorage = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] || null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
  };
})();

Object.defineProperty(window, 'localStorage', {
  value: mockLocalStorage,
  writable: true,
});

describe('CurrencyDisplayContext', () => {
  beforeEach(() => {
    mockLocalStorage.clear();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('Provider initialization', () => {
    it('should default secondaryCurrency to null when localStorage is empty', () => {
      const TestComponent = () => {
        const { secondaryCurrency } = useCurrencyDisplay();
        return <div data-testid="secondary">{secondaryCurrency}</div>;
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      expect(screen.getByTestId('secondary')).toHaveTextContent('');
    });

    it('should initialize displayMode to "base" when localStorage is empty', () => {
      const TestComponent = () => {
        const { displayMode } = useCurrencyDisplay();
        return <div data-testid="mode">{displayMode}</div>;
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      expect(screen.getByTestId('mode')).toHaveTextContent('base');
    });

    it('should initialize from existing localStorage value for secondaryCurrency', () => {
      mockLocalStorage.setItem('open_finance_secondary_currency', 'EUR');

      const TestComponent = () => {
        const { secondaryCurrency } = useCurrencyDisplay();
        return <div data-testid="secondary">{secondaryCurrency}</div>;
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      expect(screen.getByTestId('secondary')).toHaveTextContent('EUR');
    });

    it('should initialize from existing localStorage value for displayMode', () => {
      mockLocalStorage.setItem('open_finance_amount_display_mode', 'native');

      const TestComponent = () => {
        const { displayMode } = useCurrencyDisplay();
        return <div data-testid="mode">{displayMode}</div>;
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      expect(screen.getByTestId('mode')).toHaveTextContent('native');
    });
  });

  describe('setSecondaryCurrency', () => {
    it('should update state and persist to localStorage when setting a currency', () => {
      const TestComponent = () => {
        const { secondaryCurrency, setSecondaryCurrency } = useCurrencyDisplay();
        return (
          <div>
            <div data-testid="secondary">{secondaryCurrency}</div>
            <button onClick={() => setSecondaryCurrency('USD')}>Set USD</button>
          </div>
        );
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      expect(screen.getByTestId('secondary')).toHaveTextContent('');
      expect(mockLocalStorage.getItem('open_finance_secondary_currency')).toBeNull();

      act(() => {
        screen.getByText('Set USD').click();
      });

      expect(screen.getByTestId('secondary')).toHaveTextContent('USD');
      expect(mockLocalStorage.setItem).toHaveBeenCalledWith(
        'open_finance_secondary_currency',
        'USD'
      );
    });

    it('should remove localStorage entry when setting to null', () => {
      mockLocalStorage.setItem('open_finance_secondary_currency', 'EUR');

      const TestComponent = () => {
        const { secondaryCurrency, setSecondaryCurrency } = useCurrencyDisplay();
        return (
          <div>
            <div data-testid="secondary">{secondaryCurrency}</div>
            <button onClick={() => setSecondaryCurrency(null)}>Clear</button>
          </div>
        );
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      expect(screen.getByTestId('secondary')).toHaveTextContent('EUR');

      act(() => {
        screen.getByText('Clear').click();
      });

      expect(screen.getByTestId('secondary')).toHaveTextContent('');
      expect(mockLocalStorage.removeItem).toHaveBeenCalledWith('open_finance_secondary_currency');
    });

    it('should trim whitespace from currency codes', () => {
      const TestComponent = () => {
        const { secondaryCurrency, setSecondaryCurrency } = useCurrencyDisplay();
        return (
          <div>
            <div data-testid="secondary">{secondaryCurrency}</div>
            <button onClick={() => setSecondaryCurrency(' USD ')}>Set USD</button>
          </div>
        );
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      act(() => {
        screen.getByText('Set USD').click();
      });

      expect(screen.getByTestId('secondary')).toHaveTextContent('USD');
      expect(mockLocalStorage.setItem).toHaveBeenCalledWith(
        'open_finance_secondary_currency',
        'USD'
      );
    });

    it('should treat empty string as null', () => {
      const TestComponent = () => {
        const { secondaryCurrency, setSecondaryCurrency } = useCurrencyDisplay();
        return (
          <div>
            <div data-testid="secondary">{secondaryCurrency}</div>
            <button onClick={() => setSecondaryCurrency('')}>Set Empty</button>
          </div>
        );
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      screen.getByText('Set Empty').click();

      expect(screen.getByTestId('secondary')).toHaveTextContent('');
      expect(mockLocalStorage.removeItem).toHaveBeenCalledWith('open_finance_secondary_currency');
    });
  });

  describe('setDisplayMode', () => {
    it('should update state and persist to localStorage', () => {
      const TestComponent = () => {
        const { displayMode, setDisplayMode } = useCurrencyDisplay();
        return (
          <div>
            <div data-testid="mode">{displayMode}</div>
            <button onClick={() => setDisplayMode('native')}>Set Native</button>
          </div>
        );
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      expect(screen.getByTestId('mode')).toHaveTextContent('base');

      act(() => {
        screen.getByText('Set Native').click();
      });

      expect(screen.getByTestId('mode')).toHaveTextContent('native');
      expect(mockLocalStorage.setItem).toHaveBeenCalledWith(
        'open_finance_amount_display_mode',
        'native'
      );
    });
  });

  describe('Error handling', () => {
    it('should handle localStorage errors gracefully', () => {
      mockLocalStorage.getItem.mockImplementation(() => {
        throw new Error('localStorage error');
      });

      const TestComponent = () => {
        const { displayMode, secondaryCurrency } = useCurrencyDisplay();
        return (
          <div>
            <div data-testid="mode">{displayMode}</div>
            <div data-testid="secondary">{secondaryCurrency}</div>
          </div>
        );
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      expect(screen.getByTestId('mode')).toHaveTextContent('base');
      expect(screen.getByTestId('secondary')).toHaveTextContent('');
    });

    it('should handle localStorage setItem errors', () => {
      mockLocalStorage.setItem.mockImplementation(() => {
        throw new Error('localStorage error');
      });

      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      const TestComponent = () => {
        const { setDisplayMode } = useCurrencyDisplay();
        return <button onClick={() => setDisplayMode('native')}>Set Mode</button>;
      };

      render(
        <CurrencyDisplayProvider>
          <TestComponent />
        </CurrencyDisplayProvider>
      );

      screen.getByText('Set Mode').click();

      expect(consoleSpy).toHaveBeenCalledWith(
        'Failed to persist currency display mode:',
        expect.any(Error)
      );

      consoleSpy.mockRestore();
    });
  });

  describe('Hook usage outside provider', () => {
    it('should throw error when useCurrencyDisplay is used outside provider', () => {
      const TestComponent = () => {
        useCurrencyDisplay();
        return <div>Test</div>;
      };

      expect(() => render(<TestComponent />)).toThrow(
        'useCurrencyDisplay must be used within a CurrencyDisplayProvider'
      );
    });
  });
});

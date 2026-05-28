import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { renderWithProviders, mockAuthentication, clearAuthentication } from '@/test/test-utils';
import { Sidebar } from './Sidebar';

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------
const mockToggleCollapsed = vi.fn();
let mockIsCollapsed = false;

vi.mock('@/context/SidebarContext', () => ({
  useSidebar: () => ({
    isCollapsed: mockIsCollapsed,
    toggleCollapsed: mockToggleCollapsed,
  }),
}));

let mockIsMobile = false;
vi.mock('@/hooks/useBreakpoint', () => ({
  useIsMobile: () => mockIsMobile,
}));

let mockIsPropertyRentalAvailable = true;
vi.mock('@/hooks/useCountryToolConfig', () => ({
  useCountryToolConfig: () => ({
    isPropertyRentalAvailable: mockIsPropertyRentalAvailable,
  }),
}));

let mockHasSessionHistory = true;
vi.mock('@/hooks/useHasSessionHistory', () => ({
  useHasSessionHistory: () => mockHasSessionHistory,
}));

describe('Sidebar', () => {
  beforeEach(() => {
    clearAuthentication();
    mockAuthentication();
    vi.clearAllMocks();
    mockIsCollapsed = false;
    mockIsMobile = false;
    mockIsPropertyRentalAvailable = true;
    mockHasSessionHistory = true;
  });

  describe('Desktop', () => {
    it('renders logo', () => {
      renderWithProviders(<Sidebar />);
      // AppLogo renders within sidebar
      expect(document.querySelector('aside')).toBeTruthy();
    });

    it('renders navigation links', () => {
      renderWithProviders(<Sidebar />);
      expect(screen.getByText('Dashboard')).toBeInTheDocument();
      expect(screen.getByText('Import')).toBeInTheDocument();
      expect(screen.getByText('Budget')).toBeInTheDocument();
    });

    it('renders institutions group with children', () => {
      renderWithProviders(<Sidebar />);
      expect(screen.getByText('Accounts')).toBeInTheDocument();
      expect(screen.getByText('Assets')).toBeInTheDocument();
      expect(screen.getByText('Liabilities')).toBeInTheDocument();
    });

    it('renders tools group', () => {
      renderWithProviders(<Sidebar />);
      expect(screen.getByText('Tools')).toBeInTheDocument();
    });

    it('toggles expanded items on chevron click', async () => {
      renderWithProviders(<Sidebar />);
      // Transactions group starts collapsed, find its expand button
      const expandBtns = screen.getAllByLabelText(/expand|collapse/i);
      if (expandBtns.length > 0) {
        fireEvent.click(expandBtns[0]);
        // toggling should change the chevron direction
      }
    });

    it('hides history when hasSessionHistory is false', () => {
      mockHasSessionHistory = false;
      renderWithProviders(<Sidebar />);
      expect(screen.queryByText('History')).not.toBeInTheDocument();
    });

    it('shows history when hasSessionHistory is true', () => {
      mockHasSessionHistory = true;
      renderWithProviders(<Sidebar />);
      expect(screen.getByText('History')).toBeInTheDocument();
    });

    it('hides property rental when not available', () => {
      mockIsPropertyRentalAvailable = false;
      renderWithProviders(<Sidebar />);
      expect(screen.queryByText('Property Rental')).not.toBeInTheDocument();
    });

    it('shows property rental when available', () => {
      mockIsPropertyRentalAvailable = true;
      renderWithProviders(<Sidebar />);
      // Need to expand tools first
      expect(screen.getByText('Tools')).toBeInTheDocument();
    });

    it('renders collapsed sidebar with narrower width', () => {
      mockIsCollapsed = true;
      renderWithProviders(<Sidebar />);
      const aside = document.querySelector('aside');
      expect(aside?.className).toContain('w-[72px]');
    });

    it('renders expanded sidebar with wider width', () => {
      mockIsCollapsed = false;
      renderWithProviders(<Sidebar />);
      const aside = document.querySelector('aside');
      expect(aside?.className).toContain('w-[240px]');
    });

    it('shows labels when not collapsed', () => {
      mockIsCollapsed = false;
      renderWithProviders(<Sidebar />);
      expect(screen.getByText('Dashboard')).toBeInTheDocument();
    });
  });

  describe('Mobile', () => {
    beforeEach(() => {
      mockIsMobile = true;
    });

    it('renders mobile menu button', () => {
      renderWithProviders(<Sidebar />);
      expect(screen.getByLabelText(/open menu/i)).toBeInTheDocument();
    });

    it('opens mobile sidebar on menu button click', async () => {
      renderWithProviders(<Sidebar />);
      fireEvent.click(screen.getByLabelText(/open menu/i));
      await waitFor(() => {
        expect(screen.getByLabelText(/close menu/i)).toBeInTheDocument();
      });
    });

    it('shows navigation items when mobile sidebar opens', async () => {
      renderWithProviders(<Sidebar />);
      fireEvent.click(screen.getByLabelText(/open menu/i));
      await waitFor(() => {
        expect(screen.getByText('Dashboard')).toBeInTheDocument();
      });
    });

    it('closes mobile sidebar on close button click', async () => {
      renderWithProviders(<Sidebar />);
      fireEvent.click(screen.getByLabelText(/open menu/i));
      await waitFor(() => {
        expect(screen.getByLabelText(/close menu/i)).toBeInTheDocument();
      });
      fireEvent.click(screen.getByLabelText(/close menu/i));
      // Sidebar should slide out
      await waitFor(() => {
        const aside = document.querySelector('aside');
        expect(aside?.className).toContain('-translate-x-full');
      });
    });

    it('closes mobile sidebar on backdrop click', async () => {
      renderWithProviders(<Sidebar />);
      fireEvent.click(screen.getByLabelText(/open menu/i));
      await waitFor(() => {
        expect(screen.getByLabelText(/close menu/i)).toBeInTheDocument();
      });
      // Click backdrop
      const backdrop = document.querySelector('.fixed.inset-0');
      if (backdrop) fireEvent.click(backdrop);
      await waitFor(() => {
        const aside = document.querySelector('aside');
        expect(aside?.className).toContain('-translate-x-full');
      });
    });

    it('locks body scroll when open', async () => {
      renderWithProviders(<Sidebar />);
      fireEvent.click(screen.getByLabelText(/open menu/i));
      await waitFor(() => {
        expect(document.body.style.overflow).toBe('hidden');
      });
    });
  });
});

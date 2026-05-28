import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useKeyboardShortcuts } from './useKeyboardShortcuts';

const mockNavigate = vi.fn();
const mockToggleCollapsed = vi.fn();

vi.mock('react-router', () => ({
    useNavigate: () => mockNavigate,
}));

vi.mock('@/context/SidebarContext', () => ({
    useSidebar: () => ({ toggleCollapsed: mockToggleCollapsed }),
}));

describe('useKeyboardShortcuts', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('calls onFocusSearch on Ctrl+K', () => {
        const onFocusSearch = vi.fn();
        renderHook(() => useKeyboardShortcuts({ onFocusSearch }));
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'k', ctrlKey: true }));
        expect(onFocusSearch).toHaveBeenCalled();
    });

    it('toggles sidebar on Ctrl+B', () => {
        renderHook(() => useKeyboardShortcuts());
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'b', ctrlKey: true }));
        expect(mockToggleCollapsed).toHaveBeenCalled();
    });

    it('navigates to /dashboard on key 1', () => {
        renderHook(() => useKeyboardShortcuts());
        window.dispatchEvent(new KeyboardEvent('keydown', { key: '1' }));
        expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
    });

    it('navigates to /transactions on key 3', () => {
        renderHook(() => useKeyboardShortcuts());
        window.dispatchEvent(new KeyboardEvent('keydown', { key: '3' }));
        expect(mockNavigate).toHaveBeenCalledWith('/transactions');
    });

    it('does not navigate when input is focused', () => {
        renderHook(() => useKeyboardShortcuts());
        const input = document.createElement('input');
        document.body.appendChild(input);
        input.focus();
        window.dispatchEvent(new KeyboardEvent('keydown', { key: '1' }));
        expect(mockNavigate).not.toHaveBeenCalled();
        document.body.removeChild(input);
    });

    it('does not navigate on unrecognized key', () => {
        renderHook(() => useKeyboardShortcuts());
        window.dispatchEvent(new KeyboardEvent('keydown', { key: '0' }));
        expect(mockNavigate).not.toHaveBeenCalled();
    });
});

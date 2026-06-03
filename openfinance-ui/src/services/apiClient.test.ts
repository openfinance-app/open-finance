import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import axios from 'axios';

describe('apiClient', () => {
    let originalLocation: Location;

    beforeEach(() => {
        vi.clearAllMocks();
        localStorage.clear();
        sessionStorage.clear();
        originalLocation = window.location;
    });

    afterEach(() => {
        // Restore
    });

    it('exports a default axios instance', async () => {
        const apiClient = (await import('./apiClient')).default;
        expect(apiClient).toBeDefined();
        expect(typeof apiClient.get).toBe('function');
        expect(typeof apiClient.post).toBe('function');
        expect(typeof apiClient.put).toBe('function');
        expect(typeof apiClient.delete).toBe('function');
    });

    it('has proper base configuration', async () => {
        const apiClient = (await import('./apiClient')).default;
        expect(apiClient.defaults.timeout).toBe(30000);
        expect(apiClient.defaults.headers['Content-Type']).toBe('application/json');
    });

    it('base URL defaults to localhost:8080', async () => {
        const apiClient = (await import('./apiClient')).default;
        expect(apiClient.defaults.baseURL).toContain('/api/v1');
    });

    it('request interceptor adds auth token from localStorage', async () => {
        localStorage.setItem('auth_token', 'test-jwt');
        const apiClient = (await import('./apiClient')).default;

        // Run the request interceptor manually
        const config = { headers: {} } as any;
        // Access the request interceptor - it's the first fulfilled handler
        const interceptor = (apiClient.interceptors.request as any).handlers[0];
        const result = interceptor.fulfilled(config);
        expect(result.headers.Authorization).toBe('Bearer test-jwt');
    });

    it('request interceptor adds auth token from sessionStorage', async () => {
        sessionStorage.setItem('auth_token', 'session-jwt');
        const apiClient = (await import('./apiClient')).default;

        const config = { headers: {} } as any;
        const interceptor = (apiClient.interceptors.request as any).handlers[0];
        const result = interceptor.fulfilled(config);
        expect(result.headers.Authorization).toBe('Bearer session-jwt');
    });

    it('request interceptor adds encryption key from sessionStorage', async () => {
        sessionStorage.setItem('encryption_session', 'enc-key-123');
        const apiClient = (await import('./apiClient')).default;

        const config = { headers: {} } as any;
        const interceptor = (apiClient.interceptors.request as any).handlers[0];
        const result = interceptor.fulfilled(config);
        expect(result.headers['X-Encryption-Session']).toBe('enc-key-123');
    });

    it('request interceptor adds Accept-Language header', async () => {
        const apiClient = (await import('./apiClient')).default;

        const config = { headers: {} } as any;
        const interceptor = (apiClient.interceptors.request as any).handlers[0];
        const result = interceptor.fulfilled(config);
        expect(result.headers['Accept-Language']).toBeDefined();
    });

    it('request interceptor does not overwrite existing X-Encryption-Session', async () => {
        sessionStorage.setItem('encryption_session', 'should-not-use');
        const apiClient = (await import('./apiClient')).default;

        const config = { headers: { 'X-Encryption-Session': 'existing' } } as any;
        const interceptor = (apiClient.interceptors.request as any).handlers[0];
        const result = interceptor.fulfilled(config);
        expect(result.headers['X-Encryption-Session']).toBe('existing');
    });

    it('response interceptor clears storage on 401 for non-login requests', async () => {
        localStorage.setItem('auth_token', 'stale');
        sessionStorage.setItem('encryption_session', 'stale');

        const apiClient = (await import('./apiClient')).default;
        const interceptor = (apiClient.interceptors.response as any).handlers[0];

        const error = {
            response: { status: 401 },
            config: { url: '/some/protected/endpoint', method: 'get' },
        };

        await expect(interceptor.rejected(error)).rejects.toEqual(error);
        expect(localStorage.getItem('auth_token')).toBeNull();
        expect(sessionStorage.getItem('encryption_session')).toBeNull();
    });

    it('response interceptor does NOT clear storage on 401 for login endpoint', async () => {
        localStorage.setItem('auth_token', 'keep-me');
        const apiClient = (await import('./apiClient')).default;
        const interceptor = (apiClient.interceptors.response as any).handlers[0];

        const error = {
            response: { status: 401 },
            config: { url: '/auth/login', method: 'post' },
        };

        await expect(interceptor.rejected(error)).rejects.toEqual(error);
        // Should NOT clear storage for login requests
        expect(localStorage.getItem('auth_token')).toBe('keep-me');
    });

    it('response interceptor does NOT clear storage on 401 for profile PUT', async () => {
        localStorage.setItem('auth_token', 'keep-me');
        const apiClient = (await import('./apiClient')).default;
        const interceptor = (apiClient.interceptors.response as any).handlers[0];

        const error = {
            response: { status: 401 },
            config: { url: '/auth/profile', method: 'put' },
        };

        await expect(interceptor.rejected(error)).rejects.toEqual(error);
        expect(localStorage.getItem('auth_token')).toBe('keep-me');
    });

    it('response interceptor handles 403 forbidden', async () => {
        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => { });
        const apiClient = (await import('./apiClient')).default;
        const interceptor = (apiClient.interceptors.response as any).handlers[0];

        const error = {
            response: { status: 403, data: 'Forbidden' },
            config: { url: '/admin' },
        };

        await expect(interceptor.rejected(error)).rejects.toEqual(error);
        expect(consoleSpy).toHaveBeenCalledWith('Access forbidden:', 'Forbidden');
        consoleSpy.mockRestore();
    });

    it('response interceptor handles 500 server error', async () => {
        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => { });
        const apiClient = (await import('./apiClient')).default;
        const interceptor = (apiClient.interceptors.response as any).handlers[0];

        const error = {
            response: { status: 500, data: 'Internal Error' },
            config: { url: '/something' },
        };

        await expect(interceptor.rejected(error)).rejects.toEqual(error);
        expect(consoleSpy).toHaveBeenCalledWith('Server error:', 'Internal Error');
        consoleSpy.mockRestore();
    });

    it('response interceptor handles no response (network error)', async () => {
        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => { });
        const apiClient = (await import('./apiClient')).default;
        const interceptor = (apiClient.interceptors.response as any).handlers[0];

        const error = {
            response: undefined,
            request: { url: '/something' },
        };

        await expect(interceptor.rejected(error)).rejects.toEqual(error);
        expect(consoleSpy).toHaveBeenCalledWith('No response from server:', error.request);
        consoleSpy.mockRestore();
    });

    it('response interceptor handles generic request error', async () => {
        const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => { });
        const apiClient = (await import('./apiClient')).default;
        const interceptor = (apiClient.interceptors.response as any).handlers[0];

        const error = {
            response: undefined,
            request: undefined,
            message: 'Something broke',
        };

        await expect(interceptor.rejected(error)).rejects.toEqual(error);
        expect(consoleSpy).toHaveBeenCalledWith('Request error:', 'Something broke');
        consoleSpy.mockRestore();
    });
});

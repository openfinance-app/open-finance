import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import i18n from '../i18n';

// API base URL - defaults to backend running on port 8080
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

// Create axios instance with default configuration
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000, // 30 seconds
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor - add JWT token and encryption key to requests
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Add JWT token from localStorage or sessionStorage
    const token = localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token');
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Add encryption session token to X-Encryption-Session header
    const sessionToken = sessionStorage.getItem('encryption_session');

    if (sessionToken && config.headers && !config.headers['X-Encryption-Session']) {
      config.headers['X-Encryption-Session'] = sessionToken;
    }

    // Add Accept-Language header for backend localization (REQ-3.6.1)
    if (config.headers) {
      config.headers['Accept-Language'] = i18n.language ?? 'en';
    }

    return config;
  },
  (error: AxiosError) => {
    return Promise.reject(error);
  }
);

// Response interceptor - handle errors globally
apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  (error: AxiosError) => {
    if (error.response) {
      // Server responded with error status
      const status = error.response.status;

      if (status === 401) {
        // Unauthorized - clear token and redirect to login.
        // IMPORTANT: Do NOT redirect when the 401 comes from the login endpoint
        // itself (wrong credentials) — let the component handle that error.
        const isLoginRequest = error.config?.url?.includes('/auth/login');
        // A 401 on profile update means the user supplied the wrong currentPassword,
        // not an expired session — let the component handle that error.
        const isProfileUpdate =
          error.config?.url?.includes('/auth/profile') && error.config?.method === 'put';
        if (!isLoginRequest && !isProfileUpdate) {
          // An authenticated request was rejected (expired/invalid token).
          // Clear stale tokens and send the user back to login.
          try {
            localStorage.removeItem('auth_token');
            sessionStorage.removeItem('encryption_session');
            sessionStorage.removeItem('session_start_time');
          } catch (e) {
            // ignore storage errors
          }

          // Use full redirect to ensure React state is cleared; keep simple to avoid
          // depending on router internals inside this module.
          window.location.href = '/login';
        }
        // If it IS a login request or a profile update with wrong password,
        // fall through and let the rejection propagate to the calling component.
      } else if (status === 403) {
        // Forbidden
        console.error('Access forbidden:', error.response.data);
      } else if (status >= 500) {
        // Server error
        console.error('Server error:', error.response.data);
      }
    } else if (error.request) {
      // Request was made but no response received
      console.error('No response from server:', error.request);
    } else {
      // Something else happened
      console.error('Request error:', error.message);
    }

    return Promise.reject(error);
  }
);

export default apiClient;

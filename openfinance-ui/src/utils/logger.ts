/**
 * Frontend logging utility with different log levels and formatting.
 * Provides structured logging for development and production environments.
 */

type LogLevel = 'debug' | 'info' | 'warn' | 'error';

interface LogContext {
  [key: string]: unknown;
}

class Logger {
  private isDevelopment: boolean;
  private component?: string;

  constructor(component?: string) {
    this.isDevelopment = import.meta.env.DEV;
    this.component = component;
  }

  /**
   * Debug level logging - only in development
   */
  debug(message: string, context?: LogContext): void {
    if (this.isDevelopment) {
      this.log('debug', message, context);
    }
  }

  /**
   * Info level logging
   */
  info(message: string, context?: LogContext): void {
    this.log('info', message, context);
  }

  /**
   * Warning level logging
   */
  warn(message: string, context?: LogContext): void {
    this.log('warn', message, context);
  }

  /**
   * Error level logging
   */
  error(message: string, error?: Error | unknown, context?: LogContext): void {
    const errorContext: LogContext = {
      ...context,
      error:
        error instanceof Error
          ? {
              name: error.name,
              message: error.message,
              stack: error.stack,
            }
          : error,
    };

    this.log('error', message, errorContext);
  }

  /**
   * Core logging method
   */
  private log(level: LogLevel, message: string, context?: LogContext): void {
    const timestamp = new Date().toISOString();
    const componentTag = this.component ? `[${this.component}]` : '';
    const formattedMessage = `${timestamp} ${componentTag} ${message}`;

    const logData = context ? [formattedMessage, context] : [formattedMessage];

    switch (level) {
      case 'debug':
        console.debug(...logData);
        break;
      case 'info':
        console.info(...logData);
        break;
      case 'warn':
        console.warn(...logData);
        break;
      case 'error':
        console.error(...logData);
        break;
    }

    // In production, you might want to send errors to a logging service
    if (!this.isDevelopment && level === 'error') {
      this.sendToLoggingService(level, message, context);
    }
  }

  /**
   * Send logs to external logging service (e.g., Sentry, LogRocket)
   * Placeholder for future implementation
   */
  private sendToLoggingService(_level: LogLevel, _message: string, _context?: LogContext): void {
    // Send logs to an external logging endpoint if configured.
    // Use Vite env var VITE_LOGGING_SERVICE_URL to configure the endpoint.
    // Keep this fire-and-forget to avoid delaying UI code.
    try {
      const endpoint = (import.meta.env as any).VITE_LOGGING_SERVICE_URL as string | undefined;
      if (!endpoint) return;

      const payload = {
        timestamp: new Date().toISOString(),
        component: this.component ?? null,
        level: _level,
        message: _message,
        context: _context ?? null,
      };

      // Fire-and-forget; do not await to avoid blocking the main thread.
      // Swallow network errors but surface a dev warning when possible.
      void fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        keepalive: true,
      }).catch(err => {
        // don't throw in production logging path
        if (this.isDevelopment) {
          console.warn('Failed to send log to external service', err);
        }
      });
    } catch (err) {
      if (this.isDevelopment) {
        console.warn('Logger.sendToLoggingService error', err);
      }
    }
  }

  /**
   * Performance timing utility
   */
  time(label: string): () => void {
    const startTime = performance.now();

    return () => {
      const endTime = performance.now();
      const duration = endTime - startTime;
      this.debug(`Timer: ${label} took ${duration.toFixed(2)}ms`);
    };
  }

  /**
   * Log API request
   */
  logApiRequest(method: string, url: string, data?: unknown): void {
    this.debug(`API Request: ${method} ${url}`, { data });
  }

  /**
   * Log API response
   */
  logApiResponse(method: string, url: string, status: number, data?: unknown): void {
    const level = status >= 400 ? 'error' : 'debug';
    this[level](`API Response: ${method} ${url} - ${status}`, { data });
  }

  /**
   * Log API error
   */
  logApiError(method: string, url: string, error: Error): void {
    this.error(`API Error: ${method} ${url}`, error);
  }
}

/**
 * Create a logger instance for a specific component
 */
export function createLogger(component?: string): Logger {
  return new Logger(component);
}

/**
 * Default logger instance
 */
export const logger = new Logger();

/**
 * Development-only console logging
 */
export const devLog = (...args: unknown[]): void => {
  if (import.meta.env.DEV) {
    console.log(...args);
  }
};

/**
 * Table logging for structured data (development only)
 */
export const devTable = (data: unknown): void => {
  if (import.meta.env.DEV) {
    console.table(data);
  }
};

export default Logger;

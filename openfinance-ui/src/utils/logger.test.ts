import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createLogger, logger, devLog, devTable } from '@/utils/logger';

describe('Logger', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(console, 'debug').mockImplementation(() => {});
    vi.spyOn(console, 'info').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('creates a logger instance', () => {
    const logger = createLogger('TestComponent');
    expect(logger).toBeDefined();
  });

  it('logs info messages', () => {
    const logger = createLogger('Test');
    logger.info('Test message');
    expect(console.info).toHaveBeenCalled();
  });

  it('logs info with context', () => {
    const logger = createLogger('Test');
    logger.info('Test message', { key: 'value' });
    expect(console.info).toHaveBeenCalled();
  });

  it('logs warnings', () => {
    const logger = createLogger('Test');
    logger.warn('Warning message');
    expect(console.warn).toHaveBeenCalled();
  });

  it('logs errors', () => {
    const logger = createLogger('Test');
    logger.error('Error message', new Error('test'));
    expect(console.error).toHaveBeenCalled();
  });

  it('logs errors with non-Error objects', () => {
    const logger = createLogger('Test');
    logger.error('Error message', 'string error');
    expect(console.error).toHaveBeenCalled();
  });

  it('creates logger without component name', () => {
    const logger = createLogger();
    logger.info('No component');
    expect(console.info).toHaveBeenCalled();
  });

  it('provides time utility', () => {
    const logger = createLogger('Test');
    const end = logger.time('operation');
    expect(typeof end).toBe('function');
    end(); // Should not throw
  });

  it('logs API requests', () => {
    const logger = createLogger('API');
    logger.logApiRequest('GET', '/api/users');
    // In dev mode, debug is called
  });

  it('logs API responses', () => {
    const logger = createLogger('API');
    logger.logApiResponse('GET', '/api/users', 200, { data: [] });
  });

  it('logs API errors', () => {
    const logger = createLogger('API');
    logger.logApiError('GET', '/api/users', new Error('Network error'));
    expect(console.error).toHaveBeenCalled();
  });

  it('logs API response errors for status >= 400', () => {
    const logger = createLogger('API');
    logger.logApiResponse('GET', '/api/users', 500, { error: 'Server error' });
    expect(console.error).toHaveBeenCalled();
  });

  it('logs debug messages in development mode', () => {
    const log = createLogger('Test');
    log.debug('Debug message');
    expect(console.debug).toHaveBeenCalled();
  });

  it('logs debug with context', () => {
    const log = createLogger('Test');
    log.debug('Debug message', { detail: 'value' });
    expect(console.debug).toHaveBeenCalled();
    const args = (console.debug as ReturnType<typeof vi.fn>).mock.lastCall;
    expect(args![1]).toEqual({ detail: 'value' });
  });

  it('includes component tag in formatted message', () => {
    const log = createLogger('MyComponent');
    log.info('hello');
    const args = (console.info as ReturnType<typeof vi.fn>).mock.lastCall;
    expect(args![0]).toContain('[MyComponent]');
    expect(args![0]).toContain('hello');
  });

  it('omits component tag when no component provided', () => {
    const log = createLogger();
    log.info('hello');
    const args = (console.info as ReturnType<typeof vi.fn>).mock.lastCall;
    expect(args![0]).not.toContain('[');
    expect(args![0]).toContain('hello');
  });

  it('time utility logs duration', () => {
    const log = createLogger('Perf');
    const end = log.time('fetch');
    end();
    expect(console.debug).toHaveBeenCalled();
    const args = (console.debug as ReturnType<typeof vi.fn>).mock.lastCall;
    expect(args![0]).toContain('Timer: fetch took');
  });

  it('error extracts Error properties', () => {
    const log = createLogger('Test');
    const err = new Error('boom');
    log.error('Failed', err);
    const args = (console.error as ReturnType<typeof vi.fn>).mock.lastCall;
    expect(args![1]).toMatchObject({
      error: { name: 'Error', message: 'boom' },
    });
  });

  it('logApiRequest logs method and url', () => {
    const log = createLogger('API');
    log.logApiRequest('POST', '/api/data', { body: true });
    expect(console.debug).toHaveBeenCalled();
    const args = (console.debug as ReturnType<typeof vi.fn>).mock.lastCall;
    expect(args![0]).toContain('API Request: POST /api/data');
  });
});

describe('devLog', () => {
  beforeEach(() => {
    vi.spyOn(console, 'log').mockImplementation(() => {});
  });

  it('logs in development mode', () => {
    devLog('hello', 42);
    expect(console.log).toHaveBeenCalledWith('hello', 42);
  });
});

describe('devTable', () => {
  beforeEach(() => {
    vi.spyOn(console, 'table').mockImplementation(() => {});
  });

  it('calls console.table in development mode', () => {
    devTable([{ a: 1 }]);
    expect(console.table).toHaveBeenCalledWith([{ a: 1 }]);
  });
});

describe('logApiResponse context shape', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(console, 'debug').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('passes response data as context (not as the error) for status >= 400', () => {
    const log = createLogger('API');
    log.logApiResponse('GET', '/api/users', 500, { error: 'Server error' });
    const args = (console.error as ReturnType<typeof vi.fn>).mock.lastCall;
    expect(args![0]).toContain('API Response: GET /api/users - 500');
    expect(args![1]).toMatchObject({ data: { error: 'Server error' } });
  });

  it('passes response data as debug context for successful responses', () => {
    const log = createLogger('API');
    log.logApiResponse('GET', '/api/users', 200, [{ id: 1 }]);
    const args = (console.debug as ReturnType<typeof vi.fn>).mock.lastCall;
    expect(args![1]).toEqual({ data: [{ id: 1 }] });
  });
});

describe('production logging path', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(console, 'debug').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
    fetchMock.mockReset().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchMock);
    vi.stubEnv('VITE_LOGGING_SERVICE_URL', 'https://logs.example.test/ingest');
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  // Logger caches import.meta.env.DEV in the constructor; flip the field to
  // exercise the production branch that jsdom (DEV=true) never reaches.
  const productionLogger = () => {
    const log = createLogger('Prod');
    (log as unknown as { isDevelopment: boolean }).isDevelopment = false;
    return log;
  };

  it('does not emit debug logs in production', () => {
    productionLogger().debug('hidden');
    expect(console.debug).not.toHaveBeenCalled();
  });

  it('sends production errors to the configured logging endpoint', () => {
    productionLogger().error('kaboom', new Error('bad'));
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.lastCall as [string, RequestInit];
    expect(url).toBe('https://logs.example.test/ingest');
    expect(init.method).toBe('POST');
    expect(init.keepalive).toBe(true);
    const payload = JSON.parse(init.body as string) as Record<string, unknown>;
    expect(payload).toMatchObject({ component: 'Prod', level: 'error', message: 'kaboom' });
    expect(typeof payload.timestamp).toBe('string');
  });

  it('does not send non-error levels to the logging endpoint', () => {
    productionLogger().info('just info');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('does nothing when no logging endpoint is configured', () => {
    vi.stubEnv('VITE_LOGGING_SERVICE_URL', '');
    productionLogger().error('kaboom');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('never throws when the logging request rejects in production', async () => {
    fetchMock.mockRejectedValue(new Error('network down'));
    expect(() => productionLogger().error('kaboom')).not.toThrow();
    await Promise.resolve();
    expect(console.warn).not.toHaveBeenCalled();
  });

  it('warns in development when the logging request rejects', async () => {
    fetchMock.mockRejectedValue(new Error('network down'));
    const log = createLogger('Dev');
    // Direct private call: the public path only sends in production.
    (
      log as unknown as { sendToLoggingService: (l: string, m: string) => void }
    ).sendToLoggingService('error', 'kaboom');
    await Promise.resolve();
    await Promise.resolve();
    expect(console.warn).toHaveBeenCalledWith(
      'Failed to send log to external service',
      expect.any(Error)
    );
  });

  it('swallows synchronous logging failures and warns in development', () => {
    fetchMock.mockImplementation(() => {
      throw new Error('sync failure');
    });
    const log = createLogger('Dev');
    expect(() =>
      (
        log as unknown as { sendToLoggingService: (l: string, m: string) => void }
      ).sendToLoggingService('error', 'kaboom')
    ).not.toThrow();
    expect(console.warn).toHaveBeenCalledWith(
      'Logger.sendToLoggingService error',
      expect.any(Error)
    );
  });
});

describe('default logger export', () => {
  beforeEach(() => {
    vi.spyOn(console, 'info').mockImplementation(() => {});
  });

  it('is a usable Logger instance', () => {
    logger.info('default logger test');
    expect(console.info).toHaveBeenCalled();
  });
});

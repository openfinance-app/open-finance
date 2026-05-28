import { describe, it, expect, vi, beforeEach } from 'vitest';
import { createLogger, logger, devLog, devTable } from '@/utils/logger';

describe('Logger', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(console, 'debug').mockImplementation(() => { });
    vi.spyOn(console, 'info').mockImplementation(() => { });
    vi.spyOn(console, 'warn').mockImplementation(() => { });
    vi.spyOn(console, 'error').mockImplementation(() => { });
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
    vi.spyOn(console, 'log').mockImplementation(() => { });
  });

  it('logs in development mode', () => {
    devLog('hello', 42);
    expect(console.log).toHaveBeenCalledWith('hello', 42);
  });
});

describe('devTable', () => {
  beforeEach(() => {
    vi.spyOn(console, 'table').mockImplementation(() => { });
  });

  it('calls console.table in development mode', () => {
    devTable([{ a: 1 }]);
    expect(console.table).toHaveBeenCalledWith([{ a: 1 }]);
  });
});

describe('default logger export', () => {
  beforeEach(() => {
    vi.spyOn(console, 'info').mockImplementation(() => { });
  });

  it('is a usable Logger instance', () => {
    logger.info('default logger test');
    expect(console.info).toHaveBeenCalled();
  });
});

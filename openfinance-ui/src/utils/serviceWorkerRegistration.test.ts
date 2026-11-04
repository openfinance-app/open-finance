import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { registerServiceWorker, unregisterServiceWorker } from '@/utils/serviceWorkerRegistration';

beforeEach(() => {
  vi.spyOn(console, 'info').mockImplementation(() => {});
  vi.spyOn(console, 'error').mockImplementation(() => {});
});
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

it('registers the production worker only after page load and can unregister it', async () => {
  vi.stubEnv('PROD', true);
  const registration = { scope: '/', unregister: vi.fn().mockResolvedValue(true) };
  const register = vi.fn().mockResolvedValue(registration);
  vi.stubGlobal('navigator', { serviceWorker: { register, ready: Promise.resolve(registration) } });
  const listener = vi.spyOn(window, 'addEventListener').mockImplementation(() => {});
  registerServiceWorker();
  expect(register).not.toHaveBeenCalled();
  const onLoad = listener.mock.calls.find(([type]) => type === 'load')![1] as EventListener;
  onLoad(new Event('load'));
  await vi.waitFor(() => expect(register).toHaveBeenCalledWith('/sw.js'));
  unregisterServiceWorker();
  await vi.waitFor(() => expect(registration.unregister).toHaveBeenCalledOnce());
});
it('contains registration and unregistration failures rather than rejecting globally', async () => {
  vi.stubEnv('PROD', true);
  const failure = new Error('Worker unavailable');
  vi.stubGlobal('navigator', {
    serviceWorker: {
      register: vi.fn().mockRejectedValue(failure),
      ready: Promise.resolve({ unregister: vi.fn().mockRejectedValue(failure) }),
    },
  });
  const listener = vi.spyOn(window, 'addEventListener').mockImplementation(() => {});
  registerServiceWorker();
  (listener.mock.calls.find(([type]) => type === 'load')![1] as EventListener)(new Event('load'));
  unregisterServiceWorker();
  await vi.waitFor(() => expect(console.error).toHaveBeenCalledTimes(2));
  expect(console.error).toHaveBeenCalledWith('Service Worker registration failed:', failure);
});
it('does not install a worker in development or when the browser lacks support', () => {
  const register = vi.fn();
  vi.stubEnv('PROD', false);
  vi.stubGlobal('navigator', { serviceWorker: { register } });
  const listener = vi.spyOn(window, 'addEventListener');
  registerServiceWorker();
  expect(listener).not.toHaveBeenCalled();
  vi.stubEnv('PROD', true);
  vi.stubGlobal('navigator', {});
  registerServiceWorker();
  unregisterServiceWorker();
  expect(register).not.toHaveBeenCalled();
});

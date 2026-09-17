/**
 * MSW Server Setup for Testing
 *
 * Configures Mock Service Worker to intercept HTTP requests
 * during test execution. This server runs in Node.js environment.
 */

import { setupServer } from 'msw/node';
import { handlers } from './handlers';

/**
 * Create MSW server with default handlers
 */
export const server = setupServer(...handlers);

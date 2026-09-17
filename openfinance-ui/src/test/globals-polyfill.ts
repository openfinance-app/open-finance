/**
 * Polyfills for Node.js Web Streams API globals required by MSW's interceptors
 * in the vmThreads pool environment.
 * Must be loaded BEFORE setup.ts (and therefore before MSW) via setupFiles.
 */
import { TransformStream, ReadableStream, WritableStream } from 'node:stream/web';

if (typeof globalThis.TransformStream === 'undefined') {
  // @ts-expect-error — injecting into VM context global
  globalThis.TransformStream = TransformStream;
}
if (typeof globalThis.ReadableStream === 'undefined') {
  // @ts-expect-error — injecting into VM context global
  globalThis.ReadableStream = ReadableStream;
}
if (typeof globalThis.WritableStream === 'undefined') {
  // @ts-expect-error — injecting into VM context global
  globalThis.WritableStream = WritableStream;
}

// @vitest-environment node
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';
import { createServer } from 'vite';
import { expect, it } from 'vitest';

it('keeps a lazy page available while an editor truncates and rewrites its source', async () => {
  const root = await mkdtemp(join(tmpdir(), 'openfinance-vite-'));
  const file = join(root, 'TransactionsPage.tsx');
  const url = '/TransactionsPage.tsx';
  await writeFile(file, "export default function TransactionsPage() { return 'before save' }");
  const server = await createServer({
    root,
    configFile: resolve('vite.config.ts'),
    server: { middlewareMode: true, ws: false },
    optimizeDeps: { noDiscovery: true, include: [] },
  });

  try {
    const readModule = async () => (await server.transformRequest(url))?.code;
    await expect.poll(() => server.watcher.getWatched()[root]).toContain('TransactionsPage.tsx');
    expect(await readModule()).toContain('before save');

    await writeFile(file, '');
    await delay(25);
    const duringSave = await readModule();
    await writeFile(file, "export default function TransactionsPage() { return 'after save' }");

    expect(duringSave).toContain('before save');
    await expect.poll(readModule).toContain('after save');
  } finally {
    await server.close();
    await rm(root, { recursive: true, force: true });
  }
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from '../server.mjs';

test('servidor entrega a interface, assets e saúde somente por GET/HEAD', async () => {
  const server = createServer();
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    const index = await fetch(base);
    assert.equal(index.status, 200);
    assert.match(await index.text(), /MODO DEMO/);
    assert.match(index.headers.get('content-security-policy'), /default-src 'self'/);
    const demo = await fetch(`${base}/demo.js`);
    assert.equal(demo.status, 200);
    assert.match(demo.headers.get('content-type'), /javascript/);
    const health = await fetch(`${base}/healthz`);
    assert.deepEqual(await health.json(), { status: 'ok', mode: 'demo', service: 'winterhold-web' });
    const head = await fetch(`${base}/app.js`, { method: 'HEAD' });
    assert.equal(head.status, 200);
    assert.equal(await head.text(), '');
    assert.equal((await fetch(`${base}/api/v1/status`)).status, 404);
    assert.equal((await fetch(`${base}/../package.json`)).status, 404);
    assert.equal((await fetch(base, { method: 'POST' })).status, 405);
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import * as demo from '../demo.js';

test('web conecta com sessão e retoma o APK apÃ³s indisponibilidade sem usar dados da prÃ©via', async () => {
  const source = (await readFile(new URL('../app.js', import.meta.url), 'utf8'))
    .replace(/^import[\s\S]*?from '\.\/demo\.js';\s*/, '');
  const elements = new Map();
  const requests = [];
  const timers = [];
  let available = true;
  const snapshot = { ...demo.createDemoState(), running: false };
  const context = vm.createContext({
    ...demo, AbortSignal, console, sessionStorage: { getItem: () => 'test-key', setItem() {}, removeItem() {} },
    document: {
      querySelector(selector) {
        if (!elements.has(selector)) elements.set(selector, { hidden: true, innerHTML: '', textContent: '', addEventListener() {} });
        return elements.get(selector);
      },
      addEventListener() {}
    },
    window: { addEventListener() {} }, location: { protocol: 'http:', hostname: 'localhost' },
    setInterval(callback) { timers.push(callback); },
    fetch: async (path, options) => {
      requests.push({ path, options });
      if (!available) throw new Error('offline');
      return { ok: true, json: async () => path === '/healthz' ? { mode: 'android-demo' } : snapshot };
    }
  });
  // Exercise connection logic without the unrelated navigation/chart rendering.
  vm.runInContext(source + '\nrender = () => {};', context);
  const settle = () => new Promise(resolve => setImmediate(resolve));
  await settle();
  assert.equal(vm.runInContext('backend', context), 'android');
  assert.equal(vm.runInContext('state.running', context), false);
  assert.ok(requests.some(r => r.path === '/api/v1/state'));
  assert.ok(requests.filter(r => r.path === '/api/v1/state').every(r => r.options.credentials === 'same-origin' && !r.options.headers?.['X-Winterhold-Key']));

  available = false;
  timers[0]();
  await settle();
  assert.equal(vm.runInContext('backend', context), 'error');
  assert.equal(elements.get('#connection-banner').hidden, false);
  assert.equal(await vm.runInContext('sendAction({type:"running",value:true}, () => { throw new Error("fallback") })', context), false);

  available = true;
  timers[0]();
  await settle();
  assert.equal(vm.runInContext('backend', context), 'android');
  assert.equal(elements.get('#connection-banner').hidden, true);
  assert.equal(vm.runInContext('state.running', context), false);
});

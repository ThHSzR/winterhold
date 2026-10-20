import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import * as demo from '../demo.js';

test('web shows separate environment identity and bounds without injecting names into markup', async () => {
  const source = (await readFile(new URL('../app.js', import.meta.url), 'utf8'))
    .replace(/^import[\s\S]*?from '\.\/demo\.js';\s*/, '')
    .replace(/\nboot\(\);\s*$/, '');
  const elements = new Map();
  const context = vm.createContext({
    ...demo, sessionStorage: { getItem: () => 'key' },
    document: {
      querySelector(selector) {
        if (!elements.has(selector)) elements.set(selector, { innerHTML: '', hidden: true });
        return elements.get(selector);
      },
      addEventListener() {}
    },
    window: { addEventListener() {} },
    setInterval() {}
  });
  vm.runInContext(source, context);
  const result = vm.runInContext(`
    backend = 'android'; state.mode = 'esp'; state.selectedDeviceId = 'lab-01';
    state.devices = [{ id: 'lab-01', name: '<script>alert(1)</script>', icon: 'lab', online: true,
      limits: { temperatureMin: 19, temperatureMax: 24, humidityMin: 35, humidityMax: 65, voltageMin: 205, voltageMax: 235 } },
      { id: 'sala-02', name: 'Sala 02', icon: 'classroom', online: false,
      limits: { temperatureMin: 18, temperatureMax: 30, humidityMin: 30, humidityMax: 70, voltageMin: 198, voltageMax: 242 } }];
    ({ overview: overview(), environments: environmentsPage() });`, context);
  assert.match(result.overview, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/);
  assert.doesNotMatch(result.overview, /<script>alert\(1\)<\/script>/);
  assert.match(result.overview, /19,0–24,0 °C/);
  assert.match(result.environments, /205,0–235,0 V/);
  assert.match(result.environments, /Sala 02/);
  assert.match(result.environments, /data-action="edit-environment"/);
});

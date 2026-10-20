import test from 'node:test';
import assert from 'node:assert/strict';
import {
  createDemoState, advance, simulateAlert, setOffline, resolveAlert, selectDemoDevice, setDemoReading,
  addContact, toggleContactItem, routeAlert, health, CHANNELS
} from '../demo.js';

test('escopo visual acompanha o APK e mantém apenas saúde do celular', () => {
  const state = createDemoState();
  assert.deepEqual(Object.keys(CHANNELS), ['sms', 'email', 'whatsapp']);
  assert.deepEqual(Object.keys(state.deviceHealth), ['battery', 'memory', 'storage', 'uptime']);
  assert.equal(Object.hasOwn(state, 'infrastructure'), false);
  assert.equal(Object.hasOwn(state, 'automations'), false);
});

test('cinco ambientes demo têm leituras próprias e desvios apenas fictícios', () => {
  const state = createDemoState(1_000_000);
  assert.equal(state.devices.length, 5);
  const previous = state.samples[0].temperature;
  setDemoReading(state, 'demo_servidores', 30, 40, 220, 1_000_001);
  assert.equal(state.samples[0].temperature, previous);
  assert.equal(state.events[0].simulated, true);
  assert.equal(state.events[0].deviceId, 'demo_servidores');
  selectDemoDevice(state, 'demo_servidores');
  assert.equal(state.samples[0].temperature, 30);
  assert.throws(() => selectDemoDevice(state, 'inexistente'), /Ambiente desconhecido/);
});

test('amostras demo avançam somente quando a simulação e a conexão estão ativas', () => {
  const state = createDemoState(1_000_000);
  assert.equal(state.samples.length, 36);
  assert.equal(advance(state, 1_005_000), true);
  assert.equal(state.samples[0].time, 1_005_000);
  state.running = false;
  assert.equal(advance(state), false);
  state.running = true;
  setOffline(state, true, 1_006_000);
  assert.equal(advance(state), false);
  assert.equal(health(state).tone, 'offline');
  setOffline(state, false, 1_007_000);
  assert.equal(state.events[0].title, 'Comunicação restabelecida');
  assert.equal(state.events.find(item => item.title === 'Dispositivo sem comunicação').resolved, true);
});

test('evento é criado, filtrado por variável e canal, e resolvido sem entrega real', () => {
  const state = createDemoState();
  const alert = simulateAlert(state, 'voltage', 1_000_000);
  assert.equal(alert.simulated, true);
  let routes = routeAlert(state, alert);
  assert.deepEqual(routes.map(route => route.contact.id), ['maintenance', 'electrical']);
  state.enabledChannels = ['email'];
  assert.deepEqual(routeAlert(state, alert), []);
  state.enabledChannels = ['sms'];
  toggleContactItem(state, 'electrical', 'metrics', 'voltage');
  routes = routeAlert(state, alert);
  assert.deepEqual(routes.map(route => route.contact.id), ['maintenance']);
  resolveAlert(state, alert.id);
  assert.equal(alert.resolved, true);
  assert.throws(() => simulateAlert(state, 'unknown'), /Variável desconhecida/);
});

test('perfil adicionado recebe identificador e configuração independentes', () => {
  const state = createDemoState();
  const contact = addContact(state, ' Operação ', '');
  assert.equal(contact.name, 'Operação');
  assert.equal(contact.role, 'Equipe');
  assert.ok(contact.id.startsWith('demo-'));
  assert.throws(() => addContact(state, ' ', ''), /Informe um nome/);
  toggleContactItem(state, contact.id, 'channels', 'sms');
  assert.deepEqual(contact.channels, []);
});

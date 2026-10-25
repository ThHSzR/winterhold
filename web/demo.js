export const METRICS = {
  temperature: { label: 'Temperatura', unit: '°C', short: 'Temp.' },
  humidity: { label: 'Umidade', unit: '%', short: 'Umid.' },
  voltage: { label: 'Tensão', unit: 'V', short: 'Tensão' }
};

export const CHANNELS = {
  sms: 'SMS',
  email: 'E-mail',
  whatsapp: 'WhatsApp'
};

let sequence = 0;
const nextId = () => `demo-${Date.now()}-${++sequence}`;
const event = (time, metric, title, detail, severity, resolved = false) => ({
  id: nextId(), time, metric, title, detail, severity, resolved, simulated: true
});

const demoProfiles = [
  ['demo_sala01', 'Sala de aula 01', 'classroom', 19, 26, 35, 65, 198, 242, true],
  ['demo_lab02', 'Laboratório 02', 'lab', 20, 24, 40, 60, 205, 235, true],
  ['demo_servidores', 'Sala de servidores', 'server', 17, 22, 30, 55, 208, 232, false],
  ['demo_adm', 'Administração', 'office', 21, 28, 35, 70, 200, 240, false],
  ['demo_auditorio', 'Auditório', 'snow', 18, 25, 30, 68, 198, 242, false]
];
export function createDemoState(now = Date.now()) {
  const devices = demoProfiles.map(([id, name, icon, temperatureMin, temperatureMax,
    humidityMin, humidityMax, voltageMin, voltageMax, favorite]) => ({
    id, name, icon, favorite, online: true, limits: { temperatureMin, temperatureMax,
      humidityMin, humidityMax, voltageMin, voltageMax }
  }));
  const demoSamples = Object.fromEntries(devices.map((device, offset) => [device.id,
    Array.from({ length: 36 }, (_, i) => ({
      time: now - i * 5 * 60_000, deviceId: device.id,
      temperature: 22 + offset * .5 + .9 * Math.sin((36 - i) / 5),
      humidity: 52 + offset * 2 + 4 * Math.sin((36 - i) / 7),
      voltage: 220 + 2.5 * Math.sin((36 - i) / 3)
    }))]));
  return {
    tick: 36, mode: 'demo', devices, selectedDeviceId: devices[0].id, demoSamples,
    samples: demoSamples[devices[0].id],
    events: [
      event(now - 32 * 60_000, 'temperature', 'Temperatura acima do limite', 'Sala 01 · 27,1 °C por 5 min · limite 26 °C', 'warning', true),
      event(now - 95 * 60_000, 'voltage', 'Oscilação de tensão', 'Sala 01 · 194 V · faixa configurada 198–242 V', 'critical', true),
      event(now - 4 * 60 * 60_000, null, 'Monitoramento iniciado', 'Central de demonstração · ESP32 simulado', 'info', true)
    ],
    contacts: [
      { id: 'maintenance', name: 'Equipe de manutenção', role: 'Manutenção', metrics: ['temperature', 'humidity', 'voltage'], channels: ['sms', 'whatsapp'], active: true },
      { id: 'electrical', name: 'Técnico elétrico', role: 'Elétrica', metrics: ['voltage'], channels: ['sms'], active: true },
      { id: 'management', name: 'Gestão de instalações', role: 'Gestão', metrics: ['temperature', 'humidity'], channels: ['email', 'whatsapp'], active: true }
    ],
    limits: { temperature: 26, humidity: 70, nominalVoltage: 220, tolerance: 10 },
    enabledChannels: ['sms', 'email', 'whatsapp'], running: true, offline: false,
    settings: { smtpHost: '', smtpUser: '', smtpFrom: '', smtpConfigured: false, bridgeUrl: '',
      bridgeConfigured: false, mqttSecret: 'prévia independente', mqttStatus: 'Sem broker',
      webStatus: 'Prévia Node', webUrls: [], smsGranted: false, notificationsGranted: false, batteryExempt: false },
    deviceHealth: { battery: 83, memory: 46, storage: 62, uptime: '12h 08min' }
  };
}

export function selectDemoDevice(state, id) {
  if (!state.demoSamples?.[id]) throw new Error('Ambiente desconhecido');
  state.selectedDeviceId = id;
  state.samples = state.demoSamples[id];
}
export function setDemoReading(state, id, temperature, humidity, voltage, now = Date.now()) {
  const device = state.devices?.find(item => item.id === id);
  if (!device) throw new Error('Ambiente desconhecido');
  if (!(temperature >= -40 && temperature <= 85 && humidity >= 0 && humidity <= 100 && voltage >= 0 && voltage <= 300))
    throw new Error('Leitura fora da faixa física');
  const samples = state.demoSamples[id];
  samples.unshift({ time: now, deviceId: id, temperature, humidity, voltage });
  samples.length = Math.min(samples.length, 60);
  if (state.selectedDeviceId === id) state.samples = samples;
  for (const [metric, value] of Object.entries({ temperature, humidity, voltage })) {
    const low = device.limits[`${metric}Min`], high = device.limits[`${metric}Max`];
    if (value < low || value > high) {
      const alert = event(now, metric, `${METRICS[metric].label} ${value < low ? 'abaixo do mínimo' : 'acima do máximo'}`,
        `${device.name} · ${value} ${METRICS[metric].unit} · faixa ${low}–${high}`, metric === 'voltage' ? 'critical' : 'warning');
      alert.deviceId = id; state.events.unshift(alert);
    }
  }
}
export function advance(state, now = Date.now()) {
  if (!state.running || state.offline) return false;
  state.tick++;
  if (state.demoSamples) {
    for (const [id, samples] of Object.entries(state.demoSamples)) {
      const current = samples[0];
      setDemoReading(state, id, current.temperature + .08 * Math.sin(state.tick / 5),
        current.humidity + .15 * Math.sin(state.tick / 7), current.voltage + .1 * Math.sin(state.tick / 3), now);
    }
  } else {
    state.samples.unshift({ time: now, temperature: 23.4 + .9 * Math.sin(state.tick / 5),
      humidity: 58 + 4 * Math.sin(state.tick / 7), voltage: 220 + 2.5 * Math.sin(state.tick / 3) });
    if (state.samples.length > 60) state.samples.length = 60;
  }
  return true;
}

export function simulateAlert(state, metric, now = Date.now()) {
  if (!Object.hasOwn(METRICS, metric)) throw new Error('Variável desconhecida');
  const details = {
    temperature: ['Temperatura acima do limite', `Sala 01 · 28,2 °C · limite ${state.limits.temperature} °C`, 'warning'],
    humidity: ['Umidade acima do limite', `Sala 01 · 76% · limite ${state.limits.humidity}%`, 'warning'],
    voltage: ['Oscilação de tensão', `Sala 01 · 190 V · nominal ${state.limits.nominalVoltage} V`, 'critical']
  };
  const [title, detail, severity] = details[metric];
  const created = event(now, metric, title, detail, severity);
  state.events.unshift(created);
  return created;
}

export function setOffline(state, offline, now = Date.now()) {
  if (state.offline === offline) return;
  state.offline = offline;
  if (offline) {
    state.events.unshift(event(now, null, 'Dispositivo sem comunicação',
      'Sala 01 · ESP32 simulado · evento de demonstração', 'critical'));
  } else {
    const missing = state.events.find(item => item.title === 'Dispositivo sem comunicação' && !item.resolved);
    if (missing) missing.resolved = true;
    state.events.unshift(event(now, null, 'Comunicação restabelecida',
      'Sala 01 · ESP32 simulado · evento de demonstração', 'info', true));
  }
}

export function resolveAlert(state, id) {
  const found = state.events.find(item => item.id === id);
  if (found) found.resolved = true;
}

export function addContact(state, name, role) {
  const cleaned = name.trim();
  if (!cleaned || cleaned.length > 60) throw new Error('Informe um nome com até 60 caracteres.');
  const contact = {
    id: nextId(), name: cleaned, role: role.trim().slice(0, 60) || 'Equipe',
    metrics: Object.keys(METRICS), channels: ['sms'], active: true
  };
  state.contacts.push(contact);
  return contact;
}

export function toggleContactItem(state, id, group, value) {
  if (group !== 'metrics' && group !== 'channels') return;
  if (!Object.hasOwn(group === 'metrics' ? METRICS : CHANNELS, value)) return;
  const contact = state.contacts.find(item => item.id === id);
  if (!contact) return;
  contact[group] = contact[group].includes(value)
    ? contact[group].filter(item => item !== value)
    : [...contact[group], value];
}

export function routeAlert(state, alert) {
  return state.contacts.filter(contact => contact.active &&
    (alert.metric === null || contact.metrics.includes(alert.metric)))
    .map(contact => ({
      contact,
      channels: contact.channels.filter(channel => state.enabledChannels.includes(channel))
    }))
    .filter(route => route.channels.length > 0);
}

export function health(state) {
  if (state.offline) return { label: 'SEM SINAL', tone: 'offline' };
  if (!state.running) return { label: 'PAUSADO', tone: 'warning' };
  if (state.events.some(item => !item.resolved)) return { label: 'ATENÇÃO', tone: 'warning' };
  return { label: 'SIMULANDO', tone: 'normal' };
}

import {
  METRICS, CHANNELS, createDemoState, advance, simulateAlert,
  setOffline, resolveAlert, addContact, toggleContactItem, routeAlert, health,
  selectDemoDevice, setDemoReading
} from './demo.js';

const state = createDemoState();
let backend = 'loading';
sessionStorage.removeItem('winterhold-web-key');
const pages = {
  inicio: ['Visão geral', 'home'],
  ambientes: ['Ambientes', 'database'],
  historico: ['Histórico', 'history'],
  alertas: ['Alertas', 'bell'],
  equipe: ['Equipe', 'users'],
  ajustes: ['Ajustes', 'settings']
};
const mainPages = ['inicio', 'ambientes', 'alertas', 'historico'];
let metric = 'temperature';
let alertFilter = 'all';
let dialog = null;
let lastFocused = null;
const content = document.querySelector('#app-content');
const dialogRoot = document.querySelector('#dialog-root');
const connectionBanner = document.querySelector('#connection-banner');

function showUnlock() {
  dialog = null;
  dialogRoot.innerHTML = '';
  document.querySelector('#logout-button').hidden = true;
  backend = 'locked';
  connectionBanner.hidden = true;
  content.innerHTML = `<section class="card unlock-card"><span class="eyebrow">CENTRAL ANDROID</span><h1>Acessar Winterhold</h1><p>Use o usuário e a senha cadastrados nos Ajustes do aplicativo.</p><form id="unlock-form"><label class="form-field">Usuário<input name="username" autocomplete="username" required maxlength="40"></label><label class="form-field">Senha<input name="password" type="password" autocomplete="current-password" required maxlength="256"></label><button class="btn" type="submit">Entrar</button></form></section>`;
}
function connectionError(message) {
  backend = 'error';
  connectionBanner.hidden = false;
  connectionBanner.textContent = message;
  content.innerHTML = `<div class="card empty">${escapeHtml(message)}</div>`;
}
async function refreshFromApk() {
  try {
    const response = await fetch('/api/v1/state', {
      credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(8_000)
    });
    if (response.status === 401) { showUnlock(); return; }
    if (!response.ok) throw new Error(`Central indisponível (${response.status})`);
    Object.assign(state, await response.json());
    backend = 'android';
    connectionBanner.hidden = true;
    if (!dialog) render();
  } catch (error) {
    connectionError(`Conexão com o APK perdida. ${error.message}`);
  }
}
async function sendAction(payload, previewAction, savedFormId = null) {
  if (backend === 'android') {
    try {
      const response = await fetch('/api/v1/action', {
        method: 'POST',
        credentials: 'same-origin', headers: { 'Content-Type': 'application/json', 'X-Winterhold-Request': '1' },
        signal: AbortSignal.timeout(8_000),
        body: JSON.stringify(payload)
      });
      if (response.status === 401) { showUnlock(); return false; }
      if (!response.ok) throw new Error((await response.json()).error || `Erro ${response.status}`);
      Object.assign(state, await response.json());
      connectionBanner.hidden = true;
    } catch (error) {
      connectionBanner.hidden = false;
      connectionBanner.textContent = `Ação não aplicada no APK: ${error.message}`;
      return false;
    }
  } else if (backend === 'preview') previewAction();
  else return false;
  render(savedFormId);
  return true;
}
async function boot() {
  content.innerHTML = '<div class="card empty">Conectando à central…</div>';
  try {
    const response = await fetch('/healthz', { cache: 'no-store', signal: AbortSignal.timeout(8_000) });
    if (!response.ok) throw new Error('Servidor indisponível');
    const health = await response.json();
    if (health.mode === 'android-central' || health.mode === 'android-demo') {
      document.querySelector('.page-footer').textContent = 'Winterhold · Central no APK. Saúde do celular medida no Android.';
      if (location.protocol === 'http:' && !['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname)) { location.replace(`https://${location.hostname}:8792/`); return; }
      await refreshFromApk();
    } else if (health.mode === 'demo') {
      backend = 'preview';
      render();
    } else connectionError('Servidor Winterhold desconhecido.');
  } catch (error) { connectionError(`Não foi possível acessar a central: ${error.message}`); }
}

const paths = {
  home: '<path d="m3 10 9-7 9 7v10H3z"/><path d="M9 20v-7h6v7"/>',
  history: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l4 3"/>',
  bell: '<path d="M5 17h14l-2-3V9a5 5 0 0 0-10 0v5zM10 21h4"/>',
  users: '<circle cx="9" cy="8" r="3"/><path d="M3 20v-2a6 6 0 0 1 12 0v2zM17 5a3 3 0 0 1 0 6m1 5a4 4 0 0 1 3 4"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M10 2h4l.6 2.2 2 1.1 2.2-.7 2 3.4-1.6 1.6v2.8l1.6 1.6-2 3.4-2.2-.7-2 1.1L14 22h-4l-.6-2.2-2-1.1-2.2.7-2-3.4 1.6-1.6v-2.8L3.2 10l2-3.4 2.2.7 2-1.1z"/>',
  thermometer: '<path d="M10 14V5a2 2 0 0 1 4 0v9a4 4 0 1 1-4 0z"/><path d="M12 8v9"/>',
  drop: '<path d="M12 2c3 5 7 8 7 12a7 7 0 0 1-14 0c0-4 4-7 7-12z"/>',
  bolt: '<path d="M13 2 5 13h7l-1 9 8-12h-7z"/>',
  radio: '<path d="M12 12h.01M8 8a6 6 0 0 0 0 8m8-8a6 6 0 0 1 0 8M5 5a10 10 0 0 0 0 14m14-14a10 10 0 0 1 0 14"/>',
  database: '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 4 16 4 16 0V5M4 12c0 4 16 4 16 0"/>',
  message: '<path d="M4 4h16v12H9l-5 4z"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 7 9-7"/>',
  check: '<path d="m5 12 5 5 9-10"/>',
  alert: '<path d="M12 2 2 21h20zM12 9v5m0 3v.01"/>',
  plus: '<path d="M12 4v16M4 12h16"/>',
  arrow: '<path d="M4 12h16m-6-6 6 6-6 6"/>'
};
function icon(name) {
  return `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.radio}</svg>`;
}
function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[char]);
}
const decimal = value => value == null ? '—' : new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value);
const environmentIcons = { snow: '❄️', classroom: '🏫', lab: '🧪', office: '🏢', server: '🖥️' };
const environmentName = id => {
  const profile = state.devices?.find(device => device.id === id);
  return profile ? `${environmentIcons[profile.icon] || '❄️'} ${profile.name}` : id;
};
const environmentChoices = () => `<div class="chip-row environment-choices" role="group" aria-label="Selecionar ambiente">${(state.devices || []).filter(device => device.favorite).map(device =>
  `<button class="chip ${state.selectedDeviceId === device.id ? 'active' : ''}" data-action="selected-device" data-id="${escapeHtml(device.id)}" aria-pressed="${state.selectedDeviceId === device.id}">${escapeHtml(environmentName(device.id))}${device.online ? ' · online' : ''}</button>`).join('')}</div>`;
const time = value => new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' }).format(value);
const dateTime = value => new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }).format(value);
const currentPage = () => {
  const key = location.hash.slice(1);
  return Object.hasOwn(pages, key) ? key : 'inicio';
};
function navItem(page, mobile = false) {
  const [label, symbol] = pages[page];
  const active = currentPage() === page;
  return `<a href="#${page}" class="${mobile ? '' : 'nav-link '}${active ? 'active' : ''}" ${active ? 'aria-current="page"' : ''}>${icon(symbol)}<span>${label}</span></a>`;
}
function pageHead(eyebrow, title, subtitle, action = '') {
  return `<div class="page-head"><div><span class="eyebrow">${eyebrow}</span><h1>${title}</h1><p>${subtitle}</p></div>${action ? `<div class="page-actions">${action}</div>` : ''}</div>`;
}
function pill(label, tone = 'normal') {
  return `<span class="pill ${tone}"><span class="pulse"></span>${escapeHtml(label)}</span>`;
}
function section(title, action = '') {
  return `<div class="section-heading"><h2>${title}</h2>${action}</div>`;
}
function chart(values, compact = false) {
  const arr = [...values].filter(value => value != null).reverse();
  if (arr.length < 2) return '';
  const min = Math.min(...arr), max = Math.max(...arr), range = Math.max(max - min, .5);
  const points = arr.map((value, index) =>
    `${(index * 500 / (arr.length - 1)).toFixed(1)},${(105 - (value - min) * 85 / range).toFixed(1)}`);
  const area = `0,120 ${points.join(' ')} 500,120`;
  return `<svg viewBox="0 0 500 120" preserveAspectRatio="none" role="img" aria-label="Gráfico demonstrativo de tendência">
    ${compact ? '' : '<path class="chart-grid" d="M0 20H500M0 62H500M0 105H500"/>'}
    <polygon class="chart-fill" points="${area}"/><polyline class="chart-path" points="${points.join(' ')}"/>
  </svg>`;
}
function eventIcon(item) {
  return `<span class="event-icon ${item.severity}">${icon(item.resolved ? 'check' : 'alert')}</span>`;
}
function overview() {
  const latest = state.samples[0] || { temperature: null, humidity: null, voltage: null, time: null };
  const live = backend === 'android' && state.mode === 'esp';
  const selected = state.devices?.find(device => device.id === state.selectedDeviceId);
  const status = live ? { label: selected?.online ? 'Online' : 'Sem sinal', tone: selected?.online ? 'normal' : 'offline' } : health(state);
  const relevant = live ? state.events.filter(item => !item.simulated && item.deviceId === state.selectedDeviceId) : state.events.filter(item => item.simulated && (!item.deviceId || item.deviceId === state.selectedDeviceId));
  const rows = relevant.slice(0, 3).map(item => `<div class="event-row">
    ${eventIcon(item)}<div class="event-copy"><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(item.detail)}</small></div>
    <span class="event-time">${time(item.time)}</span></div>`).join('');
  const device = state.deviceHealth;
  return `${pageHead('PAINEL / AMBIENTE', 'Sua central', live ? `${escapeHtml(selected ? environmentName(selected.id) : 'Aguardando ESP32')} · ${escapeHtml(state.selectedDeviceId || '')}` : `${escapeHtml(environmentName(state.selectedDeviceId))} · demonstração fictícia`)}
    ${environmentChoices()}
    <div class="overview-grid">
      <section class="card hero" aria-label="Temperatura atual demonstrativa">
        <div class="hero-top"><span class="kicker">TEMPERATURA AMBIENTE</span>${pill(status.label, status.tone)}</div>
        <div class="hero-number">${decimal(latest.temperature)}<sup>°C</sup></div>
        <div class="hero-caption">${live ? 'Amostra ESP32' : 'Amostra simulada'} · faixa ${decimal(selected?.limits.temperatureMin)}–${decimal(selected?.limits.temperatureMax)} °C</div>
        <div class="hero-chart">${chart(state.samples.slice(0, 18).map(s => s.temperature), true)}</div>
        <div class="hero-bottom flex-between"><span>${live ? 'Última amostra recebida' : 'Última amostra fictícia'}</span><strong>${latest.time ? time(latest.time) : '—'}</strong></div>
      </section>
      <div class="metric-stack">
        ${metricCard('humidity', latest.humidity, `${decimal(selected?.limits.humidityMin)}–${decimal(selected?.limits.humidityMax)}%`)}
        ${metricCard('voltage', latest.voltage, `${decimal(selected?.limits.voltageMin)}–${decimal(selected?.limits.voltageMax)} V`)}
      </div>
    </div>
    <div class="home-quick"><a class="btn secondary" href="#ambientes">Gerenciar ambientes →</a><a class="btn ghost" href="#alertas">Alertas abertos: ${state.events.filter(item => !item.resolved && item.simulated !== live).length}</a></div>
    <div class="below-grid">
      <div>${section('Central e dispositivo')}
        <section class="card card-pad status-list">
          <div class="status-row"><span class="status-dot ${live ? (selected?.online ? '' : 'offline') : state.offline ? 'offline' : state.running ? '' : 'idle'}"></span><div><strong>${live && selected ? escapeHtml(environmentName(selected.id)) : 'ESP32 → celular'}</strong><small>${live ? `MQTT · ${escapeHtml(selected?.id || 'aguardando')}` : 'Conexão local simulada'}</small></div><span class="status-right">${live ? (selected?.online ? 'Online' : 'Sem sinal') : state.offline ? 'Sem sinal (demo)' : state.running ? 'Simulando' : 'Pausado'}</span></div>
          <div class="divider"></div>
          <div class="status-row"><span class="circle-icon">${icon('database')}</span><div><strong>Histórico</strong><small>${backend === 'android' ? 'Persistido no celular' : 'Na memória do navegador'}</small></div><span class="status-right">${state.samples.length} amostras</span></div>
          <div class="integration-mini"><span>MQTT · ${backend === 'android' && state.mqttAvailable ? 'ativo' : 'indisponível'}</span><span>API local · ${backend === 'android' ? 'ativa' : 'prévia'}</span></div>
        </section></div>
      <div>${section('Eventos recentes', '<a class="subtle-link" href="#alertas">Ver todos →</a>')}
        <section class="card card-pad events-list">${rows}</section></div>
    </div>
    ${section('Saúde do celular central', `<span class="tag">${backend === 'android' ? 'MEDIDA PELO APK' : 'DADOS FICTÍCIOS'}</span>`)}
    <section class="card card-pad device-health" aria-label="Saúde do celular central">
      <div><span>Bateria</span><strong>${device.battery == null ? '—' : `${device.battery}%`}</strong></div>
      <div><span>Memória</span><strong>${device.memory == null ? '—' : `${device.memory}%`}</strong></div>
      <div><span>Armazenamento</span><strong>${device.storage == null ? '—' : `${device.storage}%`}</strong></div>
      <div><span>Tempo ligado</span><strong>${escapeHtml(device.uptime)}</strong></div>
    </section>
    <p class="muted-note">${live ? 'Leituras MQTT e eventos são processados no APK.' : 'A prévia usa dados fictícios.'} Saúde do celular medida pelo Android ao acessar o APK.</p>`;
}
function metricCard(key, value, hint) {
  const iconName = key === 'humidity' ? 'drop' : 'bolt';
  return `<section class="card metric-card"><div class="metric-card-top"><span>${METRICS[key].label.toUpperCase()}</span><span class="metric-icon">${icon(iconName)}</span></div>
    <div class="metric-value">${decimal(value)} <span>${METRICS[key].unit}</span></div>
    <div class="metric-card-foot"><span>${hint}</span><span class="tiny-line">${chart(state.samples.slice(0, 12).map(s => s[key]), true)}</span></div></section>`;
}
function historyPage() {
  const samples = state.samples;
  const selected = METRICS[metric];
  const rows = samples.slice(0, 24).map(sample => `<tr><td>${dateTime(sample.time)}</td><td>${selected.label}</td><td>${decimal(sample[metric])} ${selected.unit}</td></tr>`).join('');
  const filters = Object.entries(METRICS).map(([key, value]) =>
    `<button class="chip ${key === metric ? 'active' : ''}" data-action="metric" data-metric="${key}" aria-pressed="${key === metric}">${value.label}</button>`).join('');
  return `${pageHead('LEITURAS / HISTÓRICO', 'Histórico', backend === 'android' ? 'Amostras locais persistidas no APK' : 'Amostras geradas neste navegador')}
    ${environmentChoices()}
    <div class="chip-row" role="group" aria-label="Variável do histórico">${filters}</div>
    ${section('Tendência')}
    <section class="card chart-card"><div class="chart-head"><span class="kicker">${selected.label.toUpperCase()}</span><strong>${decimal(samples[0]?.[metric])} <small>${selected.unit}</small></strong></div>
      <div class="chart-area">${chart(samples.map(sample => sample[metric]))}</div>
      <div class="chart-labels"><span>${samples.length ? dateTime(samples[samples.length - 1].time) : '—'}</span><span>${samples.length ? dateTime(samples[0].time) : '—'}</span></div></section>
    ${section(`Amostras (${samples.length})`)}
    <section class="card card-pad table-wrap"><table class="data-table"><thead><tr><th>HORÁRIO</th><th>VARIÁVEL</th><th>VALOR</th></tr></thead><tbody>${rows}</tbody></table></section>
    <p class="muted-note">${backend === 'android' ? 'Histórico local mantido no celular; até 60 amostras por dispositivo.' : 'Prévia independente com dados fictícios.'}</p>`;
}
function alertCard(item) {
  const label = item.resolved ? 'RESOLVIDO' : 'ABERTO';
  const tone = item.resolved ? 'ok' : item.severity;
  return `<article class="card alert-card ${item.severity}">
    <div class="alert-top">${eventIcon(item)}<div class="event-copy"><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(item.detail)}</small></div></div>
    <div class="alert-meta">${pill(label, tone)}<span class="tag">${item.simulated ? 'DEMO' : escapeHtml(environmentName(item.deviceId))}</span><span class="time">${dateTime(item.time)}</span></div>
    <div class="alert-actions"><button class="btn secondary small" data-action="routes" data-id="${item.id}">Ver entregas</button>
    ${item.resolved ? '' : `<button class="btn ghost small" data-action="resolve" data-id="${item.id}">Marcar resolvido</button>`}</div></article>`;
}
function alertsPage() {
  const filters = [['all', 'Todos'], ['open', 'Abertos'], ['resolved', 'Resolvidos']]
    .map(([key, label]) => `<button class="chip ${alertFilter === key ? 'active' : ''}" data-action="alert-filter" data-filter="${key}" aria-pressed="${alertFilter === key}">${label}</button>`).join('');
  const shown = state.events.filter(item => item.simulated === (state.mode !== 'esp')).filter(item => alertFilter === 'all' || (alertFilter === 'open' ? !item.resolved : item.resolved));
  return `${pageHead('EVENTOS / NOTIFICAÇÕES', 'Alertas', 'Ocorrências e estados de entrega',
    `<a class="btn secondary" href="#equipe">Destinatários →</a>`)}
    ${state.mode === 'demo' ? `<section class="card settings-card"><h2>Testar notificações</h2><p>Envia de verdade aos destinos habilitados. Confira os contatos antes de testar.</p><div class="chip-row">${Object.entries(METRICS).map(([key, value]) => `<button class="btn secondary small" data-action="test-alert" data-metric="${key}">Testar ${value.label}</button>`).join('')}</div></section>` : ''}
    <div class="chip-row" role="group" aria-label="Filtrar alertas">${filters}</div>
    ${section(`Eventos (${shown.length})`)}
    <div class="alert-list">${shown.length ? shown.map(alertCard).join('') : '<div class="card empty">Nenhum evento neste filtro.</div>'}</div>
    <p class="muted-note">${backend === 'android' ? 'Violações e normalizações do demo enviam mensagens reais marcadas SIMULADO, conforme destinos, métricas e canais.' : 'A prévia independente usa somente eventos locais, sem envios externos.'}</p>`;
}
function peoplePage() {
  const cards = state.contacts.map(contact => `<article class="card contact-card">
    <div class="contact-top"><span class="avatar">${escapeHtml(contact.name.slice(0, 1).toLocaleUpperCase('pt-BR'))}</span>
      <div class="contact-name"><strong>${escapeHtml(contact.name)}</strong><small>${escapeHtml(contact.role)}</small></div>
      <label class="toggle"><span class="sr-only">Perfil ativo</span><input type="checkbox" data-action="contact-active" data-id="${contact.id}" ${contact.active ? 'checked' : ''} aria-label="Ativar perfil ${escapeHtml(contact.name)}"></label></div>
    <div class="contact-tags">${contact.metrics.map(m => `<span class="tag">${METRICS[m].label}</span>`).join('') || '<span class="tag">Sem variável</span>'}</div>
    <div class="contact-bottom"><span>${contact.channels.map(c => CHANNELS[c]).join(' · ') || 'Sem canal'}</span>
      <button class="subtle-link" data-action="edit-contact" data-id="${contact.id}">Editar destinos e filtros</button></div></article>`).join('');
  return `${pageHead('DESTINATÁRIOS / PERFIS', 'Equipe', 'Classes de serviço por variável e canal',
    `<button class="btn" data-action="add-contact">${icon('plus')} Novo perfil</button>`)}
    <div class="people-list">${cards}</div>
    <p class="muted-note">Configure destinos e filtros. No APK, violações e normalizações do demo e os testes explícitos podem gerar envios reais.</p>`;
}
function rangeRow(key, label, min, max, unit) {
  return `<div class="range-row"><div class="flex-between"><span>${label}</span><strong id="limit-${key}">${state.limits[key]} ${unit}</strong></div>
    <input type="range" min="${min}" max="${max}" step="1" value="${state.limits[key]}" data-action="limit" data-key="${key}" aria-label="${label}"></div>`;
}
function environmentsPage() {
  const devices = [...(state.devices || [])].sort((a, b) => Number(!!b.favorite) - Number(!!a.favorite) || a.name.localeCompare(b.name));
  return `${pageHead('ESP32 / ESPAÇOS', 'Ambientes', state.mode === 'demo' ? 'Cinco ambientes fictícios · ajuste leituras e limites' : 'Um ESP32 por espaço · personalize limites e fixe favoritos',
    backend === 'android' && state.mode === 'esp' ? '<button class="btn" data-action="add-environment">+ Adicionar</button>' : '')}
    ${state.mode === 'demo' ? `<section class="card settings-card"><h2>Leitura fictícia</h2><form id="demo-reading-form"><label class="form-field">Ambiente<select name="id">${devices.map(device => `<option value="${device.id}" ${device.id === state.selectedDeviceId ? 'selected' : ''}>${escapeHtml(device.name)}</option>`).join('')}</select></label><div class="environment-ranges">${[['temperature', 'Temperatura °C'], ['humidity', 'Umidade %'], ['voltage', 'Tensão V']].map(([key, label]) => `<label class="form-field">${label}<input type="number" name="${key}" step="any" required value="${state.samples[0]?.[key] ?? ''}"></label>`).join('')}</div><button class="btn" type="submit">Aplicar leitura</button></form><p>Violações e normalizações enviam alertas reais identificados como SIMULADO conforme os destinos e canais configurados.</p></section>` : ''}
    <div class="environment-list">${devices.map(device => `<article class="card environment-card">
      <span class="environment-avatar" aria-hidden="true">${environmentIcons[device.icon] || '❄️'}</span>
      <div class="environment-copy"><strong>${escapeHtml(device.name)}</strong><small>${escapeHtml(device.id)} · ${state.mode === 'demo' ? 'demonstração' : device.online ? 'online' : 'aguardando ESP'}</small>
        <small>${decimal(device.limits.temperatureMin)}–${decimal(device.limits.temperatureMax)} °C · ${decimal(device.limits.humidityMin)}–${decimal(device.limits.humidityMax)}% · ${decimal(device.limits.voltageMin)}–${decimal(device.limits.voltageMax)} V</small></div>
      <div class="environment-actions"><button class="btn ghost small ${device.favorite ? 'is-favorite' : ''}" data-action="favorite-environment" data-id="${escapeHtml(device.id)}" aria-pressed="${!!device.favorite}" aria-label="${device.favorite ? 'Desafixar' : 'Fixar'} ${escapeHtml(device.name)} no início">★</button>
      <button class="btn ghost small" data-action="edit-environment" data-id="${escapeHtml(device.id)}">Editar</button></div></article>`).join('') || '<section class="card empty">Nenhum ambiente cadastrado. O ESP32 também aparece aqui após a primeira leitura.</section>'}</div>`;
}
function settingsPage() {
  const config = state.settings || {};
  const channels = Object.entries(CHANNELS).map(([key, label]) =>
    `<div class="settings-line"><span>${label}</span><label class="toggle"><input type="checkbox" data-action="channel" data-channel="${key}" ${state.enabledChannels.includes(key) ? 'checked' : ''} aria-label="Habilitar ${label}"></label></div>`).join('');
  const url = (config.webUrls || []).map(item => `<li>${escapeHtml(item)}</li>`).join('');
  return `${pageHead('CENTRAL / AJUSTES', 'Ajustes', 'Configurações compartilhadas com o aplicativo Android')}
    <div class="settings-grid">
      <section class="card settings-card"><h2>Central e acesso</h2><p>${escapeHtml(config.webStatus || 'Prévia independente')}</p><ul>${url}</ul>
        <p>HTTPS na porta 8792. Confira o certificado SHA-256 nos Ajustes do APK antes do primeiro login. HTTP só permite autenticação em localhost via ADB.</p>
        <p>Permissão para notificações: ${config.notificationsGranted ? 'concedida' : 'pendente'} · bateria sem restrição: ${config.batteryExempt ? 'sim' : 'pendente'}. Conceda permissões do sistema no celular.</p>
        <p>Usuários, senhas e revogação de sessões são gerenciados exclusivamente no aplicativo.</p></section>
      <section class="card settings-card"><h2>ESP32 / MQTT</h2><p>${escapeHtml(config.mqttStatus || 'Sem broker')}</p><p>Porta 1883 · usuário winterhold · host: IP do celular</p><p>Chave MQTT: <code>${escapeHtml(config.mqttSecret || '—')}</code></p></section>
      <section class="card settings-card"><h2>Canais de alerta</h2>${channels}<p>SMS neste celular: ${config.smsGranted ? 'permitido' : 'permissão pendente'}.</p><p>Edite destinos em <a href="#equipe">Equipe</a>.</p></section>
      <section class="card settings-card"><h2>Demonstração</h2><p>Condições simuladas podem enviar SMS, e-mail e WhatsApp reais aos destinatários cadastrados.</p><div class="settings-line"><span>Usar dados fictícios</span><label class="toggle"><input type="checkbox" data-action="mode" ${state.mode === 'demo' ? 'checked' : ''} aria-label="Ativar demonstração"></label></div>
        ${state.mode === 'demo' ? `<div class="settings-line"><span>Atualização automática</span><label class="toggle"><input type="checkbox" data-action="running" ${state.running ? 'checked' : ''}></label></div><div class="settings-line"><span>Simular desconexão</span><label class="toggle"><input type="checkbox" data-action="offline" ${state.offline ? 'checked' : ''}></label></div>` : '<p>Ative para explorar cinco ambientes mesmo sem ESP conectado.</p>'}</section>
    </div>
    <section class="card settings-card"><h2>E-mail e WhatsApp</h2><p>${escapeHtml(config.bridgeRuntime || "")}</p><p>SMTP TLS porta 465 · senha ${config.smtpConfigured ? 'configurada' : 'pendente'}. Ponte Baileys · chave ${config.bridgeConfigured ? 'configurada' : 'pendente'}.</p>
      <form id="delivery-form" autocomplete="off">
        <label class="form-field">Servidor SMTP<input name="smtpHost" value="${escapeHtml(config.smtpHost || '')}" placeholder="smtp.exemplo.com"></label>
        <label class="form-field">Usuário SMTP<input name="smtpUser" value="${escapeHtml(config.smtpUser || '')}"></label>
        <label class="form-field">E-mail remetente<input name="smtpFrom" type="email" value="${escapeHtml(config.smtpFrom || '')}"></label>
        <label class="form-field">Senha SMTP (vazio mantém a atual)<input name="smtpPassword" type="password" autocomplete="new-password"></label>
        <label class="form-field">URL da ponte Node/Baileys<input name="bridgeUrl" type="url" value="${escapeHtml(config.bridgeUrl || '')}" placeholder="https://ponte.exemplo.com"></label>
        <label class="form-field">Chave da ponte (vazio mantém a atual)<input name="bridgeToken" type="password" autocomplete="new-password"></label>
        <button class="btn" type="submit">Salvar integrações</button>
      </form><button class="btn secondary" data-action="bridge-status">Ver conexão e QR do WhatsApp</button><button class="btn ghost" data-action="bridge-relink">Novo pareamento</button><div id="bridge-status" role="status"></div>
      <p>Credenciais são guardadas no Android e não são devolvidas pela API. Use HTTPS na ponte; HTTP só é aceito para 127.0.0.1 no aparelho.</p></section>`;
}

// Keep edited form nodes (including passwords, partial numbers and selection)
// in the page while replacing the surrounding live data. Drafts stay in the DOM.
function updateContent(markup, savedFormId = null) {
  const focused = document.activeElement;
  const forms = [...content.querySelectorAll('form')].filter(form =>
    form.id !== savedFormId && (form.dataset.dirty === 'true' || form.contains(focused)));
  const selection = forms.some(form => form.contains(focused)) && focused.selectionStart != null
    ? [focused.selectionStart, focused.selectionEnd, focused.selectionDirection] : null;
  content.innerHTML = markup;
  for (const form of forms) content.querySelector(`#${form.id}`)?.replaceWith(form);
  if (forms.some(form => form.contains(focused)) && content.contains(focused)) {
    focused.focus({ preventScroll: true });
    if (selection) focused.setSelectionRange(...selection);
  }
}
for (const eventName of ['input', 'change']) {
  document.addEventListener(eventName, event => {
    const form = event.target.form;
    if (form && content.contains(form)) form.dataset.dirty = 'true';
  });
}

function render(savedFormId = null) {
  document.querySelector('#logout-button').hidden = backend !== 'android';
  if (backend === 'loading' || backend === 'error' || backend === 'locked') return;
  const page = currentPage();
  document.querySelector('#side-nav').innerHTML = Object.keys(pages).map(p => navItem(p)).join('');
  document.querySelector('#bottom-nav').innerHTML = mainPages.map(p => navItem(p, true)).join('');
  document.querySelector('#page-heading').textContent = pages[page][0];
  document.querySelector('#header-date').textContent = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
  document.title = `${pages[page][0]} · Winterhold`;
  document.querySelector('.demo-badge').innerHTML = `<span class="pulse"></span>${backend === 'android' ? 'CENTRAL ANDROID' : 'PRÉVIA'}`;
  document.querySelector('.sidebar-note').innerHTML = `<span class="pulse"></span><strong>${backend === 'android' ? (state.mode === 'demo' ? 'DEMONSTRAÇÃO' : 'ESP32 / MQTT') : 'PRÉVIA'}</strong><span>${backend === 'android' && state.mode === 'esp' ? escapeHtml(state.selectedDeviceId ? environmentName(state.selectedDeviceId) : 'Aguardando leituras') : escapeHtml(environmentName(state.selectedDeviceId))}</span>`;
  updateContent(({ inicio: overview, ambientes: environmentsPage, historico: historyPage, alertas: alertsPage, equipe: peoplePage, ajustes: settingsPage })[page](), savedFormId);
  if (dialog) renderDialog();
}
function openDialog(type, id = null) {
  lastFocused = document.activeElement;
  dialog = { type, id };
  renderDialog();
  dialogRoot.querySelector('button, input')?.focus();
}
function closeDialog() {
  dialog = null;
  dialogRoot.innerHTML = '';
  if (lastFocused?.isConnected) lastFocused.focus();
}
function dialogFrame(title, body, actions) {
  dialogRoot.innerHTML = `<div class="dialog-backdrop" data-action="dismiss"><div class="dialog" role="dialog" aria-modal="true" aria-labelledby="dialog-title">
    <div class="dialog-head"><h2 id="dialog-title">${title}</h2><button class="icon-button" data-action="close-dialog" aria-label="Fechar">×</button></div>
    ${body}<div class="dialog-actions">${actions}</div></div></div>`;
}
function renderDialog() {
  if (!dialog) return;
  if (dialog.type === 'simulate') {
    const buttons = Object.entries(METRICS).map(([key, value]) =>
      `<button class="btn secondary" data-action="create-alert" data-metric="${key}">${value.label}</button>`).join(' ');
    dialogFrame('Criar alerta demo', `<p>Selecione a variável. O evento ficará apenas na memória do navegador.</p><div class="chip-row">${buttons}</div>`,
      '<button class="btn ghost" data-action="close-dialog">Cancelar</button>');
  } else if (dialog.type === 'routes') {
    const item = state.events.find(e => e.id === dialog.id);
    if (!item) { closeDialog(); return; }
    const routes = routeAlert(state, item);
    const rows = routes.map(route =>
      `<div class="route-row"><strong>${escapeHtml(route.contact.name)}</strong><small>${route.channels.map(c => `${CHANNELS[c]} · ${escapeHtml(state.deliveries?.find(d => d.eventId === item.id && d.contactId === route.contact.id && d.channel === c)?.destination || 'sem destino')}: ${escapeHtml(state.deliveries?.find(d => d.eventId === item.id && d.contactId === route.contact.id && d.channel === c)?.status || 'aguardando configuração')}`).join(' · ')}</small></div>`).join('');
    dialogFrame('Entregas', `<p>${escapeHtml(item.title)}.</p>${rows || '<div class="empty">Nenhum perfil ativo combina com este evento.</div>'}`,
      '<button class="btn" data-action="close-dialog">Fechar</button>');
  } else if (dialog.type === 'add-contact') {
    dialogFrame('Novo destinatário', `<p>Cadastre o perfil e depois edite telefone e e-mail.</p>
      <label class="form-field">Nome do perfil<input id="new-name" maxlength="60" required autocomplete="off" placeholder="Ex.: Equipe de manutenção"></label>
      <label class="form-field">Função<input id="new-role" maxlength="60" autocomplete="off" placeholder="Ex.: Elétrica"></label>
      <div class="notice">O perfil começa com todas as variáveis e canal SMS. Depois, edite seus filtros.</div>`,
      '<button class="btn ghost" data-action="close-dialog">Cancelar</button><button class="btn" data-action="save-contact">Adicionar</button>');
  } else if (dialog.type === 'edit-contact') {
    const contact = state.contacts.find(c => c.id === dialog.id);
    if (!contact) { closeDialog(); return; }
    const toggles = (group, values, labels) => Object.entries(labels).map(([key, label]) =>
      `<div class="settings-line"><span>${label.label || label}</span><label class="toggle"><input type="checkbox" data-action="contact-item" data-id="${contact.id}" data-group="${group}" data-value="${key}" ${values.includes(key) ? 'checked' : ''} aria-label="${label.label || label}"></label></div>`).join('');
    dialogFrame(`Filtros · ${escapeHtml(contact.name)}`, `<p>Defina telefone em E.164 (+55…) e e-mail, depois selecione os filtros.</p>
      <label class="form-field">Telefone<input id="contact-phone" type="tel" value="${escapeHtml(contact.phone || '')}" placeholder="+5511999999999"></label>
      <label class="form-field">E-mail<input id="contact-email" type="email" value="${escapeHtml(contact.email || '')}"></label>
      <button class="btn secondary small" data-action="save-destination">Salvar destinos</button>
      <span class="kicker">VARIÁVEIS</span>${toggles('metrics', contact.metrics, METRICS)}
      <span class="kicker">CANAIS</span>${toggles('channels', contact.channels, CHANNELS)}
      <p>Desativar todos os canais deixa o perfil sem rota.</p>`,
      '<button class="btn" data-action="close-dialog">Concluir</button>');
  } else if (dialog.type === 'environment') {
    const current = state.devices?.find(device => device.id === dialog.id);
    const limits = current?.limits || { temperatureMin: 18, temperatureMax: 26,
      humidityMin: 30, humidityMax: 70, voltageMin: 198, voltageMax: 242 };
    const numberField = (label, key, min, max) => `<label class="form-field">${label}<input type="number" name="${key}" min="${min}" max="${max}" step="any" required value="${limits[key]}"></label>`;
    dialogFrame(current ? 'Editar ambiente' : 'Novo ambiente', `<form id="environment-form" autocomplete="off">
      <label class="form-field">DEVICE_ID do firmware<input name="id" pattern="[A-Za-z0-9_-]{1,40}" maxlength="40" required value="${escapeHtml(current?.id || '')}" ${current ? 'readonly' : ''}></label>
      <label class="form-field">Nome do ambiente<input name="name" maxlength="60" required value="${escapeHtml(current?.name || '')}" placeholder="Ex.: Laboratório 02"></label>
      <label class="form-field">Ícone<select name="icon">${Object.entries(environmentIcons).map(([key, symbol]) => `<option value="${key}" ${current?.icon === key ? 'selected' : ''}>${symbol} ${key}</option>`).join('')}</select></label>
      <div class="environment-ranges"><strong>Temperatura (°C)</strong>${numberField('Mínimo', 'temperatureMin', -40, 85)}${numberField('Máximo', 'temperatureMax', -40, 85)}
      <strong>Umidade (%)</strong>${numberField('Mínimo', 'humidityMin', 0, 100)}${numberField('Máximo', 'humidityMax', 0, 100)}
      <strong>Tensão (V)</strong>${numberField('Mínimo', 'voltageMin', 0, 300)}${numberField('Máximo', 'voltageMax', 0, 300)}</div>
      <p id="environment-error" class="form-error" role="alert"></p></form>`,
      '<button class="btn ghost" data-action="close-dialog">Cancelar</button><button class="btn" data-action="save-environment">Salvar ambiente</button>');
  }
}

document.addEventListener('click', async e => {
  const target = e.target.closest('[data-action]');
  if (!target) return;
  const action = target.dataset.action;
  if (action === 'mode' && target.checked && !window.confirm('O demo pode enviar SMS, e-mail e WhatsApp reais aos destinatários cadastrados. Ativar e permitir alertas simulados?')) { target.checked = false; return; }
  if (action === 'dismiss' && e.target === target) { closeDialog(); return; }
  if (action === 'bridge-relink') {
    if (!window.confirm('Desconectar a sessão deste APK e gerar um novo QR?')) return;
    const response = await fetch('/api/v1/bridge', { method: 'POST', credentials: 'same-origin', headers: { 'X-Winterhold-Request': '1' } });
    connectionBanner.hidden = false;
    connectionBanner.textContent = response.ok ? 'Novo pareamento solicitado. Consulte o estado/QR.' : 'Não foi possível solicitar novo pareamento.';
    return;
  }
  if (action === 'bridge-status') {
    const status = document.querySelector('#bridge-status');
    if (backend !== 'android') { status.textContent = 'Conexão disponível apenas com o APK.'; return; }
    try {
      const response = await fetch('/api/v1/bridge', { credentials: 'same-origin', cache: 'no-store' });
      if (!response.ok) throw new Error((await response.json()).error || `HTTP ${response.status}`);
      const value = await response.json();
      status.innerHTML = `<p>WhatsApp: ${escapeHtml(value.connection)}</p>${value.qr?.startsWith('data:image/png;base64,') ? `<img src="${value.qr}" width="220" height="220" alt="QR para vincular WhatsApp">` : ''}`;
    } catch (error) { status.textContent = `Ponte indisponível: ${error.message}`; }
    return;
  }
  if (action === 'close-dialog') { closeDialog(); return; }
  if (action === 'simulate') { openDialog('simulate'); return; }
  if (action === 'routes') { openDialog('routes', target.dataset.id); return; }
  if (action === 'add-contact') { openDialog('add-contact'); return; }
  if (action === 'edit-contact') { openDialog('edit-contact', target.dataset.id); return; }
  if (action === 'save-destination') {
    const phoneInput = dialogRoot.querySelector('#contact-phone');
    const emailInput = dialogRoot.querySelector('#contact-email');
    const phone = phoneInput.value.trim(), email = emailInput.value.trim();
    if (phone && !/^\+[1-9][0-9]{7,14}$/.test(phone)) { phoneInput.setCustomValidity('Use o formato internacional +55…'); phoneInput.reportValidity(); return; }
    if (email && !emailInput.checkValidity()) { emailInput.reportValidity(); return; }
    if (await sendAction({ type: 'contactDestination', id: dialog.id, phone, email }, () => { Object.assign(state.contacts.find(c => c.id === dialog.id), { phone, email }); })) closeDialog();
    return;
  }
  if (action === 'add-environment') { openDialog('environment'); return; }
  if (action === 'favorite-environment') {
    const device = state.devices?.find(item => item.id === target.dataset.id);
    if (device) await sendAction({ type: 'favoriteEnvironment', id: device.id, value: !device.favorite }, () => { device.favorite = !device.favorite; });
    return;
  }
  if (action === 'edit-environment') { openDialog('environment', target.dataset.id); return; }
  if (action === 'save-environment') {
    const form = dialogRoot.querySelector('#environment-form');
    if (!form.reportValidity()) return;
    const values = new FormData(form);
    const keys = ['temperatureMin', 'temperatureMax', 'humidityMin', 'humidityMax', 'voltageMin', 'voltageMax'];
    const limits = Object.fromEntries(keys.map(key => [key, Number(values.get(key))]));
    const error = dialogRoot.querySelector('#environment-error');
    if (['temperature', 'humidity', 'voltage'].some(key => limits[`${key}Min`] >= limits[`${key}Max`])) {
      error.textContent = 'O mínimo deve ser menor que o máximo em cada variável.';
      return;
    }
    const environment = { id: String(values.get('id')), name: String(values.get('name')).trim(), icon: String(values.get('icon')), limits };
    if (await sendAction({ type: 'saveEnvironment', environment }, () => {})) { closeDialog(); render(); }
    else error.textContent = 'Não foi possível salvar. Confira os campos e a conexão com o APK.';
    return;
  }
  if (action === 'metric') { metric = target.dataset.metric; render(); return; }
  if (action === 'selected-device') {
    await sendAction({ type: 'selectedDevice', value: target.dataset.id }, () => selectDemoDevice(state, target.dataset.id));
    return;
  }
  if (action === 'alert-filter') { alertFilter = target.dataset.filter; render(); return; }
  if (action === 'test-alert') {
    if (backend !== 'android') { connectionBanner.hidden = false; connectionBanner.textContent = 'Testes de envio exigem o APK e destinos configurados.'; return; }
    await sendAction({ type: 'testAlert', metric: target.dataset.metric }, () => {});
    return;
  }
  if (action === 'create-alert') {
    if (await sendAction({ type: 'simulateAlert', metric: target.dataset.metric },
      () => simulateAlert(state, target.dataset.metric))) {
      closeDialog(); render(); location.hash = 'alertas';
    }
    return;
  }
  if (action === 'resolve') {
    await sendAction({ type: 'resolveAlert', id: target.dataset.id },
      () => resolveAlert(state, target.dataset.id));
    return;
  }
  if (action === 'save-contact') {
    try {
      const name = dialogRoot.querySelector('#new-name').value;
      const role = dialogRoot.querySelector('#new-role').value;
      if (!name.trim()) throw new Error('Informe um nome.');
      if (await sendAction({ type: 'addContact', name, role }, () => addContact(state, name, role))) {
        closeDialog(); render();
      }
    } catch (error) {
      dialogRoot.querySelector('#new-name').setCustomValidity(error.message);
      dialogRoot.querySelector('#new-name').reportValidity();
    }
  }
});
document.addEventListener('change', async e => {
  const target = e.target;
  const action = target.dataset.action;
  if (action === 'mode' && target.checked && !window.confirm('O demo pode enviar SMS, e-mail e WhatsApp reais aos destinatários cadastrados. Ativar e permitir alertas simulados?')) { target.checked = false; return; }
  if (backend === 'android') {
    const payload = ({
      running: () => ({ type: 'running', value: target.checked }),
      mode: () => ({ type: 'mode', value: target.checked ? 'demo' : 'esp', acknowledgeRealDelivery: target.checked }),
      offline: () => ({ type: 'offline', value: target.checked }),
      channel: () => ({ type: 'channel', channel: target.dataset.channel, value: target.checked }),
      'contact-active': () => ({ type: 'contactActive', id: target.dataset.id, value: target.checked }),
      'contact-item': () => ({ type: 'contactItem', id: target.dataset.id, group: target.dataset.group, value: target.dataset.value }),
      limit: () => ({ type: 'limit', key: target.dataset.key, value: Number(target.value) })
    })[action]?.();
    if (payload) { await sendAction(payload, () => {}); render(); }
    return;
  }
  if (action === 'mode') { state.mode = target.checked ? 'demo' : 'esp'; state.running = target.checked; }
  else if (action === 'running') state.running = target.checked;
  else if (action === 'offline') setOffline(state, target.checked);
  else if (action === 'channel') {
    state.enabledChannels = target.checked
      ? [...new Set([...state.enabledChannels, target.dataset.channel])]
      : state.enabledChannels.filter(c => c !== target.dataset.channel);
  } else if (action === 'contact-active') {
    const contact = state.contacts.find(c => c.id === target.dataset.id);
    if (contact) contact.active = target.checked;
  } else if (action === 'contact-item') {
    toggleContactItem(state, target.dataset.id, target.dataset.group, target.dataset.value);
  } else if (action === 'limit') {
    const key = target.dataset.key;
    if (Object.hasOwn(state.limits, key)) state.limits[key] = Number(target.value);
  } else return;
  render();
});
document.addEventListener('keydown', e => {
  if (e.key === 'Escape' && dialog) closeDialog();
  if (e.key === 'Enter' && dialog?.type === 'add-contact' && e.target.matches('#new-name, #new-role')) {
    e.preventDefault();
    dialogRoot.querySelector('[data-action="save-contact"]').click();
  }
});
window.addEventListener('hashchange', () => {
  closeDialog();
  render();
  window.scrollTo({ top: 0, behavior: 'auto' });
  content.focus({ preventScroll: true });
});
setInterval(() => {
  if (backend === 'android') refreshFromApk();
  else if (backend === 'preview' && advance(state) && !dialog) render();
  else if (backend === 'error') boot();
}, 5_000);
boot();

document.addEventListener('submit', async event => {
  if (event.target.id !== 'unlock-form') return;
  event.preventDefault();
  const fields = new FormData(event.target);
  try {
    const response = await fetch('/api/v1/login', { method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-Winterhold-Request': '1' },
      body: JSON.stringify({ username: String(fields.get('username')).trim(), password: String(fields.get('password')) }) });
    event.target.reset();
    if (!response.ok) throw new Error((await response.json()).error || 'Falha no login');
    await refreshFromApk();
  } catch (error) { connectionBanner.hidden = false; connectionBanner.textContent = error.message; }
});

document.addEventListener('submit', async event => {
  if (event.target.id === 'demo-reading-form') {
    event.preventDefault();
    const fields = new FormData(event.target);
    const id = String(fields.get('id'));
    const [temperature, humidity, voltage] = ['temperature', 'humidity', 'voltage'].map(key => Number(fields.get(key)));
    if (await sendAction({ type: 'setDemoReading', id, temperature, humidity, voltage },
      () => setDemoReading(state, id, temperature, humidity, voltage), event.target.id)) {
      await sendAction({ type: 'selectedDevice', value: id }, () => selectDemoDevice(state, id));
    }
  }
  if (event.target.id === 'delivery-form') {
    event.preventDefault();
    const fields = new FormData(event.target);
    const payload = { type: 'deliverySettings', ...Object.fromEntries(fields.entries()) };
    if (await sendAction(payload, () => {
      Object.assign(state.settings, { smtpHost: payload.smtpHost, smtpUser: payload.smtpUser,
        smtpFrom: payload.smtpFrom, bridgeUrl: payload.bridgeUrl });
    }, event.target.id)) { connectionBanner.hidden = false; connectionBanner.textContent = 'Configurações salvas no celular.'; }
  }
});

document.querySelector('#logout-button').addEventListener('click', async () => {
  try {
    const response = await fetch('/api/v1/logout', { method: 'POST', credentials: 'same-origin', headers: { 'X-Winterhold-Request': '1' } });
    if (!response.ok) throw new Error('Não foi possível encerrar a sessão');
    showUnlock(); document.querySelector('#logout-button').hidden = true;
  } catch (error) { connectionBanner.hidden = false; connectionBanner.textContent = error.message; }
});

import {
  METRICS, CHANNELS, createDemoState, advance, simulateAlert,
  setOffline, resolveAlert, addContact, toggleContactItem, routeAlert, health
} from './demo.js';

const state = createDemoState();
const pages = {
  inicio: ['Visão geral', 'home'],
  historico: ['Histórico', 'history'],
  alertas: ['Alertas', 'bell'],
  equipe: ['Equipe', 'users'],
  ambiente: ['Ambiente', 'thermometer'],
  infraestrutura: ['Infraestrutura', 'radio'],
  automacoes: ['Automações', 'bolt'],
  ajustes: ['Ajustes', 'settings']
};
const mainPages = ['inicio', 'ambiente', 'infraestrutura', 'alertas'];
let metric = 'temperature';
let alertFilter = 'all';
let dialog = null;
let lastFocused = null;
const content = document.querySelector('#app-content');
const dialogRoot = document.querySelector('#dialog-root');

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
const decimal = value => new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value);
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
  if (values.length < 2) return '';
  const arr = [...values].reverse();
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
  const latest = state.samples[0];
  const status = health(state);
  const rows = state.events.slice(0, 3).map(item => `<div class="event-row">
    ${eventIcon(item)}<div class="event-copy"><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(item.detail)}</small></div>
    <span class="event-time">${time(item.time)}</span></div>`).join('');
  return `${pageHead('PAINEL / AMBIENTE', 'Visão geral', 'Sala 01 · Ambiente climatizado · leituras fictícias')}
    <div class="overview-grid">
      <section class="card hero" aria-label="Temperatura atual demonstrativa">
        <div class="hero-top"><span class="kicker">TEMPERATURA AMBIENTE</span>${pill(status.label, status.tone)}</div>
        <div class="hero-number">${decimal(latest.temperature)}<sup>°C</sup></div>
        <div class="hero-caption">Amostra simulada · limite de exemplo ${state.limits.temperature} °C</div>
        <div class="hero-chart">${chart(state.samples.slice(0, 18).map(s => s.temperature), true)}</div>
        <div class="hero-bottom flex-between"><span>Última amostra fictícia</span><strong>${time(latest.time)}</strong></div>
      </section>
      <div class="metric-stack">
        ${metricCard('humidity', latest.humidity, `Limite ${state.limits.humidity}%`)}
        ${metricCard('voltage', latest.voltage, `Nominal ${state.limits.nominalVoltage} V`)}
      </div>
    </div>
    <div class="below-grid">
      <div>${section('Central e dispositivo')}
        <section class="card card-pad status-list">
          <div class="status-row"><span class="status-dot ${state.offline ? 'offline' : state.running ? '' : 'idle'}"></span><div><strong>ESP32 → celular</strong><small>Conexão local simulada</small></div><span class="status-right">${state.offline ? 'Sem sinal (demo)' : state.running ? 'Simulando' : 'Pausado'}</span></div>
          <div class="divider"></div>
          <div class="status-row"><span class="circle-icon">${icon('database')}</span><div><strong>Histórico</strong><small>Somente na memória do navegador</small></div><span class="status-right">${state.samples.length} amostras</span></div>
          <div class="integration-mini"><span>MQTT · futuro</span><span>API · futuro</span><span>SQLite · futuro</span></div>
        </section></div>
      <div>${section('Eventos recentes', '<a class="subtle-link" href="#alertas">Ver todos →</a>')}
        <section class="card card-pad events-list">${rows}</section></div>
    </div>
    <div class="quick-links"><a class="quick-link" href="#ambiente">${icon('thermometer')} Ambiente e sensores →</a><a class="quick-link" href="#infraestrutura">${icon('radio')} Homelab e serviços →</a><a class="quick-link" href="#automacoes">${icon('bolt')} Regras e automações →</a></div>
    <p class="muted-note">A central MQTT, o envio de mensagens e o comando do ar-condicionado não estão conectados nesta demonstração.</p>`;
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
  return `${pageHead('LEITURAS / HISTÓRICO', 'Histórico', 'Amostras geradas neste navegador · não persistidas')}
    <div class="chip-row" role="group" aria-label="Variável do histórico">${filters}</div>
    ${section('Tendência demonstrativa')}
    <section class="card chart-card"><div class="chart-head"><span class="kicker">${selected.label.toUpperCase()}</span><strong>${decimal(samples[0][metric])} <small>${selected.unit}</small></strong></div>
      <div class="chart-area">${chart(samples.map(sample => sample[metric]))}</div>
      <div class="chart-labels"><span>${dateTime(samples[samples.length - 1].time)}</span><span>${dateTime(samples[0].time)}</span></div></section>
    ${section(`Amostras (${samples.length})`)}
    <section class="card card-pad table-wrap"><table class="data-table"><thead><tr><th>HORÁRIO</th><th>VARIÁVEL</th><th>VALOR</th></tr></thead><tbody>${rows}</tbody></table></section>
    <p class="muted-note">Esta tela ainda não consulta SQLite. O histórico começa com 36 amostras fictícias e recebe novos valores a cada cinco segundos enquanto a simulação estiver ativa.</p>`;
}
function alertCard(item) {
  const label = item.resolved ? 'RESOLVIDO' : 'ABERTO';
  const tone = item.resolved ? 'ok' : item.severity;
  return `<article class="card alert-card ${item.severity}">
    <div class="alert-top">${eventIcon(item)}<div class="event-copy"><strong>${escapeHtml(item.title)}</strong><small>${escapeHtml(item.detail)}</small></div></div>
    <div class="alert-meta">${pill(label, tone)}<span class="tag">DEMO</span><span class="time">${dateTime(item.time)}</span></div>
    <div class="alert-actions"><button class="btn secondary small" data-action="routes" data-id="${item.id}">Ver roteamento</button>
    ${item.resolved ? '' : `<button class="btn ghost small" data-action="resolve" data-id="${item.id}">Marcar resolvido</button>`}</div></article>`;
}
function alertsPage() {
  const filters = [['all', 'Todos'], ['open', 'Abertos'], ['resolved', 'Resolvidos']]
    .map(([key, label]) => `<button class="chip ${alertFilter === key ? 'active' : ''}" data-action="alert-filter" data-filter="${key}" aria-pressed="${alertFilter === key}">${label}</button>`).join('');
  const shown = state.events.filter(item => alertFilter === 'all' || (alertFilter === 'open' ? !item.resolved : item.resolved));
  return `${pageHead('EVENTOS / NOTIFICAÇÕES', 'Alertas', 'Eventos e rotas simulados; nenhum canal envia mensagens',
    `<button class="btn" data-action="simulate">${icon('plus')} Simular alerta</button>`)}
    <div class="chip-row" role="group" aria-label="Filtrar alertas">${filters}</div>
    ${section(`Eventos (${shown.length})`)}
    <div class="alert-list">${shown.length ? shown.map(alertCard).join('') : '<div class="card empty">Nenhum evento neste filtro.</div>'}</div>
    <p class="muted-note">SMS, e-mail e WhatsApp aqui representam somente a decisão de roteamento. A entrega real será implementada e validada em outra fase.</p>`;
}
function peoplePage() {
  const cards = state.contacts.map(contact => `<article class="card contact-card">
    <div class="contact-top"><span class="avatar">${escapeHtml(contact.name.slice(0, 1).toLocaleUpperCase('pt-BR'))}</span>
      <div class="contact-name"><strong>${escapeHtml(contact.name)}</strong><small>${escapeHtml(contact.role)}</small></div>
      <label class="toggle"><span class="sr-only">Perfil ativo</span><input type="checkbox" data-action="contact-active" data-id="${contact.id}" ${contact.active ? 'checked' : ''} aria-label="Ativar perfil ${escapeHtml(contact.name)}"></label></div>
    <div class="contact-tags">${contact.metrics.map(m => `<span class="tag">${METRICS[m].label}</span>`).join('') || '<span class="tag">Sem variável</span>'}</div>
    <div class="contact-bottom"><span>${contact.channels.map(c => CHANNELS[c]).join(' · ') || 'Sem canal'}</span>
      <button class="subtle-link" data-action="edit-contact" data-id="${contact.id}">Editar filtros</button></div></article>`).join('');
  return `${pageHead('DESTINATÁRIOS / PERFIS', 'Equipe', 'Classes de serviço por variável e canal',
    `<button class="btn" data-action="add-contact">${icon('plus')} Novo perfil</button>`)}
    <div class="people-list">${cards}</div>
    <p class="muted-note">Perfis fictícios sem números ou endereços. Alterações valem apenas enquanto esta página permanece aberta.</p>`;
}
function rangeRow(key, label, min, max, unit) {
  return `<div class="range-row"><div class="flex-between"><span>${label}</span><strong id="limit-${key}">${state.limits[key]} ${unit}</strong></div>
    <input type="range" min="${min}" max="${max}" step="1" value="${state.limits[key]}" data-action="limit" data-key="${key}" aria-label="${label}"></div>`;
}
function settingsPage() {
  const low = Math.round(state.limits.nominalVoltage * (1 - state.limits.tolerance / 100));
  const high = Math.round(state.limits.nominalVoltage * (1 + state.limits.tolerance / 100));
  const channels = Object.entries(CHANNELS).map(([key, label]) =>
    `<div class="settings-line"><span>${label}</span><label class="toggle"><input type="checkbox" data-action="channel" data-channel="${key}" ${state.enabledChannels.includes(key) ? 'checked' : ''} aria-label="Incluir ${label} na prévia de roteamento"></label></div>`).join('');
  const integrations = [
    ['radio', 'ESP32 / MQTT', 'Celular como cliente MQTT e central de processamento.'],
    ['database', 'Histórico / API', 'SQLite no celular e API local para a interface web.'],
    ['message', 'SMS', 'Envio após validar modem, permissões e entrega.'],
    ['mail', 'E-mail', 'Provedor autenticado, fila e confirmação.'],
    ['message', 'WhatsApp / Telegram', 'Entrega de alertas após configurar credenciais e políticas de cada canal.'],
    ['bell', 'Notificação Android', 'Avisos locais gerados pela central no celular.'],
    ['bolt', 'Ar-condicionado / IR', 'Comandos após validar emissor, receptor e retorno de estado.']
  ].map(([symbol, title, detail]) =>
    `<article class="card integration-card"><span class="metric-icon">${icon(symbol)}</span><h3>${title}</h3><p>${detail}</p><span class="tag">PLANEJADO</span></article>`).join('');
  return `${pageHead('CENTRAL / CONFIGURAÇÃO', 'Ajustes', 'Controles locais para explorar o protótipo')}
    <div class="settings-grid">
      <section class="card settings-card"><h2>Simulação</h2><p>Teste os estados do painel sem nenhum dispositivo.</p>
        <div class="settings-line"><span>Gerar novas leituras</span><label class="toggle"><input type="checkbox" data-action="running" ${state.running ? 'checked' : ''} aria-label="Gerar novas leituras"></label></div>
        <div class="settings-line"><span>ESP32 sem comunicação</span><label class="toggle"><input type="checkbox" data-action="offline" ${state.offline ? 'checked' : ''} aria-label="Simular dispositivo desconectado"></label></div>
        <p class="muted-note">A desconexão cria um evento fictício e pausa as novas amostras até a reconexão.</p></section>
      <section class="card settings-card"><h2>Canais na prévia</h2><p>Filtros visuais por perfil; nada será enviado.</p>${channels}</section>
    </div>
    ${section('Limiares de exemplo')}
    <section class="card settings-card">
      ${rangeRow('temperature', 'Temperatura máxima', 20, 35, '°C')}
      ${rangeRow('humidity', 'Umidade máxima', 40, 90, '%')}
      ${rangeRow('nominalVoltage', 'Tensão nominal', 100, 250, 'V')}
      ${rangeRow('tolerance', 'Tolerância da tensão', 2, 20, '%')}
      <div class="notice">Faixa ilustrativa: <strong id="voltage-range">${low}–${high} V</strong>. Alterar limiares não avalia amostras antigas nem cria alertas automáticos nesta demo.</div>
    </section>
    ${section('Integrações futuras')}
    <div class="integration-grid">${integrations}</div>
    <p class="muted-note">O navegador não acessará o broker MQTT diretamente. O celular receberá as mensagens dos ESPs, processará as regras e servirá dados à web por uma API autenticada.</p>`;
}
function infoTile(label, value, note) {
  return `<div class="card info-tile"><span class="kicker">${label}</span><strong>${value}</strong><small>${note}</small></div>`;
}
function environmentPage() {
  const room = state.environment;
  const devices = state.infrastructure.devices.map(device =>
    `<div class="device-row"><span class="status-dot ${state.offline && device.name.includes('Sala') ? 'offline' : ''}"></span><div><strong>${escapeHtml(device.name)}</strong><small>${escapeHtml(device.kind)}</small></div><b>${state.offline && device.name.includes('Sala') ? 'Sem sinal' : escapeHtml(device.value)}</b></div>`).join('');
  return `${pageHead('IOT / SALA 01', 'Ambiente', 'Mapa visual dos sensores e atuadores · valores fictícios')}
    <div class="info-grid">
      ${infoTile('TEMPERATURA', `${decimal(state.samples[0].temperature)} °C`, 'DHT22 · amostra demo')}
      ${infoTile('UMIDADE', `${decimal(state.samples[0].humidity)} %`, 'DHT22 · amostra demo')}
      ${infoTile('LUMINOSIDADE', `${room.light} %`, 'Sensor planejado')}
      ${infoTile('PRÓXIMA AULA', room.nextClass, 'Agenda ilustrativa')}
    </div>
    ${section('Estado da sala')}
    <div class="state-grid">
      <div class="card state-card">${icon('users')}<div><span>Presença / movimento</span><strong>${room.presence ? 'Detectada' : 'Ausente'}</strong><small>PIR / mmWave planejado</small></div></div>
      <div class="card state-card">${icon('home')}<div><span>Porta</span><strong>${room.doorOpen ? 'Aberta' : 'Fechada'}</strong><small>Contato magnético planejado</small></div></div>
      <div class="card state-card">${icon('thermometer')}<div><span>Ar-condicionado</span><strong>${room.acOn ? 'Ligado' : 'Desligado'} · ${room.setpoint} °C</strong><small>Estado lógico ilustrativo · IR futuro</small></div></div>
      <div class="card state-card">${icon('bolt')}<div><span>Relé</span><strong>${room.relayOn ? 'Ativado' : 'Desativado'}</strong><small>Atuador ilustrativo</small></div></div>
    </div>
    ${section('Dispositivos')}
    <section class="card card-pad device-list">${devices}</section>
    <p class="muted-note">Nenhum botão nesta página envia comando ao ESP ou ao ar-condicionado. Agenda, presença, porta, IR e relé são apenas a representação do planejamento.</p>`;
}
function infrastructurePage() {
  const infra = state.infrastructure;
  const services = infra.services.map(service =>
    `<div class="device-row"><span class="status-dot"></span><div><strong>${escapeHtml(service.name)}</strong><small>${escapeHtml(service.detail)}</small></div><b>Simulado</b></div>`).join('');
  return `${pageHead('HOMELAB / CENTRAL', 'Infraestrutura', 'Saúde do celular e dos serviços · telemetria fictícia')}
    <div class="info-grid">
      ${infoTile('UPTIME', infra.uptime, 'Central Android / Termux')}
      ${infoTile('CPU', `${infra.cpu}%`, 'Uso de exemplo')}
      ${infoTile('RAM', `${infra.ram}%`, 'Uso de exemplo')}
      ${infoTile('BATERIA', `${infra.battery}%`, 'Fonte de energia')}
      ${infoTile('ARMAZENAMENTO', `${infra.storage}%`, 'Uso de exemplo')}
      ${infoTile('LATÊNCIA', `${infra.latency} ms`, 'Internet · exemplo')}
    </div>
    <div class="settings-grid">
      <section class="card settings-card"><h2>Energia e rede</h2>
        <div class="settings-line"><span>Energia AC</span>${pill(infra.power ? 'PRESENTE' : 'AUSENTE', infra.power ? 'normal' : 'warning')}</div>
        <div class="settings-line"><span>Internet</span>${pill(infra.internet ? 'CONECTADA' : 'OFFLINE', infra.internet ? 'normal' : 'warning')}</div>
        <p>Queda/restauração, bateria baixa e duração da interrupção farão parte do registro de incidentes.</p></section>
      <section class="card settings-card"><h2>Caminho dos dados</h2>
        <div class="data-flow"><span>ESP / Pico</span><span>MQTT</span><span>Celular</span><span>API web</span></div>
        <p>O celular centralizará regras, banco e alertas. Estes indicadores ainda não consultam a central.</p></section>
    </div>
    ${section('Serviços monitorados')}
    <section class="card card-pad device-list">${services}</section>
    <p class="muted-note">Estados, uptime e latência são valores fixos de demonstração. Nenhuma sondagem de serviço ou comando remoto é executado.</p>`;
}
function automationsPage() {
  const cards = state.automations.map((rule, index) =>
    `<article class="card rule-card"><span class="circle-icon">${icon('bolt')}</span><div><h3>${escapeHtml(rule.name)}</h3><p>${escapeHtml(rule.detail)}</p></div><span class="tag">${rule.enabled ? 'CENÁRIO DEMO' : 'PLANEJADO'}</span></article>`).join('');
  return `${pageHead('REGRAS / CENÁRIOS', 'Automações', 'Fluxos previstos para a central Android · nenhuma regra em execução')}
    <div class="rule-list">${cards}</div>
    ${section('Eventos previstos')}
    <section class="card settings-card"><p>Presença, agenda, temperatura, porta, energia e disponibilidade dos serviços serão entradas das regras. O celular registrará transições para evitar alertas repetidos e oferecerá comandos autorizados após a integração.</p></section>
    <p class="muted-note">Wake-on-LAN, watchdog, reinício de serviços e comandos IR estão apenas no plano visual. A demo não controla equipamentos.</p>`;
}
function render() {
  const page = currentPage();
  document.querySelector('#side-nav').innerHTML = Object.keys(pages).map(p => navItem(p)).join('');
  document.querySelector('#bottom-nav').innerHTML = mainPages.map(p => navItem(p, true)).join('');
  document.querySelector('#page-heading').textContent = pages[page][0];
  document.querySelector('#header-date').textContent = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
  document.title = `${pages[page][0]} · Winterhold demo`;
  content.innerHTML = ({ inicio: overview, historico: historyPage, alertas: alertsPage, equipe: peoplePage, ambiente: environmentPage, infraestrutura: infrastructurePage, automacoes: automationsPage, ajustes: settingsPage })[page]();
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
      `<div class="route-row"><strong>${escapeHtml(route.contact.name)}</strong><small>${route.channels.map(c => CHANNELS[c]).join(' · ')}</small></div>`).join('');
    dialogFrame('Roteamento simulado', `<p>${escapeHtml(item.title)}. Nenhuma mensagem foi enviada.</p>${rows || '<div class="empty">Nenhum perfil ativo combina com este evento.</div>'}`,
      '<button class="btn" data-action="close-dialog">Fechar</button>');
  } else if (dialog.type === 'add-contact') {
    dialogFrame('Novo perfil demo', `<p>Sem telefone ou e-mail nesta fase.</p>
      <label class="form-field">Nome do perfil<input id="new-name" maxlength="60" required autocomplete="off" placeholder="Ex.: Equipe de manutenção"></label>
      <label class="form-field">Função<input id="new-role" maxlength="60" autocomplete="off" placeholder="Ex.: Elétrica"></label>
      <div class="notice">O perfil começa com todas as variáveis e canal SMS. Depois, edite seus filtros.</div>`,
      '<button class="btn ghost" data-action="close-dialog">Cancelar</button><button class="btn" data-action="save-contact">Adicionar</button>');
  } else if (dialog.type === 'edit-contact') {
    const contact = state.contacts.find(c => c.id === dialog.id);
    if (!contact) { closeDialog(); return; }
    const toggles = (group, values, labels) => Object.entries(labels).map(([key, label]) =>
      `<div class="settings-line"><span>${label.label || label}</span><label class="toggle"><input type="checkbox" data-action="contact-item" data-id="${contact.id}" data-group="${group}" data-value="${key}" ${values.includes(key) ? 'checked' : ''} aria-label="${label.label || label}"></label></div>`).join('');
    dialogFrame(`Filtros · ${escapeHtml(contact.name)}`, `<p>Escolha as variáveis e os canais para esta classe de serviço.</p>
      <span class="kicker">VARIÁVEIS</span>${toggles('metrics', contact.metrics, METRICS)}
      <span class="kicker">CANAIS</span>${toggles('channels', contact.channels, CHANNELS)}
      <p>As chaves modificam apenas a prévia. Desativar todos os canais deixa o perfil sem rota.</p>`,
      '<button class="btn" data-action="close-dialog">Concluir</button>');
  }
}

document.addEventListener('click', e => {
  const target = e.target.closest('[data-action]');
  if (!target) return;
  const action = target.dataset.action;
  if (action === 'dismiss' && e.target === target) { closeDialog(); return; }
  if (action === 'close-dialog') { closeDialog(); return; }
  if (action === 'simulate') { openDialog('simulate'); return; }
  if (action === 'routes') { openDialog('routes', target.dataset.id); return; }
  if (action === 'add-contact') { openDialog('add-contact'); return; }
  if (action === 'edit-contact') { openDialog('edit-contact', target.dataset.id); return; }
  if (action === 'metric') { metric = target.dataset.metric; render(); return; }
  if (action === 'alert-filter') { alertFilter = target.dataset.filter; render(); return; }
  if (action === 'create-alert') { simulateAlert(state, target.dataset.metric); closeDialog(); render(); location.hash = 'alertas'; return; }
  if (action === 'resolve') { resolveAlert(state, target.dataset.id); render(); return; }
  if (action === 'save-contact') {
    try {
      addContact(state, dialogRoot.querySelector('#new-name').value, dialogRoot.querySelector('#new-role').value);
      closeDialog(); render();
    } catch (error) {
      dialogRoot.querySelector('#new-name').setCustomValidity(error.message);
      dialogRoot.querySelector('#new-name').reportValidity();
    }
  }
});
document.addEventListener('change', e => {
  const target = e.target;
  const action = target.dataset.action;
  if (action === 'running') state.running = target.checked;
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
setInterval(() => { if (advance(state) && !dialog) render(); }, 5_000);
render();

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import QRCode from 'qrcode';
import makeWASocket, { useMultiFileAuthState, DisconnectReason } from '@whiskeysockets/baileys';
import pino from 'pino';

const token = process.env.WINTERHOLD_BRIDGE_TOKEN;
if (!token || token.length < 32) throw new Error('Defina WINTERHOLD_BRIDGE_TOKEN com ao menos 32 caracteres aleatórios');
const port = Number(process.env.WINTERHOLD_BRIDGE_PORT || 8791);
const host = process.env.WINTERHOLD_BRIDGE_HOST || '127.0.0.1';
const dataDir = path.resolve(process.env.WINTERHOLD_BRIDGE_DATA || './private-session');
fs.mkdirSync(dataDir, { recursive: true, mode: 0o700 });
const ledgerPath = path.join(dataDir, 'reserved.json');
const reserved = new Set(fs.existsSync(ledgerPath) ? JSON.parse(fs.readFileSync(ledgerPath, 'utf8')) : []);
let socket, qr = null, connection = 'starting', reconnectTimer, attempts = 0;
const quiet = pino({ level: 'silent' });

function authenticated(req) {
  const given = req.headers.authorization?.replace(/^Bearer /, '') || '';
  const a = Buffer.from(given), b = Buffer.from(token);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
function reply(res, status, value) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(value));
}
function reserve(id) {
  reserved.add(id);
  const temporary = `${ledgerPath}.tmp`;
  fs.writeFileSync(temporary, JSON.stringify([...reserved]), { mode: 0o600 });
  fs.renameSync(temporary, ledgerPath);
}
async function connect() {
  const { state, saveCreds } = await useMultiFileAuthState(path.join(dataDir, 'auth'));
  socket = makeWASocket({ auth: state, logger: quiet, printQRInTerminal: false,
    markOnlineOnConnect: false, syncFullHistory: false });
  socket.ev.on('creds.update', saveCreds);
  socket.ev.on('connection.update', ({ connection: current, lastDisconnect, qr: nextQr }) => {
    if (nextQr) { qr = nextQr; connection = 'pairing'; console.log('Emparelhe o WhatsApp pelo QR em /status via APK.'); }
    if (current === 'open') { qr = null; connection = 'connected'; attempts = 0; }
    if (current === 'close') {
      qr = null; connection = 'disconnected';
      const code = lastDisconnect?.error?.output?.statusCode;
      clearTimeout(reconnectTimer);
      if (code !== DisconnectReason.loggedOut) reconnectTimer = setTimeout(() => connect().catch(retry), Math.min(60_000, 2000 * 2 ** Math.min(attempts++, 5)));
      else connection = 'logged_out';
    }
  });
}
function retry() {
  connection = 'reconnecting';
  clearTimeout(reconnectTimer);
  reconnectTimer = setTimeout(() => connect().catch(retry), 10_000);
}
connect().catch(retry);
http.createServer(async (req, res) => {
  if (!authenticated(req)) return reply(res, 401, { error: 'Não autorizado' });
  if (req.method === 'POST' && req.url === '/relink') {
    clearTimeout(reconnectTimer);
    socket?.ev.removeAllListeners('connection.update');
    socket?.end(undefined);
    const auth = path.join(dataDir, 'auth');
    if (fs.existsSync(auth)) fs.renameSync(auth, path.join(dataDir, `previous-auth-${Date.now()}`));
    qr = null; connection = 'starting'; attempts = 0;
    connect().catch(retry);
    return reply(res, 202, { status: 'pairing_requested' });
  }
  if (req.method === 'GET' && req.url === '/status') {
    return reply(res, 200, { connection, qr: qr ? await QRCode.toDataURL(qr, { margin: 2 }) : null });
  }
  if (req.method !== 'POST' || req.url !== '/send') return reply(res, 404, { error: 'Rota desconhecida' });
  let body = '';
  try {
    for await (const part of req) {
      body += part;
      if (body.length > 8192) throw new Error('Corpo muito longo');
    }
    const { id, to, message } = JSON.parse(body);
    if (typeof id !== 'string' || !/^[\w:-]{1,180}$/.test(id) || typeof to !== 'string' ||
      !/^\+[1-9][0-9]{7,14}$/.test(to) || typeof message !== 'string' || !message || message.length > 1500)
      return reply(res, 400, { error: 'Mensagem inválida' });
    if (reserved.has(id)) return reply(res, 202, { status: 'already_reserved' });
    if (connection !== 'connected') return reply(res, 503, { error: 'WhatsApp não conectado' });
    reserve(id); // No automatic retry: an interrupted submission is uncertain.
    await socket.sendMessage(`${to.slice(1)}@s.whatsapp.net`, { text: message });
    return reply(res, 202, { status: 'submitted' });
  } catch (error) {
    console.error('Falha de envio; confira a entrega antes de tentar novamente.');
    return reply(res, 503, { error: 'Envio incerto; consulte o telefone antes de repetir' });
  }
}).listen(port, host, () => console.log(`Ponte Baileys em ${host}:${port}`));

process.on('SIGTERM', () => { clearTimeout(reconnectTimer); socket?.end(undefined); process.exit(0); });
// The Android foreground service owns this process, including abrupt parent death.
if (process.env.WINTERHOLD_ANDROID_PARENT) setInterval(() => {
  if (process.ppid !== Number(process.env.WINTERHOLD_ANDROID_PARENT)) process.exit(0);
}, 2000).unref();

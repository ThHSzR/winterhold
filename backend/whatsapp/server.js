import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import QRCode from 'qrcode';
import makeWASocket, { useMultiFileAuthState, DisconnectReason } from '@whiskeysockets/baileys';

const token = process.env.WINTERHOLD_BRIDGE_TOKEN;
if (!token || token.length < 32) throw new Error('Defina WINTERHOLD_BRIDGE_TOKEN com ao menos 32 caracteres aleatórios');
const port = Number(process.env.WINTERHOLD_BRIDGE_PORT || 8791);
const host = process.env.WINTERHOLD_BRIDGE_HOST || '127.0.0.1';
const dataDir = path.resolve(process.env.WINTERHOLD_BRIDGE_DATA || './private-session');
fs.mkdirSync(dataDir, { recursive: true, mode: 0o700 });
const ledgerPath = path.join(dataDir, 'reserved.json');
const reserved = new Set(fs.existsSync(ledgerPath) ? JSON.parse(fs.readFileSync(ledgerPath, 'utf8')) : []);
let socket, qr = null, connection = 'starting', reconnectTimer;

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
  socket = makeWASocket({ auth: state, printQRInTerminal: false });
  socket.ev.on('creds.update', saveCreds);
  socket.ev.on('connection.update', ({ connection: current, lastDisconnect, qr: nextQr }) => {
    if (nextQr) { qr = nextQr; connection = 'pairing'; console.log('Emparelhe o WhatsApp pelo QR em /status via APK.'); }
    if (current === 'open') { qr = null; connection = 'connected'; console.log('WhatsApp conectado.'); }
    if (current === 'close') {
      qr = null; connection = 'disconnected';
      const code = lastDisconnect?.error?.output?.statusCode;
      if (code !== DisconnectReason.loggedOut) reconnectTimer = setTimeout(() => connect().catch(console.error), 5000);
      else console.error('Sessão encerrada; remova private-session/auth e emparelhe novamente.');
    }
  });
}
await connect();
http.createServer(async (req, res) => {
  if (!authenticated(req)) return reply(res, 401, { error: 'Não autorizado' });
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
    console.error('Falha de envio:', error);
    return reply(res, 503, { error: 'Envio incerto; consulte o telefone antes de repetir' });
  }
}).listen(port, host, () => console.log(`Ponte Baileys em ${host}:${port}`));

process.on('SIGTERM', () => { clearTimeout(reconnectTimer); socket?.end(undefined); process.exit(0); });

import makeWASocket, { DisconnectReason, useMultiFileAuthState, proto } from '@whiskeysockets/baileys';
import pino from 'pino';
import qr from 'qrcode-terminal';
import { createServer } from 'node:http';
import { mkdir, readFile, writeFile, rename } from 'node:fs/promises';
import { join } from 'node:path';
import { randomUUID, timingSafeEqual } from 'node:crypto';

const token = process.env.WINTERHOLD_BRIDGE_TOKEN;
if (!token || token.length < 32) throw Error('Configure WINTERHOLD_BRIDGE_TOKEN com o token exibido no APK.');
const root = process.env.WINTERHOLD_BRIDGE_DATA || join(process.cwd(), '.local');
await mkdir(root, { recursive: true, mode: 0o700 });
const ledgerPath = join(root, 'ledger.json');
const records = new Map(Object.entries(JSON.parse(await readFile(ledgerPath, 'utf8').catch(e => {
  if (e.code === 'ENOENT') return '{}';
  throw e;
}))));
let persistQueue = Promise.resolve();
function persist() {
  const snapshot = JSON.stringify(Object.fromEntries([...records].slice(-2000)));
  persistQueue = persistQueue.then(async () => {
    const temp = `${ledgerPath}.${randomUUID()}.tmp`;
    await writeFile(temp, snapshot, { mode: 0o600 });
    await rename(temp, ledgerPath);
  });
  return persistQueue;
}
function allowed(req) {
  const provided = req.headers.authorization?.replace(/^Bearer /, '') || '';
  const a = Buffer.from(token), b = Buffer.from(provided);
  return a.length === b.length && timingSafeEqual(a, b);
}
let socket, connected = false;
const { state, saveCreds } = await useMultiFileAuthState(join(root, 'session'));
function connect() {
  const current = makeWASocket({ auth: state, logger: pino({ level: 'warn' }), printQRInTerminal: false });
  socket = current;
  current.ev.on('creds.update', saveCreds);
  current.ev.on('connection.update', update => {
    if (update.qr) qr.generate(update.qr, { small: true });
    if (update.connection === 'open') { connected = true; console.log('Ponte WhatsApp conectada.'); }
    if (update.connection === 'close') {
      connected = false;
      const code = update.lastDisconnect?.error?.output?.statusCode;
      if (code === DisconnectReason.loggedOut) console.error('Sessão desconectada. Pareie novamente.');
      else setTimeout(connect, 3000);
    }
  });
  current.ev.on('messages.update', updates => {
    for (const { key, update } of updates) {
      const id = key.id;
      const record = [...records.values()].find(item => item.messageId === id);
      if (!record || key.fromMe === false || typeof update.status !== 'number') continue;
      const Status = proto.WebMessageInfo.Status;
      if (update.status >= Status.DELIVERY_ACK) record.status = 'delivered';
      else if (update.status >= Status.SERVER_ACK && record.status !== 'delivered') record.status = 'server_ack';
      else if (update.status === Status.ERROR) record.status = 'uncertain';
      persist().catch(console.error);
    }
  });
}
connect();
const send = async (id, to, text) => {
  if (records.has(id)) return records.get(id);
  if (!connected || !socket) throw Error('WhatsApp indisponível ou ainda não pareado');
  const found = await socket.onWhatsApp(to.slice(1));
  if (!found?.[0]?.exists || !found[0].jid) throw Error('Número não registrado no WhatsApp');
  const messageId = randomUUID().replaceAll('-', '').slice(0, 20).toUpperCase();
  const record = { status: 'reserved', messageId, at: new Date().toISOString() };
  records.set(id, record);
  await persist(); // Durable reservation before Baileys sendMessage; no automatic resend on crash.
  try {
    await socket.sendMessage(found[0].jid, { text }, { messageId });
    if (record.status === 'reserved') record.status = 'submitted';
    await persist();
  } catch (error) {
    record.status = 'uncertain';
    await persist();
    throw error;
  }
  return record;
};
createServer(async (req, res) => {
  const reply = (code, value) => { res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(value)); };
  if (!allowed(req)) return reply(401, { error: 'Não autorizado' });
  if (req.method === 'GET' && req.url === '/healthz') return reply(200, { connected });
  if (req.method === 'GET' && req.url?.startsWith('/status?id=')) return reply(200, records.get(new URL(req.url, 'http://localhost').searchParams.get('id')) || { status: 'unknown' });
  if (req.method !== 'POST' || req.url !== '/send') return reply(404, { error: 'Rota desconhecida' });
  let body = '';
  for await (const chunk of req) { body += chunk; if (body.length > 4096) return reply(413, { error: 'Corpo muito grande' }); }
  try {
    const { id, to, text } = JSON.parse(body);
    if (!/^[A-Za-z0-9:_-]{1,180}$/.test(id) || !/^\+[1-9][0-9]{7,14}$/.test(to) || typeof text !== 'string' || !text.length || text.length > 1000)
      return reply(400, { error: 'Dados inválidos' });
    const value = await send(id, to, text);
    return reply(200, value);
  } catch (error) { console.error('Falha WhatsApp:', error.message); return reply(503, { error: error.message }); }
}).listen(8791, '127.0.0.1', () => console.log('Ponte escutando somente em 127.0.0.1:8791'));

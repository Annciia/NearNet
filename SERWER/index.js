import https from 'https';
import express from 'express';
import cors from 'cors';
import morgan from 'morgan';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import Database from 'better-sqlite3';
import { nanoid } from 'nanoid';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = process.env.PORT || 3002;
const JWT_SECRET = process.env.JWT_SECRET || crypto.randomBytes(64).toString('hex');
const DATA_DIR = path.join(__dirname,'data');
fs.mkdirSync(DATA_DIR, { recursive: true });
const DB_PATH = path.join(DATA_DIR, 'app.db');


const CERT_DIR = path.join(__dirname, 'cert');
const SSL_KEY_PATH  = process.env.SSL_KEY_PATH  || path.join(CERT_DIR, 'key.pem');
const SSL_CERT_PATH = process.env.SSL_CERT_PATH || path.join(CERT_DIR, 'cert.pem');


console.log("SSL_KEY_PATH: "+process.env.SSL_KEY_PATH);
console.log("SSL_CERT_PATH: "+process.env.SSL_CERT_PATH);

const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  login TEXT UNIQUE NOT NULL,
  name TEXT DEFAULT '',
  avatar TEXT DEFAULT '',
  publicKey TEXT DEFAULT '',
  passwordHash TEXT NOT NULL,
  additionalSettings TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS rooms (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT DEFAULT '',
  avatar TEXT DEFAULT '',
  passwordHash TEXT DEFAULT '',
  isPrivate INTEGER DEFAULT 0,
  isVisible INTEGER DEFAULT 1,
  idAdmin TEXT,
  additionalSettings TEXT DEFAULT '',
  FOREIGN KEY (idAdmin) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS roomUsers (
  roomId TEXT NOT NULL,
  userId TEXT NOT NULL,
  addedAt TEXT DEFAULT (datetime('now')),
  PRIMARY KEY (roomId, userId),
  FOREIGN KEY (roomId) REFERENCES rooms(id) ON DELETE CASCADE,
  FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  roomId TEXT NOT NULL,
  userId TEXT,
  timestamp TEXT NOT NULL,
  messageType TEXT NOT NULL,
  data TEXT NOT NULL,
  additionalData TEXT NOT NULL DEFAULT '',
  FOREIGN KEY (roomId) REFERENCES rooms(id) ON DELETE CASCADE,
  FOREIGN KEY (userId) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ts TEXT NOT NULL,
  level TEXT NOT NULL,
  event TEXT NOT NULL,
  details TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS roomAccessRequests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  roomId TEXT NOT NULL,
  userId TEXT NOT NULL,
  status TEXT DEFAULT 'pending',
  encryptedRoomKey TEXT DEFAULT '',
  requestedAt TEXT DEFAULT (datetime('now')),
  FOREIGN KEY (roomId) REFERENCES rooms(id) ON DELETE CASCADE,
  FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE
);
`);


const app = express();
app.use(cors());
app.use(express.json({ limit: '100mb' }));
app.use(morgan('dev'));

function logEvent(level, event, details = {}) {
  const stmt = db.prepare('INSERT INTO logs (ts, level, event, details) VALUES (?,?,?,?)');
  stmt.run(new Date().toISOString(), level, event, JSON.stringify(details));
}

function toUserData(row) {
  return {
    idUser: row.id,
    name: row.name ?? row.login,
    avatar: row.avatar ?? '',
    publicKey: row.publicKey ?? '',
    password: '',
    additionalSettings: row.additionalSettings ?? ''
  };
}

function auth(req, res, next) {
  const h = req.headers['authorization'] ?? '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) return res.status(401).json({ error: 'no token' });
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    req.user = payload;
    next();
  } catch (e) {
    return res.status(401).json({ error: 'invalid token' });
  }
}

const sseClients = new Map();
function broadcastPackage(roomId, pkg) {
  const set = sseClients.get(roomId);
  if (!set) return;
  const data = `data: ${JSON.stringify(pkg)}\n\n`;
  for (const res of set) {
    res.write(data);
  }
}

console.log("ENV: "+process.env.ENV);
if(process.env.ENV == "TEST"){
app.get('/', (req, res) => {
  const LIMIT = 100;

  const users = db.prepare(`
    SELECT
      id,
      CASE WHEN length(coalesce(login,''))>${LIMIT} THEN substr(login,1,${LIMIT})||'...' ELSE login END AS login,
      CASE WHEN length(coalesce(name,''))>${LIMIT} THEN substr(name,1,${LIMIT})||'...' ELSE name END AS name,
      CASE WHEN length(coalesce(avatar,''))>${LIMIT} THEN substr(avatar,1,${LIMIT})||'...' ELSE avatar END AS avatar,
      CASE WHEN length(coalesce(publicKey,''))>${LIMIT} THEN substr(publicKey,1,${LIMIT})||'...' ELSE publicKey END AS publicKey,
      CASE WHEN length(coalesce(passwordHash,''))>${LIMIT} THEN substr(passwordHash,1,${LIMIT})||'...' ELSE passwordHash END AS passwordHash,
      CASE WHEN length(coalesce(additionalSettings,''))>${LIMIT} THEN substr(additionalSettings,1,${LIMIT})||'...' ELSE additionalSettings END AS additionalSettings
    FROM users
  `).all();

  const rooms = db.prepare(`
    SELECT
      id,
      CASE WHEN length(coalesce(name,''))>${LIMIT} THEN substr(name,1,${LIMIT})||'...' ELSE name END AS name,
      CASE WHEN length(coalesce(description,''))>${LIMIT} THEN substr(description,1,${LIMIT})||'...' ELSE description END AS description,
      CASE WHEN length(coalesce(avatar,''))>${LIMIT} THEN substr(avatar,1,${LIMIT})||'...' ELSE avatar END AS avatar,
      CASE WHEN length(coalesce(passwordHash,''))>${LIMIT} THEN substr(passwordHash,1,${LIMIT})||'...' ELSE passwordHash END AS passwordHash,
      isPrivate,
      isVisible,
      CASE WHEN length(coalesce(idAdmin,''))>${LIMIT} THEN substr(idAdmin,1,${LIMIT})||'...' ELSE idAdmin END AS idAdmin,
      CASE WHEN length(coalesce(additionalSettings,''))>${LIMIT} THEN substr(additionalSettings,1,${LIMIT})||'...' ELSE additionalSettings END AS additionalSettings
    FROM rooms
    ORDER BY name
  `).all();

  const roomUsers = db.prepare(`
    SELECT
      CASE WHEN length(coalesce(roomId,''))>${LIMIT} THEN substr(roomId,1,${LIMIT})||'...' ELSE roomId END AS roomId,
      CASE WHEN length(coalesce(userId,''))>${LIMIT} THEN substr(userId,1,${LIMIT})||'...' ELSE userId END AS userId,
      CASE WHEN length(coalesce(addedAt,''))>${LIMIT} THEN substr(addedAt,1,${LIMIT})||'...' ELSE addedAt END AS addedAt
    FROM roomUsers
  `).all();

  const messages = db.prepare(`
    SELECT
      id,
      CASE WHEN length(coalesce(roomId,''))>${LIMIT} THEN substr(roomId,1,${LIMIT})||'...' ELSE roomId END AS roomId,
      CASE WHEN length(coalesce(userId,''))>${LIMIT} THEN substr(userId,1,${LIMIT})||'...' ELSE userId END AS userId,
      CASE WHEN length(coalesce(timestamp,''))>${LIMIT} THEN substr(timestamp,1,${LIMIT})||'...' ELSE timestamp END AS timestamp,
      CASE WHEN length(coalesce(messageType,''))>${LIMIT} THEN substr(messageType,1,${LIMIT})||'...' ELSE messageType END AS messageType,
      CASE WHEN length(coalesce(data,''))>${LIMIT} THEN substr(data,1,${LIMIT})||'...' ELSE data END AS data,
      CASE WHEN length(coalesce(additionalData,''))>${LIMIT} THEN substr(additionalData,1,${LIMIT})||'...' ELSE additionalData END AS additionalData
    FROM messages
    ORDER BY id DESC
    LIMIT 200
  `).all();

  const roomAccessRequests = db.prepare(`
    SELECT
      id,
      CASE WHEN length(coalesce(roomId,''))>${LIMIT} THEN substr(roomId,1,${LIMIT})||'...' ELSE roomId END AS roomId,
      CASE WHEN length(coalesce(userId,''))>${LIMIT} THEN substr(userId,1,${LIMIT})||'...' ELSE userId END AS userId,
      CASE WHEN length(coalesce(status,''))>${LIMIT} THEN substr(status,1,${LIMIT})||'...' ELSE status END AS status,
      CASE WHEN length(coalesce(encryptedRoomKey,''))>${LIMIT} THEN substr(encryptedRoomKey,1,${LIMIT})||'...' ELSE encryptedRoomKey END AS encryptedRoomKey,
      CASE WHEN length(coalesce(requestedAt,''))>${LIMIT} THEN substr(requestedAt,1,${LIMIT})||'...' ELSE requestedAt END AS requestedAt
    FROM roomAccessRequests
  `).all();

  const logs = db.prepare(`
    SELECT
      id,
      CASE WHEN length(coalesce(ts,''))>${LIMIT} THEN substr(ts,1,${LIMIT})||'...' ELSE ts END AS ts,
      CASE WHEN length(coalesce(level,''))>${LIMIT} THEN substr(level,1,${LIMIT})||'...' ELSE level END AS level,
      CASE WHEN length(coalesce(event,''))>${LIMIT} THEN substr(event,1,${LIMIT})||'...' ELSE event END AS event,
      CASE WHEN length(coalesce(details,''))>${LIMIT} THEN substr(details,1,${LIMIT})||'...' ELSE details END AS details
    FROM logs
    ORDER BY id DESC
    LIMIT 200
  `).all();


const escape = s => String(s).replace(/[&<>]/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[ch]));

const renderTable = (title, rows, tableName) => {
  if (!rows.length) return `<h2>${title}</h2><p><i>empty</i></p>`;
  const headers = Object.keys(rows[0]);
  const thead = `<tr>${headers.map(h => `<th>${escape(h)}</th>`).join('')}</tr>`;
  const tbody = rows.map(r =>
    `<tr>${headers.map(h => {
      const val = r[h] ?? '';
      const short = escape(val);
      const baseId = r.id || r.roomId || '';
      const extra = tableName === 'roomUsers' ? ` data-extraid="${r.userId || ''}"` : '';
      return `<td data-table="${tableName}" data-id="${baseId}" data-column="${h}"${extra}><pre>${short}</pre></td>`;
    }).join('')}</tr>`
  ).join('');
  return `<h2>${title}</h2><table border="1" cellpadding="6" cellspacing="0">${thead}${tbody}</table>`;
};



  const html = `<!doctype html>
<html><head><meta charset="utf-8"><title>Dev Website</title>
<style>body{font-family:Inter,system-ui,Arial;padding:20px} table{border-collapse:collapse;margin-bottom:24px} th{background:#eee}</style>
</head><body>
<h1>Database + Logs</h1>
<form action="/admin/wipe" method="POST" onsubmit="return confirm('Confirm to delete everything')">
  <button type="submit" style="background:#b00020;color:#fff;border:none;padding:8px 12px;border-radius:6px;cursor:pointer">
    Delete everything
  </button>
</form>
${renderTable('users', users, 'users')}
${renderTable('rooms', rooms, 'rooms')}
${renderTable('roomUsers', roomUsers, 'roomUsers')}
${renderTable('messages (last 200)', messages, 'messages')}
${renderTable('roomAccessRequests', roomAccessRequests, 'roomAccessRequests')}
${renderTable('logs (last 200)', logs, 'logs')}

<script>
document.addEventListener('click', async e => {
  const td = e.target.closest('td[data-table]');
  if (!td) return;
  const table = td.dataset.table;
  const id = td.dataset.id;
  const column = td.dataset.column;
  const extraid = td.dataset.extraid || null;
  if (!column) return;

 
  const loading = document.createElement('div');
  loading.textContent = 'Pobieranie danych...';
  Object.assign(loading.style, {
    position: 'fixed',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    background: '#222',
    color: '#fff',
    padding: '10px 20px',
    borderRadius: '8px',
    opacity: '0.9',
    zIndex: '9999',
    fontFamily: 'system-ui',
    fontSize: '16px'
  });
  document.body.appendChild(loading);

  try {
    const res = await fetch('/dev/value', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ table, column, id, extraid })
    });
    if (!res.ok) throw new Error('fetch failed');
    const { value } = await res.json();

    loading.remove();

    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(value);
      } else {
        const ta = document.createElement('textarea');
        ta.value = value; document.body.appendChild(ta);
        ta.select(); document.execCommand('copy'); ta.remove();
      }
    } catch(_) {}

    const note = document.createElement('div');
    note.textContent = 'Skopiowano zmienną ' + String(value).length + ' znaków.';
    Object.assign(note.style, {
      position: 'fixed',
      top: '50%',
      left: '50%',
      transform: 'translate(-50%, -50%)',
      background: '#333',
      color: '#fff',
      padding: '10px 20px',
      borderRadius: '8px',
      opacity: '0.9',
      zIndex: '9999',
      fontFamily: 'system-ui',
      fontSize: '16px'
    });
    document.body.appendChild(note);
    setTimeout(() => note.remove(), 2000);

  } catch (err) {
    console.error('copy error', err);
    loading.textContent = 'Błąd podczas pobierania danych.';
    loading.style.background = '#b00020';
    setTimeout(() => loading.remove(), 2000);
  }
});
</script>



</body></html>`;
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.send(html);
});

app.post('/dev/value', (req, res) => {
  const { table, column, id, extraid } = req.body || {};
  if (!table || !column)
    return res.status(400).json({ error: 'bad request' });

  try {
    let sql, params;
    if (table === 'roomUsers') {
      if (!id || !extraid) return res.status(400).json({ error: 'missing composite key' });
      sql = `SELECT ${column} AS value FROM roomUsers WHERE roomId = ? AND userId = ?`;
      params = [id, extraid];
    } else if (id) {
      sql = `SELECT ${column} AS value FROM ${table} WHERE id = ?`;
      params = [id];
    } else {
      sql = `SELECT ${column} AS value FROM ${table} LIMIT 1`;
      params = [];
    }

    const row = db.prepare(sql).get(...params);
    if (!row) return res.status(404).json({ error: 'not found' });

    const value = row.value != null ? String(row.value) : '';
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    res.json({ value });
  } catch (e) {
    logEvent('error', 'devValueQueryFailed', { table, column, id, extraid, err: String(e) });
    res.status(400).json({ error: 'invalid request' });
  }
});

app.post('/admin/wipe', (req, res) => {
  try {
    db.exec(`
      DELETE FROM roomUsers;
      DELETE FROM messages;
      DELETE FROM rooms;
      DELETE FROM users;
      DELETE FROM roomAccessRequests;
      DELETE FROM logs;
    `);
  } catch (e) {
    console.error('wipe error', e);
  }
  res.redirect('/');
});
}

app.post('/api/register', (req, res) => {
  const { login, password, publicKey = '' } = req.body || {};
  if (!login || !password) return res.status(400).json({ error: 'login and password required' });
  const exists = db.prepare('SELECT 1 FROM users WHERE login = ?').get(login);
  if (exists) return res.status(409).json({ error: 'login taken' });
  const id = nanoid();
  const hash = bcrypt.hashSync(password, 10);
  db.prepare('INSERT INTO users (id, login, passwordHash, name, publicKey) VALUES (?,?,?,?,?)')
    .run(id, login, hash, login, publicKey);
  logEvent('info', 'register', { login, id });
  return res.json({ success: true });
});

app.post('/api/login', (req, res) => {
  const { login, password } = req.body || {};
  logEvent('info', 'tryingToLogin', { login, password });

  if (!login || !password) {
    return res.status(400).json({
      success: false,
      error: 'Login and password required'
    });
  }

  const row = db.prepare('SELECT * FROM users WHERE login = ?').get(login);
  if (!row) {
    return res.status(404).json({
      success: false,
      error: 'User not found'
    });
  }

  const hash = row.passwordHash || row.password_hash;
  if (!hash) {
    logEvent('error', 'loginMissingHash', { login });
    return res.status(500).json({
      success: false,
      error: 'Password hash missing on server'
    });
  }

  const ok = bcrypt.compareSync(password, hash);
  if (!ok) {
    return res.status(401).json({
      success: false,
      error: 'Wrong password'
    });
  }

  const token = jwt.sign({ id: row.id, login: row.login }, JWT_SECRET, { expiresIn: '7d' });
  logEvent('info', 'login', { login: row.login, id: row.id });

  return res.status(200).json({
    success: true,
    token,
    userData: toUserData(row)
  });
});

app.use('/api', auth);

app.delete('/api/user', (req, res) => {
  const { password } = req.body || {};
  const row = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
  if (!row) return res.status(404).json({ error: 'not found' });
  const ok = bcrypt.compareSync(password || '', row.passwordHash);
  if (!ok) return res.status(403).json({ error: 'bad password' });
  db.prepare('DELETE FROM roomUsers WHERE userId = ?').run(req.user.id);
  db.prepare('DELETE FROM users WHERE id = ?').run(req.user.id);
  logEvent('warn', 'deleteUser', { id: req.user.id });
  return res.json({ success: true });
});

app.put('/api/user', (req, res) => {
  const u = req.body || {};
  const row = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
  if (!row) return res.status(404).json({ error: 'not found' });
  const name = u.name ?? row.name;
  const avatar = u.avatar !== undefined ? u.avatar : row.avatar;
  const publicKey = u.publicKey !== undefined ? u.publicKey : row.publicKey;
  const additionalSettings = u.additionalSettings !== undefined ? u.additionalSettings : row.additionalSettings;

  let passwordHash = row.passwordHash;
  if (u.newPassword) {
    if (!u.password)return res.status(400).json({ error: 'current password required to change password' });
    const ok = bcrypt.compareSync(u.password, row.passwordHash);
    if (!ok)return res.status(403).json({ error: 'current password incorrect' });
    passwordHash = bcrypt.hashSync(u.newPassword, 10);
  }
  db.prepare('UPDATE users SET name=?, avatar=?, publicKey=?, additionalSettings=?, passwordHash=? WHERE id=?')
    .run(name, avatar, publicKey, additionalSettings, passwordHash, req.user.id);
  const updated = db.prepare('SELECT * FROM users WHERE id = ?').get(req.user.id);
  logEvent('info', 'updateUser', { id: req.user.id, passwordChanged: !!u.newPassword });
  return res.json(toUserData(updated));
});

function toRoomData(r) {
  return {
    idRoom: r.id,
    name: r.name,
    description: r.description ?? '',
    avatar: r.avatar,
    password: '',
    isPrivate: !!r.isPrivate,
    isVisible: !!r.isVisible,
    idAdmin: r.idAdmin,
    additionalSettings: r.additionalSettings ?? ''
  };
}

app.post('/api/rooms', (req, res) => {
  const r = req.body || {};
  const id = nanoid();
  const name = r.name ?? 'Room';
  const description = r.description ?? '';
  const avatar = r.avatar ?? '';
  const passHash = r.password ? bcrypt.hashSync(r.password, 10) : '';
  const isPrivate = r.isPrivate ? 1 : 0;
  const isVisible = r.isVisible !== false ? 1 : 0;
  const idAdmin = req.user.id;
  const additionalSettings = r.additionalSettings ?? '';
  db.prepare('INSERT INTO rooms (id, name, description, avatar, passwordHash, isPrivate, isVisible, idAdmin, additionalSettings) VALUES (?,?,?,?,?,?,?,?,?)')
    .run(id, name, description, avatar, passHash, isPrivate, isVisible, idAdmin, additionalSettings);
  db.prepare('INSERT OR IGNORE INTO roomUsers (roomId, userId) VALUES (?,?)').run(id, idAdmin);
  db.prepare('INSERT INTO roomAccessRequests (roomId, userId, status) VALUES (?, ?, ?)').run(id, idAdmin, 'accepted');
  const row = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  logEvent('info', 'addRoom', { roomId: id, by: idAdmin });
  return res.json(toRoomData(row));
});

app.put('/api/rooms/:id', (req, res) => {
  const id = req.params.id;
  const row = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  if (!row) return res.status(404).json({ error: 'not found' });
  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?').get(id, req.user.id);
  if (!inRoom) return res.status(403).json({ error: 'not in room' });

  const r = req.body || {};
  const name = r.name ?? row.name;
  const description = r.description !== undefined ? r.description : row.description;
  const avatar = r.avatar !== undefined ? r.avatar : row.avatar;
  const isPrivate = r.isPrivate !== undefined ? (r.isPrivate ? 1 : 0) : row.isPrivate;
  const additionalSettings = r.additionalSettings ?? row.additionalSettings;

  let passHash = row.passwordHash;
  let isVisible = row.isVisible;

  if (row.idAdmin == null || row.idAdmin === req.user.id) {
    if (r.password !== undefined) {
      passHash = r.password ? bcrypt.hashSync(r.password, 10) : row.passwordHash;
    }
    if (r.isVisible !== undefined) {
      isVisible = r.isVisible ? 1 : 0;
    }
  }else{
    if( r.password !== ''){
       return res.status(403).json({ error: 'not admin' });
    }
  }


  db.prepare('UPDATE rooms SET name=?, description=?, avatar=?, passwordHash=?, isPrivate=?, isVisible=?, additionalSettings=? WHERE id=?')
    .run(name, description, avatar, passHash, isPrivate, isVisible, additionalSettings, id);
  const updated = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  logEvent('info', 'updateRoom', { roomId: id, by: req.user.id });
  return res.json(toRoomData(updated));
});

app.delete('/api/rooms/:id', (req, res) => {
  const id = req.params.id;
  const row = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  if (!row) return res.status(404).json({ error: 'not found' });
  if (row.idAdmin !== req.user.id) return res.status(403).json({ error: 'not admin' });
  db.prepare('DELETE FROM roomUsers WHERE roomId = ?').run(id);
  db.prepare('DELETE FROM messages WHERE roomId = ?').run(id);
  db.prepare('DELETE FROM rooms WHERE id = ?').run(id);
  logEvent('warn', 'deleteRoom', { roomId: id, by: req.user.id });
  return res.json({ success: true });
});

app.get('/api/rooms/mine', (req, res) => {
  const rows = db.prepare(`
    SELECT rooms.* FROM rooms
    JOIN roomUsers ON roomUsers.roomId = rooms.id
    WHERE roomUsers.userId = ?
    ORDER BY rooms.name
  `).all(req.user.id);
  logEvent('info', 'showMyRooms', { userId: req.user.id, rooms: rows.map(toRoomData) });
  return res.json({ rooms: rows.map(toRoomData) });
});

app.get('/api/rooms', (req, res) => {
  const rows = db.prepare(`SELECT * FROM rooms ORDER BY name`).all();
  return res.json({ rooms: rows.map(toRoomData) });
});

app.get('/api/rooms/:id/users', (req, res) => {
  const id = req.params.id;
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  if (!room) return res.status(404).json({ error: 'not found' });
  const users = db.prepare(`
    SELECT users.* FROM users
    JOIN roomUsers ON roomUsers.userId = users.id
    WHERE roomUsers.roomId = ?
  `).all(id);
  return res.json({ roomData: toRoomData(room), userList: { rooms: users.map(toUserData) } });
});

app.post('/api/rooms/:id/add-user', (req, res) => {
  const id = req.params.id;
  const { login } = req.body || {};
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  if (!room) return res.status(404).json({ error: 'not found' });
  if (room.idAdmin !== req.user.id) return res.status(403).json({ error: 'not admin' });
  const user = db.prepare('SELECT * FROM users WHERE login = ?').get(login);
  if (!user) return res.status(404).json({ error: 'no such user' });
  db.prepare('INSERT OR IGNORE INTO roomUsers (roomId, userId) VALUES (?,?)').run(id, user.id);
  logEvent('info', 'addUserToRoom', { roomId: id, login });
  return res.json({ success: true });
});

app.post('/api/rooms/:id/remove-user', (req, res) => {
  const roomId = req.params.id;
  const { userId } = req.body || {};
  if (!userId) return res.status(400).json({ error: 'userId required' });
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(roomId);
  if (!room) return res.status(404).json({ error: 'room not found' });
  if (room.idAdmin !== req.user.id) return res.status(403).json({ error: 'not admin' });
  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?').get(roomId, userId);
  if (!inRoom) return res.status(400).json({ error: 'user not in room' });
  db.prepare('DELETE FROM roomUsers WHERE roomId = ? AND userId = ?').run(roomId, userId);
  db.prepare('DELETE FROM roomAccessRequests WHERE roomId = ? AND userId = ?').run(roomId, userId);
  db.prepare('UPDATE messages SET userId = NULL WHERE roomId = ? AND userId = ?').run(roomId, userId);
  if (room.idAdmin === userId) db.prepare('UPDATE rooms SET idAdmin = NULL WHERE id = ?').run(roomId);
  logEvent('warn', 'removeUserFromRoom', { roomId, removedUserId: userId, by: req.user.id });
  return res.json({ success: true });
});


app.post('/api/rooms/:id/leave', (req, res) => {
  const roomId = req.params.id;
  const userId = req.user.id;
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(roomId);
  if (!room) return res.status(404).json({ error: 'room not found' });
  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?').get(roomId, userId);
  if (!inRoom) return res.status(400).json({ error: 'not in room' });
  db.prepare('DELETE FROM roomUsers WHERE roomId = ? AND userId = ?').run(roomId, userId);
  db.prepare('DELETE FROM roomAccessRequests WHERE roomId = ? AND userId = ?').run(roomId, userId);
  db.prepare('UPDATE messages SET userId = NULL WHERE roomId = ? AND userId = ?').run(roomId, userId);
  if (room.idAdmin === userId) {
    db.prepare('UPDATE rooms SET idAdmin = NULL WHERE id = ?').run(roomId);
    logEvent('info', 'adminLeftRoom', { roomId, oldAdmin: userId });
  }
  logEvent('info', 'leaveRoom', { roomId, userId });
  return res.json({ success: true });
});

app.post('/api/rooms/join', (req, res) => {
  const { idRoom } = req.body || {};
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(idRoom);
  if (!room) return res.status(404).json({ error: 'not found' });
  if (room.isPrivate) return res.status(403).json({ error: 'not public' });
  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?').get(idRoom, req.user.id);
  if (!inRoom)db.prepare('INSERT INTO roomUsers (roomId, userId) VALUES (?, ?)').run(idRoom,req.user.id);
  
  logEvent('info', 'joinPublicRoom', { roomId: idRoom, userId: req.user.id, alreadyInRoom: !!inRoom,});
  return res.json({ success: true, alreadyInRoom: !!inRoom });
});


app.get('/api/users/:id/publicKey', auth, (req, res) => {
  const id = req.params.id;
  const row = db.prepare('SELECT publicKey FROM users WHERE id = ?').get(id);
  if (!row) return res.status(404).json({ error: 'user not found' });
  return res.json({ id, publicKey: row.publicKey || '' });
});

app.post('/api/rooms/askForAccess', (req, res) => {
  const { idRoom } = req.body || {};
  if (!idRoom) return res.status(400).json({ error: 'idRoom required' });
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(idRoom);
  if (!room) return res.status(404).json({ error: 'room not found' });

  if (!room.idAdmin) return res.status(400).json({ error: 'no admin' });

  const alreadyMember = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?')
    .get(idRoom, req.user.id);
  if (alreadyMember) return res.status(400).json({ error: 'already in room' });

  const existingRequest = db.prepare("SELECT * FROM roomAccessRequests WHERE roomId = ? AND userId = ? AND status = 'pending'")
    .get(idRoom, req.user.id);
  if (existingRequest) return res.status(400).json({ error: 'request already pending' });

  db.prepare("INSERT INTO roomAccessRequests (roomId, userId, status) VALUES (?, ?, 'pending')")
    .run(idRoom, req.user.id);
  logEvent('info', 'askForAccess', { roomId: idRoom, userId: req.user.id });
  return res.json({ success: true });
});

app.get('/api/rooms/:id/requests', (req, res) => {
  const id = req.params.id;
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  if (!room) return res.status(404).json({ error: 'room not found' });
  if (room.idAdmin !== req.user.id) return res.status(403).json({ error: 'not admin' });

  const requests = db.prepare(`
    SELECT roomAccessRequests.*, users.login, users.name, users.avatar
    FROM roomAccessRequests
    JOIN users ON users.id = roomAccessRequests.userId
    WHERE roomAccessRequests.roomId = ? AND roomAccessRequests.status = 'pending'
    ORDER BY roomAccessRequests.requestedAt DESC
  `).all(id);

  return res.json({ requests });
});

app.get('/api/rooms/:id/room_users_status', auth, (req, res) => {
  const roomId = req.params.id;
  const userId = req.user.id;

  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?')
    .get(roomId, userId);
  if (!inRoom) return res.status(403).json({ error: 'not in room' });

  const rows = db.prepare(`
    SELECT 
      r.userId,
      u.publicKey,
      r.status,
      r.encryptedRoomKey,
      r.requestedAt
    FROM roomAccessRequests r
    JOIN users u ON u.id = r.userId
    WHERE r.roomId = ?
      AND r.status IN ('requestJoin','declaredPasswordCheck', 'passwordReadyToCheck', 'waitingForKey')
    ORDER BY r.requestedAt DESC
  `).all(roomId);

  logEvent('info', 'roomUsersStatusQueried', { roomId, by: userId, count: rows.length });

  return res.json({
    roomId,
    statuses: rows.map(r => ({
      userId: r.userId,
      status: r.status,
      encryptedRoomKey: r.encryptedRoomKey,
      requestedAt: r.requestedAt,
      publicKey: r.publicKey
    }))
  });
});


app.get('/api/rooms/:id/my-request', auth, (req, res) => {
  const roomId = req.params.id;
  const userId = req.user.id;

  const room = db.prepare('SELECT id, name FROM rooms WHERE id = ?').get(roomId);
  if (!room) return res.status(404).json({ error: 'room not found' });

  const reqRow = db.prepare(`
    SELECT id, status, encryptedRoomKey, requestedAt
    FROM roomAccessRequests
    WHERE roomId = ? AND userId = ?
  `).get(roomId, userId);

  if (!reqRow) {
    const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?')
      .get(roomId, userId);
    if (inRoom) {
      return res.json({ status: 'inRoom', encryptedRoomKey: '', message: 'already member of room' });
    }
    return res.status(404).json({ error: 'no access request found' });
  }

  if (reqRow.encryptedRoomKey) {
    return res.json({
      status: reqRow.status,
      encryptedRoomKey: reqRow.encryptedRoomKey,
      requestedAt: reqRow.requestedAt
    });
  }

  return res.json({
    status: reqRow.status,
    requestedAt: reqRow.requestedAt
  });
});


app.delete('/api/rooms/:id/my-request', auth, (req, res) => {
  const roomId = req.params.id;
  const userId = req.user.id;

  const deleted = db.prepare('DELETE FROM roomAccessRequests WHERE roomId = ? AND userId = ?')
    .run(roomId, userId);
  
  return res.json({ success: deleted.changes > 0 });
});


app.post('/api/rooms/:id/request-join-by-password', auth, (req, res) => {
  const idRoom = req.params.id;
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(idRoom);
  if (!room) return res.status(404).json({ error: 'room not found' });

  const already = db.prepare('SELECT * FROM roomAccessRequests WHERE roomId = ? AND userId = ?')
    .get(idRoom, req.user.id);
  if (already) return res.status(400).json({ error: 'request already exists' });

  db.prepare(`
    INSERT INTO roomAccessRequests (roomId, userId, status)
    VALUES (?, ?, 'requestJoin')
  `).run(idRoom, req.user.id);

  logEvent('info', 'requestJoin', { roomId: idRoom, userId: req.user.id });
  return res.json({ success: true });
});


app.post('/api/rooms/:id/declare-password-check', auth, (req, res) => {
  const idRoom = req.params.id;
  const { targetUserId } = req.body || {};
  if (!targetUserId) {
    return res.status(400).json({ error: 'targetUserId required' });
  }
  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?')
    .get(idRoom, req.user.id);
  if (!inRoom) {
    return res.status(403).json({ error: 'not in room' });
  }
  const reqRow = db.prepare(`
    SELECT * FROM roomAccessRequests 
    WHERE roomId = ? AND userId = ? AND status = 'requestJoin'
  `).get(idRoom, targetUserId);
  
  if (!reqRow) {
    return res.status(404).json({ error: 'no matching request' });
  }
  const recentDeclaration = db.prepare(`
    SELECT * FROM roomAccessRequests 
    WHERE roomId = ? AND userId = ? 
    AND status = 'declaredPasswordCheck'
    AND datetime(requestedAt, '+30 seconds') > datetime('now')
  `).get(idRoom, targetUserId);
  
  if (recentDeclaration) {
    logEvent('warn', 'declarePasswordCheckBlocked', { 
      roomId: idRoom, 
      blocker: req.user.id, 
      targetUserId,
      reason: 'someone else is already checking within 30s timeout'
    });
    return res.status(409).json({ 
      error: 'someone else is already checking this password' 
    });
  }
  db.prepare(`
    UPDATE roomAccessRequests
    SET status = 'declaredPasswordCheck', 
        encryptedRoomKey = ?,
        requestedAt = datetime('now')
    WHERE id = ?
  `).run(req.user.id, reqRow.id);

  logEvent('info', 'declaredPasswordCheck', { 
    roomId: idRoom, 
    checker: req.user.id, 
    targetUserId 
  });
  
  return res.json({ success: true });
});

app.post('/api/rooms/:id/reset-password-check/:userId', auth, (req, res) => {
  const { id: roomId, userId: targetUserId } = req.params;
  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?')
    .get(roomId, req.user.id);
  if (!inRoom) {
    return res.status(403).json({ error: 'not in room' });
  }
  const reqRow = db.prepare(`
    SELECT * FROM roomAccessRequests 
    WHERE roomId = ? AND userId = ? AND status = 'declaredPasswordCheck'
  `).get(roomId, targetUserId);

  if (!reqRow) {
    return res.status(404).json({ 
      error: 'no declaredPasswordCheck to reset' 
    });
  }
  db.prepare(`
    UPDATE roomAccessRequests
    SET status = 'requestJoin',         
        encryptedRoomKey = '',           
        requestedAt = datetime('now')   
    WHERE id = ?
  `).run(reqRow.id);

  logEvent('info', 'resetPasswordCheck', { 
    roomId, 
    targetUserId, 
    resetBy: req.user.id,
    reason: 'timeout exceeded'
  });
  
  return res.json({ 
    success: true, 
    message: 'password check reset to requestJoin' 
  });
});

app.post('/api/rooms/:id/send-encrypted-password', auth, (req, res) => {
  const idRoom = req.params.id;
  const { encryptedPassword } = req.body || {};
  if (!encryptedPassword) return res.status(400).json({ error: 'encryptedPassword required' });

  const reqRow = db.prepare(`
    SELECT * FROM roomAccessRequests WHERE roomId = ? AND userId = ? AND status = 'declaredPasswordCheck'
  `).get(idRoom, req.user.id);
  if (!reqRow) return res.status(400).json({ error: 'invalid request state' });

  db.prepare(`
    UPDATE roomAccessRequests
    SET status = 'passwordReadyToCheck', encryptedRoomKey = ?
    WHERE id = ?
  `).run(encryptedPassword, reqRow.id);

  logEvent('info', 'passwordReadyToCheck', { roomId: idRoom, userId: req.user.id });
  return res.json({ success: true });
});

app.post('/api/rooms/:id/send-room-key', auth, (req, res) => {
  const roomId = req.params.id;
  const { targetUserId, encryptedRoomKey } = req.body || {};
  if (!targetUserId || !encryptedRoomKey)
    return res.status(400).json({ error: 'targetUserId and encryptedRoomKey required' });

  const senderId = req.user.id;

  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?')
    .get(roomId, senderId);
  if (!inRoom) return res.status(403).json({ error: 'sender not in room' });

  const reqRow = db.prepare('SELECT * FROM roomAccessRequests WHERE roomId = ? AND userId = ?')
    .get(roomId, targetUserId);
  
  if (!reqRow) {
    return res.status(404).json({ error: 'no access request found' });
  }
  
  if (reqRow.status !== 'waitingForKey' && reqRow.status !== 'passwordReadyToCheck') {
    return res.status(400).json({ error: 'target does not need encrypted key' });
  }

  db.prepare("UPDATE roomAccessRequests SET status = 'accepted', encryptedRoomKey = ? WHERE id = ?")
    .run(encryptedRoomKey, reqRow.id);
  

  db.prepare("INSERT OR IGNORE INTO roomUsers (roomId, userId) VALUES (?, ?)")
    .run(roomId, targetUserId);

  logEvent('info', 'roomKeySent', { roomId, from: senderId, to: targetUserId });
  return res.json({ success: true });
}); 

app.post('/api/rooms/:id/reject-password/:userId', auth, (req, res) => {
  const { id: roomId, userId: targetUserId } = req.params;
  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?')
    .get(roomId, req.user.id);
  if (!inRoom) {
    return res.status(403).json({ error: 'not in room' });
  }

  const reqRow = db.prepare(`
    SELECT * FROM roomAccessRequests 
    WHERE roomId = ? AND userId = ? AND status = 'passwordReadyToCheck'
  `).get(roomId, targetUserId);

  if (!reqRow) {
    return res.status(404).json({ 
      error: 'no passwordReadyToCheck to reject' 
    });
  }

  db.prepare(`
    DELETE FROM roomAccessRequests
    WHERE id = ?
  `).run(reqRow.id);

  logEvent('info', 'passwordRejected', { 
    roomId, 
    targetUserId, 
    rejectedBy: req.user.id,
    reason: 'incorrect password'
  });
  
  return res.json({ 
    success: true, 
    message: 'password rejected' 
  });
});

app.post('/api/rooms/:id/request-key-again', (req, res) => {
  const roomId = req.params.id;
  const userId = req.user.id;

  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?')
    .get(roomId, userId);
  if (!inRoom) return res.status(403).json({ error: 'not in room' });

  const reqRow = db.prepare('SELECT * FROM roomAccessRequests WHERE roomId = ? AND userId = ?')
    .get(roomId, userId);

  if (!reqRow)
    return res.status(404).json({ error: 'no access request found' });

  if (reqRow.status !== 'accepted')
    return res.status(400).json({ error: 'can only re-request key if status is accepted' });

  db.prepare(`
    UPDATE roomAccessRequests
    SET status = 'waitingForKey',
        encryptedRoomKey = '',
        requestedAt = datetime('now')
    WHERE id = ?
  `).run(reqRow.id);

  logEvent('info', 'userRequestedKeyAgain', { roomId, userId, previousStatus: reqRow.status });
  return res.json({ success: true, message: 'key re-request registered, status set to waitingForKey' });
});

app.post('/api/rooms/:id/set-admin', (req, res) => {
  const id = req.params.id;
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  if (!room) return res.status(404).json({ error: 'room not found' });

  if (room.idAdmin) return res.status(400).json({ error: 'admin already set' });

  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?').get(id, req.user.id);
  if (!inRoom) return res.status(403).json({ error: 'not in room' });

  db.prepare('UPDATE rooms SET idAdmin = ? WHERE id = ?').run(req.user.id, id);
  logEvent('info', 'setAdmin', { roomId: id, newAdmin: req.user.id });
  return res.json({ success: true });
});


app.post('/api/rooms/:id/unset-admin', (req, res) => {
  const id = req.params.id;
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  if (!room) return res.status(404).json({ error: 'room not found' });

  if (room.idAdmin !== req.user.id) return res.status(403).json({ error: 'not admin' });

  db.prepare('UPDATE rooms SET idAdmin = NULL WHERE id = ?').run(id);
  logEvent('info', 'unsetAdmin', { roomId: id, oldAdmin: req.user.id });
  return res.json({ success: true });
});

app.post('/api/rooms/:id/requests/:userId/respond', (req, res) => {
  const { id, userId } = req.params;
  const { action, encryptedRoomKey = '' } = req.body || {};

  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(id);
  if (!room) return res.status(404).json({ error: 'room not found' });
  if (room.idAdmin !== req.user.id) return res.status(403).json({ error: 'not admin' });
  logEvent('info', 'respondRequest', { roomId: id, userId });
  const reqRow = db.prepare("SELECT * FROM roomAccessRequests WHERE roomId = ? AND userId = ? AND status = 'pending'")
    .get(id, userId);
  if (!reqRow) return res.status(404).json({ error: 'no pending request' });

if (action === 'accept') {
  db.prepare("UPDATE roomAccessRequests SET status = 'accepted', encryptedRoomKey = ? WHERE id = ?")
    .run(encryptedRoomKey, reqRow.id);
  db.prepare("INSERT OR IGNORE INTO roomUsers (roomId, userId) VALUES (?, ?)").run(id, userId);
  logEvent('info', 'requestAccepted', { roomId: id, userId, encryptedRoomKey: !!encryptedRoomKey });
  } else if (action === 'reject') {
    db.prepare("UPDATE roomAccessRequests SET status = 'rejected' WHERE id = ?").run(reqRow.id);
    logEvent('info', 'requestRejected', { roomId: id, userId });
  } else {
    return res.status(400).json({ error: 'invalid action' });
  }

  return res.json({ success: true, action });
});

app.post('/api/messages/send', (req, res) => {
  const pkg = req.body || {};
  const roomId = pkg.channelId;
  if (!roomId) return res.status(400).json({ error: 'channelId required' });
  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?').get(roomId, req.user.id);
  if (!inRoom) return res.status(403).json({ error: 'user not in room' });
  const ins = db.prepare('INSERT INTO messages (roomId, userId, timestamp, messageType, data, additionalData) VALUES (?,?,?,?,?,?)');
  for (const m of (pkg.messageList || [])) {
    ins.run(roomId, req.user.id, m.timestamp || new Date().toISOString(), m.messageType ?? '', m.data ?? '', m.additionalData ?? '');
  }
  logEvent('info', 'sendMessage', { roomId, count: (pkg.messageList || []).length, by: req.user.id });
  broadcastPackage(roomId, pkg);
  return res.json({ success: true });
});

app.get('/api/messages/stream/:roomId', (req, res) => {
  const roomId = req.params.roomId;
  const userId = req.query.userId ?? '';
  const inRoom = db.prepare('SELECT 1 FROM roomUsers WHERE roomId = ? AND userId = ?').get(roomId, userId);

  if (!inRoom) {
    logEvent('warn', 'sseConnectNotMember', { roomId, userId });
    res.status(403).json({ error: 'user not in room' });
    return;
  }

  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive'
  });
  res.write('retry: 3000\n\n');

  if (!sseClients.has(roomId)) sseClients.set(roomId, new Set());
  sseClients.get(roomId).add(res);

  logEvent('info', 'sseConnect', { roomId, userId });

  req.on('close', () => {
    sseClients.get(roomId)?.delete(res);
    logEvent('info', 'sseDisconnect', { roomId, userId });
  });
});

app.post('/api/messages/request-last', (req, res) => {
  const r = req.body || {};
  const roomId = r.idRoom || r.roomId || r.id;
  if (!roomId) return res.status(400).json({ error: 'idRoom required' });
  const rows = db.prepare('SELECT * FROM messages WHERE roomId = ? ORDER BY id DESC LIMIT 10000').all(roomId);
  const msgList = rows.reverse().map(row => ({
    userId: row.userId,
    timestamp: row.timestamp,
    messageType: row.messageType,
    data: row.data,
    additionalData: row.additionalData
  }));
  const room = db.prepare('SELECT * FROM rooms WHERE id = ?').get(roomId);
  const pkg = { channelId: roomId, encryptedPassword: "", messageList: msgList };
  return res.json({
    roomData: room ? toRoomData(room) : null,
    login: req.user.login,
    package: pkg
  });
});

app.post('/api/messages/ack-last', (req, res) => {
  logEvent('info', 'ackLastMessages', {});
  return res.json({ success: true });
});

app.use((req, res) => {
  res.status(404).json({ error: 'not found' });
});

const key  = fs.readFileSync(SSL_KEY_PATH);
const cert = fs.readFileSync(SSL_CERT_PATH);

const httpsServer = https.createServer({ key, cert }, app);

httpsServer.listen(PORT, () => {
  console.log(`Server started on port ${PORT}`);
  logEvent('info', 'serverStarted', { PORT, protocol: 'https' });
});

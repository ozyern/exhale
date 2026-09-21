// Exhale Music Together relay.
//
// Exhale's "Together" keeps every phone playing the same song at the same second. On one network the
// host phone runs the room itself. Across the internet the phones cannot reach each other, so they
// meet here: this server is a router that speaks the same protocol the host phone's own room speaks
// (see app/.../together/TogetherMessages.kt), holds no music and no accounts, and forgets a room the
// moment it is empty.
//
//   POST /v1/together/sessions          create a room        -> { sessionId, code, hostKey, guestKey, wsUrl, settings }
//   POST /v1/together/sessions/resolve  a code to a room     -> { sessionId, guestKey, wsUrl, settings }
//   GET  /v1/together/ws                the room's WebSocket (first frame: client_hello)
//   GET  /health
//
// Run:  npm install && node server.mjs        (PORT, RELAY_TOKEN and TRUST_PROXY are optional, see README)

import http from 'node:http';
import crypto from 'node:crypto';
import { performance } from 'node:perf_hooks';
import { WebSocketServer } from 'ws';

const PORT = Number(process.env.PORT || 8787);
/** When set, every request must carry `Authorization: Bearer <token>`. Left unset, the relay is open. */
const TOKEN = (process.env.RELAY_TOKEN || '').trim();
const PROTOCOL_VERSION = 1;

/** How long a room outlives its host's connection, so a host who backgrounds the app can come back. */
const HOST_GRACE_MS = 2 * 60_000;
const IDLE_ROOM_MS = 30 * 60_000;
const MAX_ROOMS = 2_000;
const MAX_GUESTS = 32;
const MAX_FRAME_BYTES = 1024 * 1024;
const CODE_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; // no 0/O, 1/I/L

const startedAt = performance.now();
/** The clock guests are handed in every pong; it is the same one room states are stamped with. */
const clockMs = () => Math.round(performance.now() - startedAt) + 1;

/** @type {Map<string, Room>} */
const rooms = new Map();
/** @type {Map<string, string>} code -> sessionId */
const codes = new Map();

/**
 * @typedef {{ id: string, clientId: string, name: string, ws: import('ws').WebSocket, pending: boolean }} Guest
 * @typedef {{
 *   id: string, code: string, hostKey: string, guestKey: string, hostName: string, settings: object,
 *   host: null | { ws: import('ws').WebSocket, participantId: string },
 *   hostGoneAt: number | null, guests: Map<string, Guest>, banned: Set<string>,
 *   lastState: object | null, touchedAt: number
 * }} Room
 */

const json = (res, status, body) => {
    res.writeHead(status, { 'content-type': 'application/json', 'cache-control': 'no-store' });
    res.end(JSON.stringify(body));
};
const send = (ws, message) => {
    if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(message));
};
const uuid = () => crypto.randomUUID();
const safeEqual = (a, b) => {
    const x = Buffer.from(String(a));
    const y = Buffer.from(String(b));
    return x.length === y.length && crypto.timingSafeEqual(x, y);
};

// ── A little rate limiting, per address, so a room cannot be guessed at or the memory filled ──

const hits = new Map();
function limited(ip, bucket, perMinute) {
    const now = Date.now();
    const key = `${bucket}:${ip}`;
    const recent = (hits.get(key) || []).filter((t) => now - t < 60_000);
    recent.push(now);
    hits.set(key, recent);
    return recent.length > perMinute;
}
setInterval(() => {
    const now = Date.now();
    for (const [key, times] of hits) if (times.every((t) => now - t >= 60_000)) hits.delete(key);
}, 60_000).unref();

const clientIp = (req) => (process.env.TRUST_PROXY ? String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() : '') || req.socket.remoteAddress || '?';
const wsUrlFor = (req) => {
    const proto = process.env.TRUST_PROXY && req.headers['x-forwarded-proto'] === 'https' ? 'wss' : req.socket.encrypted ? 'wss' : 'ws';
    return `${proto}://${req.headers.host}/v1/together/ws`;
};
const authorised = (req) => !TOKEN || safeEqual(String(req.headers.authorization || ''), `Bearer ${TOKEN}`);

function newCode() {
    for (let attempt = 0; attempt < 50; attempt++) {
        let code = '';
        for (let i = 0; i < 6; i++) code += CODE_ALPHABET[crypto.randomInt(CODE_ALPHABET.length)];
        if (!codes.has(code)) return code;
    }
    return null;
}

function sanitiseSettings(raw) {
    const s = raw && typeof raw === 'object' ? raw : {};
    return {
        allowGuestsToAddTracks: s.allowGuestsToAddTracks !== false,
        allowGuestsToControlPlayback: s.allowGuestsToControlPlayback === true,
        requireHostApprovalToJoin: s.requireHostApprovalToJoin === true,
    };
}

async function readJson(req) {
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
        size += chunk.length;
        if (size > 16_384) throw new Error('too large');
        chunks.push(chunk);
    }
    return chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {};
}

const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, 'http://x');
    if (req.method === 'GET' && url.pathname === '/health') return json(res, 200, { ok: true, rooms: rooms.size });
    if (!authorised(req)) return json(res, 401, { ok: false, error: 'Unauthorized' });
    const ip = clientIp(req);

    try {
        if (req.method === 'POST' && url.pathname === '/v1/together/sessions') {
            if (limited(ip, 'create', 10)) return json(res, 429, { ok: false, error: 'Too many requests, please try again later' });
            if (rooms.size >= MAX_ROOMS) return json(res, 503, { ok: false, error: 'The relay is full right now' });
            const body = await readJson(req);
            const code = newCode();
            if (!code) return json(res, 503, { ok: false, error: 'No room codes are free right now' });
            /** @type {Room} */
            const room = {
                id: uuid(),
                code,
                hostKey: crypto.randomBytes(24).toString('base64url'),
                guestKey: crypto.randomBytes(24).toString('base64url'),
                hostName: String(body.hostDisplayName || 'Host').trim().slice(0, 40) || 'Host',
                settings: sanitiseSettings(body.settings),
                host: null,
                hostGoneAt: Date.now(),
                guests: new Map(),
                banned: new Set(),
                lastState: null,
                touchedAt: Date.now(),
            };
            rooms.set(room.id, room);
            codes.set(code, room.id);
            return json(res, 200, { sessionId: room.id, code, hostKey: room.hostKey, guestKey: room.guestKey, wsUrl: wsUrlFor(req), settings: room.settings });
        }

        if (req.method === 'POST' && url.pathname === '/v1/together/sessions/resolve') {
            if (limited(ip, 'resolve', 30)) return json(res, 429, { ok: false, error: 'Too many requests, please try again later' });
            const body = await readJson(req);
            const room = rooms.get(codes.get(String(body.code || '').trim().toUpperCase()) || '');
            if (!room) return json(res, 404, { ok: false, error: 'Session not found' });
            return json(res, 200, { sessionId: room.id, guestKey: room.guestKey, wsUrl: wsUrlFor(req), settings: room.settings });
        }
    } catch {
        return json(res, 400, { ok: false, error: 'Bad request' });
    }
    json(res, 404, { ok: false, error: 'Not found' });
});

const wss = new WebSocketServer({ noServer: true, maxPayload: MAX_FRAME_BYTES });

server.on('upgrade', (req, socket, head) => {
    const url = new URL(req.url, 'http://x');
    if (url.pathname !== '/v1/together/ws' || !authorised(req)) {
        socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
        return socket.destroy();
    }
    wss.handleUpgrade(req, socket, head, (ws) => onConnection(ws));
});

const participantOf = (guest) => ({ id: guest.id, name: guest.name, isHost: false, isPending: guest.pending, isConnected: true });

function onConnection(ws) {
    ws.isAlive = true;
    ws.on('pong', () => (ws.isAlive = true));
    /** @type {{ room: Room, role: 'host' | 'guest', guest?: Guest } | null} */
    let me = null;

    // A connection that never says hello is not kept.
    const helloTimer = setTimeout(() => !me && ws.close(4000, 'Handshake required'), 10_000);

    ws.on('message', (data, isBinary) => {
        if (isBinary) return;
        let message;
        try {
            message = JSON.parse(data.toString('utf8'));
        } catch {
            return;
        }
        if (!me) {
            clearTimeout(helloTimer);
            me = hello(ws, message);
            return;
        }
        me.room.touchedAt = Date.now();
        if (me.role === 'host') fromHost(me.room, message);
        else fromGuest(me.room, me.guest, message);
    });

    ws.on('close', () => {
        clearTimeout(helloTimer);
        if (!me) return;
        if (me.role === 'host') {
            if (me.room.host?.ws === ws) {
                me.room.host = null;
                me.room.hostGoneAt = Date.now();
            }
        } else if (me.room.guests.get(me.guest.id)?.ws === ws) {
            me.room.guests.delete(me.guest.id);
            if (me.room.host) send(me.room.host.ws, { type: 'participant_left', sessionId: me.room.id, participantId: me.guest.id, reason: 'Disconnected' });
        }
    });
    ws.on('error', () => {});
}

/** The first frame decides who this is. */
function hello(ws, message) {
    const refuse = (text, code) => {
        send(ws, { type: 'server_error', sessionId: message?.sessionId ?? null, message: text, code });
        ws.close(4001, text);
        return null;
    };
    if (message?.type !== 'client_hello') return refuse('Handshake required', 'handshake');
    if (message.protocolVersion !== PROTOCOL_VERSION) return refuse('Unsupported protocol version', 'protocol');
    const room = rooms.get(String(message.sessionId));
    if (!room) return refuse('Session not found', 'not_found');
    room.touchedAt = Date.now();
    const clientId = String(message.clientId || '').slice(0, 64) || uuid();
    const name = String(message.displayName || '').trim().slice(0, 40);

    if (safeEqual(message.sessionKey, room.hostKey)) {
        // A host who comes back replaces the connection they lost.
        if (room.host) room.host.ws.close(4002, 'Replaced');
        const participantId = uuid();
        room.host = { ws, participantId };
        room.hostGoneAt = null;
        send(ws, { type: 'server_welcome', protocolVersion: PROTOCOL_VERSION, sessionId: room.id, participantId, role: 'HOST', isPending: false, settings: room.settings });
        // Whoever was waiting or already in when the host dropped is told about again.
        for (const guest of room.guests.values()) {
            send(ws, guest.pending
                ? { type: 'join_request', sessionId: room.id, participant: participantOf(guest) }
                : { type: 'participant_joined', sessionId: room.id, participant: participantOf(guest) });
        }
        return { room, role: 'host' };
    }

    if (!safeEqual(message.sessionKey, room.guestKey)) return refuse('Invalid session', 'invalid');
    if (room.banned.has(clientId)) return refuse('You were removed from this session', 'banned');
    if (!room.host) return refuse("The host isn't connected", 'host_offline');

    // The same phone joining again (a network change) replaces its old connection rather than doubling up.
    for (const [id, other] of room.guests) {
        if (other.clientId === clientId) {
            room.guests.delete(id);
            other.ws.close(4002, 'Replaced');
            send(room.host.ws, { type: 'participant_left', sessionId: room.id, participantId: id, reason: 'Rejoined' });
        }
    }
    if (room.guests.size >= MAX_GUESTS) return refuse('This room is full', 'full');

    const guest = { id: uuid(), clientId, name: name || 'Guest', ws, pending: room.settings.requireHostApprovalToJoin };
    room.guests.set(guest.id, guest);
    send(ws, { type: 'server_welcome', protocolVersion: PROTOCOL_VERSION, sessionId: room.id, participantId: guest.id, role: 'GUEST', isPending: guest.pending, settings: room.settings });
    send(room.host.ws, guest.pending
        ? { type: 'join_request', sessionId: room.id, participant: participantOf(guest) }
        : { type: 'participant_joined', sessionId: room.id, participant: participantOf(guest) });
    // Someone arriving mid-song is brought up to date at once rather than at the next change.
    if (!guest.pending && room.lastState) send(ws, { type: 'room_state', state: stateFor(room, guest) });
    return { room, role: 'guest', guest };
}

/**
 * What one guest is shown. A guest waiting for approval sees no queue and no one else's name, and the
 * timing is the relay's own: guests measure the clock against *this* server's pongs, so a state stamped
 * with the host phone's clock would put every guest out by the gap between two devices' uptimes.
 */
function stateFor(room, guest) {
    const state = { ...room.lastState, sentAtElapsedRealtimeMs: room.lastState.sentAtElapsedRealtimeMs };
    if (!guest.pending) return state;
    const host = (state.participants || []).find((p) => p.isHost);
    return { ...state, participants: host ? [host] : [], queue: [], queueHash: '', currentIndex: 0, isPlaying: false, positionMs: 0 };
}

function fromHost(room, message) {
    switch (message.type) {
        case 'room_state': {
            if (!message.state || typeof message.state !== 'object') return;
            // Stamped on arrival, in the clock the guests use.
            room.lastState = { ...message.state, sessionId: room.id, sentAtElapsedRealtimeMs: clockMs() };
            if (message.state.settings) room.settings = sanitiseSettings(message.state.settings);
            for (const guest of room.guests.values()) send(guest.ws, { type: 'room_state', state: stateFor(room, guest) });
            break;
        }
        case 'join_decision': {
            const guest = room.guests.get(message.participantId);
            if (!guest || !guest.pending) return;
            send(guest.ws, { type: 'join_decision', sessionId: room.id, participantId: guest.id, approved: !!message.approved });
            if (message.approved) {
                guest.pending = false;
                if (room.lastState) send(guest.ws, { type: 'room_state', state: stateFor(room, guest) });
            } else {
                room.guests.delete(guest.id);
                guest.ws.close(1000, 'Not approved');
            }
            break;
        }
        case 'kick':
        case 'ban': {
            const guest = room.guests.get(message.participantId);
            if (!guest) return;
            if (message.type === 'ban') room.banned.add(guest.clientId);
            send(guest.ws, { type: message.type, sessionId: room.id, participantId: guest.id, reason: message.reason ?? null });
            room.guests.delete(guest.id);
            guest.ws.close(1000, message.type === 'ban' ? 'Banned' : 'Removed');
            break;
        }
        default:
            break;
    }
}

function fromGuest(room, guest, message) {
    switch (message.type) {
        case 'heartbeat_ping':
            send(guest.ws, {
                type: 'heartbeat_pong',
                sessionId: room.id,
                pingId: message.pingId,
                clientElapsedRealtimeMs: message.clientElapsedRealtimeMs,
                serverElapsedRealtimeMs: clockMs(),
            });
            break;
        case 'control_request':
        case 'add_track_request':
            // Only a guest who is in, and only as themselves: the id on the request is not the guest's to choose.
            if (!guest.pending && room.host) send(room.host.ws, { ...message, sessionId: room.id, participantId: guest.id });
            break;
        case 'client_leave':
            guest.ws.close(1000, 'Left');
            break;
        default:
            break;
    }
}

// ── Housekeeping: dead sockets, rooms nobody came back to ──

setInterval(() => {
    for (const ws of wss.clients) {
        if (!ws.isAlive) {
            ws.terminate();
            continue;
        }
        ws.isAlive = false;
        ws.ping();
    }
    const now = Date.now();
    for (const room of rooms.values()) {
        const hostGone = !room.host && room.hostGoneAt && now - room.hostGoneAt > HOST_GRACE_MS;
        if (hostGone || now - room.touchedAt > IDLE_ROOM_MS) {
            for (const guest of room.guests.values()) {
                send(guest.ws, { type: 'server_error', sessionId: room.id, message: 'The host ended the session', code: 'ended' });
                guest.ws.close(1000, 'Ended');
            }
            room.host?.ws.close(1000, 'Ended');
            rooms.delete(room.id);
            codes.delete(room.code);
        }
    }
}, 25_000).unref();

export { server, rooms, wss };

if (process.argv[1] && import.meta.url === new URL(`file://${process.argv[1].replace(/\\/g, '/')}`).href) {
    server.listen(PORT, () => console.log(`Exhale Together relay on :${PORT}${TOKEN ? ' (token required)' : ''}`));
}

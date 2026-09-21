import test from 'node:test';
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';
import { server, rooms, wss } from './server.mjs';

let base;
let wsBase;
test.before(async () => {
    await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    const { port } = server.address();
    base = `http://127.0.0.1:${port}`;
    wsBase = `ws://127.0.0.1:${port}/v1/together/ws`;
});
test.after(() => {
    for (const client of wss.clients) client.terminate();
    server.closeAllConnections();
    server.close();
});

const post = async (path, body) => {
    const res = await fetch(base + path, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
    return { status: res.status, body: await res.json() };
};

/** A phone: a socket with a queue of what it has been sent, and a way to wait for the next of a kind. */
async function phone(sessionId, sessionKey, name, clientId = name) {
    const ws = new WebSocket(wsBase);
    const inbox = [];
    const waiting = [];
    ws.on('message', (data) => {
        const m = JSON.parse(data.toString());
        const i = waiting.findIndex((w) => w.type === m.type);
        if (i >= 0) waiting.splice(i, 1)[0].resolve(m);
        else inbox.push(m);
    });
    const closed = new Promise((resolve) => ws.on('close', (code) => resolve(code)));
    await new Promise((resolve, reject) => { ws.on('open', resolve); ws.on('error', reject); });
    ws.send(JSON.stringify({ type: 'client_hello', protocolVersion: 1, sessionId, sessionKey, clientId, displayName: name }));
    return {
        ws,
        closed,
        send: (m) => ws.send(JSON.stringify(m)),
        next: (type, ms = 2000) => {
            const i = inbox.findIndex((m) => m.type === type);
            if (i >= 0) return Promise.resolve(inbox.splice(i, 1)[0]);
            return new Promise((resolve, reject) => {
                const entry = { type, resolve };
                waiting.push(entry);
                setTimeout(() => reject(new Error(`no ${type} within ${ms}ms`)), ms).unref();
            });
        },
        none: async (type, ms = 300) => {
            await new Promise((r) => setTimeout(r, ms));
            assert.equal(inbox.find((m) => m.type === type), undefined, `unexpected ${type}`);
        },
    };
}

async function room(settings = {}) {
    const { body } = await post('/v1/together/sessions', { hostDisplayName: 'Host', settings });
    const host = await phone(body.sessionId, body.hostKey, 'Host');
    assert.equal((await host.next('server_welcome')).role, 'HOST');
    return { ...body, host };
}

test('a guest joins with a code and the host is told', async () => {
    const r = await room();
    const resolved = await post('/v1/together/sessions/resolve', { code: r.code.toLowerCase() });
    assert.equal(resolved.status, 200);
    assert.equal(resolved.body.sessionId, r.sessionId);
    const guest = await phone(r.sessionId, resolved.body.guestKey, 'Asha');
    const welcome = await guest.next('server_welcome');
    assert.equal(welcome.role, 'GUEST');
    assert.equal(welcome.isPending, false);
    const joined = await r.host.next('participant_joined');
    assert.equal(joined.participant.name, 'Asha');
    assert.equal(joined.participant.id, welcome.participantId);
});

test('room state reaches guests stamped with the relay clock, and late joiners get it at once', async () => {
    const r = await room();
    const early = await phone(r.sessionId, r.guestKey, 'Early');
    await early.next('server_welcome');
    r.host.send({ type: 'room_state', state: { sessionId: 'ignored', hostId: 'h', participants: [], queue: [{ id: 'a', title: 'A' }], currentIndex: 0, isPlaying: true, positionMs: 4200, sentAtElapsedRealtimeMs: 999_999_999 } });
    const seen = (await early.next('room_state')).state;
    assert.equal(seen.positionMs, 4200);
    assert.equal(seen.sessionId, r.sessionId);
    assert.notEqual(seen.sentAtElapsedRealtimeMs, 999_999_999, "the host phone's clock must not leak through");
    const late = await phone(r.sessionId, r.guestKey, 'Late');
    await late.next('server_welcome');
    assert.equal((await late.next('room_state')).state.queue[0].id, 'a');
});

test('a guest cannot speak for someone else, and heartbeats are answered', async () => {
    const r = await room();
    const guest = await phone(r.sessionId, r.guestKey, 'Ben');
    const { participantId } = await guest.next('server_welcome');
    guest.send({ type: 'control_request', sessionId: 'x', participantId: 'the-host', action: { type: 'pause' } });
    const forwarded = await r.host.next('control_request');
    assert.equal(forwarded.participantId, participantId);
    assert.equal(forwarded.sessionId, r.sessionId);
    guest.send({ type: 'heartbeat_ping', sessionId: r.sessionId, pingId: 7, clientElapsedRealtimeMs: 123 });
    const pong = await guest.next('heartbeat_pong');
    assert.equal(pong.pingId, 7);
    assert.equal(pong.clientElapsedRealtimeMs, 123);
    assert.ok(pong.serverElapsedRealtimeMs > 0);
});

test('approval: a waiting guest sees nothing until the host lets them in', async () => {
    const r = await room({ requireHostApprovalToJoin: true });
    const guest = await phone(r.sessionId, r.guestKey, 'Chloe');
    const welcome = await guest.next('server_welcome');
    assert.equal(welcome.isPending, true);
    const request = await r.host.next('join_request');
    assert.equal(request.participant.isPending, true);
    r.host.send({ type: 'room_state', state: { hostId: 'h', participants: [{ id: 'h', name: 'Host', isHost: true }, { id: 'x', name: 'Someone' }], queue: [{ id: 'a', title: 'A' }], currentIndex: 0, isPlaying: true, positionMs: 1 } });
    const hidden = (await guest.next('room_state')).state;
    assert.deepEqual(hidden.queue, []);
    assert.equal(hidden.participants.length, 1, "a waiting guest is not shown the room's other names");
    guest.send({ type: 'control_request', participantId: welcome.participantId, action: { type: 'pause' } });
    await r.host.none('control_request');
    r.host.send({ type: 'join_decision', sessionId: r.sessionId, participantId: welcome.participantId, approved: true });
    assert.equal((await guest.next('join_decision')).approved, true);
    assert.equal((await guest.next('room_state')).state.queue.length, 1);
});

test('a refused guest is turned away, and a banned phone cannot come back', async () => {
    const r = await room({ requireHostApprovalToJoin: true });
    const no = await phone(r.sessionId, r.guestKey, 'Dan');
    const noId = (await no.next('server_welcome')).participantId;
    await r.host.next('join_request');
    r.host.send({ type: 'join_decision', participantId: noId, approved: false });
    assert.equal((await no.next('join_decision')).approved, false);
    await no.closed;

    const r2 = await room();
    const bad = await phone(r2.sessionId, r2.guestKey, 'Eve', 'eve-phone');
    const badId = (await bad.next('server_welcome')).participantId;
    await r2.host.next('participant_joined');
    r2.host.send({ type: 'ban', sessionId: r2.sessionId, participantId: badId, reason: 'no' });
    assert.equal((await bad.next('ban')).participantId, badId);
    await bad.closed;
    const again = await phone(r2.sessionId, r2.guestKey, 'Eve', 'eve-phone');
    assert.equal((await again.next('server_error')).code, 'banned');
});

test('leaving is announced, and a phone that rejoins replaces itself', async () => {
    const r = await room();
    const first = await phone(r.sessionId, r.guestKey, 'Fay', 'fay-phone');
    const firstId = (await first.next('server_welcome')).participantId;
    await r.host.next('participant_joined');
    const second = await phone(r.sessionId, r.guestKey, 'Fay', 'fay-phone');
    await second.next('server_welcome');
    assert.equal((await r.host.next('participant_left')).participantId, firstId);
    await first.closed;
    second.send({ type: 'client_leave', sessionId: r.sessionId, participantId: 'ignored' });
    await second.closed;
    assert.equal((await r.host.next('participant_left')).reason, 'Disconnected');
});

test('wrong keys, missing rooms and an absent host are refused with a reason', async () => {
    const r = await room();
    const wrong = await phone(r.sessionId, 'not-the-key', 'Gus');
    assert.equal((await wrong.next('server_error')).code, 'invalid');
    const missing = await phone('no-such-room', r.guestKey, 'Gus');
    assert.equal((await missing.next('server_error')).code, 'not_found');
    assert.equal((await post('/v1/together/sessions/resolve', { code: 'ZZZZZZ' })).status, 404);
    r.host.ws.close();
    await r.host.closed;
    await new Promise((resolve) => setTimeout(resolve, 100));
    const orphan = await phone(r.sessionId, r.guestKey, 'Hal');
    assert.equal((await orphan.next('server_error')).code, 'host_offline');
});

test('a host who reconnects is handed everyone who was already in', async () => {
    const r = await room();
    const guest = await phone(r.sessionId, r.guestKey, 'Ida');
    await guest.next('server_welcome');
    await r.host.next('participant_joined');
    r.host.ws.close();
    await r.host.closed;
    const back = await phone(r.sessionId, r.hostKey, 'Host');
    await back.next('server_welcome');
    assert.equal((await back.next('participant_joined')).participant.name, 'Ida');
    assert.ok(rooms.has(r.sessionId));
});

/**
 * ═══════════════════════════════════════════════════════════════
 *  messaging-engine E2E 자동 테스트 러너
 * ═══════════════════════════════════════════════════════════════
 *
 *  사용법:  node load-test/e2e/test-runner.js
 *
 *  사전 조건:
 *    - gateway-service  (:8080) 실행 중
 *    - chat-service     (:8081) 실행 중
 *    - presence-service (:8083) + Redis 실행 중  (없으면 presence 테스트 skip)
 *
 *  전체 시나리오:
 *    1. Auth (토큰 발급)
 *    2. ChatRoom CRUD (생성, 조회, 입장, 나가기)
 *    3. Message 전송 / 조회 / 페이징
 *    4. Reply (답장 전송, reply 필드 검증)
 *    5. Delete (삭제, 재삭제 400, 타인삭제 403, 답장 replyToContent 연쇄 업데이트)
 *    6. WebSocket 실시간 (메시지 수신, 삭제 이벤트, 답장 broadcast)
 *    7. ReadReceipt (읽음 표시, 안읽음 카운트)
 *    8. Presence (heartbeat, online, typing) — Redis 필요
 */
const SockJS = require('sockjs-client');
const Stomp = require('stompjs');

const BASE = 'http://localhost:8080';

// ─── Test Framework ───
let passed = 0, failed = 0, skipped = 0;
const results = [];
function log(msg) { console.log(`  ${msg}`); }
function pass(name) { passed++; results.push({ s: '✅', name }); console.log(`  ✅ ${name}`); }
function fail(name, reason) { failed++; results.push({ s: '❌', name, reason }); console.log(`  ❌ ${name} — ${reason}`); }
function skip(name, reason) { skipped++; results.push({ s: '⏭️', name, reason }); console.log(`  ⏭️ ${name} — ${reason}`); }
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function api(method, path, body) {
    const url = `${BASE}${path}`;
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);
    const text = await res.text();
    try { return { status: res.status, data: JSON.parse(text) }; } catch { return { status: res.status, data: text }; }
}

function connectStomp(label) {
    return new Promise((resolve, reject) => {
        const socket = new SockJS(`${BASE}/ws`);
        const client = Stomp.over(socket);
        client.debug = () => {};
        client.connect({}, () => resolve(client), (err) => reject(new Error(`[${label}] WS fail: ${err}`)));
    });
}

async function isServiceUp(port) {
    try {
        const r = await fetch(`http://localhost:${port}/actuator/health`);
        return r.ok;
    } catch { return false; }
}

// ═══════════════════════════════════════
async function run() {
    console.log('\n═══════════════════════════════════════════════════');
    console.log('  messaging-engine E2E 자동 테스트');
    console.log('═══════════════════════════════════════════════════\n');

    // 서비스 상태 확인
    const gw = await isServiceUp(8080);
    const chat = await isServiceUp(8081);
    const presence = await isServiceUp(8083);
    log(`Gateway: ${gw?'UP':'DOWN'}  Chat: ${chat?'UP':'DOWN'}  Presence: ${presence?'UP':'DOWN'}`);
    if (!gw || !chat) { fail('서비스 상태', 'gateway 또는 chat-service가 실행 중이 아닙니다'); return printSummary(); }

    const userA = { id: 80001, name: 'TestUserA' };
    const userB = { id: 80002, name: 'TestUserB' };

    // ═══════════════════════════════════════
    // SECTION 1: Auth
    // ═══════════════════════════════════════
    console.log('\n── 1. Auth ──');
    try {
        const ra = await api('POST', '/api/auth/token', { userId: userA.id, username: userA.name });
        const rb = await api('POST', '/api/auth/token', { userId: userB.id, username: userB.name });
        userA.token = ra.data.data.accessToken;
        userB.token = rb.data.data.accessToken;
        if (userA.token && userB.token) pass('JWT 토큰 발급 (2명)');
        else fail('JWT 토큰 발급', `A=${!!userA.token} B=${!!userB.token}`);
    } catch (e) { fail('JWT 토큰 발급', e.message); return printSummary(); }

    try {
        const r = await api('POST', '/api/auth/token', { username: 'no-id' });
        if (r.status === 400) pass('토큰 발급 - userId 누락 시 400');
        else fail('토큰 발급 유효성', `expected 400, got ${r.status}`);
    } catch (e) { fail('토큰 유효성', e.message); }

    // ═══════════════════════════════════════
    // SECTION 2: ChatRoom CRUD
    // ═══════════════════════════════════════
    console.log('\n── 2. ChatRoom CRUD ──');
    let roomId;

    // 생성
    try {
        const r = await api('POST', '/api/chat/rooms', { name: `E2E_${Date.now()}`, description: '테스트방', creatorId: userA.id });
        roomId = r.data.data.id;
        if (roomId) pass(`채팅방 생성 (id=${roomId})`);
        else fail('채팅방 생성', JSON.stringify(r.data));
    } catch (e) { fail('채팅방 생성', e.message); return printSummary(); }

    // 이름 없이 생성 → 400
    try {
        const r = await api('POST', '/api/chat/rooms', { description: '이름없음', creatorId: userA.id });
        if (r.status === 400) pass('채팅방 생성 - name 누락 시 400');
        else fail('채팅방 생성 유효성', `expected 400, got ${r.status}`);
    } catch (e) { fail('채팅방 유효성', e.message); }

    // 내 채팅방 목록 조회 (카카오톡 모델 — 참여 중인 방만)
    try {
        const r = await api('GET', `/api/chat/rooms?userId=${userA.id}`);
        if (r.status === 200 && Array.isArray(r.data.data) && r.data.data.some(room => room.id === roomId))
            pass('내 채팅방 목록 조회');
        else fail('내 채팅방 목록 조회', `status=${r.status}`);
    } catch (e) { fail('내 채팅방 목록', e.message); }

    // 상세 조회
    try {
        const r = await api('GET', `/api/chat/rooms/${roomId}`);
        if (r.status === 200 && r.data.data.id === roomId) pass('채팅방 상세 조회');
        else fail('채팅방 상세 조회', JSON.stringify(r.data));
    } catch (e) { fail('채팅방 상세', e.message); }

    // 존재하지 않는 방 조회 → 404
    try {
        const r = await api('GET', '/api/chat/rooms/999999');
        if (r.status === 404) pass('존재하지 않는 채팅방 → 404');
        else fail('채팅방 404', `expected 404, got ${r.status}`);
    } catch (e) { fail('채팅방 404', e.message); }

    // 초대 (creator가 userB를 초대)
    try {
        const r = await api('POST', `/api/chat/rooms/${roomId}/invite`, {
            inviterId: userA.id, userIds: [userB.id]
        });
        if (r.status === 200) pass('채팅방 초대 (A가 B 초대)');
        else fail('채팅방 초대', `status=${r.status}`);
    } catch (e) { fail('채팅방 초대', e.message); }

    // 초대된 사용자가 방 목록에 보이는지 확인
    try {
        const r = await api('GET', `/api/chat/rooms?userId=${userB.id}`);
        const found = (r.data.data || []).some(room => room.id === roomId);
        if (r.status === 200 && found) pass('초대 후 B의 채팅방 목록에 표시');
        else fail('초대 후 목록', JSON.stringify(r.data));
    } catch (e) { fail('초대 후 목록', e.message); }

    // ═══════════════════════════════════════
    // SECTION 3: WebSocket 연결 + 구독
    // ═══════════════════════════════════════
    console.log('\n── 3. WebSocket 연결 ──');
    let clientA, clientB;
    const eventsA = { messages: [], deletes: [] };
    const eventsB = { messages: [], deletes: [] };

    try {
        clientA = await connectStomp('A');
        clientB = await connectStomp('B');
        pass('WebSocket STOMP 연결 (2명)');
    } catch (e) { fail('WebSocket 연결', e.message); return printSummary(); }

    clientA.subscribe(`/topic/rooms/${roomId}/messages`, f => eventsA.messages.push(JSON.parse(f.body)));
    clientA.subscribe(`/topic/rooms/${roomId}/delete`, f => eventsA.deletes.push(JSON.parse(f.body)));
    clientB.subscribe(`/topic/rooms/${roomId}/messages`, f => eventsB.messages.push(JSON.parse(f.body)));
    clientB.subscribe(`/topic/rooms/${roomId}/delete`, f => eventsB.deletes.push(JSON.parse(f.body)));
    await sleep(500);
    pass('채팅방 토픽 구독 (/messages, /delete)');

    // ═══════════════════════════════════════
    // SECTION 4: Message 전송 + 조회
    // ═══════════════════════════════════════
    console.log('\n── 4. 메시지 전송 / 조회 ──');
    let msg1Id, msg2Id, msg3Id;

    // 기본 전송
    try {
        const r = await api('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userA.id, senderName: userA.name, content: '첫번째 메시지', type: 'TEXT'
        });
        msg1Id = r.data.data.id;
        if (msg1Id && r.data.data.content === '첫번째 메시지' && r.data.data.status === 'ACTIVE')
            pass(`메시지 전송 (id=${msg1Id})`);
        else fail('메시지 전송', JSON.stringify(r.data));
    } catch (e) { fail('메시지 전송', e.message); }

    // 실시간 수신 검증
    await sleep(1000);
    if (eventsB.messages.find(m => m.id === msg1Id)) pass('WebSocket: 상대방 실시간 수신');
    else fail('WebSocket 실시간 수신', `B events: ${eventsB.messages.length}건`);

    // 두번째, 세번째 메시지
    try {
        const r2 = await api('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userB.id, senderName: userB.name, content: '두번째 메시지', type: 'TEXT'
        });
        msg2Id = r2.data.data.id;
        const r3 = await api('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userA.id, senderName: userA.name, content: '세번째 메시지', type: 'TEXT'
        });
        msg3Id = r3.data.data.id;
        if (msg2Id && msg3Id) pass('추가 메시지 2건 전송');
        else fail('추가 메시지', `msg2=${msg2Id} msg3=${msg3Id}`);
    } catch (e) { fail('추가 메시지', e.message); }

    // 비회원 전송 → 에러
    try {
        const r = await api('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: 99999, senderName: 'outsider', content: '침입', type: 'TEXT'
        });
        if (r.status === 403) pass('비회원 메시지 전송 → 403');
        else fail('비회원 전송 차단', `expected 403, got ${r.status}`);
    } catch (e) { fail('비회원 차단', e.message); }

    // 페이징 조회
    try {
        const r = await api('GET', `/api/chat/rooms/${roomId}/messages?page=0&size=2`);
        const page = r.data.data;
        if (page.content.length === 2 && page.totalElements >= 3)
            pass(`메시지 페이징 조회 (size=2, total=${page.totalElements})`);
        else fail('메시지 페이징', `content.length=${page.content?.length}, total=${page.totalElements}`);
    } catch (e) { fail('메시지 페이징', e.message); }

    // 빈 내용 전송 → 400
    try {
        const r = await api('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userA.id, senderName: userA.name, content: '', type: 'TEXT'
        });
        if (r.status === 400) pass('빈 메시지 전송 → 400');
        else fail('빈 메시지 유효성', `expected 400, got ${r.status}`);
    } catch (e) { fail('빈 메시지', e.message); }

    // ═══════════════════════════════════════
    // SECTION 5: Reply (답장)
    // ═══════════════════════════════════════
    console.log('\n── 5. 답장 (Reply) ──');
    let replyMsgId;

    // 답장 전송
    eventsA.messages.length = 0;
    try {
        const r = await api('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userB.id, senderName: userB.name, content: '답장입니다', type: 'TEXT', replyToId: msg1Id
        });
        replyMsgId = r.data.data.id;
        const d = r.data.data;
        if (d.replyToId === msg1Id && d.replyToContent === '첫번째 메시지' && d.replyToSender === userA.name)
            pass('답장 전송 — replyTo 필드 정상');
        else fail('답장 필드', `replyToId=${d.replyToId}, content=${d.replyToContent}, sender=${d.replyToSender}`);
    } catch (e) { fail('답장 전송', e.message); }

    // WebSocket broadcast에 reply 필드 포함 검증
    await sleep(1000);
    try {
        const wsReply = eventsA.messages.find(m => m.id === replyMsgId);
        if (wsReply && wsReply.replyToId === msg1Id && wsReply.replyToContent)
            pass('WebSocket: 답장 broadcast에 reply 필드 포함');
        else fail('WS 답장 broadcast', `found=${!!wsReply}, replyToId=${wsReply?.replyToId}`);
    } catch (e) { fail('WS 답장', e.message); }

    // 존재하지 않는 메시지에 답장
    try {
        const r = await api('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userA.id, senderName: userA.name, content: '유령답장', type: 'TEXT', replyToId: 999999
        });
        if (r.status === 404) pass('존재하지 않는 메시지에 답장 → 404');
        else fail('유령 답장', `expected 404, got ${r.status}`);
    } catch (e) { fail('유령 답장', e.message); }

    // ═══════════════════════════════════════
    // SECTION 6: Delete (삭제)
    // ═══════════════════════════════════════
    console.log('\n── 6. 삭제 (Delete) ──');

    // 타인 삭제 → 403
    try {
        const r = await api('DELETE', `/api/chat/rooms/${roomId}/messages/${msg1Id}?userId=${userB.id}`);
        if (r.status === 403) pass('타인 메시지 삭제 → 403');
        else fail('타인 삭제', `expected 403, got ${r.status}`);
    } catch (e) { fail('타인 삭제', e.message); }

    // 정상 삭제 + 실시간 이벤트
    eventsB.deletes.length = 0;
    try {
        const r = await api('DELETE', `/api/chat/rooms/${roomId}/messages/${msg1Id}?userId=${userA.id}`);
        if (r.status === 200) pass('본인 메시지 삭제 → 200');
        else fail('메시지 삭제', `expected 200, got ${r.status}`);
    } catch (e) { fail('메시지 삭제', e.message); }

    await sleep(1000);
    if (eventsB.deletes.find(d => d.messageId === msg1Id)) pass('WebSocket: 삭제 이벤트 실시간 수신');
    else fail('WS 삭제 이벤트', `B deletes: ${JSON.stringify(eventsB.deletes)}`);

    // 재삭제 → 400
    try {
        const r = await api('DELETE', `/api/chat/rooms/${roomId}/messages/${msg1Id}?userId=${userA.id}`);
        if (r.status === 400) pass('이미 삭제된 메시지 재삭제 → 400');
        else fail('재삭제', `expected 400, got ${r.status}`);
    } catch (e) { fail('재삭제', e.message); }

    // 삭제 후 조회 → status=DELETED
    try {
        const r = await api('GET', `/api/chat/rooms/${roomId}/messages?page=0&size=50`);
        const del = r.data.data.content.find(m => m.id === msg1Id);
        if (del && del.status === 'DELETED') pass('삭제된 메시지 조회 → status=DELETED');
        else fail('삭제 상태 조회', `status=${del?.status}`);
    } catch (e) { fail('삭제 조회', e.message); }

    // 핵심: 답장의 replyToContent가 "삭제된 메시지"로 연쇄 업데이트
    try {
        const r = await api('GET', `/api/chat/rooms/${roomId}/messages?page=0&size=50`);
        const reply = r.data.data.content.find(m => m.id === replyMsgId);
        if (reply && reply.replyToContent === '삭제된 메시지')
            pass('답장의 replyToContent → "삭제된 메시지" 연쇄 업데이트');
        else fail('답장 연쇄 업데이트', `replyToContent="${reply?.replyToContent}"`);
    } catch (e) { fail('답장 연쇄', e.message); }

    // ═══════════════════════════════════════
    // SECTION 7: Read Receipt (읽음 표시)
    // ═══════════════════════════════════════
    console.log('\n── 7. 읽음 표시 (Read Receipt) ──');

    // 안읽음 카운트 (읽기 전)
    try {
        const r = await api('GET', `/api/chat/rooms/${roomId}/unread?userId=${userB.id}`);
        const count = r.data.data;
        if (typeof count === 'number' && count > 0) pass(`안읽음 카운트 (읽기 전): ${count}건`);
        else fail('안읽음 카운트', `value=${count}`);
    } catch (e) { fail('안읽음 카운트', e.message); }

    // 최신 메시지 ID 조회 (읽음 표시 기준으로 사용)
    let latestMsgId;
    try {
        const r = await api('GET', `/api/chat/rooms/${roomId}/messages?page=0&size=1`);
        latestMsgId = r.data.data.content[0].id;
    } catch (e) { latestMsgId = replyMsgId; }

    // 읽음 표시 (최신 메시지까지)
    try {
        const r = await api('POST', `/api/chat/rooms/${roomId}/read`, { userId: userB.id, lastMessageId: latestMsgId });
        if (r.status === 200) pass('읽음 표시 (markAsRead)');
        else fail('읽음 표시', `status=${r.status}`);
    } catch (e) { fail('읽음 표시', e.message); }

    // 안읽음 카운트 (읽은 후)
    try {
        const r = await api('GET', `/api/chat/rooms/${roomId}/unread?userId=${userB.id}`);
        const count = r.data.data;
        if (count === 0) pass('안읽음 카운트 (읽은 후): 0건');
        else fail('읽은 후 안읽음', `expected 0, got ${count}`);
    } catch (e) { fail('읽은 후 안읽음', e.message); }

    // 새 메시지 후 안읽음 +1
    try {
        await api('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userA.id, senderName: userA.name, content: '읽음 후 새 메시지', type: 'TEXT'
        });
        const r = await api('GET', `/api/chat/rooms/${roomId}/unread?userId=${userB.id}`);
        if (r.data.data >= 1) pass(`새 메시지 후 안읽음 증가: ${r.data.data}건`);
        else fail('안읽음 증가', `expected >=1, got ${r.data.data}`);
    } catch (e) { fail('안읽음 증가', e.message); }

    // ═══════════════════════════════════════
    // SECTION 8: Presence (접속 상태) — Redis 필요
    // ═══════════════════════════════════════
    console.log('\n── 8. Presence (접속 상태) ──');
    if (!presence) {
        skip('Presence 전체', 'presence-service (:8083)가 실행 중이 아닙니다');
    } else {
        // Heartbeat
        try {
            const r = await api('POST', '/api/presence/heartbeat', { userId: userA.id });
            if (r.status === 200) pass('Heartbeat 전송');
            else fail('Heartbeat', `status=${r.status}`);
        } catch (e) { fail('Heartbeat', e.message); }

        // Online 확인
        try {
            const r = await api('GET', `/api/presence/users/${userA.id}`);
            if (r.status === 200 && (r.data.data?.online === true || r.data.data?.status === 'ONLINE'))
                pass('사용자 접속 상태 확인 (ONLINE)');
            else fail('접속 상태', JSON.stringify(r.data));
        } catch (e) { fail('접속 상태', e.message); }

        // 전체 온라인 목록
        try {
            const r = await api('GET', '/api/presence/users/online');
            if (r.status === 200) pass('전체 온라인 사용자 조회');
            else fail('온라인 목록', `status=${r.status}`);
        } catch (e) { fail('온라인 목록', e.message); }

        // Typing
        try {
            const r = await api('POST', '/api/presence/typing', { userId: userA.id, chatRoomId: roomId });
            if (r.status === 200) pass('타이핑 상태 전송');
            else fail('타이핑', `status=${r.status}`);
        } catch (e) { fail('타이핑', e.message); }

        // Typing 유저 조회
        try {
            const r = await api('GET', `/api/presence/typing/${roomId}`);
            if (r.status === 200 && Array.isArray(r.data.data)) pass('타이핑 유저 조회');
            else fail('타이핑 조회', JSON.stringify(r.data));
        } catch (e) { fail('타이핑 조회', e.message); }

        // Disconnect
        try {
            const r = await api('DELETE', `/api/presence/users/${userA.id}`);
            if (r.status === 200) pass('사용자 접속 해제');
            else fail('접속 해제', `status=${r.status}`);
        } catch (e) { fail('접속 해제', e.message); }
    }

    // ═══════════════════════════════════════
    // SECTION 9: ChatRoom 나가기
    // ═══════════════════════════════════════
    console.log('\n── 9. 채팅방 나가기 ──');
    try {
        const r = await api('DELETE', `/api/chat/rooms/${roomId}/members/${userB.id}`);
        if (r.status === 200) pass('채팅방 나가기');
        else fail('채팅방 나가기', `status=${r.status}`);
    } catch (e) { fail('채팅방 나가기', e.message); }

    // 나간 후 내 채팅방 목록에서 사라지는지 확인
    try {
        const r = await api('GET', `/api/chat/rooms?userId=${userB.id}`);
        const myRooms = r.data.data || [];
        const found = myRooms.some(room => room.id === roomId);
        if (r.status === 200 && !found) pass('나간 후 채팅방 목록에서 제거 확인');
        else fail('나간 후 목록 제거', `room still in list: ${JSON.stringify(myRooms.map(r => r.id))}`);
    } catch (e) { fail('나간 후 목록 제거', e.message); }

    // 나간 후 메시지 전송 → 에러
    try {
        const r = await api('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userB.id, senderName: userB.name, content: '나간후 전송', type: 'TEXT'
        });
        if (r.status === 403) pass('나간 후 메시지 전송 → 403');
        else fail('나간 후 전송', `expected 403, got ${r.status}`);
    } catch (e) { fail('나간 후 전송', e.message); }

    // Cleanup
    clientA.disconnect();
    clientB.disconnect();
    printSummary();
}

function printSummary() {
    console.log('\n═══════════════════════════════════════════════════');
    console.log('  📊 테스트 결과');
    console.log('═══════════════════════════════════════════════════');
    results.forEach(r => {
        const detail = r.reason ? ` — ${r.reason}` : '';
        console.log(`  ${r.s} ${r.name}${detail}`);
    });
    console.log(`\n  ✅ ${passed} passed  |  ❌ ${failed} failed  |  ⏭️ ${skipped} skipped  |  Total: ${passed+failed+skipped}`);
    console.log('═══════════════════════════════════════════════════\n');
    process.exit(failed > 0 ? 1 : 0);
}

run().catch(e => { console.error('Fatal:', e); process.exit(1); });

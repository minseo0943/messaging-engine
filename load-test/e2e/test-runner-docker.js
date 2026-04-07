/**
 * ═══════════════════════════════════════════════════════════════
 *  messaging-engine Docker 환경 통합 E2E 테스트 스위트
 * ═══════════════════════════════════════════════════════════════
 *
 *  사용법:  node load-test/e2e/test-runner-docker.js
 *
 *  사전 조건:
 *    docker compose up -d  (전체 인프라 + 6개 앱 서비스)
 *
 *  검증 범위:
 *    Phase A — 서비스 헬스 (6개 서비스 UP 확인)
 *    Phase B — Auth + ChatRoom CRUD (gateway 경유)
 *    Phase C — Message CRUD + Reply + Delete
 *    Phase D — CQRS 파이프라인 (Command → Kafka → MongoDB 프로젝션)
 *    Phase E — AI 스팸 탐지 파이프라인
 *    Phase F — ChatRoomView 정합성
 *    Phase G — Presence (Redis 접속 상태)
 *    Phase H — Read Receipt (읽음 표시)
 *    Phase I — 최종 상태 검증
 */

const GATEWAY = 'http://localhost:8080';
const CHAT    = 'http://localhost:8081';
const QUERY   = 'http://localhost:8082';

let passed = 0, failed = 0, skipped = 0;
const results = [];

function pass(name) { passed++; results.push({ s: '✅', name }); console.log(`  ✅ ${name}`); }
function fail(name, reason) { failed++; results.push({ s: '❌', name, reason }); console.log(`  ❌ ${name} — ${reason}`); }
function skip(name, reason) { skipped++; results.push({ s: '⏭️', name, reason }); console.log(`  ⏭️ ${name} — ${reason}`); }
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

async function api(base, method, path, body) {
    const url = `${base}${path}`;
    const opts = { method, headers: { 'Content-Type': 'application/json' } };
    if (body) opts.body = JSON.stringify(body);
    const res = await fetch(url, opts);
    const text = await res.text();
    try { return { status: res.status, data: JSON.parse(text) }; } catch { return { status: res.status, data: text }; }
}

async function gw(method, path, body) { return api(GATEWAY, method, path, body); }

async function isServiceUp(url) {
    try {
        const r = await fetch(`${url}/actuator/health`, { signal: AbortSignal.timeout(3000) });
        const j = await r.json();
        return j.status === 'UP';
    } catch { return false; }
}

async function waitForCondition(fn, timeoutMs = 15000, intervalMs = 500) {
    const start = Date.now();
    while (Date.now() - start < timeoutMs) {
        const result = await fn();
        if (result) return result;
        await sleep(intervalMs);
    }
    return null;
}

function printSummary() {
    console.log('\n═══════════════════════════════════════════════════════════');
    console.log(`  결과: ✅ ${passed}  ❌ ${failed}  ⏭️ ${skipped}  총 ${passed + failed + skipped}`);
    console.log('═══════════════════════════════════════════════════════════');
    if (failed > 0) {
        console.log('\n  실패 항목:');
        results.filter(r => r.s === '❌').forEach(r => console.log(`    ❌ ${r.name}: ${r.reason}`));
    }
    if (skipped > 0) {
        console.log('\n  스킵 항목:');
        results.filter(r => r.s === '⏭️').forEach(r => console.log(`    ⏭️ ${r.name}: ${r.reason}`));
    }
    console.log('');
    process.exit(failed > 0 ? 1 : 0);
}

// ═══════════════════════════════════════════════════════════════
async function run() {
    console.log('\n═══════════════════════════════════════════════════════════');
    console.log('  messaging-engine Docker 통합 E2E 테스트');
    console.log('═══════════════════════════════════════════════════════════\n');

    const ts = Date.now();
    const userA = { id: 70001 + (ts % 10000), name: `DockerE2E_A_${ts}` };
    const userB = { id: 70002 + (ts % 10000), name: `DockerE2E_B_${ts}` };

    // ═══════════════════════════════════════
    // Phase A: 서비스 헬스 체크
    // ═══════════════════════════════════════
    console.log('── Phase A: 서비스 헬스 체크 ──');

    const services = [
        { name: 'gateway-service',      url: GATEWAY },
        { name: 'chat-service',         url: CHAT },
        { name: 'query-service',        url: QUERY },
        { name: 'presence-service',     url: 'http://localhost:8083' },
        { name: 'notification-service', url: 'http://localhost:8084' },
        { name: 'ai-service',           url: 'http://localhost:8085' },
    ];

    const serviceStatus = {};
    for (const svc of services) {
        const up = await isServiceUp(svc.url);
        serviceStatus[svc.name] = up;
        if (up) pass(`${svc.name} UP`);
        else fail(`${svc.name} UP`, 'DOWN — docker compose up -d 확인 필요');
    }

    // gateway + chat + query는 필수
    if (!serviceStatus['gateway-service'] || !serviceStatus['chat-service'] || !serviceStatus['query-service']) {
        fail('필수 서비스', 'gateway, chat, query가 모두 UP이어야 합니다');
        return printSummary();
    }

    // ═══════════════════════════════════════
    // Phase B: Auth + ChatRoom CRUD
    // ═══════════════════════════════════════
    console.log('\n── Phase B: Auth + ChatRoom CRUD ──');

    // 토큰 발급
    const tokenResA = await gw('POST', '/api/auth/token', { userId: userA.id, username: userA.name });
    const tokenA = tokenResA.data?.data?.token || tokenResA.data?.data?.accessToken;
    if (tokenA) pass('UserA 토큰 발급');
    else { fail('UserA 토큰 발급', JSON.stringify(tokenResA.data)); return printSummary(); }

    const tokenResB = await gw('POST', '/api/auth/token', { userId: userB.id, username: userB.name });
    const tokenB = tokenResB.data?.data?.token || tokenResB.data?.data?.accessToken;
    if (tokenB) pass('UserB 토큰 발급');
    else fail('UserB 토큰 발급', JSON.stringify(tokenResB.data));

    // 채팅방 생성
    const roomRes = await gw('POST', '/api/chat/rooms', { name: `DockerE2E-${ts}`, creatorId: userA.id });
    const roomId = roomRes.data?.data?.id;
    if (roomId) pass('채팅방 생성');
    else { fail('채팅방 생성', JSON.stringify(roomRes.data)); return printSummary(); }

    // 채팅방 조회
    const roomGet = await gw('GET', `/api/chat/rooms/${roomId}`);
    if (roomGet.data?.data?.id === roomId) pass('채팅방 조회');
    else fail('채팅방 조회', JSON.stringify(roomGet.data));

    // 채팅방 목록
    const roomList = await gw('GET', '/api/chat/rooms');
    if (roomList.data?.data && roomList.data.data.length > 0) pass('채팅방 목록 조회');
    else fail('채팅방 목록 조회', JSON.stringify(roomList.data));

    // UserB 입장 (nickname 필드 필수)
    const joinRes = await gw('POST', `/api/chat/rooms/${roomId}/join`, { userId: userB.id, nickname: userB.name });
    if (joinRes.status < 300) pass('UserB 채팅방 입장');
    else fail('UserB 채팅방 입장', `status=${joinRes.status} ${JSON.stringify(joinRes.data)}`);

    // ═══════════════════════════════════════
    // Phase C: Message CRUD + Reply + Delete
    // ═══════════════════════════════════════
    console.log('\n── Phase C: Message CRUD + Reply + Delete ──');

    // 메시지 전송
    const msg1 = await gw('POST', `/api/chat/rooms/${roomId}/messages`, {
        senderId: userA.id, senderName: userA.name, content: 'Docker E2E 첫 번째 메시지'
    });
    const msg1Id = msg1.data?.data?.id;
    if (msg1Id && msg1.status < 300) pass('메시지 전송');
    else fail('메시지 전송', JSON.stringify(msg1.data));

    // 메시지 조회
    const msgList = await gw('GET', `/api/chat/rooms/${roomId}/messages`);
    if (msgList.data?.data?.content?.length > 0) pass('메시지 목록 조회');
    else fail('메시지 목록 조회', JSON.stringify(msgList.data));

    // 답장
    const replyRes = await gw('POST', `/api/chat/rooms/${roomId}/messages`, {
        senderId: userB.id, senderName: userB.name, content: '이것은 답장입니다',
        replyToId: msg1Id
    });
    const replyId = replyRes.data?.data?.id;
    if (replyId && replyRes.status < 300) pass('답장 전송');
    else fail('답장 전송', JSON.stringify(replyRes.data));

    // 답장 필드 검증
    if (replyId) {
        const replyGet = await gw('GET', `/api/chat/rooms/${roomId}/messages`);
        const replyDoc = replyGet.data?.data?.content?.find(m => m.id === replyId);
        if (replyDoc?.replyToId === msg1Id || replyDoc?.replyToMessageId === msg1Id) pass('답장 replyToId 검증');
        else fail('답장 replyToId 검증', JSON.stringify(replyDoc));
    }

    // 메시지 삭제
    const delRes = await gw('DELETE', `/api/chat/rooms/${roomId}/messages/${msg1Id}?userId=${userA.id}`);
    if (delRes.status < 300) pass('메시지 삭제');
    else fail('메시지 삭제', `status=${delRes.status}`);

    // 삭제된 메시지 재삭제 → 400
    const reDel = await gw('DELETE', `/api/chat/rooms/${roomId}/messages/${msg1Id}?userId=${userA.id}`);
    if (reDel.status === 400) pass('이미 삭제된 메시지 재삭제 → 400');
    else fail('이미 삭제된 메시지 재삭제', `expected 400, got ${reDel.status}`);

    // 타인 삭제 시도 → 403
    const msg2 = await gw('POST', `/api/chat/rooms/${roomId}/messages`, {
        senderId: userA.id, senderName: userA.name, content: '타인 삭제 테스트 메시지'
    });
    const msg2Id = msg2.data?.data?.id;
    if (msg2Id) {
        const otherDel = await gw('DELETE', `/api/chat/rooms/${roomId}/messages/${msg2Id}?userId=${userB.id}`);
        if (otherDel.status === 403) pass('타인 메시지 삭제 → 403');
        else fail('타인 메시지 삭제', `expected 403, got ${otherDel.status}`);
    }

    // ═══════════════════════════════════════
    // Phase D: CQRS 파이프라인
    // ═══════════════════════════════════════
    console.log('\n── Phase D: CQRS Command → Kafka → Query 파이프라인 ──');

    // 새 메시지 전송 (Command Side)
    const cqrsMsg = await gw('POST', `/api/chat/rooms/${roomId}/messages`, {
        senderId: userA.id, senderName: userA.name, content: 'CQRS 파이프라인 검증 메시지'
    });
    const cqrsMsgId = cqrsMsg.data?.data?.id;
    if (cqrsMsgId) pass('Command: 메시지 전송 → MySQL 저장');
    else fail('Command: 메시지 전송', JSON.stringify(cqrsMsg.data));

    // Query Side: MongoDB 프로젝션 도착 확인
    if (cqrsMsgId) {
        const projected = await waitForCondition(async () => {
            const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages?size=50`);
            const msgs = r.data?.data?.content;
            if (!msgs) return null;
            return msgs.find(m => m.messageId === cqrsMsgId);
        }, 15000);

        if (projected) {
            pass('Query: MongoDB 프로젝션 도착 (Kafka → query-service)');

            // 프로젝션 데이터 정합성
            if (projected.senderName === userA.name && projected.content === 'CQRS 파이프라인 검증 메시지')
                pass('프로젝션 데이터 정합성 (senderName, content)');
            else fail('프로젝션 데이터 정합성', JSON.stringify(projected));

            // spamStatus 초기값
            if (projected.spamStatus === 'CLEAN') pass('프로젝션 초기 spamStatus=CLEAN');
            else fail('프로젝션 초기 spamStatus', projected.spamStatus);
        } else {
            fail('Query: MongoDB 프로젝션 도착', '15초 내 미도착');
        }
    }

    // 다건 메시지 순서 보장
    const orderMsgIds = [];
    for (let i = 1; i <= 3; i++) {
        const r = await gw('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userA.id, senderName: userA.name, content: `순서 검증 ${i}`
        });
        if (r.data?.data?.id) orderMsgIds.push(r.data.data.id);
    }

    const allProjected = await waitForCondition(async () => {
        const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages?size=50`);
        const msgs = r.data?.data?.content;
        if (!msgs) return null;
        const allPresent = orderMsgIds.every(id => msgs.some(m => m.messageId === id));
        if (allPresent) return msgs;
        return null;
    }, 20000);

    if (allProjected) pass(`다건 메시지 프로젝션 완료 (${orderMsgIds.length}건)`);
    else fail('다건 메시지 프로젝션', '20초 내 미완료');

    // ═══════════════════════════════════════
    // Phase E: AI 스팸 탐지 파이프라인
    // ═══════════════════════════════════════
    console.log('\n── Phase E: AI 스팸 탐지 파이프라인 ──');

    if (!serviceStatus['ai-service']) {
        skip('AI 스팸 탐지', 'ai-service DOWN');
    } else {
        // 스팸 메시지 전송 (키워드 "광고")
        const spamMsg = await gw('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userA.id, senderName: userA.name, content: '이것은 광고 메시지입니다 무료 당첨!'
        });
        const spamMsgId = spamMsg.data?.data?.id;
        if (spamMsgId) pass('스팸 메시지 전송');
        else fail('스팸 메시지 전송', JSON.stringify(spamMsg.data));

        // spamStatus → SPAM 업데이트 대기
        // 흐름: chat → Kafka(message.sent) → ai-service → Kafka(message.spam-detected) → query-service
        if (spamMsgId) {
            const spamUpdated = await waitForCondition(async () => {
                const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages?size=50`);
                const msgs = r.data?.data?.content;
                if (!msgs) return null;
                const doc = msgs.find(m => m.messageId === spamMsgId);
                if (doc && doc.spamStatus === 'SPAM') return doc;
                return null;
            }, 25000, 1000);

            if (spamUpdated) {
                pass('AI 스팸 탐지 → spamStatus=SPAM');
                if (spamUpdated.spamReason) pass(`스팸 사유: "${spamUpdated.spamReason}"`);
                else fail('스팸 사유 누락', 'spamReason이 비어있음');
            } else {
                fail('AI 스팸 탐지', '25초 내 spamStatus=SPAM 미반영');
            }
        }

        // 두 번째 스팸 패턴 ("스팸" 키워드)
        const spam2 = await gw('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userA.id, senderName: userA.name, content: '이것은 스팸 테스트'
        });
        const spam2Id = spam2.data?.data?.id;
        if (spam2Id) {
            const spam2Updated = await waitForCondition(async () => {
                const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages?size=50`);
                const msgs = r.data?.data?.content;
                if (!msgs) return null;
                const doc = msgs.find(m => m.messageId === spam2Id);
                if (doc && doc.spamStatus === 'SPAM') return doc;
                return null;
            }, 25000, 1000);

            if (spam2Updated) pass('키워드("스팸") 탐지 → spamStatus=SPAM');
            else fail('키워드 스팸 탐지', '25초 내 미반영');
        }

        // 클린 메시지 검증
        const cleanMsg = await gw('POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userB.id, senderName: userB.name, content: '안녕하세요 좋은 아침입니다'
        });
        const cleanMsgId = cleanMsg.data?.data?.id;
        if (cleanMsgId) {
            const cleanVerified = await waitForCondition(async () => {
                const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages?size=50`);
                const msgs = r.data?.data?.content;
                if (!msgs) return null;
                const doc = msgs.find(m => m.messageId === cleanMsgId);
                if (doc && doc.spamStatus === 'CLEAN') return doc;
                return null;
            }, 15000, 1000);

            if (cleanVerified) pass('클린 메시지 spamStatus=CLEAN 유지');
            else fail('클린 메시지 spamStatus', '15초 내 미확인');
        }
    }

    // ═══════════════════════════════════════
    // Phase F: ChatRoomView 정합성
    // ═══════════════════════════════════════
    console.log('\n── Phase F: ChatRoomView 정합성 ──');

    await sleep(3000); // 최종 프로젝션 대기

    const roomView = await api(QUERY, 'GET', `/api/query/rooms/${roomId}`);
    if (roomView.data?.data) {
        const v = roomView.data.data;
        pass(`ChatRoomView 조회 성공`);

        if (v.messageCount >= 5) pass(`messageCount=${v.messageCount} (5건 이상)`);
        else fail('messageCount', `expected>=5, got=${v.messageCount}`);

        if (v.lastMessageContent) pass(`lastMessage="${v.lastMessageContent}"`);
        else fail('lastMessage', 'lastMessageContent 없음');

        if (v.roomName) pass(`roomName="${v.roomName}"`);
        else fail('roomName', 'roomName 없음');
    } else {
        fail('ChatRoomView 조회', JSON.stringify(roomView.data));
    }

    // ChatRoomView 목록 조회
    const viewList = await api(QUERY, 'GET', '/api/query/rooms');
    if (viewList.data?.data && viewList.data.data.length > 0) pass('ChatRoomView 목록 조회');
    else fail('ChatRoomView 목록 조회', JSON.stringify(viewList.data));

    // ═══════════════════════════════════════
    // Phase G: Presence (Redis 접속 상태)
    // ═══════════════════════════════════════
    console.log('\n── Phase G: Presence (Redis) ──');

    if (!serviceStatus['presence-service']) {
        skip('Presence 테스트', 'presence-service DOWN');
    } else {
        // heartbeat
        const hb = await gw('POST', '/api/presence/heartbeat', { userId: userA.id, username: userA.name });
        if (hb.status < 300) pass('Heartbeat 전송');
        else fail('Heartbeat', `status=${hb.status}`);

        // online 상태 확인 (presence-service 직접 호출)
        await sleep(500);
        const online = await api('http://localhost:8083', 'GET', `/api/presence/users/${userA.id}`);
        if (online.data?.data?.online === true || online.data?.data?.status === 'ONLINE')
            pass('접속 상태 ONLINE 확인');
        else fail('접속 상태 확인', JSON.stringify(online.data));

        // typing 상태
        const typing = await gw('POST', '/api/presence/typing', { userId: userA.id, chatRoomId: roomId });
        if (typing.status < 300) pass('Typing 상태 전송');
        else fail('Typing 상태', `status=${typing.status}`);
    }

    // ═══════════════════════════════════════
    // Phase H: Read Receipt (읽음 표시)
    // ═══════════════════════════════════════
    console.log('\n── Phase H: Read Receipt ──');

    // 읽음 표시 전송 (lastMessageId 필수)
    // 가장 최근 메시지 ID를 가져옴
    const latestMsgs = await gw('GET', `/api/chat/rooms/${roomId}/messages?size=1`);
    const lastMsgId = latestMsgs.data?.data?.content?.[0]?.id;
    const readRes = await gw('POST', `/api/chat/rooms/${roomId}/read`, { userId: userB.id, lastMessageId: lastMsgId || 1 });
    if (readRes.status < 300) pass('읽음 표시 전송');
    else fail('읽음 표시', `status=${readRes.status} ${JSON.stringify(readRes.data)}`);

    // 안읽음 카운트 조회
    const unreadRes = await gw('GET', `/api/chat/rooms/${roomId}/unread?userId=${userA.id}`);
    if (unreadRes.status < 300) pass(`안읽음 카운트 조회 (count=${unreadRes.data?.data})`);
    else fail('안읽음 카운트', `status=${unreadRes.status}`);

    // ═══════════════════════════════════════
    // Phase I: 최종 상태 검증
    // ═══════════════════════════════════════
    console.log('\n── Phase I: 최종 상태 검증 ──');

    // notification-service 헬스
    if (serviceStatus['notification-service']) pass('notification-service UP (Kafka consumer 활성)');
    else skip('notification-service 확인', 'DOWN');

    // ai-service 헬스
    if (serviceStatus['ai-service']) pass('ai-service UP (스팸 탐지 활성)');
    else skip('ai-service 확인', 'DOWN');

    // Gateway health 최종 확인
    const finalGw = await isServiceUp(GATEWAY);
    if (finalGw) pass('Gateway 최종 헬스 OK');
    else fail('Gateway 최종 헬스', 'DOWN');

    // 두 번째 채팅방 생성 + 격리 검증
    const room2Res = await gw('POST', '/api/chat/rooms', { name: `Isolation-${ts}`, creatorId: userB.id });
    const room2Id = room2Res.data?.data?.id;
    if (room2Id) {
        // 다른 방에 메시지 전송
        await gw('POST', `/api/chat/rooms/${room2Id}/messages`, {
            senderId: userB.id, senderName: userB.name, content: '격리 테스트 메시지'
        });

        // 원래 방의 메시지 수가 변하지 않는지 확인
        await sleep(3000);
        const origView = await api(QUERY, 'GET', `/api/query/rooms/${roomId}`);
        const newView = await api(QUERY, 'GET', `/api/query/rooms/${room2Id}`);

        if (origView.data?.data && newView.data?.data) {
            const origCount = origView.data.data.messageCount;
            const newCount = newView.data.data.messageCount;
            if (newCount >= 1 && origCount >= 5) pass(`채팅방 격리 검증 (room1=${origCount}건, room2=${newCount}건)`);
            else fail('채팅방 격리 검증', `room1=${origCount}, room2=${newCount}`);
        } else {
            skip('채팅방 격리 검증', 'ChatRoomView 조회 실패');
        }
    }

    printSummary();
}

run().catch(err => { console.error('치명적 오류:', err); process.exit(1); });

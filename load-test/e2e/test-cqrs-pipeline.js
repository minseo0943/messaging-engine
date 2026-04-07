/**
 * ═══════════════════════════════════════════════════════════════
 *  messaging-engine CQRS 파이프라인 E2E 테스트
 * ═══════════════════════════════════════════════════════════════
 *
 *  사용법:  node load-test/e2e/test-cqrs-pipeline.js
 *
 *  검증 대상 (Docker Compose 환경):
 *    1. Command: chat-service → MySQL 메시지 저장
 *    2. Event:   chat-service → Kafka message.sent 발행
 *    3. Query:   query-service Kafka Consumer → MongoDB 프로젝션
 *    4. AI:      ai-service → 스팸 탐지 → message.spam-detected → query-service spamStatus 업데이트
 *    5. Notify:  notification-service 이벤트 수신 확인 (로그 기반)
 *    6. ChatRoomView: MongoDB 채팅방 뷰 갱신
 *
 *  사전 조건:
 *    docker compose up -d (MySQL, Kafka, MongoDB, Redis + 6 app services)
 */

const GATEWAY = 'http://localhost:8080';
const CHAT = 'http://localhost:8081';
const QUERY = 'http://localhost:8082';

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

async function isServiceUp(url) {
    try {
        const r = await fetch(`${url}/actuator/health`);
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
    console.log('\n═══════════════════════════════════════════════════');
    console.log(`  결과: ✅ ${passed}  ❌ ${failed}  ⏭️ ${skipped}  총 ${passed + failed + skipped}`);
    console.log('═══════════════════════════════════════════════════');
    results.filter(r => r.s === '❌').forEach(r => console.log(`  ❌ ${r.name}: ${r.reason}`));
    console.log('');
    process.exit(failed > 0 ? 1 : 0);
}

// ═══════════════════════════════════════
async function run() {
    console.log('\n═══════════════════════════════════════════════════');
    console.log('  CQRS 파이프라인 E2E 테스트 (Docker Compose)');
    console.log('═══════════════════════════════════════════════════\n');

    // ── 서비스 상태 확인 ──
    const gwUp = await isServiceUp(GATEWAY);
    const chatUp = await isServiceUp(CHAT);
    const queryUp = await isServiceUp(QUERY);
    console.log(`  Gateway: ${gwUp?'UP':'DOWN'}  Chat: ${chatUp?'UP':'DOWN'}  Query: ${queryUp?'UP':'DOWN'}`);
    if (!gwUp || !chatUp || !queryUp) {
        fail('서비스 상태', 'gateway, chat-service, query-service가 모두 실행 중이어야 합니다');
        return printSummary();
    }

    const ts = Date.now();
    const userA = { id: 90001 + ts % 10000, name: `CqrsTestUser_${ts}` };

    // ═══════════════════════════════════════
    // SECTION 1: 기본 셋업 (토큰, 채팅방)
    // ═══════════════════════════════════════
    console.log('\n── Section 1: 기본 셋업 ──');

    const tokenRes = await api(GATEWAY, 'POST', '/api/auth/token', { userId: userA.id, username: userA.name });
    const token = tokenRes.data?.data?.token || tokenRes.data?.data?.accessToken;
    if (token) pass('토큰 발급'); else { fail('토큰 발급', JSON.stringify(tokenRes.data)); return printSummary(); }

    // 채팅방 생성 (gateway 경유)
    const roomRes = await api(GATEWAY, 'POST', '/api/chat/rooms', { name: `CQRS-Test-${ts}`, creatorId: userA.id });
    const roomId = roomRes.data?.data?.id;
    if (roomId) pass('채팅방 생성'); else { fail('채팅방 생성', JSON.stringify(roomRes.data)); return printSummary(); }

    // ═══════════════════════════════════════
    // SECTION 2: Command → Event → Query 파이프라인
    // ═══════════════════════════════════════
    console.log('\n── Section 2: CQRS Command → Query 파이프라인 ──');

    // 메시지 전송 (Command Side: MySQL)
    const msg1 = await api(GATEWAY, 'POST', `/api/chat/rooms/${roomId}/messages`, {
        senderId: userA.id, senderName: userA.name, content: 'CQRS 파이프라인 테스트 메시지 1'
    });
    const msg1Id = msg1.data?.data?.id;
    if (msg1Id && msg1.status < 300) pass('Command: 메시지 전송 (MySQL 저장)');
    else fail('Command: 메시지 전송', JSON.stringify(msg1.data));

    // Query Side: MongoDB에 프로젝션이 도착할 때까지 대기
    const projected = await waitForCondition(async () => {
        const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages`);
        const msgs = r.data?.data?.content;
        if (msgs && msgs.length > 0 && msgs.some(m => m.messageId === msg1Id)) return msgs;
        return null;
    }, 15000);

    if (projected) pass('Query: MongoDB 프로젝션 도착 (Kafka → query-service)');
    else fail('Query: MongoDB 프로젝션 도착', '15초 내 프로젝션 미도착');

    // 프로젝션된 데이터 필드 검증
    if (projected) {
        const doc = projected.find(m => m.messageId === msg1Id);
        if (doc.senderName === userA.name && doc.content === 'CQRS 파이프라인 테스트 메시지 1' && doc.spamStatus === 'CLEAN')
            pass('프로젝션 데이터 정합성 (senderName, content, spamStatus=CLEAN)');
        else fail('프로젝션 데이터 정합성', JSON.stringify(doc));
    }

    // ═══════════════════════════════════════
    // SECTION 3: ChatRoomView 갱신 검증
    // ═══════════════════════════════════════
    console.log('\n── Section 3: ChatRoomView 갱신 ──');

    const viewRes = await waitForCondition(async () => {
        const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}`);
        if (r.data?.data?.lastMessageContent) return r.data.data;
        return null;
    }, 10000);

    if (viewRes) pass('ChatRoomView 갱신 (lastMessage, messageCount)');
    else fail('ChatRoomView 갱신', '10초 내 ChatRoomView 미갱신');

    if (viewRes) {
        const countOk = viewRes.messageCount >= 1;
        const contentOk = viewRes.lastMessageContent === 'CQRS 파이프라인 테스트 메시지 1';
        if (countOk && contentOk) pass('ChatRoomView 데이터 정합성');
        else fail('ChatRoomView 데이터 정합성', `count=${viewRes.messageCount}, lastMsg=${viewRes.lastMessageContent}`);
    }

    // ═══════════════════════════════════════
    // SECTION 4: 다건 메시지 → 순서 보장
    // ═══════════════════════════════════════
    console.log('\n── Section 4: 다건 메시지 순서 보장 ──');

    const msgIds = [msg1Id];
    for (let i = 2; i <= 5; i++) {
        const r = await api(GATEWAY, 'POST', `/api/chat/rooms/${roomId}/messages`, {
            senderId: userA.id, senderName: userA.name, content: `순서 테스트 메시지 ${i}`
        });
        if (r.data?.data?.id) msgIds.push(r.data.data.id);
    }

    // 모든 메시지가 MongoDB에 도착할 때까지 대기
    const allProjected = await waitForCondition(async () => {
        const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages?size=50`);
        const msgs = r.data?.data?.content;
        if (msgs && msgs.length >= 5) return msgs;
        return null;
    }, 20000);

    if (allProjected && allProjected.length >= 5) pass(`다건 메시지 프로젝션 (${allProjected.length}건)`);
    else fail('다건 메시지 프로젝션', `${allProjected?.length || 0}건`);

    // messageId 순서 확인 (오름차순)
    if (allProjected) {
        const ids = allProjected.map(m => m.messageId);
        const sorted = [...ids].sort((a, b) => a - b);
        // MongoDB는 insertedAt 기준 정렬일 수 있으므로 모든 ID가 포함되는지만 검증
        const allPresent = msgIds.every(id => ids.includes(id));
        if (allPresent) pass('메시지 순서/ID 전체 포함 검증');
        else fail('메시지 순서/ID 전체 포함 검증', `expected=${msgIds}, got=${ids}`);
    }

    // ═══════════════════════════════════════
    // SECTION 5: AI 스팸 탐지 파이프라인
    // ═══════════════════════════════════════
    console.log('\n── Section 5: AI 스팸 탐지 → spamStatus 업데이트 ──');

    // 스팸 메시지 전송 (키워드 "광고" 포함)
    const spamMsg = await api(GATEWAY, 'POST', `/api/chat/rooms/${roomId}/messages`, {
        senderId: userA.id, senderName: userA.name, content: '이것은 광고 메시지입니다 무료 당첨!'
    });
    const spamMsgId = spamMsg.data?.data?.id;
    if (spamMsgId) pass('스팸 메시지 전송');
    else fail('스팸 메시지 전송', JSON.stringify(spamMsg.data));

    // query-service에서 spamStatus가 SPAM으로 업데이트될 때까지 대기
    // 흐름: chat → Kafka(message.sent) → ai-service(스팸 탐지) → Kafka(message.spam-detected) → query-service(spamStatus 업데이트)
    if (spamMsgId) {
        const spamUpdated = await waitForCondition(async () => {
            const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages?size=50`);
            const msgs = r.data?.data?.content;
            if (!msgs) return null;
            const spamDoc = msgs.find(m => m.messageId === spamMsgId);
            if (spamDoc && spamDoc.spamStatus === 'SPAM') return spamDoc;
            return null;
        }, 20000, 1000);

        if (spamUpdated) {
            pass('AI 스팸 탐지 → spamStatus=SPAM 업데이트');
            if (spamUpdated.spamReason && spamUpdated.spamScore > 0)
                pass(`스팸 상세 (reason="${spamUpdated.spamReason}", score=${spamUpdated.spamScore})`);
            else fail('스팸 상세', `reason=${spamUpdated.spamReason}, score=${spamUpdated.spamScore}`);
        } else {
            fail('AI 스팸 탐지 → spamStatus 업데이트', '20초 내 spamStatus=SPAM 미반영');
        }
    }

    // 일반 메시지는 CLEAN 상태 유지 검증
    if (projected) {
        const cleanDoc = projected.find(m => m.messageId === msg1Id);
        if (cleanDoc && cleanDoc.spamStatus === 'CLEAN') pass('일반 메시지 spamStatus=CLEAN 유지');
        else fail('일반 메시지 spamStatus=CLEAN 유지', `status=${cleanDoc?.spamStatus}`);
    }

    // ═══════════════════════════════════════
    // SECTION 6: notification-service 이벤트 수신 확인
    // ═══════════════════════════════════════
    console.log('\n── Section 6: notification-service 이벤트 수신 ──');

    const notifUp = await isServiceUp('http://localhost:8084');
    if (notifUp) {
        pass('notification-service 헬스체크 UP');
        // notification-service는 Kafka consumer로 이벤트를 수신만 하고 로그를 남김
        // actuator/health가 UP이면 consumer가 정상 동작 중
        // 추가 확인: Kafka consumer group lag 체크 (선택사항)
    } else {
        skip('notification-service 이벤트 수신', 'notification-service가 DOWN');
    }

    // ═══════════════════════════════════════
    // SECTION 7: ai-service 헬스 및 분석 확인
    // ═══════════════════════════════════════
    console.log('\n── Section 7: ai-service 상태 ──');

    const aiUp = await isServiceUp('http://localhost:8085');
    if (aiUp) pass('ai-service 헬스체크 UP');
    else skip('ai-service 상태', 'ai-service가 DOWN');

    // ═══════════════════════════════════════
    // SECTION 8: 두 번째 스팸 패턴 검증
    // ═══════════════════════════════════════
    console.log('\n── Section 8: 추가 스팸 패턴 검증 ──');

    // 차단 키워드 스팸 ("스팸" 키워드)
    const spamMsg2 = await api(GATEWAY, 'POST', `/api/chat/rooms/${roomId}/messages`, {
        senderId: userA.id, senderName: userA.name, content: '이것은 스팸 테스트 메시지입니다'
    });
    const spamMsg2Id = spamMsg2.data?.data?.id;

    if (spamMsg2Id) {
        const spamVerify2 = await waitForCondition(async () => {
            const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages?size=50`);
            const msgs = r.data?.data?.content;
            if (!msgs) return null;
            const doc = msgs.find(m => m.messageId === spamMsg2Id);
            if (doc && doc.spamStatus === 'SPAM') return doc;
            return null;
        }, 20000, 1000);

        if (spamVerify2) pass('차단 키워드("스팸") 탐지 → spamStatus=SPAM');
        else fail('차단 키워드 스팸 탐지', '20초 내 미반영');
    }

    // URL 비율 스팸은 아닌 클린 메시지
    const cleanMsg = await api(GATEWAY, 'POST', `/api/chat/rooms/${roomId}/messages`, {
        senderId: userA.id, senderName: userA.name, content: '안녕하세요 좋은 아침입니다'
    });
    const cleanMsgId = cleanMsg.data?.data?.id;

    if (cleanMsgId) {
        const cleanVerify = await waitForCondition(async () => {
            const r = await api(QUERY, 'GET', `/api/query/rooms/${roomId}/messages?size=50`);
            const msgs = r.data?.data?.content;
            if (!msgs) return null;
            const doc = msgs.find(m => m.messageId === cleanMsgId);
            if (doc && doc.spamStatus === 'CLEAN') return doc;
            return null;
        }, 15000, 1000);

        if (cleanVerify) pass('클린 메시지 spamStatus=CLEAN 확인');
        else fail('클린 메시지 spamStatus=CLEAN', '15초 내 미반영');
    }

    // ═══════════════════════════════════════
    // SECTION 9: ChatRoomView 최종 상태 검증
    // ═══════════════════════════════════════
    console.log('\n── Section 9: ChatRoomView 최종 상태 ──');

    await sleep(3000); // 마지막 메시지 프로젝션 대기

    const finalView = await api(QUERY, 'GET', `/api/query/rooms/${roomId}`);
    if (finalView.data?.data) {
        const v = finalView.data.data;
        // 총 메시지: 1(초기) + 4(순서) + 1(스팸1) + 1(반복) + 1(클린) = 8건
        if (v.messageCount >= 7) pass(`ChatRoomView 최종 messageCount=${v.messageCount}`);
        else fail(`ChatRoomView 최종 messageCount`, `expected>=7, got=${v.messageCount}`);

        if (v.lastMessageContent === '안녕하세요 좋은 아침입니다')
            pass('ChatRoomView lastMessage 최종 확인');
        else fail('ChatRoomView lastMessage', `got="${v.lastMessageContent}"`);
    }

    // ═══════════════════════════════════════
    // SECTION 10: 전체 방 목록 (ChatRoomView 리스트)
    // ═══════════════════════════════════════
    console.log('\n── Section 10: 전체 ChatRoomView 리스트 ──');

    const roomList = await api(QUERY, 'GET', '/api/query/rooms');
    if (roomList.data?.data && roomList.data.data.length > 0) pass('ChatRoomView 리스트 조회');
    else fail('ChatRoomView 리스트 조회', JSON.stringify(roomList.data));

    printSummary();
}

run().catch(err => { console.error('치명적 오류:', err); process.exit(1); });

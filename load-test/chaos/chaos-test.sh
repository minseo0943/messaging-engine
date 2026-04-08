#!/bin/bash
# ═══════════════════════════════════════════════════
#  messaging-engine Chaos Test Suite
# ═══════════════════════════════════════════════════
#
# 시나리오:
#   1. Kafka 브로커 다운 → Outbox에 이벤트 쌓임 → 복구 후 자동 발행
#   2. Redis 다운 → Presence Graceful Degradation
#   3. chat-service 강제 종료 → Gateway Circuit Breaker
#   4. query-service 다운 → 읽기 불가, 쓰기는 정상
#

CHAT="http://localhost:8081"
QUERY="http://localhost:8082"
PRESENCE="http://localhost:8083"
GW="http://localhost:8080"
PASS=0
FAIL=0
RESULTS=""

pass() { PASS=$((PASS+1)); RESULTS+="  ✅ $1\n"; echo "  ✅ $1"; }
fail() { FAIL=$((FAIL+1)); RESULTS+="  ❌ $1 — $2\n"; echo "  ❌ $1 — $2"; }
header() { echo -e "\n── $1 ──"; }

wait_for_service() {
    local url=$1 max=$2
    for i in $(seq 1 $max); do
        status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$url/actuator/health")
        [ "$status" = "200" ] && return 0
        sleep 2
    done
    return 1
}

echo ""
echo "═══════════════════════════════════════════════════"
echo "  messaging-engine Chaos Test"
echo "═══════════════════════════════════════════════════"

# ──────────────────────────────────
header "SCENARIO 1: Kafka 브로커 다운 → Outbox 복구"
# ──────────────────────────────────

# 1-1. Kafka 다운 전 메시지 전송 (정상)
ROOM_RES=$(curl -s -X POST "$CHAT/api/chat/rooms" \
  -H "Content-Type: application/json" \
  -d '{"name":"Chaos-Room","description":"chaos test","creatorId":1}')
ROOM_ID=$(echo "$ROOM_RES" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
echo "  채팅방 생성: id=$ROOM_ID"

MSG_BEFORE=$(curl -s -X POST "$CHAT/api/chat/rooms/$ROOM_ID/messages" \
  -H "Content-Type: application/json" \
  -d '{"senderId":1,"senderName":"chaos-user","content":"before-kafka-down"}')
MSG_ID_BEFORE=$(echo "$MSG_BEFORE" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
[ -n "$MSG_ID_BEFORE" ] && pass "Kafka 정상 시 메시지 전송 (id=$MSG_ID_BEFORE)" || fail "Kafka 정상 시 메시지 전송" "response: $MSG_BEFORE"

# 1-2. Kafka 강제 중단
docker stop messaging-kafka > /dev/null 2>&1
sleep 3

# 1-3. Kafka 다운 중 메시지 전송 — DB에는 저장, Outbox에 쌓임
MSG_DURING=$(curl -s -X POST "$CHAT/api/chat/rooms/$ROOM_ID/messages" \
  -H "Content-Type: application/json" \
  -d '{"senderId":1,"senderName":"chaos-user","content":"during-kafka-down"}')
MSG_ID_DURING=$(echo "$MSG_DURING" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
[ -n "$MSG_ID_DURING" ] && pass "Kafka 다운 중 메시지 저장 성공 (Outbox에 보관)" || fail "Kafka 다운 중 메시지 저장" "response: $MSG_DURING"

# 1-4. Kafka 복구
docker start messaging-kafka > /dev/null 2>&1
echo "  Kafka 복구 대기 중 (30초)..."
sleep 30

# 1-5. 복구 후 query-service에서 메시지 확인 (Outbox → Kafka → MongoDB 전파)
sleep 10  # Outbox poller 5초 주기 + 전파 시간
QUERY_RES=$(curl -s "$QUERY/api/query/rooms/$ROOM_ID/messages?page=0&size=10")
HAS_MSG=$(echo "$QUERY_RES" | python3 -c "
import sys,json
data = json.load(sys.stdin)
msgs = data.get('data',{}).get('content',[])
found = any('during-kafka-down' in m.get('content','') for m in msgs)
print('true' if found else 'false')
" 2>/dev/null)
[ "$HAS_MSG" = "true" ] && pass "Kafka 복구 후 Outbox 이벤트 자동 전파 확인" || fail "Kafka 복구 후 Outbox 전파" "query에서 메시지 미발견"

# ──────────────────────────────────
header "SCENARIO 2: Redis 다운 → Graceful Degradation"
# ──────────────────────────────────

# 2-1. Redis 정상 시 heartbeat
HB_BEFORE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$PRESENCE/api/presence/heartbeat" \
  -H "Content-Type: application/json" -d '{"userId":9999}')
[ "$HB_BEFORE" = "200" ] && pass "Redis 정상 시 heartbeat 성공" || fail "Redis 정상 시 heartbeat" "status=$HB_BEFORE"

# 2-2. Redis 강제 중단
docker stop messaging-redis > /dev/null 2>&1
sleep 3

# 2-3. Redis 다운 중 presence 조회 — OFFLINE 반환 (에러 아님)
PRESENCE_RES=$(curl -s "$PRESENCE/api/presence/9999")
STATUS=$(echo "$PRESENCE_RES" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])" 2>/dev/null)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$PRESENCE/api/presence/9999")
if [ "$HTTP_CODE" = "200" ] && [ "$STATUS" = "OFFLINE" ]; then
    pass "Redis 다운 시 Graceful Degradation (OFFLINE 반환, 에러 없음)"
else
    fail "Redis 다운 시 Graceful Degradation" "status=$HTTP_CODE, body=$PRESENCE_RES"
fi

# 2-4. Redis 다운 중 heartbeat — 에러 없이 무시
HB_DOWN=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$PRESENCE/api/presence/heartbeat" \
  -H "Content-Type: application/json" -d '{"userId":9999}')
[ "$HB_DOWN" = "200" ] && pass "Redis 다운 중 heartbeat도 에러 없이 처리" || fail "Redis 다운 중 heartbeat" "status=$HB_DOWN"

# 2-5. Redis 다운 중 채팅 메시지 전송 — 영향 없음
MSG_REDIS_DOWN=$(curl -s -X POST "$CHAT/api/chat/rooms/$ROOM_ID/messages" \
  -H "Content-Type: application/json" \
  -d '{"senderId":1,"senderName":"chaos-user","content":"redis-is-down-but-chat-works"}')
MSG_ID_RD=$(echo "$MSG_REDIS_DOWN" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
[ -n "$MSG_ID_RD" ] && pass "Redis 다운이 채팅 메시지 전송에 영향 없음" || fail "Redis 다운 시 채팅" "response: $MSG_REDIS_DOWN"

# 2-6. Redis 복구
docker start messaging-redis > /dev/null 2>&1
sleep 5

# 2-7. Redis 복구 후 정상 동작 확인
HB_AFTER=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$PRESENCE/api/presence/heartbeat" \
  -H "Content-Type: application/json" -d '{"userId":9999}')
PRESENCE_AFTER=$(curl -s "$PRESENCE/api/presence/9999")
STATUS_AFTER=$(echo "$PRESENCE_AFTER" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])" 2>/dev/null)
[ "$STATUS_AFTER" = "ONLINE" ] && pass "Redis 복구 후 heartbeat → ONLINE 정상" || fail "Redis 복구 후" "status=$STATUS_AFTER"

# ──────────────────────────────────
header "SCENARIO 3: chat-service 다운 → Gateway 장애 격리"
# ──────────────────────────────────

# 3-1. chat-service 강제 중단
docker stop messaging-chat > /dev/null 2>&1
sleep 3

# 3-2. Gateway를 통한 메시지 전송 시도 — 빠른 실패 (CB)
GW_RES=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 \
  -X POST "$GW/api/chat/rooms/$ROOM_ID/messages" \
  -H "Content-Type: application/json" \
  -d '{"senderId":1,"senderName":"chaos-user","content":"chat-is-down"}')
if [ "$GW_RES" = "503" ] || [ "$GW_RES" = "502" ] || [ "$GW_RES" = "500" ]; then
    pass "chat-service 다운 시 Gateway 에러 반환 (status=$GW_RES)"
else
    fail "chat-service 다운 시 Gateway" "expected 5xx, got $GW_RES"
fi

# 3-3. query-service는 여전히 정상 동작
QUERY_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$QUERY/api/query/rooms/$ROOM_ID/messages?page=0&size=5")
[ "$QUERY_STATUS" = "200" ] && pass "chat-service 다운이 query-service 읽기에 영향 없음" || fail "chat-service 다운 시 query" "status=$QUERY_STATUS"

# 3-4. presence-service도 정상
PRES_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$PRESENCE/api/presence/9999")
[ "$PRES_STATUS" = "200" ] && pass "chat-service 다운이 presence-service에 영향 없음" || fail "chat-service 다운 시 presence" "status=$PRES_STATUS"

# 3-5. chat-service 복구
docker start messaging-chat > /dev/null 2>&1
echo "  chat-service 복구 대기 중..."
if wait_for_service "$CHAT" 60; then
    pass "chat-service 복구 완료"
else
    fail "chat-service 복구" "timeout"
fi

# 3-6. 복구 후 메시지 전송 정상
sleep 3
MSG_AFTER=$(curl -s -X POST "$CHAT/api/chat/rooms/$ROOM_ID/messages" \
  -H "Content-Type: application/json" \
  -d '{"senderId":1,"senderName":"chaos-user","content":"chat-recovered"}')
MSG_ID_AFTER=$(echo "$MSG_AFTER" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
[ -n "$MSG_ID_AFTER" ] && pass "chat-service 복구 후 메시지 전송 정상" || fail "chat-service 복구 후 전송" "response: $MSG_AFTER"

# ──────────────────────────────────
header "SCENARIO 4: query-service 다운 → 쓰기/읽기 분리 검증"
# ──────────────────────────────────

# 4-1. query-service 강제 중단
docker stop messaging-query > /dev/null 2>&1
sleep 3

# 4-2. query-service 다운 중에도 메시지 전송 성공 (CQRS: Command는 독립)
MSG_QD=$(curl -s -X POST "$CHAT/api/chat/rooms/$ROOM_ID/messages" \
  -H "Content-Type: application/json" \
  -d '{"senderId":1,"senderName":"chaos-user","content":"query-down-but-write-works"}')
MSG_ID_QD=$(echo "$MSG_QD" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
[ -n "$MSG_ID_QD" ] && pass "query-service 다운 중 메시지 쓰기 정상 (CQRS 분리)" || fail "query-service 다운 시 쓰기" "response: $MSG_QD"

# 4-3. query-service 복구 → Kafka 이벤트 소비하여 자동 동기화
docker start messaging-query > /dev/null 2>&1
echo "  query-service 복구 대기 중..."
if wait_for_service "$QUERY" 60; then
    sleep 10  # Consumer가 밀린 이벤트 처리할 시간
    QUERY_SYNC=$(curl -s "$QUERY/api/query/rooms/$ROOM_ID/messages?page=0&size=20")
    HAS_SYNC=$(echo "$QUERY_SYNC" | python3 -c "
import sys,json
data = json.load(sys.stdin)
msgs = data.get('data',{}).get('content',[])
found = any('query-down-but-write-works' in m.get('content','') for m in msgs)
print('true' if found else 'false')
" 2>/dev/null)
    [ "$HAS_SYNC" = "true" ] && pass "query-service 복구 후 밀린 이벤트 자동 동기화 확인" || fail "query-service 복구 후 동기화" "메시지 미발견"
else
    fail "query-service 복구" "timeout"
fi

# ═══════════════════════════════════════════════════
echo ""
echo "═══════════════════════════════════════════════════"
echo "  📊 Chaos Test 결과"
echo "═══════════════════════════════════════════════════"
echo -e "$RESULTS"
echo "  ✅ $PASS passed  |  ❌ $FAIL failed"
echo "═══════════════════════════════════════════════════"

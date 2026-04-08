-- Sliding Window Log Rate Limiter (ZSET 기반)
-- KEYS[1] = rate limit key (e.g., rate_limit:user:123 or rate_limit:ip:1.2.3.4)
-- ARGV[1] = window size in milliseconds
-- ARGV[2] = max requests allowed in window
-- ARGV[3] = current timestamp in milliseconds
--
-- Returns: {current_count, remaining, retry_after_seconds}

local key = KEYS[1]
local window_ms = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local window_start = now - window_ms

-- 1. 윈도우 밖의 만료된 요청 제거
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- 2. 현재 윈도우 내 요청 수 확인
local count = redis.call('ZCARD', key)

if count < limit then
    -- 3a. 제한 미만: 새 요청 추가 (score=timestamp, member=timestamp:random으로 유니크 보장)
    redis.call('ZADD', key, now, now .. ':' .. math.random(1000000))
    redis.call('PEXPIRE', key, window_ms)
    return {count + 1, limit - count - 1, 0}
else
    -- 3b. 제한 초과: 가장 오래된 요청이 만료되는 시점 계산
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retry_after = 0
    if #oldest >= 2 then
        retry_after = math.ceil((tonumber(oldest[2]) + window_ms - now) / 1000)
        if retry_after < 1 then retry_after = 1 end
    end
    return {count, 0, retry_after}
end

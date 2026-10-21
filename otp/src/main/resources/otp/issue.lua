-- Issue a code atomically. KEYS: sends, code, attempts, last.
-- ARGV: max sends, send window (ms), code hash, code lifetime (ms), max attempts, resend after (ms).
-- Returns {sends, 0} on success, or {refusal, ms until it lifts}: -1 = send limit, -2 = too early
-- after the last code, -3 = locked by too many wrong codes. A new code never resets the attempts, so
-- a lockout lasts until its window ends.
local attempts = tonumber(redis.call('GET', KEYS[3]) or '0')
if attempts >= tonumber(ARGV[5]) then
  return {-3, redis.call('PTTL', KEYS[3])}
end
local wait = redis.call('PTTL', KEYS[4])
if wait > 0 then
  return {-2, wait}
end
local sends = redis.call('INCR', KEYS[1])
if sends == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
if sends > tonumber(ARGV[1]) then
  return {-1, redis.call('PTTL', KEYS[1])}
end
redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])
if tonumber(ARGV[6]) > 0 then
  redis.call('SET', KEYS[4], '1', 'PX', ARGV[6])
end
return {sends, 0}

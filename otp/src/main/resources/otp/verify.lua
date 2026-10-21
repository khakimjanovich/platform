-- Check a code atomically, so a code can never be used twice, even by two requests at the same moment.
-- KEYS: code, attempts. ARGV: max attempts, send window (ms), hash of the code to check.
-- Returns {1, 0} = right, {0, 0} = expired or never sent, {-1, 0} = wrong,
-- {-2, ms until it lifts} = too many attempts. Attempts count for the whole send window, across codes.
local attempts = tonumber(redis.call('GET', KEYS[2]) or '0')
if attempts >= tonumber(ARGV[1]) then
  return {-2, redis.call('PTTL', KEYS[2])}
end
local stored = redis.call('GET', KEYS[1])
if not stored then
  return {0, 0}
end
attempts = redis.call('INCR', KEYS[2])
if attempts == 1 then
  redis.call('PEXPIRE', KEYS[2], ARGV[2])
end
-- Both values are keyed hashes the caller can't compute, so a plain comparison leaks nothing useful.
if stored ~= ARGV[3] then
  return {-1, 0}
end
redis.call('DEL', KEYS[1], KEYS[2])
return {1, 0}

-- Check a code atomically, so a code can never be used twice, even by two requests at the same moment.
-- KEYS: code, attempts. ARGV: max attempts, code lifetime (ms), hash of the code to check.
-- Returns 1 = right, 0 = expired or never sent, -1 = wrong, -2 = too many attempts.
local stored = redis.call('GET', KEYS[1])
if not stored then
  return 0
end
local attempts = redis.call('INCR', KEYS[2])
if attempts == 1 then
  redis.call('PEXPIRE', KEYS[2], ARGV[2])
end
if attempts > tonumber(ARGV[1]) then
  return -2
end
-- Both values are keyed hashes the caller can't compute, so a plain comparison leaks nothing useful.
if stored ~= ARGV[3] then
  return -1
end
redis.call('DEL', KEYS[1], KEYS[2])
return 1

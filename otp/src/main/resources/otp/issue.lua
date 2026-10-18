-- Issue a code atomically: count the send, refuse past the limit, store the code hash, reset the attempts.
-- KEYS: sends, code, attempts. ARGV: max sends, send window (ms), code hash, code lifetime (ms).
local sends = redis.call('INCR', KEYS[1])
if sends == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[2])
end
if sends > tonumber(ARGV[1]) then
  return -1
end
redis.call('SET', KEYS[2], ARGV[3], 'PX', ARGV[4])
redis.call('DEL', KEYS[3])
return sends

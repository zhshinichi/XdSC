-- 固定窗口限流：对 KEYS[1] 计数，首次设置过期时间。
-- ARGV[1] = 窗口秒数; ARGV[2] = 上限
-- 返回 1 放行; 0 拒绝
local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
if current > tonumber(ARGV[2]) then
    return 0
end
return 1

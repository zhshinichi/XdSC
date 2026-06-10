-- 原子地完成"防重复 + 扣减库存"。
-- KEYS[1] = 库存 key (course:stock:{courseId})
-- KEYS[2] = 已选学生集合 key (course:enrolled:{courseId})
-- ARGV[1] = 学生 id
-- 返回: -2 未初始化(未预热); -3 已选过; -1 已抢完; >=0 成功(返回剩余名额)
local stockKey = KEYS[1]
local setKey = KEYS[2]
local studentId = ARGV[1]

if redis.call('EXISTS', stockKey) == 0 then
    return -2
end
if redis.call('SISMEMBER', setKey, studentId) == 1 then
    return -3
end
local stock = tonumber(redis.call('GET', stockKey))
if stock <= 0 then
    return -1
end
redis.call('DECR', stockKey)
redis.call('SADD', setKey, studentId)
return stock - 1

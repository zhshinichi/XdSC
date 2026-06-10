-- 原子地回滚一次选课：从已选集合移除学生并回补一个名额。
-- KEYS[1] = 库存 key; KEYS[2] = 已选学生集合 key; ARGV[1] = 学生 id
-- 返回: 1 回滚成功; 0 该学生本就未选(无需回滚)
local stockKey = KEYS[1]
local setKey = KEYS[2]
local studentId = ARGV[1]

if redis.call('SISMEMBER', setKey, studentId) == 1 then
    redis.call('SREM', setKey, studentId)
    redis.call('INCR', stockKey)
    return 1
end
return 0

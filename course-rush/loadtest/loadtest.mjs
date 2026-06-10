// 高校抢课系统 —— 端到端压测脚本（验证不超卖 + 测延迟/吞吐）
// 用法: node loadtest.mjs [并发数] [课程容量] [后端地址]
// 例:   node loadtest.mjs 500 50 http://localhost:8080
//
// 流程: 建管理员 -> 建开放批次 -> 建课程(容量CAP)并预热 -> 注册N个学生 ->
//       N个学生并发抢课 -> 统计成功数/延迟 -> 用管理员统计接口校验"不超卖"。

const N = parseInt(process.argv[2] || '500', 10)
const CAP = parseInt(process.argv[3] || '50', 10)
const BASE = process.argv[4] || 'http://localhost:8088'

async function call(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = 'Bearer ' + token
  const res = await fetch(BASE + path, {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  return { status: res.status, data: text ? JSON.parse(text) : null }
}

function uniq(p) { return p + '_' + Date.now() + '_' + Math.floor(Math.random() * 1e6) }

async function main() {
  console.log(`并发=${N}, 容量=${CAP}, 后端=${BASE}`)

  // 1. 管理员
  const adminName = uniq('admin')
  const admin = (await call('POST', '/api/auth/register',
    { username: adminName, name: adminName, password: 'pw', role: 'ADMIN' })).data
  console.log('管理员就绪')

  // 2. 批次（现在开放，2小时窗口）
  const now = Date.now()
  const batch = (await call('POST', '/api/batches', {
    name: uniq('batch'),
    openAt: new Date(now - 3600_000).toISOString(),
    closeAt: new Date(now + 3600_000).toISOString(),
  }, admin.token)).data

  // 3. 课程 + 预热
  const course = (await call('POST', '/api/courses', {
    name: uniq('课程'), teacher: '张老师', timeSlot: '周一', capacity: CAP, batchId: batch.id,
  }, admin.token)).data
  await call('POST', `/api/admin/courses/${course.id}/preheat`, null, admin.token)
  console.log(`课程就绪 id=${course.id}, 已预热 ${CAP} 个名额`)

  // 4. 注册 N 个学生（分批，避免瞬时过多连接）
  const tokens = []
  const BATCH = 100
  for (let i = 0; i < N; i += BATCH) {
    const chunk = []
    for (let j = i; j < Math.min(i + BATCH, N); j++) {
      const u = uniq('stu' + j)
      chunk.push(call('POST', '/api/auth/register',
        { username: u, name: u, password: 'pw', role: 'STUDENT' }).then(r => tokens.push(r.data.token)))
    }
    await Promise.all(chunk)
  }
  console.log(`已注册 ${tokens.length} 个学生，开始并发抢课...`)

  // 5. 并发抢课，记录延迟
  const latencies = []
  let ok = 0, soldOut = 0, limited = 0, other = 0
  const t0 = Date.now()
  await Promise.all(tokens.map(async (tk) => {
    const s = Date.now()
    const r = await call('POST', '/api/enroll', { courseId: course.id }, tk)
    latencies.push(Date.now() - s)
    if (r.status === 200) ok++
    else if (r.status === 409) soldOut++
    else if (r.status === 429) limited++
    else other++
  }))
  const wall = Date.now() - t0

  // 6. 统计 + 不超卖校验
  latencies.sort((a, b) => a - b)
  const pct = (p) => latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * p))]
  const stats = (await call('GET', `/api/admin/courses/${course.id}/stats`, null, admin.token)).data

  console.log('\n===== 压测结果 =====')
  console.log(`总请求: ${tokens.length}, 抢中(200): ${ok}, 已满(409): ${soldOut}, 限流(429): ${limited}, 其它: ${other}`)
  console.log(`耗时: ${wall} ms, 吞吐: ${(tokens.length / wall * 1000).toFixed(0)} req/s`)
  console.log(`延迟 P50=${pct(0.5)}ms  P95=${pct(0.95)}ms  P99=${pct(0.99)}ms  Max=${latencies[latencies.length - 1]}ms`)
  console.log(`\n===== 不超卖校验 =====`)
  console.log(`课程容量=${stats.capacity}, 数据库最终选课数=${stats.enrolled}, 剩余=${stats.remaining}`)
  // 异步落库可能有短暂延迟，给一点时间后再看
  if (stats.enrolled !== CAP) {
    await new Promise(r => setTimeout(r, 2000))
    const s2 = (await call('GET', `/api/admin/courses/${course.id}/stats`, null, admin.token)).data
    console.log(`(2s后复查) 最终选课数=${s2.enrolled}`)
    console.log(s2.enrolled <= CAP ? '✅ 未超卖' : '❌ 出现超卖！')
  } else {
    console.log('✅ 未超卖：最终选课数 == 容量')
  }
}

main().catch(e => { console.error(e); process.exit(1) })

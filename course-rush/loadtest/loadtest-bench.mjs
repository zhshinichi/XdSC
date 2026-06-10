// 高校抢课系统 —— 增强版压测基准脚本（生成可视化用的 JSON 报告）
// 用法: node loadtest-bench.mjs <并发N> <容量CAP> <模式标签> <输出JSON路径> [后端地址]
// 例:   node loadtest-bench.mjs 2000 100 async ./results/async_100.json http://localhost:8088
//
// 相比 loadtest.mjs 增强：
//   - 按"抢中(winner)/落败(loser)"拆分延迟分布
//   - 计算 P50/P90/P95/P99/Max/Avg，生成延迟直方图桶
//   - 异步模式下轮询 stats，等 Kafka 消费者把事件排空后再校验不超卖
//   - 把全部结果写成 JSON，供图表脚本读取

import { writeFileSync, mkdirSync } from 'node:fs'
import { dirname } from 'node:path'

const N = parseInt(process.argv[2] || '2000', 10)
const CAP = parseInt(process.argv[3] || '100', 10)
const LABEL = process.argv[4] || 'async'           // 模式标签：async / sync
const OUT = process.argv[5] || `./results/${LABEL}_${N}_${CAP}.json`
const BASE = process.argv[6] || 'http://localhost:8088'

async function call(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = 'Bearer ' + token
  const res = await fetch(BASE + path, {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let data = null
  try { data = text ? JSON.parse(text) : null } catch { data = text }
  return { status: res.status, data }
}

function uniq(p) { return p + '_' + Date.now() + '_' + Math.floor(Math.random() * 1e6) }

function pct(sorted, p) {
  if (sorted.length === 0) return 0
  return sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))]
}

function summarize(arr) {
  if (arr.length === 0) return { count: 0, min: 0, avg: 0, p50: 0, p90: 0, p95: 0, p99: 0, max: 0 }
  const s = [...arr].sort((a, b) => a - b)
  const sum = s.reduce((a, b) => a + b, 0)
  return {
    count: s.length,
    min: s[0],
    avg: Math.round(sum / s.length),
    p50: pct(s, 0.5),
    p90: pct(s, 0.9),
    p95: pct(s, 0.95),
    p99: pct(s, 0.99),
    max: s[s.length - 1],
  }
}

const HIST_EDGES = [10, 25, 50, 100, 250, 500, 1000, 2000, 5000]
function histogram(arr) {
  const labels = ['0-10', '10-25', '25-50', '50-100', '100-250', '250-500', '500-1k', '1k-2k', '2k-5k', '5k+']
  const counts = new Array(labels.length).fill(0)
  for (const v of arr) {
    let idx = HIST_EDGES.findIndex(e => v < e)
    if (idx === -1) idx = labels.length - 1
    counts[idx]++
  }
  return labels.map((label, i) => ({ label, count: counts[i] }))
}

async function pollDrain(courseId, token, expectWinners) {
  // 异步落库：轮询直到 DB 选课数追上 Redis 赢家数（或稳定/超时）
  let last = -1, stable = 0
  const deadline = Date.now() + 30000
  let stats = null
  while (Date.now() < deadline) {
    stats = (await call('GET', `/api/admin/courses/${courseId}/stats`, null, token)).data
    if (stats.enrolled >= expectWinners) break
    if (stats.enrolled === last) { stable++; if (stable >= 6) break } else { stable = 0; last = stats.enrolled }
    await new Promise(r => setTimeout(r, 500))
  }
  return stats
}

async function main() {
  console.log(`\n========== [${LABEL}] 并发=${N}, 容量=${CAP}, 后端=${BASE} ==========`)
  const expectWinners = Math.min(N, CAP)

  // 1. 管理员
  const adminName = uniq('admin')
  const admin = (await call('POST', '/api/auth/register',
    { username: adminName, name: adminName, password: 'pw', role: 'ADMIN' })).data

  // 2. 批次（现在开放）
  const now = Date.now()
  const batch = (await call('POST', '/api/batches', {
    name: uniq('batch'),
    openAt: new Date(now - 3600_000).toISOString(),
    closeAt: new Date(now + 3600_000).toISOString(),
  }, admin.token)).data

  // 3. 课程 + 预热
  const course = (await call('POST', '/api/courses', {
    name: uniq('course'), teacher: '张老师', timeSlot: '周一12节', capacity: CAP, batchId: batch.id,
  }, admin.token)).data
  await call('POST', `/api/admin/courses/${course.id}/preheat`, null, admin.token)
  console.log(`课程 id=${course.id} 已预热 ${CAP} 名额；注册 ${N} 个学生...`)

  // 4. 注册 N 个学生（分批）
  const tokens = []
  const REG_BATCH = 200
  for (let i = 0; i < N; i += REG_BATCH) {
    const chunk = []
    for (let j = i; j < Math.min(i + REG_BATCH, N); j++) {
      const u = uniq('stu' + j)
      chunk.push(call('POST', '/api/auth/register',
        { username: u, name: u, password: 'pw', role: 'STUDENT' }).then(r => tokens.push(r.data.token)))
    }
    await Promise.all(chunk)
  }
  console.log(`已注册 ${tokens.length} 个学生，发起 ${tokens.length} 并发抢课...`)

  // 5. 并发抢课（一次性全部发出，模拟开放瞬间洪峰）
  const winLat = [], loseLat = [], allLat = []
  let ok = 0, soldOut = 0, limited = 0, other = 0
  const t0 = Date.now()
  await Promise.all(tokens.map(async (tk) => {
    const s = Date.now()
    const r = await call('POST', '/api/enroll', { courseId: course.id }, tk)
    const d = Date.now() - s
    allLat.push(d)
    if (r.status === 200) { ok++; winLat.push(d) }
    else if (r.status === 409) { soldOut++; loseLat.push(d) }
    else if (r.status === 429) { limited++; loseLat.push(d) }
    else { other++; loseLat.push(d) }
  }))
  const wall = Date.now() - t0

  // 6. 等落库排空 + 不超卖校验
  const stats = await pollDrain(course.id, admin.token, Math.min(ok, CAP))

  const result = {
    label: LABEL,
    n: N,
    capacity: CAP,
    wall_ms: wall,
    throughput_rps: Math.round(tokens.length / wall * 1000),
    outcomes: { ok, soldOut, limited, other },
    latency_all: summarize(allLat),
    latency_winners: summarize(winLat),
    latency_losers: summarize(loseLat),
    histogram_all: histogram(allLat),
    oversell: {
      capacity: stats.capacity,
      finalEnrolled: stats.enrolled,
      redisWinners: ok,
      oversold: stats.enrolled > stats.capacity,
    },
  }

  mkdirSync(dirname(OUT), { recursive: true })
  writeFileSync(OUT, JSON.stringify(result, null, 2))

  console.log('----- 结果 -----')
  console.log(`抢中(200)=${ok}  已满(409)=${soldOut}  限流(429)=${limited}  其它=${other}`)
  console.log(`总耗时=${wall}ms  吞吐=${result.throughput_rps} req/s`)
  console.log(`全部延迟  P50=${result.latency_all.p50}ms P95=${result.latency_all.p95}ms P99=${result.latency_all.p99}ms Max=${result.latency_all.max}ms`)
  console.log(`抢中延迟  P50=${result.latency_winners.p50}ms P95=${result.latency_winners.p95}ms P99=${result.latency_winners.p99}ms Max=${result.latency_winners.max}ms`)
  console.log(`不超卖    容量=${result.oversell.capacity} 最终落库=${result.oversell.finalEnrolled} ${result.oversell.oversold ? '❌ 超卖!' : '✅ 未超卖'}`)
  console.log(`已写入 ${OUT}`)
}

main().catch(e => { console.error(e); process.exit(1) })

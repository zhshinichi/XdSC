// 矩阵压测：同一后端模式下，跑多个并发档位，记录 吞吐/平均/P50/P95/P99/Max + 不超卖。
// 复用一个学生池（只注册一次），每个档位用一门新课程。
// 用法: node bench-matrix.mjs <模式标签> <容量CAP> <并发列表逗号分隔> [后端]
// 例:   node bench-matrix.mjs async 100 500,1000,2000,3000,5000 http://localhost:8088
import { writeFileSync, mkdirSync } from 'node:fs'

const MODE = process.argv[2] || 'async'
const CAP = parseInt(process.argv[3] || '100', 10)
const NLIST = (process.argv[4] || '500,1000,2000,3000,5000').split(',').map(s => parseInt(s, 10))
const BASE = process.argv[5] || 'http://localhost:8088'
const INFLIGHT = parseInt(process.argv[6] || '800', 10)   // 客户端并发上限；设为很大(≥N)即"开放瞬间一次性全发"的洪峰
const POOL = Math.max(...NLIST)
const OUT = `./results/matrix_${MODE}_${CAP}.json`

async function call(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = 'Bearer ' + token
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const text = await res.text()
  let data = null; try { data = text ? JSON.parse(text) : null } catch { data = text }
  return { status: res.status, data }
}
const uniq = (p) => p + '_' + Date.now() + '_' + Math.floor(Math.random() * 1e6)
const pctl = (s, p) => s.length ? s[Math.min(s.length - 1, Math.floor(s.length * p))] : 0
function summarize(arr) {
  if (!arr.length) return { count: 0, min: 0, avg: 0, p50: 0, p90: 0, p95: 0, p99: 0, max: 0 }
  const s = [...arr].sort((a, b) => a - b); const sum = s.reduce((a, b) => a + b, 0)
  return { count: s.length, min: s[0], avg: Math.round(sum / s.length), p50: pctl(s, .5), p90: pctl(s, .9), p95: pctl(s, .95), p99: pctl(s, .99), max: s[s.length - 1] }
}
async function pollDrain(courseId, token, expect) {
  let last = -1, stable = 0; const deadline = Date.now() + 30000; let stats = null
  while (Date.now() < deadline) {
    stats = (await call('GET', `/api/admin/courses/${courseId}/stats`, null, token)).data
    if (stats.enrolled >= expect) break
    if (stats.enrolled === last) { if (++stable >= 6) break } else { stable = 0; last = stats.enrolled }
    await new Promise(r => setTimeout(r, 500))
  }
  return stats
}
// 有界并发地发起 enroll，避免一次性几千连接打爆本机端口
async function fireEnroll(tokens, courseId, inflight = INFLIGHT) {
  const all = [], lat = []; let ok = 0, soldOut = 0, limited = 0, other = 0
  let idx = 0
  const t0 = Date.now()
  async function worker() {
    while (idx < tokens.length) {
      const tk = tokens[idx++]
      const s = Date.now()
      const r = await call('POST', '/api/enroll', { courseId }, tk)
      const d = Date.now() - s; lat.push(d)
      if (r.status === 200) ok++; else if (r.status === 409) soldOut++; else if (r.status === 429) limited++; else other++
    }
  }
  await Promise.all(Array.from({ length: Math.min(inflight, tokens.length) }, worker))
  const wall = Date.now() - t0
  return { wall, lat, ok, soldOut, limited, other }
}

async function main() {
  console.log(`\n##### 矩阵压测 [${MODE}] 容量=${CAP} 档位=${NLIST.join('/')} 池=${POOL} #####`)
  const adminName = uniq('mxadmin')
  const admin = (await call('POST', '/api/auth/register', { username: adminName, name: adminName, password: 'pw', role: 'ADMIN' })).data

  console.log(`注册学生池 ${POOL} 人...`)
  const tokens = []; const REG = 200
  for (let i = 0; i < POOL; i += REG) {
    const chunk = []
    for (let j = i; j < Math.min(i + REG, POOL); j++) {
      const u = uniq('mxs' + j)
      chunk.push(call('POST', '/api/auth/register', { username: u, name: u, password: 'pw', role: 'STUDENT' }).then(r => { if (r.data?.token) tokens.push(r.data.token) }))
    }
    await Promise.all(chunk)
    process.stdout.write(`\r  已注册 ${tokens.length}/${POOL}`)
  }
  console.log(`\n池就绪 ${tokens.length} 人。预热 JVM/缓存/连接池...`)
  // 预热：跑一轮丢弃，避免首档被 JIT 编译 + 缓存冷启动拖慢，保证测量数据可比
  {
    const now = Date.now()
    const wb = (await call('POST', '/api/batches', { name: uniq('wb'), openAt: new Date(now - 3.6e6).toISOString(), closeAt: new Date(now + 3.6e6).toISOString() }, admin.token)).data
    const wc = (await call('POST', '/api/courses', { name: uniq('wc'), teacher: 'T', timeSlot: '周一12节', capacity: 2000, batchId: wb.id }, admin.token)).data
    await call('POST', `/api/admin/courses/${wc.id}/preheat`, null, admin.token)
    await fireEnroll(tokens.slice(0, Math.min(1500, tokens.length)), wc.id)
    await pollDrain(wc.id, admin.token, Math.min(1500, tokens.length))
  }
  console.log(`预热完成。开始逐档压测...`)

  const REPEAT = 3                 // 整组档位跑 3 遍（交错），消除时间相关抖动后取每档中位
  const median = (a) => { const s = [...a].sort((x, y) => x - y); return s[Math.floor(s.length / 2)] }
  const trialsByN = Object.fromEntries(NLIST.map(N => [N, []]))
  for (let rep = 0; rep < REPEAT; rep++) {
    for (const N of NLIST) {
      const sub = tokens.slice(0, N)
      const now = Date.now()
      const batch = (await call('POST', '/api/batches', { name: uniq('b'), openAt: new Date(now - 3.6e6).toISOString(), closeAt: new Date(now + 3.6e6).toISOString() }, admin.token)).data
      const course = (await call('POST', '/api/courses', { name: uniq('c'), teacher: 'T', timeSlot: '周一12节', capacity: CAP, batchId: batch.id }, admin.token)).data
      await call('POST', `/api/admin/courses/${course.id}/preheat`, null, admin.token)
      // 预热该课程的「课程/批次」Caffeine 缓存：用池尾的专用 token 先抢 1 次，
      // 使突发时热路径不再触发缓存击穿的 DB 读（异步热路径因此完全不碰 MySQL）。
      await call('POST', '/api/enroll', { courseId: course.id }, tokens[tokens.length - 1])
      const { wall, lat, ok, soldOut, limited, other } = await fireEnroll(sub, course.id)
      const stats = await pollDrain(course.id, admin.token, Math.min(ok, CAP))
      const lt = summarize(lat)
      trialsByN[N].push({ throughput_rps: Math.round(N / wall * 1000), avg: lt.avg, p50: lt.p50, p90: lt.p90, p95: lt.p95, p99: lt.p99, max: lt.max, ok, soldOut, limited, other, finalEnrolled: stats?.enrolled, oversold: stats ? stats.enrolled > stats.capacity : null })
    }
    console.log(`  第 ${rep + 1}/${REPEAT} 遍完成`)
  }
  const rows = []
  for (const N of NLIST) {
    const trials = trialsByN[N]
    const row = {
      mode: MODE, n: N, capacity: CAP,
      throughput_rps: median(trials.map(t => t.throughput_rps)),
      avg: median(trials.map(t => t.avg)), p50: median(trials.map(t => t.p50)),
      p90: median(trials.map(t => t.p90)), p95: median(trials.map(t => t.p95)),
      p99: median(trials.map(t => t.p99)), max: median(trials.map(t => t.max)),
      ok: median(trials.map(t => t.ok)), soldOut: median(trials.map(t => t.soldOut)),
      limited: median(trials.map(t => t.limited)), other: median(trials.map(t => t.other)),
      finalEnrolled: median(trials.map(t => t.finalEnrolled)),
      oversold: trials.some(t => t.oversold),
    }
    rows.push(row)
    console.log(`[N=${N}] 中位 吞吐=${row.throughput_rps}rps 平均=${row.avg} P95=${row.p95} P99=${row.p99} Max=${row.max} | 抢中=${row.ok} 满=${row.soldOut} | 落库=${row.finalEnrolled}/${CAP} ${row.oversold ? '❌超卖' : '✅不超卖'}`)
  }

  mkdirSync('./results', { recursive: true })
  writeFileSync(OUT, JSON.stringify(rows, null, 2))
  console.log(`\n已写入 ${OUT}`)
}
main().catch(e => { console.error(e); process.exit(1) })

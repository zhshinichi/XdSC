// 读取 results/matrix_*.json，生成压测报告用的 markdown 表格。
// 用法: node bench-report.mjs > snippet.md
import { readFileSync, existsSync } from 'node:fs'

const load = (p) => existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : null
const async100 = load('./results/matrix_async_100.json')
const sync100 = load('./results/matrix_sync_100.json')

function table(rows) {
  if (!rows) return '_(无数据)_\n'
  let s = '| 并发数 | 总请求 | 吞吐(req/s) | 平均(ms) | P50 | P95 | P99 | Max | 抢中 | 最终落库 | 不超卖 |\n'
  s += '|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|:--:|\n'
  for (const r of rows) {
    s += `| ${r.n} | ${r.n} | ${r.throughput_rps} | ${r.avg} | ${r.p50} | ${r.p95} | ${r.p99} | ${r.max} | ${r.ok} | ${r.finalEnrolled}/${r.capacity} | ${r.oversold ? '❌' : '✅'} |\n`
  }
  return s
}
function compare(a, b) {
  if (!a || !b) return '_(需同时有 async 与 sync 数据)_\n'
  let s = '| 并发数 | 异步吞吐 | 同步吞吐 | 吞吐倍数 | 异步P95 | 同步P95 | 异步P99 | 同步P99 |\n'
  s += '|--:|--:|--:|--:|--:|--:|--:|--:|\n'
  const bn = Object.fromEntries(b.map(r => [r.n, r]))
  for (const ra of a) {
    const rb = bn[ra.n]; if (!rb) continue
    const mult = rb.throughput_rps ? (ra.throughput_rps / rb.throughput_rps).toFixed(2) : '-'
    s += `| ${ra.n} | ${ra.throughput_rps} | ${rb.throughput_rps} | ${mult}× | ${ra.p95} | ${rb.p95} | ${ra.p99} | ${rb.p99} |\n`
  }
  return s
}

console.log('### 异步模式（Redis 原子 + Kafka 异步落库，容量=100）\n')
console.log(table(async100))
console.log('\n### 同步模式（Redis 原子 + 内联写库，容量=100）\n')
console.log(table(sync100))
console.log('\n### 同步 vs 异步 对比\n')
console.log(compare(async100, sync100))

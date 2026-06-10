// 模拟真实选课分布：让一批学生选入若干课程，形成不同满额度（用于展示进度条/紧张/已满）。
const BASE = process.argv[2] || 'http://localhost:8088'
const call = (m, p, b, t) => fetch(BASE + p, {
  method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) },
  body: b ? JSON.stringify(b) : undefined,
}).then(async r => ({ s: r.status, d: await r.json().catch(() => null) }))

const clean = (n) => String(n).replace(/^【[^】]+】\s*/, '')
// 目标：课程名 -> 选课人数
const targets = {
  '操作系统': 200,            // 满
  '数据结构与算法': 197,       // 紧张
  '人工智能导论': 178,
  '机器学习实战': 165,
  '数据库系统原理': 140,
  '计算机网络': 128,
  'Python 程序设计': 112,
  '软件体系结构': 95,
  'Web 前端开发': 70,
  '计算机组成原理': 60,
  '云计算与容器技术': 45,
  '大数据分析': 30,
}

async function main() {
  // 1. 登录一批学生，拿 token
  const POOL = 200
  console.log('登录 ' + POOL + ' 名学生...')
  const tokens = []
  const CONC = 16
  for (let i = 1; i <= POOL; i += CONC) {
    const chunk = []
    for (let j = i; j < Math.min(i + CONC, POOL + 1); j++) {
      const sno = '25' + String(j).padStart(6, '0')
      chunk.push(call('POST', '/api/auth/login', { username: sno, password: '123456' }).then(r => { if (r.d?.token) tokens.push(r.d.token) }))
    }
    await Promise.all(chunk)
  }
  console.log('已登录 ' + tokens.length + ' 名学生')

  // 2. 课程名 -> id
  const courses = (await call('GET', '/api/courses')).d
  const idByName = {}; for (const c of courses) idByName[clean(c.name)] = c.id

  // 3. 按目标分配选课（学生可跨课程复用；后端不校验时间冲突）
  let total = 0
  for (const [name, count] of Object.entries(targets)) {
    const cid = idByName[name]; if (!cid) { console.log('跳过(未找到): ' + name); continue }
    const n = Math.min(count, tokens.length)
    for (let i = 0; i < n; i += CONC) {
      const chunk = []
      for (let j = i; j < Math.min(i + CONC, n); j++) chunk.push(call('POST', '/api/enroll', { courseId: cid }, tokens[j]))
      await Promise.all(chunk); total += chunk.length
    }
    console.log(`  ${name}: 选入 ${n}`)
  }
  console.log('完成，共发起 ' + total + ' 次选课（异步落库，余量秒级生效）✅')
}
main().catch(e => { console.error(e); process.exit(1) })

// 灌入干净的演示数据：固定账号 + 一个开放批次 + 若干像样课程并预热。
// 用法: node seed-demo.mjs [后端地址]
const BASE = process.argv[2] || 'http://localhost:8088'

async function call(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = 'Bearer ' + token
  const res = await fetch(BASE + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  const text = await res.text()
  let data = null; try { data = text ? JSON.parse(text) : null } catch { data = text }
  return { status: res.status, data }
}

// 注册；若已存在则改为登录，保证可重复运行
async function ensureUser(username, name, password, role) {
  const r = await call('POST', '/api/auth/register', { username, name, password, role })
  if (r.status === 200) return r.data
  const l = await call('POST', '/api/auth/login', { username, password })
  return l.data
}

async function main() {
  const admin = await ensureUser('admin', '教务管理员', 'admin123', 'ADMIN')
  const student = await ensureUser('student', '张三', 'student123', 'STUDENT')
  console.log('管理员 admin/admin123, 学生 student/student123 就绪')

  const now = Date.now()
  const batch = (await call('POST', '/api/batches', {
    name: '2026 秋季选课',
    openAt: new Date(now - 3600_000).toISOString(),
    closeAt: new Date(now + 7 * 24 * 3600_000).toISOString(),
  }, admin.token)).data
  console.log('开放批次 #' + batch.id + ' 已创建')

  const courses = [
    { name: '软件体系结构', teacher: '李建国', timeSlot: '周一 1-2 节', capacity: 50 },
    { name: '高等数学（上）', teacher: '王秀英', timeSlot: '周二 3-4 节', capacity: 120 },
    { name: '数据结构与算法', teacher: '陈志远', timeSlot: '周三 5-6 节', capacity: 80 },
    { name: '计算机网络', teacher: '赵敏', timeSlot: '周四 1-2 节', capacity: 100 },
    { name: '人工智能导论', teacher: '刘洋', timeSlot: '周五 3-4 节', capacity: 150 },
    { name: '操作系统（实验班）', teacher: '孙立', timeSlot: '周三 7-8 节', capacity: 2 },
  ]
  for (const c of courses) {
    const created = (await call('POST', '/api/courses', { ...c, batchId: batch.id }, admin.token)).data
    await call('POST', `/api/admin/courses/${created.id}/preheat`, null, admin.token)
    console.log(`  课程 #${created.id} ${c.name}（容量${c.capacity}）已发布并预热`)
  }
  console.log('演示数据灌入完成 ✅')
}

main().catch(e => { console.error(e); process.exit(1) })

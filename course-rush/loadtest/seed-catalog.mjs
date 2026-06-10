// 灌入计算机专业课程目录：5 门必修 + 10 门公选，每门容量 200。
// 课程性质用名称前缀【必修】/【公选】标注，前端解析为彩色徽章。
const BASE = process.argv[2] || 'http://localhost:8088'
const call = (m, p, b, t) => fetch(BASE + p, {
  method: m, headers: { 'Content-Type': 'application/json', ...(t ? { Authorization: 'Bearer ' + t } : {}) },
  body: b ? JSON.stringify(b) : undefined,
}).then(async r => ({ s: r.status, d: await r.json().catch(() => null) }))

const required = [
  ['操作系统', '孙立', '周一 1-2 节'],
  ['计算机组成原理', '李建国', '周二 3-4 节'],
  ['数据结构与算法', '陈志远', '周三 1-2 节'],
  ['计算机网络', '赵敏', '周四 3-4 节'],
  ['软件体系结构', '王秀英', '周五 1-2 节'],
]
const elective = [
  ['人工智能导论', '刘洋', '周一 5-6 节'],
  ['机器学习实战', '周婷', '周二 7-8 节'],
  ['Python 程序设计', '黄磊', '周三 5-6 节'],
  ['Web 前端开发', '吴桐', '周四 7-8 节'],
  ['数据库系统原理', '郑凯', '周五 5-6 节'],
  ['云计算与容器技术', '林浩', '周一 9-10 节'],
  ['区块链技术与应用', '徐丽', '周二 9-10 节'],
  ['网络空间安全导论', '何强', '周三 9-10 节'],
  ['计算机视觉', '马俊', '周四 9-10 节'],
  ['大数据分析', '高源', '周五 9-10 节'],
]

async function main() {
  const adm = (await call('POST', '/api/auth/login', { username: 'admin', password: 'admin123' })).d
  if (!adm?.token) throw new Error('管理员登录失败')

  const now = Date.now()
  const batch = (await call('POST', '/api/batches', {
    name: '2026 秋季选课 · 计算机科学与技术学院',
    openAt: new Date(now - 3600_000).toISOString(),
    closeAt: new Date(now + 14 * 24 * 3600_000).toISOString(),
  }, adm.token)).d
  console.log('开放批次 #' + batch.id)

  let n = 0
  for (const [name, teacher, timeSlot] of required) {
    const c = (await call('POST', '/api/courses', { name: '【必修】' + name, teacher, timeSlot, capacity: 200, batchId: batch.id }, adm.token)).d
    await call('POST', `/api/admin/courses/${c.id}/preheat`, null, adm.token); n++
  }
  for (const [name, teacher, timeSlot] of elective) {
    const c = (await call('POST', '/api/courses', { name: '【公选】' + name, teacher, timeSlot, capacity: 200, batchId: batch.id }, adm.token)).d
    await call('POST', `/api/admin/courses/${c.id}/preheat`, null, adm.token); n++
  }
  console.log(`已发布并预热 ${n} 门课程（5 必修 + 10 公选，各容量 200）✅`)
}
main().catch(e => { console.error(e); process.exit(1) })

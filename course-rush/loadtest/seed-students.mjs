// 批量创建计算机专业学生账号。
// 学号 = "25" + 六位序号（共 8 位，唯一），姓名随机中文名，密码统一 123456。
// 用法: node seed-students.mjs [数量] [后端地址]
const COUNT = parseInt(process.argv[2] || '250', 10)
const BASE = process.argv[3] || 'http://localhost:8088'

const surnames = '王李张刘陈杨赵黄周吴徐孙胡朱高林何郭马罗梁宋郑谢韩唐冯于董萧程曹袁邓许傅沈曾彭吕苏卢蒋蔡贾丁魏薛叶阎余潘杜戴夏钟汪田任姜范方石姚谭廖邹熊金陆郝孔白崔康毛邱秦江史顾侯邵孟龙万段漕钱汤尹黎易常武乔贺赖龚文'.split('')
const given1 = '伟芳娜秀英敏静丽强磊军洋勇艳杰娟涛明超秀霞平刚桂兰建国华建辉飞鹏宇浩然子轩思博文宇博睿婷婷欣怡梓涵雨萱浩宇思远佳怡子墨'.split('')
const given2 = '伟杰浩然子轩睿博文博俊熙宇航梓涵思齐雅婷诗琪欣妍梦琪嘉怡晨曦昊天天宇泽宇'.split('')

function randName(i) {
  const s = surnames[i % surnames.length]
  // 交替单字名/双字名，丰富多样
  if (i % 3 === 0) return s + given1[(i * 7) % given1.length]
  return s + given1[(i * 5) % given1.length] + given2[(i * 11) % given2.length]
}

const call = (m, p, b) => fetch(BASE + p, {
  method: m, headers: { 'Content-Type': 'application/json' }, body: b ? JSON.stringify(b) : undefined,
}).then(r => r.status)

async function main() {
  let ok = 0, exist = 0, fail = 0
  const CONC = 16
  const ids = Array.from({ length: COUNT }, (_, k) => k + 1)
  for (let i = 0; i < ids.length; i += CONC) {
    const chunk = ids.slice(i, i + CONC).map(async (seq) => {
      const sno = '25' + String(seq).padStart(6, '0') // 25000001 ...
      const s = await call('POST', '/api/auth/register', {
        username: sno, name: randName(seq), password: '123456', role: 'STUDENT',
      })
      if (s === 200) ok++; else if (s === 400 || s === 409) exist++; else fail++
    })
    await Promise.all(chunk)
    if ((i / CONC) % 4 === 0) console.log(`进度 ${Math.min(i + CONC, COUNT)}/${COUNT}`)
  }
  console.log(`完成：新建 ${ok}，已存在/重复 ${exist}，失败 ${fail}`)
  console.log('学号范围 25000001 ~ 25' + String(COUNT).padStart(6, '0') + '，密码统一 123456')
}
main().catch(e => { console.error(e); process.exit(1) })

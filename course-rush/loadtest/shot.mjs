// 用 Chrome DevTools Protocol(Node22 自带 WebSocket)真实登录并截图，验证登录后页面。
// 用法: node shot.mjs <用户名> <密码> <输出png> [url]
import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'

const USER = process.argv[2] || 'student'
const PASS = process.argv[3] || 'student123'
const OUT = process.argv[4] || 'D:\\software_work\\_app_shot.png'
const PORTAL = process.argv[5] || 'student' // student | admin
const URL = process.argv[6] || 'http://localhost:5173/'
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const PORT = 9333

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

const ud = process.env.TEMP + '\\cdp_' + Date.now()
const chrome = spawn(CHROME, [
  '--headless', '--disable-gpu', '--no-first-run', '--hide-scrollbars',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${ud}`,
  '--window-size=1460,1040', URL,
], { stdio: 'ignore' })

let ws
async function getPageWs() {
  for (let i = 0; i < 40; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()
      const page = list.find(t => t.type === 'page' && t.webSocketDebuggerUrl)
      if (page) return page.webSocketDebuggerUrl
    } catch { /* not up yet */ }
    await sleep(250)
  }
  throw new Error('CDP page target not found')
}

let _id = 0
const pending = new Map()
function send(method, params = {}) {
  const id = ++_id
  ws.send(JSON.stringify({ id, method, params }))
  return new Promise((res, rej) => pending.set(id, { res, rej }))
}

async function main() {
  const url = await getPageWs()
  ws = new WebSocket(url)
  await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data)
    if (m.id && pending.has(m.id)) { pending.get(m.id).res(m.result); pending.delete(m.id) }
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Emulation.setDeviceMetricsOverride', { width: 1460, height: 1040, deviceScaleFactor: 2, mobile: false })
  await sleep(1500) // 等首屏(登录页)渲染

  // 填表并提交
  const fill = `(()=>{
    const setV=(el,v)=>{const d=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;d.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));};
    if('${PORTAL}'==='admin'){const pb=[...document.querySelectorAll('.portal button')].find(b=>b.textContent.includes('管理员'));if(pb)pb.click();}
    const ins=[...document.querySelectorAll('input')];
    const u=ins.find(i=>i.type!=='password');
    const p=ins.find(i=>i.type==='password');
    if(!u||!p) return 'no-inputs';
    setV(u,'${USER}'); setV(p,'${PASS}');
    const sub=document.querySelector('button[type=submit]'); if(!sub) return 'no-submit';
    sub.click(); return 'submitted';
  })()`
  const r = await send('Runtime.evaluate', { expression: fill, returnByValue: true })
  console.log('fill:', r.result?.value)

  await sleep(2800) // 等登录请求 + 课程列表渲染

  if (process.env.MODAL) {
    await send('Runtime.evaluate', { expression: `document.querySelector('.course')?.click()`, returnByValue: true })
    await sleep(700)
  }

  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
  writeFileSync(OUT, Buffer.from(shot.data, 'base64'))
  console.log('saved', OUT)

  ws.close()
  chrome.kill()
  process.exit(0)
}

main().catch(e => { console.error(e); try { chrome.kill() } catch {} process.exit(1) })

import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
const USER = process.argv[2] || '25010101'
const PASS = process.argv[3] || '123123'
const OUT = process.argv[4] || 'D:\\software_work\\_app.png'
const W = +(process.argv[5] || 1760), H = +(process.argv[6] || 1000)
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const PORT = 9356
const sleep = ms => new Promise(r => setTimeout(r, ms))
const ud = process.env.TEMP + '\\cdp_app_' + process.pid
const chrome = spawn(CHROME, ['--headless', '--disable-gpu', '--no-first-run', '--hide-scrollbars',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${ud}`, `--window-size=${W},${H}`, 'http://localhost:5173/'], { stdio: 'ignore' })
let ws, _id = 0; const pending = new Map()
const send = (method, params = {}) => { const id = ++_id; ws.send(JSON.stringify({ id, method, params })); return new Promise(r => pending.set(id, r)) }
async function pageWs() { for (let i = 0; i < 40; i++) { try { const l = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); const p = l.find(t => t.type === 'page' && t.webSocketDebuggerUrl); if (p) return p.webSocketDebuggerUrl } catch {} await sleep(250) } throw new Error('no page') }
const url = await pageWs(); ws = new WebSocket(url)
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id) } }
await send('Page.enable'); await send('Runtime.enable')
await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 1.5, mobile: false })
await sleep(1600)
const ADMIN = process.env.ADMIN ? 'admin' : 'student'
const fill = `(()=>{const setV=(el,v)=>{const d=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set;d.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));};if('${ADMIN}'==='admin'){const pb=[...document.querySelectorAll('.portal button')].find(b=>b.textContent.includes('管理员'));if(pb)pb.click();}const ins=[...document.querySelectorAll('input')];const u=ins.find(i=>i.type!=='password');const p=ins.find(i=>i.type==='password');if(!u||!p)return 'no-inputs';setV(u,'${USER}');setV(p,'${PASS}');const sub=document.querySelector('button[type=submit]');if(!sub)return 'no-submit';sub.click();return 'submitted';})()`
const r = await send('Runtime.evaluate', { expression: fill, returnByValue: true })
console.log('fill:', r.result?.value)
await sleep(3000)
if (process.env.TAB) {
  await send('Runtime.evaluate', { expression: `(()=>{const b=[...document.querySelectorAll('.nav-tabs button')].find(x=>x.textContent.includes('${process.env.TAB}'));if(b)b.click();return !!b})()`, returnByValue: true })
  await sleep(1500)
}
const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
writeFileSync(OUT, Buffer.from(shot.data, 'base64')); console.log('saved', OUT)
ws.close(); chrome.kill(); process.exit(0)

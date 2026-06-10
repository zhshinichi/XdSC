import { spawn } from 'node:child_process'
import { writeFileSync } from 'node:fs'
const OUT = process.argv[2] || 'D:\\software_work\\_login.png'
const W = +(process.argv[3] || 1460), H = +(process.argv[4] || 980)
const CHROME = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
const PORT = 9355
const sleep = ms => new Promise(r => setTimeout(r, ms))
const ud = process.env.TEMP + '\\cdp_login_' + process.pid
const chrome = spawn(CHROME, ['--headless', '--disable-gpu', '--no-first-run', '--hide-scrollbars',
  `--remote-debugging-port=${PORT}`, `--user-data-dir=${ud}`, `--window-size=${W},${H}`, 'http://localhost:5173/'], { stdio: 'ignore' })
let ws, _id = 0; const pending = new Map()
const send = (method, params = {}) => { const id = ++_id; ws.send(JSON.stringify({ id, method, params })); return new Promise(r => pending.set(id, r)) }
async function pageWs() { for (let i = 0; i < 40; i++) { try { const l = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json(); const p = l.find(t => t.type === 'page' && t.webSocketDebuggerUrl); if (p) return p.webSocketDebuggerUrl } catch {} await sleep(250) } throw new Error('no page') }
const url = await pageWs(); ws = new WebSocket(url)
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = rej })
ws.onmessage = e => { const m = JSON.parse(e.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id) } }
await send('Page.enable'); await send('Runtime.enable')
await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 2, mobile: false })
await sleep(1800)
const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
writeFileSync(OUT, Buffer.from(shot.data, 'base64')); console.log('saved', OUT)
ws.close(); chrome.kill(); process.exit(0)

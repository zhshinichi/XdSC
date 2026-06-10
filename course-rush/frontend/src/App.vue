<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, h } from 'vue'
import { api, setToken, getToken } from './api'
import { getMeta } from './courseMeta'

/* ---------- 线性图标（替代 emoji，SF Symbols 风格） ---------- */
const ICON_PATHS = {
  teacher: ['M12 12a4 4 0 100-8 4 4 0 000 8z', 'M5 20c0-3.3 3.1-5.5 7-5.5s7 2.2 7 5.5'],
  clock: ['M12 21a9 9 0 100-18 9 9 0 000 18z', 'M12 7.5V12l3 2'],
  location: ['M12 21s-6.5-5.8-6.5-10.5a6.5 6.5 0 1113 0C18.5 15.2 12 21 12 21z', 'M12 12.5a2.2 2.2 0 100-4.4 2.2 2.2 0 000 4.4z'],
  building: ['M4 21h16', 'M6 21V5a1 1 0 011-1h10a1 1 0 011 1v16', 'M9.5 8h0M14.5 8h0M9.5 12h0M14.5 12h0M9.5 16h2.5'],
  search: ['M11 18a7 7 0 100-14 7 7 0 000 14z', 'M20 20l-3.5-3.5'],
  award: ['M12 14a5 5 0 100-10 5 5 0 000 10z', 'M8.5 13l-1 7 4.5-2.3L16.5 20l-1-7'],
  calendar: ['M5 7a2 2 0 012-2h10a2 2 0 012 2v11a2 2 0 01-2 2H7a2 2 0 01-2-2V7z', 'M5 10h14M9 4v3M15 4v3'],
  filter: ['M4 6h16', 'M7 12h10', 'M10 18h4'],
  calendarBig: ['M5 7a2 2 0 012-2h10a2 2 0 012 2v11a2 2 0 01-2 2H7a2 2 0 01-2-2V7z', 'M5 10h14M9 4v3M15 4v3'],
}
const Icon = (props) => h('svg', {
  class: 'ic', viewBox: '0 0 24 24', width: props.size || 16, height: props.size || 16,
  fill: 'none', stroke: 'currentColor', 'stroke-width': props.sw || 1.7,
  'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'aria-hidden': 'true',
}, (ICON_PATHS[props.name] || []).map(d => h('path', { d })))

const me = reactive({ username: '', name: '', role: '', userId: null })
const loggedIn = computed(() => !!me.username)
const isAdmin = computed(() => me.role === 'ADMIN')
const displayName = computed(() => me.name || me.username)
const tab = ref('courses')

/* ---------- Toast ---------- */
const toasts = ref([])
let toastSeq = 0
function flash(text, type = 'success') {
  const id = ++toastSeq
  toasts.value.push({ id, text, type })
  setTimeout(() => { toasts.value = toasts.value.filter(t => t.id !== id) }, 3200)
}

/* ---------- 登录 / 注册 ---------- */
const portal = ref('STUDENT')
const authForm = reactive({ username: '', name: '', password: '', mode: 'login' })
const submitting = ref(false)
const portalLabel = computed(() => portal.value === 'ADMIN' ? '管理员' : '学生')
const idLabel = computed(() => portal.value === 'ADMIN' ? '工号 / 用户名' : '学号')
const idPlaceholder = computed(() => portal.value === 'ADMIN' ? '请输入工号或用户名' : '请输入学号')

async function submitAuth() {
  if (submitting.value) return
  submitting.value = true
  try {
    const fn = authForm.mode === 'login' ? api.login : api.register
    const payload = authForm.mode === 'login'
      ? { username: authForm.username, password: authForm.password }
      : { username: authForm.username, name: authForm.name, password: authForm.password, role: portal.value }
    const res = await fn(payload)
    if (res.role !== portal.value) {
      setToken('')
      const real = res.role === 'ADMIN' ? '管理员' : '学生'
      flash(`该账号是${real}，请从「${real}」入口登录`, 'error')
      return
    }
    setToken(res.token)
    me.username = res.username; me.name = res.name || ''; me.role = res.role; me.userId = res.userId
    flash('欢迎回来，' + (res.name || res.username))
    tab.value = res.role === 'ADMIN' ? 'admin' : 'courses'
    if (res.role === 'ADMIN') {
      await Promise.all([loadCourses(), loadBatches()])
    } else {
      await Promise.all([loadCourses(), loadMy(), loadBatches()])
      startPolling()
    }
  } catch (e) { flash(e.message, 'error') }
  finally { submitting.value = false }
}
function logout() {
  setToken(''); me.username = ''; me.role = ''; me.userId = null; tab.value = 'courses'; stopPolling()
}

/* ---------- 登录页辅助入口 ---------- */
function goRegister() { authForm.mode = 'register' }
function showHint(msg) { flash(msg, 'info') }
const authHints = {
  forgot: '忘记密码请携带学生证到教务处一站式服务大厅重置，或联系院系教务员。',
  rules: '选课规则：每轮选课在开放时间内进行，必修课优先保障，公选课先到先得，超过容量进入候补。',
  contact: '教务处联系电话：029-8820-XXXX（工作日 09:00-18:00），地址：信远楼一站式服务大厅。',
}

/* ---------- 数据 ---------- */
const courses = ref([])
const availability = ref({})       // id -> {capacity, enrolled, remaining}
const myList = ref([])
const batches = ref([])
const nowTs = ref(Date.now())

const courseById = computed(() => { const m = {}; for (const c of courses.value) m[c.id] = c; return m })
const myCourseIds = computed(() => new Set(myList.value.map(e => e.courseId)))

async function loadCourses() {
  try {
    const [list, avail] = await Promise.all([api.listCourses(), api.availability().catch(() => [])])
    courses.value = list
    const m = {}; for (const a of avail) m[a.id] = a; availability.value = m
  } catch (e) { flash(e.message, 'error') }
}
async function refreshAvailability() {
  try { const avail = await api.availability(); const m = {}; for (const a of avail) m[a.id] = a; availability.value = m } catch { /* ignore */ }
}
async function loadMy() { try { myList.value = await api.myEnrollments() } catch { /* ignore */ } }
async function loadBatches() { try { batches.value = await api.listBatches() } catch { /* ignore */ } }

/* ---------- 课程名 / 类型解析 ---------- */
function catOf(name) { const m = String(name).match(/^【([^】]+)】/); return m ? m[1] : '' }
function cleanName(name) { return String(name).replace(/^【[^】]+】\s*/, '') }

/* ---------- 上课时间解析 + 冲突检测 ---------- */
function parseSlot(ts) {
  const m = String(ts).match(/周([一二三四五六日])\s*(\d+)\s*-\s*(\d+)/)
  return m ? { day: m[1], start: +m[2], end: +m[3] } : null
}
function slotsConflict(a, b) {
  const pa = parseSlot(a), pb = parseSlot(b)
  if (!pa || !pb) return false
  return pa.day === pb.day && pa.start <= pb.end && pb.start <= pa.end
}
const mySlots = computed(() => myList.value.map(e => courseById.value[e.courseId]?.timeSlot).filter(Boolean))

/* ---------- 课程视图（合并元数据 + 余量 + 状态） ---------- */
const courseViews = computed(() => courses.value.map(c => {
  const name = cleanName(c.name)
  const meta = getMeta(name, c.id)
  const av = availability.value[c.id] || { capacity: c.capacity, enrolled: 0, remaining: c.capacity }
  const cat = catOf(c.name) || '公选'
  const picked = myCourseIds.value.has(c.id)
  const conflict = !picked && mySlots.value.some(s => slotsConflict(s, c.timeSlot))
  let status
  if (picked) status = 'picked'
  else if (av.remaining <= 0) status = 'full'
  else if (conflict) status = 'conflict'
  else if (av.remaining <= Math.max(5, Math.floor(c.capacity * 0.1))) status = 'tense'
  else status = 'open'
  const pct = c.capacity ? Math.min(100, Math.round(av.enrolled / c.capacity * 100)) : 0
  return { id: c.id, name, rawName: c.name, teacher: c.teacher, timeSlot: c.timeSlot, cat, meta, ...av, picked, conflict, status, pct }
}))

/* ---------- 筛选 / 排序 ---------- */
const filterType = ref('ALL')
const filterCollege = ref('ALL')
const filterDay = ref('ALL')
const filterStatus = ref('ALL')
const search = ref('')
const sortBy = ref('default')
const filtersOpen = ref(false)

const colleges = computed(() => [...new Set(courseViews.value.map(c => c.meta.college))])
const days = ['一', '二', '三', '四', '五']

const filteredCourses = computed(() => {
  let arr = courseViews.value
  if (filterType.value !== 'ALL') arr = arr.filter(c => c.cat === filterType.value)
  if (filterCollege.value !== 'ALL') arr = arr.filter(c => c.meta.college === filterCollege.value)
  if (filterDay.value !== 'ALL') arr = arr.filter(c => parseSlot(c.timeSlot)?.day === filterDay.value)
  if (filterStatus.value !== 'ALL') arr = arr.filter(c => c.status === filterStatus.value)
  const q = search.value.trim().toLowerCase()
  if (q) arr = arr.filter(c => (c.name + c.teacher + c.meta.code).toLowerCase().includes(q))
  arr = [...arr]
  if (sortBy.value === 'remaining') arr.sort((a, b) => a.remaining - b.remaining)
  else if (sortBy.value === 'credit') arr.sort((a, b) => b.meta.credit - a.meta.credit)
  else if (sortBy.value === 'hot') arr.sort((a, b) => b.enrolled - a.enrolled)
  return arr
})
function resetFilters() {
  filterType.value = 'ALL'; filterCollege.value = 'ALL'; filterDay.value = 'ALL'
  filterStatus.value = 'ALL'; search.value = ''; sortBy.value = 'default'
}

/* ---------- 我的选课统计 ---------- */
const myViews = computed(() => myList.value.map(e => {
  const c = courseById.value[e.courseId]
  const name = c ? cleanName(c.name) : ('课程 #' + e.courseId)
  const meta = c ? getMeta(name, c.id) : { credit: 0, location: '', code: '' }
  return { id: e.courseId, name, status: e.status, createdAt: e.createdAt, timeSlot: c?.timeSlot || '', credit: meta.credit, teacher: c?.teacher || '', location: meta.location || '', code: meta.code || '' }
}))

/* ---------- 周课表网格 ---------- */
const TT_DAYS = [{ k: '一', label: '周一' }, { k: '二', label: '周二' }, { k: '三', label: '周三' }, { k: '四', label: '周四' }, { k: '五', label: '周五' }, { k: '六', label: '周六' }, { k: '日', label: '周日' }]
const TT_TIMES = ['08:00', '08:50', '09:55', '10:45', '14:00', '14:50', '15:55', '16:45', '19:00', '19:50', '20:40', '21:30']
const timetable = computed(() => {
  const grid = {}            // "dayIdx-period" -> {block,span,course,color} | {covered}
  let maxP = 8
  myViews.value.forEach((m, i) => {
    const s = parseSlot(m.timeSlot)
    if (!s) return
    const di = TT_DAYS.findIndex(d => d.k === s.day)
    if (di < 0) return
    maxP = Math.max(maxP, s.end)
    grid[di + '-' + s.start] = { block: true, span: s.end - s.start + 1, course: m, color: i % 6 }
    for (let p = s.start + 1; p <= s.end; p++) grid[di + '-' + p] = { covered: true }
  })
  const rows = []
  for (let p = 1; p <= maxP; p++) {
    const cells = []
    for (let di = 0; di < TT_DAYS.length; di++) {
      const g = grid[di + '-' + p]
      if (g && g.covered) cells.push({ skip: true })
      else if (g && g.block) cells.push({ course: g.course, span: g.span, color: g.color })
      else cells.push({ empty: true })
    }
    rows.push({ period: p, time: TT_TIMES[p - 1] || '', cells })
  }
  return { rows, days: TT_DAYS }
})
const unscheduled = computed(() => myViews.value.filter(m => !parseSlot(m.timeSlot)))
const myCredits = computed(() => myViews.value.reduce((s, v) => s + (v.credit || 0), 0))
const myConflicts = computed(() => {
  const out = []; const cs = myViews.value
  for (let i = 0; i < cs.length; i++) for (let j = i + 1; j < cs.length; j++)
    if (slotsConflict(cs[i].timeSlot, cs[j].timeSlot)) out.push([cs[i].name, cs[j].name])
  return out
})
const records = computed(() => [...myViews.value].sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt))))

/* ---------- 顶部状态区 ---------- */
const openBatch = computed(() => batches.value.find(b =>
  new Date(b.openAt).getTime() <= nowTs.value && nowTs.value <= new Date(b.closeAt).getTime()))
const rushOpen = computed(() => !!openBatch.value)
const countdown = computed(() => {
  if (!openBatch.value) return '—'
  let ms = new Date(openBatch.value.closeAt).getTime() - nowTs.value
  if (ms < 0) ms = 0
  const d = Math.floor(ms / 86400000), h = Math.floor(ms / 3600000) % 24
  const m = Math.floor(ms / 60000) % 60, s = Math.floor(ms / 1000) % 60
  return `${d}天 ${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

/* ---------- 抢课 / 退课 ---------- */
const busyId = ref(null)
async function enroll(c) {
  if (busyId.value) return
  if (isAdmin.value) { flash('管理员账号不能选课', 'error'); return }
  if (c.status === 'full') { flash('该课程名额已满', 'error'); return }
  if (c.status === 'conflict') { flash('与已选课程时间冲突，无法抢课', 'warn'); return }
  busyId.value = c.id
  try {
    const r = await api.enroll(c.id)
    flash(`抢课${r.status === 'PENDING' ? '受理成功（处理中）' : '成功'}：${c.name}`, 'success')
    await Promise.all([loadMy(), refreshAvailability()])
  } catch (e) { flash(e.message, 'error') }
  finally { busyId.value = null }
}
async function drop(c) {
  if (busyId.value) return
  busyId.value = c.id
  try {
    await api.drop(c.id)
    flash('已退课：' + c.name, 'info')
    await Promise.all([loadMy(), refreshAvailability()])
  } catch (e) { flash(e.message, 'error') }
  finally { busyId.value = null }
}

/* ---------- 详情弹窗 ---------- */
const detail = ref(null)
function openDetail(c) { detail.value = c }
function closeDetail() { detail.value = null }

/* ---------- 状态文案 ---------- */
const statusText = (s) => ({ open: '可抢', tense: '名额紧张', full: '已满', picked: '已选', conflict: '时间冲突' }[s])
const statusTag = (s) => ({ open: 'tag-open', tense: 'tag-tense', full: 'tag-full', picked: 'tag-picked', conflict: 'tag-conflict' }[s])
const catClass = (cat) => cat === '必修' ? 'badge-brand' : 'badge-info'
const fmt = (t) => t ? String(t).replace('T', ' ').slice(0, 16) : ''
const avatarText = (u) => (u || '?').slice(0, 1).toUpperCase()

/* ---------- 轮询 / 计时 ---------- */
let tick = null, poll = null
function startPolling() {
  stopPolling()
  poll = setInterval(() => { if (loggedIn.value && tab.value === 'courses') refreshAvailability() }, 5000)
}
function stopPolling() { if (poll) { clearInterval(poll); poll = null } }
onMounted(() => { tick = setInterval(() => { nowTs.value = Date.now() }, 1000) })
onUnmounted(() => { if (tick) clearInterval(tick); stopPolling() })

/* ---------- 管理后台 ---------- */
const batchForm = reactive({ name: '', openAt: '', closeAt: '' })
const courseForm = reactive({ name: '', teacher: '', timeSlot: '', capacity: 200, batchId: null })
const statsResult = ref(null)
async function createBatch() {
  try {
    await api.createBatch({ name: batchForm.name, openAt: new Date(batchForm.openAt).toISOString(), closeAt: new Date(batchForm.closeAt).toISOString() })
    flash('批次已创建'); await loadBatches()
  } catch (e) { flash(e.message, 'error') }
}
async function createCourse() {
  try {
    await api.createCourse({ ...courseForm, capacity: Number(courseForm.capacity), batchId: Number(courseForm.batchId) })
    flash('课程已发布'); await loadCourses()
  } catch (e) { flash(e.message, 'error') }
}
async function preheat(c) { try { await api.preheat(c.id); flash('已预热库存：' + cleanName(c.name)) } catch (e) { flash(e.message, 'error') } }
async function viewStats(c) { try { statsResult.value = { name: cleanName(c.name), ...(await api.stats(c.id)) } } catch (e) { flash(e.message, 'error') } }

function switchTab(t) {
  tab.value = t
  if (t === 'courses') { loadCourses() }
  if (t === 'my') loadMy()
  if (t === 'admin') { loadBatches(); loadCourses() }
}
onMounted(() => { if (getToken()) setToken('') /* 不恢复会话：清掉残留旧令牌，避免被携带导致 401 */ })
</script>

<template>
  <!-- Toasts -->
  <div class="toast-wrap">
    <div v-for="t in toasts" :key="t.id" class="toast" :class="t.type">
      <span class="dot"></span>{{ t.text }}
    </div>
  </div>

  <!-- ===================== 登录 ===================== -->
  <div v-if="!loggedIn" class="auth">
    <!-- 左：校园品牌区 -->
    <aside class="auth-brand">
      <div class="brand-top">
        <img src="/xidian-logo.png" class="brand-logo" alt="西安电子科技大学" />
        <div class="brand-name">
          <strong>西安电子科技大学</strong>
          <span>XIDIAN UNIVERSITY · 1931</span>
        </div>
      </div>

      <div class="brand-hero">
        <div class="brand-kicker">统一教务选课平台</div>
        <h1>教务选课系统</h1>
        <p>支持课程查询、抢课、退课与课表管理，当前学期选课服务正在开放，欢迎登录使用。</p>
        <div class="brand-status">
          <span class="bs-dot"></span>当前学期选课服务正在开放
        </div>
      </div>

      <div class="brand-info">
        <div class="bi-item"><span class="bi-label">当前学期</span><span class="bi-val">2025-2026 学年春季学期</span></div>
        <div class="bi-item"><span class="bi-label">当前轮次</span><span class="bi-val">第一轮抢课</span></div>
        <div class="bi-item"><span class="bi-label">开放时间</span><span class="bi-val">每日 09:00 - 18:00</span></div>
        <div class="bi-item"><span class="bi-label">系统状态</span><span class="bi-val ok"><i></i>运行正常</span></div>
      </div>

      <div class="brand-foot">软件体系结构课程项目 · Course Enrollment System</div>
    </aside>

    <!-- 右：登录操作区 -->
    <main class="auth-panel">
      <div class="auth-card">
        <div class="auth-mobile-logo">
          <img src="/xidian-logo.png" alt="logo" />
          <strong>西安电子科技大学 · 教务选课系统</strong>
        </div>

        <header class="ac-head">
          <h2>{{ portalLabel }}{{ authForm.mode === 'login' ? '登录' : '注册' }}</h2>
          <p class="auth-sub">{{ authForm.mode === 'login'
            ? (portal === 'ADMIN' ? '教务管理后台，请使用管理员账号登录' : '欢迎回来，请使用学号与密码登录')
            : (portal === 'ADMIN' ? '创建一个教务管理员账号' : '注册学籍账号开始选课') }}</p>
        </header>

        <div class="portal" role="tablist">
          <span class="portal-thumb" :class="{ right: portal === 'ADMIN' }"></span>
          <button role="tab" :class="{ on: portal === 'STUDENT' }" @click="portal = 'STUDENT'">学生</button>
          <button role="tab" :class="{ on: portal === 'ADMIN' }" @click="portal = 'ADMIN'">管理员</button>
        </div>

        <form @submit.prevent="submitAuth">
          <div class="field">
            <label>{{ idLabel }}</label>
            <input class="input" v-model="authForm.username" :placeholder="idPlaceholder" autocomplete="username" />
          </div>
          <div class="field" v-if="authForm.mode === 'register'">
            <label>姓名</label>
            <input class="input" v-model="authForm.name" placeholder="请输入真实姓名" />
          </div>
          <div class="field">
            <div class="field-top">
              <label>密码</label>
              <a v-if="authForm.mode === 'login'" href="#" class="forgot" @click.prevent="showHint(authHints.forgot)">忘记密码？</a>
            </div>
            <input class="input" v-model="authForm.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
          </div>
          <button class="btn btn-primary btn-block btn-lg" type="submit" :disabled="submitting">
            <span v-if="submitting" class="spinner"></span>
            {{ submitting ? '登录中…' : (authForm.mode === 'login' ? '登 录' : '注册并登录') }}
          </button>
        </form>

        <p class="auth-switch">{{ authForm.mode === 'login' ? '还没有账号？' : '已有账号？' }}
          <a href="#" @click.prevent="authForm.mode = authForm.mode === 'login' ? 'register' : 'login'">
            {{ authForm.mode === 'login' ? '立即注册' : '返回登录' }}</a>
        </p>

        <div class="ac-links">
          <a href="#" @click.prevent="goRegister">新用户注册</a>
          <span class="sep"></span>
          <a href="#" @click.prevent="showHint(authHints.rules)">选课规则</a>
          <span class="sep"></span>
          <a href="#" @click.prevent="showHint(authHints.contact)">联系教务处</a>
        </div>

        <div class="ac-foot">
          <span><i class="live"></i>系统运行正常</span>
          <span>开放时间 09:00 - 18:00</span>
        </div>
        <div class="ac-notice">公告：请在规定时间内完成选课</div>
      </div>
    </main>
  </div>

  <!-- ===================== 应用 ===================== -->
  <div v-else class="app">
    <header class="nav">
      <div class="nav-brand">
        <img src="/xidian-logo.png" alt="logo" />
        <div class="nav-title"><strong>西电选课系统</strong><span>Course Enrollment</span></div>
      </div>
      <nav class="nav-tabs">
        <button v-if="!isAdmin" :class="{ on: tab === 'courses' }" @click="switchTab('courses')">选课中心</button>
        <button v-if="!isAdmin" :class="{ on: tab === 'my' }" @click="switchTab('my')">我的课表</button>
        <button v-if="isAdmin" :class="{ on: tab === 'admin' }" @click="switchTab('admin')">管理后台</button>
      </nav>
      <div class="nav-user">
        <div class="avatar">{{ avatarText(displayName) }}</div>
        <div class="who"><strong>{{ displayName }}</strong>
          <span class="badge" :class="isAdmin ? 'badge-brand' : ''">{{ isAdmin ? '管理员' : '学生' }}</span></div>
        <button class="btn btn-ghost" @click="logout">退出</button>
      </div>
    </header>

    <!-- ============ 选课中心 ============ -->
    <main v-if="tab === 'courses'" class="content wide">
      <!-- 顶部抢课状态区 -->
      <div class="statusbar">
        <div class="sb-item"><span class="sb-label">当前学期</span><span class="sb-val">2026 秋季学期</span></div>
        <div class="sb-item"><span class="sb-label">当前轮次</span><span class="sb-val">第一轮抢课</span></div>
        <div class="sb-item"><span class="sb-label">抢课状态</span>
          <span class="sb-val"><span class="live" :class="{ off: !rushOpen }"></span>{{ rushOpen ? '进行中' : '未开放' }}</span></div>
        <div class="sb-item wide-item"><span class="sb-label">距本轮结束</span><span class="sb-val mono">{{ countdown }}</span></div>
        <div class="sb-sep"></div>
        <div class="sb-item"><span class="sb-label">已选课程</span><span class="sb-val">{{ myList.length }} 门</span></div>
        <div class="sb-item"><span class="sb-label">已选学分</span><span class="sb-val">{{ myCredits.toFixed(1) }}</span></div>
        <div class="sb-item"><span class="sb-label">可选课程</span><span class="sb-val">{{ courses.length }} 门</span></div>
      </div>

      <div class="layout">
        <!-- 中：课程列表 -->
        <section class="course-col">
          <!-- 顶部筛选工具条 -->
          <div class="list-toolbar">
            <div class="lt-top">
              <div class="search"><Icon name="search" class="search-ic" /><input class="input" v-model="search" placeholder="搜索课程名 / 教师 / 课程编号" /></div>
              <select class="select" v-model="filterCollege">
                <option value="ALL">全部学院</option>
                <option v-for="c in colleges" :key="c" :value="c">{{ c }}</option>
              </select>
              <select class="select" v-model="sortBy">
                <option value="default">默认排序</option>
                <option value="remaining">余量紧张优先</option>
                <option value="hot">报名热度优先</option>
                <option value="credit">学分高优先</option>
              </select>
              <button class="btn" @click="resetFilters">重置</button>
            </div>
            <div class="lt-filters">
              <div class="fchip"><span class="fc-label">类型</span>
                <div class="chips">
                  <button :class="{ on: filterType === 'ALL' }" @click="filterType = 'ALL'">全部</button>
                  <button :class="{ on: filterType === '必修' }" @click="filterType = '必修'">必修</button>
                  <button :class="{ on: filterType === '公选' }" @click="filterType = '公选'">公选</button>
                </div></div>
              <div class="fchip"><span class="fc-label">状态</span>
                <div class="chips">
                  <button :class="{ on: filterStatus === 'ALL' }" @click="filterStatus = 'ALL'">全部</button>
                  <button :class="{ on: filterStatus === 'open' }" @click="filterStatus = 'open'">可抢</button>
                  <button :class="{ on: filterStatus === 'tense' }" @click="filterStatus = 'tense'">紧张</button>
                  <button :class="{ on: filterStatus === 'full' }" @click="filterStatus = 'full'">已满</button>
                  <button :class="{ on: filterStatus === 'picked' }" @click="filterStatus = 'picked'">已选</button>
                </div></div>
              <div class="fchip"><span class="fc-label">时间</span>
                <div class="chips">
                  <button :class="{ on: filterDay === 'ALL' }" @click="filterDay = 'ALL'">全部</button>
                  <button v-for="d in days" :key="d" :class="{ on: filterDay === d }" @click="filterDay = d">周{{ d }}</button>
                </div></div>
            </div>
          </div>

          <div class="course-toolbar">
            <span>共 <b>{{ filteredCourses.length }}</b> 门课程</span>
            <span class="muted">实时余量每 5 秒刷新</span>
          </div>
          <div v-if="filteredCourses.length" class="course-list">
            <article v-for="c in filteredCourses" :key="c.id" class="course-row" :class="[{ picked: c.picked }, 'st-' + c.status]" @click="openDetail(c)">
              <div class="cr-accent" :class="c.status"></div>
              <div class="cr-main">
                <div class="cr-top">
                  <span class="c-code">{{ c.meta.code }}</span>
                  <span class="badge" :class="catClass(c.cat)">{{ c.cat }}</span>
                  <span class="badge">{{ c.meta.credit.toFixed(1) }} 学分</span>
                  <span class="tag mobile-tag" :class="statusTag(c.status)">{{ statusText(c.status) }}</span>
                </div>
                <h3 class="cr-name">{{ c.name }}</h3>
                <div class="cr-meta">
                  <span><Icon name="teacher" />{{ c.teacher }}</span>
                  <span><Icon name="clock" />{{ c.timeSlot }}</span>
                  <span><Icon name="location" />{{ c.meta.location }}</span>
                  <span><Icon name="building" />{{ c.meta.college.replace('学院', '') }}</span>
                </div>
              </div>
              <div class="cr-status">
                <span class="tag" :class="statusTag(c.status)">{{ statusText(c.status) }}</span>
              </div>
              <div class="cr-prog">
                <div class="cp-line">
                  <span>已选 <b>{{ c.enrolled }}</b> / {{ c.capacity }}</span>
                  <span :class="{ 'rem-low': c.remaining <= Math.max(5, c.capacity * 0.1) }">剩余 {{ c.remaining }}</span>
                </div>
                <div class="bar"><div class="bar-fill" :class="c.status" :style="{ width: c.pct + '%' }"></div></div>
              </div>
              <div class="cr-actions" @click.stop>
                <button class="btn btn-sm btn-link" @click="openDetail(c)">详情</button>
                <button v-if="c.picked" class="btn btn-sm btn-picked" :disabled="busyId === c.id" @click="drop(c)">
                  {{ busyId === c.id ? '处理中…' : '✓ 已选 · 退课' }}</button>
                <button v-else-if="c.status === 'full'" class="btn btn-sm" disabled>已满</button>
                <button v-else-if="c.status === 'conflict'" class="btn btn-sm btn-warn" disabled>时间冲突</button>
                <button v-else class="btn btn-sm btn-primary" :disabled="busyId === c.id" @click="enroll(c)">
                  {{ busyId === c.id ? '抢课中…' : '抢 课' }}</button>
              </div>
            </article>
          </div>
          <div v-else class="empty"><Icon name="search" :size="34" :sw="1.4" /><span>没有符合条件的课程，试试调整筛选</span></div>
        </section>

        <!-- 右：我的选课 -->
        <aside class="my-panel">
          <div class="mp-card">
            <div class="mp-head"><h3>我的选课</h3><span class="badge badge-brand">{{ myCredits.toFixed(1) }} 学分</span></div>
            <div v-if="myConflicts.length" class="conflict-warn">
              ⚠️ 时间冲突：
              <div v-for="(p, i) in myConflicts" :key="i">「{{ p[0] }}」与「{{ p[1] }}」</div>
            </div>
            <div v-if="myViews.length" class="mp-list">
              <div v-for="m in myViews" :key="m.id" class="mp-row">
                <div class="mpr-main">
                  <div class="mpr-name">{{ m.name }}</div>
                  <div class="mpr-meta">{{ m.timeSlot || '时间待定' }} · {{ m.credit.toFixed(1) }} 学分</div>
                </div>
                <button class="btn btn-sm btn-danger" :disabled="busyId === m.id" @click="drop(m)">退</button>
              </div>
            </div>
            <div v-else class="mp-empty">还没有选课</div>
          </div>
          <div class="mp-card">
            <div class="mp-head"><h3>抢课记录</h3></div>
            <div v-if="records.length" class="rec-list">
              <div v-for="r in records" :key="r.id" class="rec-row">
                <span class="rec-dot" :class="r.status === 'ENROLLED' ? 'ok' : 'pending'"></span>
                <span class="rec-name">{{ r.name }}</span>
                <span class="rec-time">{{ fmt(r.createdAt).slice(5) }}</span>
              </div>
            </div>
            <div v-else class="mp-empty">暂无记录</div>
          </div>
        </aside>
      </div>
    </main>

    <!-- ============ 我的课表 ============ -->
    <main v-else-if="tab === 'my'" class="content">
      <div class="page-head"><h2>我的课表</h2><span class="muted">已选 {{ myList.length }} 门 · {{ myCredits.toFixed(1) }} 学分</span></div>
      <template v-if="myViews.length">
        <div v-if="myConflicts.length" class="conflict-warn schedule-warn">
          ⚠️ 时间冲突：<span v-for="(p, i) in myConflicts" :key="i">「{{ p[0] }}」与「{{ p[1] }}」<template v-if="i < myConflicts.length - 1">；</template></span>
        </div>
        <div class="tt-wrap">
          <table class="timetable">
            <thead>
              <tr>
                <th class="tt-time-h">节次 / 时间</th>
                <th v-for="d in timetable.days" :key="d.k">{{ d.label }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in timetable.rows" :key="r.period">
                <td class="tt-time"><b>第 {{ r.period }} 节</b><span>{{ r.time }}</span></td>
                <template v-for="(c, di) in r.cells" :key="di">
                  <td v-if="c.empty" class="tt-cell"></td>
                  <td v-else-if="c.course" class="tt-cell tt-course" :class="'cc' + c.color" :rowspan="c.span">
                    <button class="tt-x" :disabled="busyId === c.course.id" @click="drop(c.course)" title="退课">✕</button>
                    <div class="tt-name">{{ c.course.name }}</div>
                    <div class="tt-sub">{{ c.course.location }}</div>
                    <div class="tt-sub">{{ c.course.teacher }}</div>
                    <div class="tt-tag" v-if="c.course.status !== 'ENROLLED'">处理中</div>
                  </td>
                </template>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="unscheduled.length" class="tt-unsched">
          <div class="tt-unsched-h">未排入课表（时间待定）</div>
          <div class="tt-unsched-list">
            <span v-for="m in unscheduled" :key="m.id" class="badge">{{ m.name }}
              <button class="tt-x-inline" @click="drop(m)" title="退课">✕</button></span>
          </div>
        </div>
      </template>
      <div v-else class="empty"><Icon name="calendarBig" :size="34" :sw="1.4" /><span>还没有选课，去「选课中心」抢一门吧</span></div>
    </main>

    <!-- ============ 管理后台 ============ -->
    <main v-else-if="tab === 'admin' && isAdmin" class="content">
      <div class="page-head"><h2>管理后台</h2></div>
      <div class="admin-grid">
        <div class="card pad">
          <h3 class="card-title">新建抢课批次</h3>
          <div class="field"><label>批次名称</label><input class="input" v-model="batchForm.name" placeholder="如：2026 秋季选课" /></div>
          <div class="field"><label>开放时间</label><input class="input" v-model="batchForm.openAt" type="datetime-local" /></div>
          <div class="field"><label>关闭时间</label><input class="input" v-model="batchForm.closeAt" type="datetime-local" /></div>
          <button class="btn btn-primary btn-block" @click="createBatch">创建批次</button>
          <div class="chips wrap" v-if="batches.length"><span class="badge" v-for="b in batches" :key="b.id">#{{ b.id }} {{ b.name }}</span></div>
        </div>
        <div class="card pad">
          <h3 class="card-title">发布课程</h3>
          <div class="field"><label>课程名（可加【必修】/【公选】前缀）</label><input class="input" v-model="courseForm.name" placeholder="如：【必修】编译原理" /></div>
          <div class="field"><label>教师</label><input class="input" v-model="courseForm.teacher" placeholder="授课教师" /></div>
          <div class="field"><label>上课时间</label><input class="input" v-model="courseForm.timeSlot" placeholder="如：周一 1-2 节" /></div>
          <div class="two">
            <div class="field"><label>容量</label><input class="input" v-model.number="courseForm.capacity" type="number" min="1" /></div>
            <div class="field"><label>批次 ID</label><input class="input" v-model="courseForm.batchId" placeholder="批次编号" /></div>
          </div>
          <button class="btn btn-primary btn-block" @click="createCourse">发布课程</button>
        </div>
        <div class="card pad admin-wide">
          <h3 class="card-title">库存预热 / 选课统计</h3>
          <div class="adm-list">
            <div class="adm-row" v-for="c in courses" :key="c.id">
              <div class="adm-name">{{ cleanName(c.name) }} <span class="badge badge-brand">容量 {{ c.capacity }}</span></div>
              <div class="adm-acts"><button class="btn btn-sm" @click="preheat(c)">预热</button><button class="btn btn-sm" @click="viewStats(c)">统计</button></div>
            </div>
          </div>
          <div v-if="statsResult" class="stats-box"><strong>{{ statsResult.name }}</strong>
            <div class="stats-nums"><div><b>{{ statsResult.enrolled }}</b><span>已选</span></div><div><b>{{ statsResult.capacity }}</b><span>容量</span></div><div><b>{{ statsResult.remaining }}</b><span>剩余</span></div></div></div>
        </div>
      </div>
    </main>
  </div>

  <!-- ===================== 课程详情弹窗 ===================== -->
  <transition name="fade">
    <div v-if="detail" class="modal-mask" @click.self="closeDetail">
      <div class="modal">
        <button class="modal-x" @click="closeDetail">✕</button>
        <div class="modal-head">
          <div class="mh-badges"><span class="badge" :class="catClass(detail.cat)">{{ detail.cat }}</span>
            <span class="tag" :class="statusTag(detail.status)">{{ statusText(detail.status) }}</span></div>
          <h2>{{ detail.name }}</h2>
          <div class="mh-code">{{ detail.meta.code }} · {{ detail.meta.college }}</div>
        </div>
        <div class="modal-grid">
          <div><span>学分</span><b>{{ detail.meta.credit.toFixed(1) }}</b></div>
          <div><span>授课教师</span><b>{{ detail.teacher }}</b></div>
          <div><span>上课时间</span><b>{{ detail.timeSlot }}</b></div>
          <div><span>上课地点</span><b>{{ detail.meta.location }}</b></div>
          <div><span>课程容量</span><b>{{ detail.capacity }}</b></div>
          <div><span>已选人数</span><b>{{ detail.enrolled }}</b></div>
          <div><span>剩余名额</span><b :class="{ 'rem-low': detail.remaining <= 20 }">{{ detail.remaining }}</b></div>
          <div><span>先修课程</span><b>{{ detail.meta.pre }}</b></div>
          <div><span>考核方式</span><b>{{ detail.meta.exam }}</b></div>
        </div>
        <div class="bar lg"><div class="bar-fill" :class="detail.status" :style="{ width: detail.pct + '%' }"></div></div>
        <div class="modal-desc"><div class="md-title">课程简介</div><p>{{ detail.meta.desc }}</p></div>
        <div class="modal-foot">
          <button class="btn" @click="closeDetail">关闭</button>
          <button v-if="detail.picked" class="btn btn-picked" :disabled="busyId === detail.id" @click="drop(detail); closeDetail()">退课</button>
          <button v-else-if="detail.status === 'full'" class="btn" disabled>已满</button>
          <button v-else-if="detail.status === 'conflict'" class="btn btn-warn" disabled>时间冲突</button>
          <button v-else class="btn btn-primary" :disabled="busyId === detail.id" @click="enroll(detail)">
            {{ busyId === detail.id ? '抢课中…' : '抢 课' }}</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/* ===================== 登录页 ===================== */
.auth { display: grid; grid-template-columns: 1.1fr 1fr; min-height: 100vh; background: #f6f4f2; }

/* ---- 左：校园品牌区 ---- */
.auth-brand { position: relative; display: flex; flex-direction: column; gap: 28px; padding: 56px 60px; color: #fff; overflow: hidden;
  background:
    linear-gradient(155deg, rgba(115,22,39,.82) 0%, rgba(140,28,48,.7) 42%, rgba(60,12,22,.86) 100%),
    url('/campus.jpg') center 28% / cover no-repeat; }
/* 柔和光斑 + 几何纹理，弱化压迫感 */
.auth-brand::before { content: ""; position: absolute; inset: 0; z-index: 0; pointer-events: none;
  background: radial-gradient(900px 420px at 78% -8%, rgba(255,255,255,.16), transparent 60%),
              radial-gradient(700px 500px at 12% 110%, rgba(255,255,255,.08), transparent 55%); }
.auth-brand::after { content: ""; position: absolute; right: -140px; bottom: -150px; width: 460px; height: 460px; border-radius: 50%;
  border: 56px solid rgba(255,255,255,.05); z-index: 0; }
.auth-brand > * { position: relative; z-index: 1; }

.brand-top { display: flex; align-items: center; gap: 14px; }
.brand-logo { width: 54px; height: 54px; background: #fff; border-radius: 50%; padding: 5px; box-shadow: 0 6px 18px rgba(0,0,0,.18); }
.brand-name { display: flex; flex-direction: column; line-height: 1.35; }
.brand-name strong { font-size: 17px; letter-spacing: .5px; } .brand-name span { font-size: 11px; letter-spacing: 1.5px; opacity: .82; }

.brand-hero { margin-top: auto; }
.brand-kicker { display: inline-block; font-size: 13px; font-weight: 600; letter-spacing: .5px; padding: 5px 12px; margin-bottom: 16px;
  background: rgba(255,255,255,.14); border: 1px solid rgba(255,255,255,.22); border-radius: 999px; backdrop-filter: blur(6px); }
.brand-hero h1 { font-size: 42px; line-height: 1.12; letter-spacing: 1px; margin-bottom: 16px; }
.brand-hero > p { font-size: 15px; line-height: 1.75; opacity: .92; margin: 0 0 18px; max-width: 440px; }
.brand-status { display: inline-flex; align-items: center; gap: 9px; font-size: 13.5px; opacity: .95; }
.bs-dot { width: 8px; height: 8px; border-radius: 50%; background: #5ee59a; box-shadow: 0 0 0 4px rgba(94,229,154,.25); }

.brand-info { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; margin-top: 4px;
  background: rgba(255,255,255,.14); border: 1px solid rgba(255,255,255,.18); border-radius: 16px; overflow: hidden; backdrop-filter: blur(8px); }
.bi-item { display: flex; flex-direction: column; gap: 5px; padding: 16px 20px; background: rgba(255,255,255,.06); }
.bi-label { font-size: 12px; opacity: .75; } .bi-val { font-size: 14.5px; font-weight: 600; display: flex; align-items: center; gap: 7px; }
.bi-val.ok i { width: 7px; height: 7px; border-radius: 50%; background: #5ee59a; box-shadow: 0 0 0 3px rgba(94,229,154,.28); }

.brand-foot { font-size: 12px; opacity: .65; letter-spacing: .3px; }

/* ---- 右：登录操作区 ---- */
.auth-panel { position: relative; overflow: hidden; display: flex; align-items: center; justify-content: center; padding: 40px 32px;
  background:
    radial-gradient(560px 460px at 20% 14%, rgba(164,35,58,.08), transparent 60%),
    radial-gradient(520px 520px at 92% 92%, rgba(164,35,58,.06), transparent 58%),
    linear-gradient(165deg, #fcfbfa, #f1eeec); }
/* 细微几何纹理 */
.auth-panel::before { content: ""; position: absolute; inset: 0; z-index: 0; pointer-events: none; opacity: .5;
  background-image: linear-gradient(rgba(120,40,55,.035) 1px, transparent 1px), linear-gradient(90deg, rgba(120,40,55,.035) 1px, transparent 1px);
  background-size: 30px 30px; mask-image: radial-gradient(circle at 70% 40%, #000, transparent 75%); }

.auth-card { position: relative; z-index: 1; width: 100%; max-width: 410px; padding: 40px 40px 30px;
  background: #fff; border: 1px solid rgba(20,22,40,.06); border-radius: 22px;
  box-shadow: 0 20px 60px rgba(40,18,28,.12), 0 2px 8px rgba(40,18,28,.05); }

.auth-mobile-logo { display: none; }

.ac-head { margin-bottom: 22px; }
.auth-card h2 { font-size: 26px; letter-spacing: .5px; }
.auth-sub { color: var(--muted); font-size: 13.5px; margin: 9px 0 0; line-height: 1.6; }

/* 身份切换：segmented control */
.portal { position: relative; display: grid; grid-template-columns: 1fr 1fr; padding: 4px; margin-bottom: 22px;
  background: #f1efed; border: 1px solid var(--line); border-radius: 12px; }
.portal-thumb { position: absolute; top: 4px; left: 4px; width: calc(50% - 4px); height: calc(100% - 8px);
  background: #fff; border-radius: 9px; box-shadow: 0 2px 8px rgba(40,18,28,.1); transition: transform .22s cubic-bezier(.4,0,.2,1); }
.portal-thumb.right { transform: translateX(100%); }
.portal button { position: relative; z-index: 1; padding: 10px; border: none; background: transparent; cursor: pointer;
  font-size: 14px; font-weight: 600; color: var(--muted); border-radius: 9px; transition: color .2s; font-family: inherit; }
.portal button.on { color: var(--brand); }

.field-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.field-top label { margin: 0; font-size: 12.5px; font-weight: 600; color: var(--muted); }
.forgot { font-size: 12.5px; font-weight: 500; color: var(--muted); }
.forgot:hover { color: var(--brand); }
.auth-card .field > label { margin-bottom: 0; }
.auth-card .input { padding: 12px 14px; }

/* 登录按钮加载态 */
.spinner { width: 15px; height: 15px; border: 2px solid rgba(255,255,255,.45); border-top-color: #fff; border-radius: 50%; animation: spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.btn-primary:active { transform: translateY(1px); filter: brightness(.98); }

.auth-switch { text-align: center; margin: 16px 0 0; font-size: 13px; color: var(--muted); } .auth-switch a { font-weight: 600; }

/* 辅助入口 */
.ac-links { display: flex; align-items: center; justify-content: center; gap: 14px; margin: 16px 0 4px; font-size: 12.5px; }
.ac-links a { color: var(--muted); font-weight: 500; } .ac-links a:hover { color: var(--brand); }
.ac-links .sep { width: 1px; height: 11px; background: var(--line); }

/* 卡片底部真实系统信息 */
.ac-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 20px; padding-top: 16px;
  border-top: 1px solid var(--line-soft); font-size: 12px; color: var(--muted); }
.ac-foot span { display: flex; align-items: center; gap: 7px; }
.ac-foot .live { width: 7px; height: 7px; border-radius: 50%; background: #16a34a; box-shadow: 0 0 0 3px rgba(22,163,74,.16); }
.ac-notice { margin-top: 10px; font-size: 12px; color: var(--warn); background: var(--warn-bg); border-radius: 8px; padding: 8px 12px; text-align: center; }

/* ===== 图标 ===== */
.ic { flex: none; display: inline-block; vertical-align: -2px; }

/* ===== 应用外壳 ===== */
.app { min-height: 100vh; position: relative;
  background:
    radial-gradient(820px 540px at 86% -6%, rgba(164,35,58,.18), transparent 60%),
    radial-gradient(680px 560px at 6% 6%, rgba(255,141,79,.12), transparent 56%),
    radial-gradient(760px 620px at 52% 110%, rgba(124,72,222,.12), transparent 58%),
    radial-gradient(620px 520px at 102% 62%, rgba(42,158,222,.09), transparent 55%),
    var(--bg);
  background-attachment: fixed; }
.nav { position: sticky; top: 0; z-index: 30; display: flex; align-items: center; gap: 24px; padding: 0 30px; height: 60px;
  background: rgba(245,245,247,.72); backdrop-filter: saturate(180%) blur(20px); -webkit-backdrop-filter: saturate(180%) blur(20px);
  border-bottom: 1px solid rgba(0,0,0,.06); }
.nav-brand { display: flex; align-items: center; gap: 11px; } .nav-brand img { width: 36px; height: 36px; }
.nav-title { display: flex; flex-direction: column; line-height: 1.25; }
.nav-title strong { font-size: 15px; } .nav-title span { font-size: 10.5px; color: var(--muted); letter-spacing: .5px; }
.nav-tabs { display: flex; gap: 4px; margin-left: 8px; }
.nav-tabs button { padding: 8px 16px; border: none; background: transparent; cursor: pointer; font-size: 14px; font-weight: 600; color: var(--muted); border-radius: 8px; transition: all .15s; font-family: inherit; }
.nav-tabs button:hover { background: var(--line-soft); color: var(--ink); }
.nav-tabs button.on { background: var(--brand-50); color: var(--brand); }
.nav-user { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.avatar { width: 36px; height: 36px; border-radius: 50%; flex: none; display: flex; align-items: center; justify-content: center; font-weight: 700; color: #fff; font-size: 15px; background: linear-gradient(135deg, var(--brand), var(--brand-600)); }
.who { display: flex; flex-direction: column; gap: 2px; line-height: 1.2; } .who strong { font-size: 13.5px; }

.content { width: 100%; max-width: min(1440px, 94vw); margin: 0 auto; padding: 26px 32px 60px; }
.content.wide { max-width: min(1860px, 95vw); }
.page-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 20px; } .page-head h2 { font-size: 22px; }

/* ===== 顶部状态区（浅色磨砂卡片） ===== */
.statusbar { display: flex; align-items: center; gap: 4px; flex-wrap: wrap; padding: 18px 14px; margin-bottom: 22px; border-radius: var(--r-lg);
  background: rgba(255,255,255,.72); backdrop-filter: saturate(180%) blur(20px); -webkit-backdrop-filter: saturate(180%) blur(20px);
  border: 1px solid rgba(255,255,255,.6); box-shadow: var(--sh); }
.sb-item { display: flex; flex-direction: column; gap: 4px; padding: 2px 20px; }
.sb-item + .sb-item { border-left: 1px solid var(--line-soft); }
.sb-item.wide-item { min-width: 178px; }
.sb-label { font-size: 11.5px; color: var(--muted); } .sb-val { font-size: 16px; font-weight: 600; color: var(--ink); display: flex; align-items: center; gap: 7px; }
.sb-val.mono { font-variant-numeric: tabular-nums; letter-spacing: .5px; color: var(--brand); }
.sb-sep { flex: 1; border: none !important; }
.sb-item + .sb-sep { border: none; }
.live { width: 8px; height: 8px; border-radius: 50%; background: #34c759; box-shadow: 0 0 0 4px rgba(52,199,89,.22); animation: pulse 1.8s infinite; }
.live.off { background: #c7c7cc; box-shadow: none; animation: none; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: .45; } }

.filter-toggle { display: none; margin-bottom: 14px; }

/* ===== 三栏布局 ===== */
.layout { display: grid; gap: 22px; grid-template-columns: minmax(0, 1fr) 348px; align-items: start; }

/* 左：筛选 */
.filters-panel { position: sticky; top: 76px; background: var(--card); border: 1px solid rgba(0,0,0,.04); border-radius: var(--r-lg); padding: 20px; box-shadow: var(--sh-sm); }
.fp-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; } .fp-head h3 { font-size: 16px; letter-spacing: -.01em; }
.search { position: relative; margin-bottom: 18px; }
.search .search-ic { position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: var(--muted); pointer-events: none; }
.search .input { font-size: 13px; padding-left: 36px; }
.fgroup { margin-bottom: 18px; } .fg-title { font-size: 12px; font-weight: 600; color: var(--muted); margin-bottom: 9px; }
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chips button { padding: 6px 13px; border: 1px solid transparent; background: #f0f0f3; cursor: pointer; font-size: 12.5px; font-weight: 600; color: var(--ink-soft); border-radius: 980px; transition: all .18s cubic-bezier(.4,0,.2,1); font-family: inherit; }
.chips button:hover { background: #e8e8ed; }
.chips button.on { background: var(--brand); color: #fff; box-shadow: 0 3px 10px rgba(164,35,58,.22); }

/* 中：课程列表 */
.course-col { min-width: 0; }
/* 顶部筛选工具条 */
.list-toolbar { background: rgba(255,255,255,.7); backdrop-filter: saturate(180%) blur(20px); -webkit-backdrop-filter: saturate(180%) blur(20px);
  border: 1px solid rgba(255,255,255,.7); border-radius: var(--r-lg); box-shadow: var(--sh-sm); padding: 16px 18px; margin-bottom: 16px; }
.lt-top { display: flex; gap: 10px; margin-bottom: 14px; }
.lt-top .search { flex: 1; margin-bottom: 0; }
.lt-top .select { width: auto; min-width: 132px; flex: none; }
.lt-top .btn { flex: none; }
.lt-filters { display: flex; flex-wrap: wrap; gap: 14px 26px; }
.fchip { display: flex; align-items: center; gap: 10px; }
.fc-label { font-size: 12px; font-weight: 600; color: var(--muted); flex: none; }
.course-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; font-size: 13.5px; color: var(--ink); }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(290px, 1fr)); gap: 18px; }
.course { background: var(--card); border: 1px solid rgba(0,0,0,.04); border-radius: var(--r-lg); padding: 20px; box-shadow: var(--sh-sm); cursor: pointer; transition: transform .22s cubic-bezier(.4,0,.2,1), box-shadow .22s; display: flex; flex-direction: column; gap: 12px; }
.course:hover { transform: translateY(-4px); box-shadow: var(--sh); }
.course.picked { background: linear-gradient(180deg, var(--brand-50), #fff 50%); }
.c-head { display: flex; align-items: center; justify-content: space-between; }
.c-code { font-size: 11.5px; font-weight: 600; color: var(--muted); font-family: "SF Mono", ui-monospace, monospace; letter-spacing: .4px; }
.c-name { font-size: 17px; line-height: 1.3; letter-spacing: -.01em; }
.c-badges { display: flex; gap: 6px; }
.c-meta { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 10px; font-size: 12.5px; color: var(--muted); }
.c-meta span { display: inline-flex; align-items: center; gap: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.c-meta .ic { color: #b0b0b8; }
.c-prog { margin-top: 2px; }
.cp-line { display: flex; justify-content: space-between; font-size: 12px; color: var(--muted); margin-bottom: 5px; }
.cp-line b { color: var(--ink); } .rem-low { color: var(--brand); font-weight: 700; }
.bar { height: 7px; background: var(--line-soft); border-radius: 999px; overflow: hidden; }
.bar.lg { height: 10px; margin: 6px 0 4px; }
.bar-fill { height: 100%; border-radius: 999px; background: var(--brand); transition: width .4s; }
.bar-fill.tense { background: #f59e0b; } .bar-fill.full { background: #ef4444; } .bar-fill.picked { background: #16a34a; }
.c-foot { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: auto; padding-top: 4px; }

/* ===== 课程长条（磨砂玻璃行） ===== */
.course-list { display: flex; flex-direction: column; gap: 12px; }
.course-row { position: relative; display: grid; grid-template-columns: minmax(0,1fr) 92px 190px auto; align-items: center; gap: 22px;
  padding: 18px 22px 18px 26px; cursor: pointer; overflow: hidden;
  background: rgba(255,255,255,.66); backdrop-filter: saturate(180%) blur(20px); -webkit-backdrop-filter: saturate(180%) blur(20px);
  border: 1px solid rgba(255,255,255,.7); border-radius: var(--r-lg); box-shadow: var(--sh-sm);
  transition: transform .2s cubic-bezier(.4,0,.2,1), box-shadow .2s, background .2s; }
.course-row:hover { transform: translateY(-2px); background: rgba(255,255,255,.84); box-shadow: var(--sh); }
.course-row.picked { background: linear-gradient(100deg, rgba(251,238,241,.88), rgba(255,255,255,.72)); }
.cr-accent { position: absolute; left: 0; top: 0; bottom: 0; width: 4px; background: var(--brand); }
.cr-accent.open { background: #34c759; } .cr-accent.tense { background: #ff9f0a; } .cr-accent.full { background: #c7c7cc; }
.cr-accent.picked { background: var(--brand); } .cr-accent.conflict { background: #ff453a; }

.cr-main { min-width: 0; }
.cr-top { display: flex; align-items: center; gap: 8px; margin-bottom: 7px; }
.cr-top .c-code { font-size: 11.5px; font-weight: 600; color: var(--muted); font-family: "SF Mono", ui-monospace, monospace; letter-spacing: .4px; }
.cr-name { font-size: 17px; line-height: 1.3; letter-spacing: -.01em; margin-bottom: 9px; }
.cr-meta { display: flex; flex-wrap: wrap; gap: 6px 18px; font-size: 12.5px; color: var(--muted); }
.cr-meta span { display: inline-flex; align-items: center; gap: 6px; }
.cr-meta .ic { color: #b0b0b8; }
.cr-status { display: flex; justify-content: center; }
.cr-prog { min-width: 0; }
.cr-actions { display: flex; align-items: center; gap: 8px; justify-content: flex-end; }
.mobile-tag { display: none; }

/* 状态标签 */
.tag { font-size: 11.5px; font-weight: 700; padding: 3px 10px; border-radius: 999px; }
.tag-open { background: #e7f4ec; color: #157347; } .tag-tense { background: #fff3e0; color: #b25e00; }
.tag-full { background: #f1f3f5; color: #8a93a0; } .tag-picked { background: var(--brand-50); color: var(--brand); }
.tag-conflict { background: #fdecea; color: #c5221f; }

/* 按钮补充 */
.btn-sm { padding: 6px 11px; font-size: 12.5px; }
.btn-link { background: transparent; border-color: transparent; color: var(--muted); padding: 6px 8px; }
.btn-link:hover { background: var(--line-soft); border-color: transparent; color: var(--ink); }
.btn-picked { color: #157347; border-color: #b7e0c4; background: #f3faf5; }
.btn-picked:hover { background: #e7f4ec; border-color: #93d3a8; }
.btn-warn { color: #b25e00; border-color: #f0d9b0; background: #fff8ee; }

/* 右：我的选课 */
.my-panel { position: sticky; top: 78px; display: flex; flex-direction: column; gap: 16px; }
.mp-card { background: var(--card); border: 1px solid rgba(0,0,0,.04); border-radius: var(--r-lg); padding: 18px; box-shadow: var(--sh-sm); }
.mp-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; } .mp-head h3 { font-size: 15px; }
.conflict-warn { background: var(--bad-bg); color: var(--bad); font-size: 12.5px; padding: 10px 12px; border-radius: 9px; margin-bottom: 12px; line-height: 1.6; }
.mp-list { display: flex; flex-direction: column; gap: 8px; }
.mp-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 9px 11px; background: var(--line-soft); border-radius: 9px; }
.mpr-name { font-size: 13.5px; font-weight: 600; } .mpr-meta { font-size: 11.5px; color: var(--muted); margin-top: 2px; }
.mp-empty { color: var(--muted); font-size: 13px; text-align: center; padding: 18px 0; }
.rec-list { display: flex; flex-direction: column; gap: 9px; }
.rec-row { display: flex; align-items: center; gap: 8px; font-size: 12.5px; }
.rec-dot { width: 7px; height: 7px; border-radius: 50%; flex: none; } .rec-dot.ok { background: #16a34a; } .rec-dot.pending { background: #f59e0b; }
.rec-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } .rec-time { color: var(--muted); font-variant-numeric: tabular-nums; }

.empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; text-align: center; color: var(--muted); font-size: 14.5px; padding: 72px 20px; background: var(--card); border: 1px solid rgba(0,0,0,.04); border-radius: var(--r-lg); }
.empty .ic { color: #c7c7cc; }

/* ===== 周课表网格 ===== */
.schedule-warn { margin-bottom: 16px; }
.tt-wrap { overflow-x: auto; background: var(--card); border: 1px solid rgba(0,0,0,.04); border-radius: var(--r-lg); box-shadow: var(--sh-sm); padding: 8px; }
.timetable { width: 100%; border-collapse: separate; border-spacing: 5px; table-layout: fixed; min-width: 820px; }
.timetable th { font-size: 13px; font-weight: 600; color: var(--brand); padding: 11px 6px; background: var(--brand-50); border-radius: 9px; }
.timetable th.tt-time-h { width: 96px; color: var(--muted); background: var(--line-soft); }
.tt-time { width: 96px; text-align: center; background: var(--line-soft); border-radius: 9px; padding: 8px 4px; }
.tt-time b { display: block; font-size: 12.5px; color: var(--ink); }
.tt-time span { font-size: 11px; color: var(--muted); font-variant-numeric: tabular-nums; }
.tt-cell { height: 58px; border-radius: 9px; background: #fafafa; vertical-align: top; }
.tt-course { position: relative; padding: 9px 10px; color: #fff; vertical-align: middle; box-shadow: var(--sh-sm); }
.tt-name { font-size: 13px; font-weight: 700; line-height: 1.25; }
.tt-sub { font-size: 11px; opacity: .92; margin-top: 2px; }
.tt-tag { display: inline-block; margin-top: 5px; font-size: 10px; padding: 1px 7px; background: rgba(255,255,255,.28); border-radius: 999px; }
.tt-x { position: absolute; top: 5px; right: 5px; width: 18px; height: 18px; border: none; border-radius: 50%; background: rgba(255,255,255,.25); color: #fff; cursor: pointer; font-size: 10px; line-height: 1; opacity: 0; transition: opacity .15s; }
.tt-course:hover .tt-x { opacity: 1; } .tt-x:hover { background: rgba(255,255,255,.5); }
.cc0 { background: linear-gradient(135deg, #a4233a, #8c1c30); } .cc1 { background: linear-gradient(135deg, #2e8b6b, #246b54); }
.cc2 { background: linear-gradient(135deg, #3a6ea5, #2f5a87); } .cc3 { background: linear-gradient(135deg, #c2772a, #9a5816); }
.cc4 { background: linear-gradient(135deg, #6a4ca8, #56398c); } .cc5 { background: linear-gradient(135deg, #b03a6e, #8f2a57); }
.tt-unsched { margin-top: 18px; } .tt-unsched-h { font-size: 13px; font-weight: 600; color: var(--muted); margin-bottom: 9px; }
.tt-unsched-list { display: flex; flex-wrap: wrap; gap: 8px; }
.tt-x-inline { border: none; background: transparent; color: var(--muted); cursor: pointer; margin-left: 4px; font-size: 11px; padding: 0; }
.tt-x-inline:hover { color: var(--bad); }

/* 管理后台 */
.admin-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; }
.admin-wide { grid-column: 1 / -1; } .pad { padding: 20px; }
.card-title { font-size: 15.5px; margin-bottom: 16px; padding-bottom: 12px; border-bottom: 1px solid var(--line-soft); }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.chips.wrap { margin-top: 14px; }
.adm-list { display: flex; flex-direction: column; } .adm-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 1px solid var(--line-soft); }
.adm-row:last-child { border-bottom: none; } .adm-name { font-size: 14px; font-weight: 500; display: flex; align-items: center; gap: 8px; } .adm-acts { display: flex; gap: 8px; }
.stats-box { margin-top: 16px; padding: 16px; background: var(--brand-50); border-radius: var(--r); }
.stats-nums { display: flex; gap: 28px; margin-top: 10px; } .stats-nums div { display: flex; flex-direction: column; } .stats-nums b { font-size: 24px; color: var(--brand); } .stats-nums span { font-size: 12px; color: var(--muted); }

/* ===== 弹窗 ===== */
.modal-mask { position: fixed; inset: 0; z-index: 200; background: rgba(20,18,30,.5); backdrop-filter: blur(3px); display: flex; align-items: center; justify-content: center; padding: 20px; }
.modal { position: relative; width: 100%; max-width: 560px; max-height: 90vh; overflow-y: auto; background: #fff; border-radius: 20px; padding: 28px; box-shadow: var(--sh-lg); }
.modal-x { position: absolute; top: 18px; right: 18px; width: 32px; height: 32px; border: none; background: var(--line-soft); border-radius: 50%; cursor: pointer; color: var(--muted); font-size: 14px; }
.modal-x:hover { background: var(--line); }
.mh-badges { display: flex; gap: 8px; margin-bottom: 12px; }
.modal-head h2 { font-size: 24px; } .mh-code { font-size: 13px; color: var(--muted); margin-top: 6px; }
.modal-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px 12px; margin: 22px 0 6px; }
.modal-grid div { display: flex; flex-direction: column; gap: 3px; } .modal-grid span { font-size: 11.5px; color: var(--muted); } .modal-grid b { font-size: 15px; }
.modal-desc { margin-top: 18px; } .md-title { font-size: 13px; font-weight: 700; margin-bottom: 8px; } .modal-desc p { margin: 0; font-size: 13.5px; color: var(--ink); line-height: 1.7; }
.modal-foot { display: flex; justify-content: flex-end; gap: 10px; margin-top: 24px; }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; } .fade-enter-from, .fade-leave-to { opacity: 0; }

/* ===== 响应式 ===== */
@media (max-width: 1240px) {
  .layout { grid-template-columns: minmax(0, 1fr); }
  .my-panel { position: static; flex-direction: row; }
  .my-panel .mp-card { flex: 1; }
}
/* ---- 登录页：中屏改为上下布局 ---- */
@media (max-width: 980px) {
  .auth { grid-template-columns: 1fr; grid-template-rows: auto 1fr; }
  .auth-brand { padding: 32px 36px; gap: 18px; }
  .auth-brand::after { display: none; }
  .brand-hero { margin-top: 8px; } .brand-hero h1 { font-size: 30px; margin-bottom: 10px; }
  .brand-hero > p { font-size: 13.5px; margin-bottom: 12px; max-width: none; }
  .brand-info { grid-template-columns: repeat(4, 1fr); } .bi-item { padding: 12px 14px; }
  .brand-foot { display: none; }
  .auth-panel { padding: 28px 20px 40px; }
}
@media (max-width: 860px) {
  .admin-grid { grid-template-columns: 1fr; }
  .layout { grid-template-columns: 1fr; }
  .my-panel { flex-direction: column; }
  .filter-toggle { display: inline-flex; }
  .filters-panel { display: none; position: static; }
  .filters-panel.open { display: block; }
  .nav-title span { display: none; }
  .statusbar { gap: 4px; } .sb-item { padding: 6px 12px; } .sb-sep { display: none; }
}
/* 课程长条：窄屏堆叠 */
@media (max-width: 760px) {
  .course-row { grid-template-columns: 1fr; gap: 12px; padding: 16px 18px 16px 20px; }
  .cr-status { display: none; } .mobile-tag { display: inline-flex; }
  .cr-prog { width: 100%; } .cr-actions { justify-content: flex-end; }
  .lt-top { flex-wrap: wrap; } .lt-top .search { flex: 1 1 100%; } .lt-top .select { flex: 1 1 auto; }
  .lt-filters { gap: 10px 18px; }
}
@media (max-width: 560px) {
  .nav { gap: 12px; padding: 0 14px; } .nav-title { display: none; } .who strong { display: none; }
  .content { padding: 18px 14px 40px; } .grid { grid-template-columns: 1fr; }
  .modal-grid { grid-template-columns: repeat(2, 1fr); }
  /* 登录页移动端 */
  .auth-brand { padding: 26px 22px; } .brand-hero h1 { font-size: 26px; }
  .brand-info { grid-template-columns: 1fr 1fr; }
  .auth-card { padding: 30px 24px 24px; border-radius: 18px; }
  .ac-links { gap: 10px; flex-wrap: wrap; }
}
</style>

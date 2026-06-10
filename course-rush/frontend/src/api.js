// 极简 API 封装：自动带上 JWT，统一解析错误信息。
let token = localStorage.getItem('token') || ''

export function setToken(t) {
  token = t || ''
  if (t) localStorage.setItem('token', t)
  else localStorage.removeItem('token')
}

export function getToken() {
  return token
}

async function request(method, url, body) {
  const headers = { 'Content-Type': 'application/json' }
  // 认证端点（登录/注册）不携带令牌，避免旧令牌把自己锁在门外
  if (token && !url.includes('/api/auth/')) headers['Authorization'] = 'Bearer ' + token
  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    throw new Error((data && data.message) || ('请求失败: ' + res.status))
  }
  return data
}

export const api = {
  register: (payload) => request('POST', '/api/auth/register', payload),
  login: (payload) => request('POST', '/api/auth/login', payload),
  listCourses: () => request('GET', '/api/courses'),
  availability: () => request('GET', '/api/courses/availability'),
  listBatches: () => request('GET', '/api/batches'),
  createBatch: (payload) => request('POST', '/api/batches', payload),
  createCourse: (payload) => request('POST', '/api/courses', payload),
  preheat: (courseId) => request('POST', `/api/admin/courses/${courseId}/preheat`),
  stats: (courseId) => request('GET', `/api/admin/courses/${courseId}/stats`),
  enroll: (courseId) => request('POST', '/api/enroll', { courseId }),
  drop: (courseId) => request('DELETE', `/api/enroll/${courseId}`),
  myEnrollments: () => request('GET', '/api/my/enrollments'),
}

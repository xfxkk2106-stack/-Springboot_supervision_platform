import request from './request'

// 验证用户身份（通过 Cookie）
export function verifyToken() {
  return request.get('/auth/verify')
}

// 退出登录
export function logout() {
  return request.post('/auth/logout')
}

// 生成授权码
export function generateAuthCode() {
  return request.post('/auth/code/generate')
}

// 使用授权码加入房间
export function useAuthCode(code) {
  return request.post('/auth/code/use', { code })
}

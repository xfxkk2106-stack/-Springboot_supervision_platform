import request from './request'

// 验证 token 是否有效
export function verifyToken() {
  return request.get('/auth/verify')
}

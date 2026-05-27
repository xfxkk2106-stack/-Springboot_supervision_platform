// 生成7位房间号（数字+字母，不区分大小写）
export function generateRoomCode() {
  const chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
  let code = ''
  for (let i = 0; i < 7; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return code
}

// 验证房间号格式
export function validateRoomCode(code) {
  if (!code || code.length !== 7) return false
  return /^[0-9A-Za-z]{7}$/.test(code)
}

// 房间号转大写（统一存储格式）
export function normalizeRoomCode(code) {
  return code.toUpperCase()
}

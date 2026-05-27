import request from './request'

// 创建房间
export function createRoom(data) {
  return request.post('/room/create', data)
}

// 通过邀请码加入房间
export function joinRoom(data) {
  return request.post('/room/join', data)
}

// 获取房间信息
export function getRoomInfo(roomCode) {
  return request.get(`/room/${roomCode}/info`)
}

// 获取房间成员列表
export function getRoomMembers(roomCode) {
  return request.get(`/room/${roomCode}/members`)
}

// 退出房间
export function leaveRoom() {
  return request.post('/room/leave')
}

// 注销房间（管理员）
export function dissolveRoom() {
  return request.post('/room/dissolve')
}

// 检查是否为管理员
export function checkAdmin() {
  return request.get('/room/check-admin')
}

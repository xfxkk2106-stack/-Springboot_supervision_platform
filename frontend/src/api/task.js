import request from './request'

// 创建当日小计划
export function createTask(data) {
  return request.post('/task/create', data)
}

// 获取今日任务列表（房间内所有成员）
export function getTodayTasks() {
  return request.get('/task/today')
}

// 获取我的今日任务
export function getMyTodayTasks() {
  return request.get('/task/my-today')
}

// 获取指定成员今日任务
export function getMemberTodayTasks(memberId) {
  return request.get(`/task/member/${memberId}`)
}

// 获取指定成员历史任务
export function getMemberHistoryTasks(memberId, params) {
  return request.get(`/task/member/${memberId}/history`, { params })
}

// 标记任务完成
export function completeTask(id) {
  return request.put(`/task/${id}/complete`)
}

// 删除任务
export function deleteTask(id) {
  return request.delete(`/task/${id}`)
}

// 请假
export function requestLeave() {
  return request.post('/task/leave')
}

// 取消请假
export function cancelLeave() {
  return request.delete('/task/leave')
}

// 获取房间完成状态
export function getRoomStatus() {
  return request.get('/task/room-status')
}

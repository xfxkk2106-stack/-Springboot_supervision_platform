import request from './request'

// 创建/更新每日复盘
export function createReview(data) {
  return request.post('/review/create', data)
}

// 获取今日复盘
export function getTodayReview() {
  return request.get('/review/today')
}

// 创建明日计划
export function createTomorrowPlan(data) {
  return request.post('/tomorrow/create', data)
}

// 获取明日计划
export function getTomorrowPlan() {
  return request.get('/tomorrow/today')
}

import request from './request'

// 创建学习计划
export function createPlan(data) {
  return request.post('/plan/create', data)
}

// 获取计划列表
export function getPlanList(params) {
  return request.get('/plan/list', { params })
}

// 获取指定成员的计划列表
export function getMemberPlanList(memberId, params) {
  return request.get(`/plan/member/${memberId}`, { params })
}

// 更新计划
export function updatePlan(id, data) {
  return request.put(`/plan/${id}/update`, data)
}

// 删除计划
export function deletePlan(id) {
  return request.delete(`/plan/${id}`)
}

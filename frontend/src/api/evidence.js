import request from './request'

// 上传学习证据图片
export function uploadEvidence(data) {
  return request.post('/evidence/upload', data, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

// 获取任务的证据列表
export function getTaskEvidence(taskId) {
  return request.get(`/evidence/task/${taskId}`)
}

// 审核证据
export function reviewEvidence(id, data) {
  return request.post(`/evidence/${id}/review`, data)
}

// 删除证据
export function deleteEvidence(id) {
  return request.delete(`/evidence/${id}`)
}

import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  withCredentials: true, // 自动携带 Cookie
})

// 不需要提示 401 的路径（首页验证、创建/加入房间等）
const silentAuthPaths = ['/auth/verify', '/room/create', '/room/join']

// 判断是否静默处理
function isSilentRequest(config) {
  const url = config?.url || ''
  return silentAuthPaths.some(path => url.includes(path))
}

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      // 静默路径不提示不跳转
      if (!isSilentRequest(response.config)) {
        ElMessage.error(res.message || '请求失败')
        if (res.code === 401 || res.code === 403) {
          localStorage.clear()
          setTimeout(() => {
            window.location.href = '/'
          }, 1500)
        }
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    if (error.response) {
      const silent = isSilentRequest(error.config)

      switch (error.response.status) {
        case 401:
        case 403:
          if (!silent) {
            ElMessage.error('身份已失效，请重新加入房间')
            localStorage.clear()
            setTimeout(() => {
              window.location.href = '/'
            }, 1500)
          }
          break
        case 404:
          if (!silent) ElMessage.error('请求的资源不存在')
          break
        case 500:
          if (!silent) ElMessage.error('服务器内部错误')
          break
        default:
          if (!silent) ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络连接失败，请检查网络')
    }
    return Promise.reject(error)
  }
)

export default request

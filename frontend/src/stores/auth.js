import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  // authToken 存储在 HttpOnly Cookie 中，JS 不可读取
  // 但 WebSocket 连接需要作为 query param 传递，所以内存中保留一份
  const authToken = ref('')
  const uid = ref(localStorage.getItem('uid') || '')
  const memberId = ref(localStorage.getItem('memberId') || '')
  const roomId = ref(localStorage.getItem('roomId') || '')
  const roomCode = ref(localStorage.getItem('roomCode') || '')
  const displayName = ref(localStorage.getItem('displayName') || '')
  const isAdmin = ref(localStorage.getItem('isAdmin') === 'true')

  const isLoggedIn = computed(() => !!uid.value)

  function setAuth(data) {
    authToken.value = data.authToken || ''
    uid.value = data.uid
    memberId.value = data.memberId
    roomId.value = data.roomId
    roomCode.value = data.roomCode
    displayName.value = data.displayName
    isAdmin.value = data.isAdmin || false

    localStorage.setItem('uid', data.uid)
    localStorage.setItem('memberId', data.memberId)
    localStorage.setItem('roomId', data.roomId)
    localStorage.setItem('roomCode', data.roomCode)
    localStorage.setItem('displayName', data.displayName)
    localStorage.setItem('isAdmin', data.isAdmin ? 'true' : 'false')
  }

  function clearAuth() {
    authToken.value = ''
    uid.value = ''
    memberId.value = ''
    roomId.value = ''
    roomCode.value = ''
    displayName.value = ''
    isAdmin.value = false

    localStorage.removeItem('uid')
    localStorage.removeItem('memberId')
    localStorage.removeItem('roomId')
    localStorage.removeItem('roomCode')
    localStorage.removeItem('displayName')
    localStorage.removeItem('isAdmin')
  }

  return {
    authToken,
    uid,
    memberId,
    roomId,
    roomCode,
    displayName,
    isAdmin,
    isLoggedIn,
    setAuth,
    clearAuth,
  }
})

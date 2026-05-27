import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const memberId = ref(localStorage.getItem('memberId') || '')
  const roomId = ref(localStorage.getItem('roomId') || '')
  const roomCode = ref(localStorage.getItem('roomCode') || '')
  const displayName = ref(localStorage.getItem('displayName') || '')

  const isLoggedIn = computed(() => !!token.value)

  function setAuth(data) {
    token.value = data.token
    memberId.value = data.memberId
    roomId.value = data.roomId
    roomCode.value = data.roomCode
    displayName.value = data.displayName

    localStorage.setItem('token', data.token)
    localStorage.setItem('memberId', data.memberId)
    localStorage.setItem('roomId', data.roomId)
    localStorage.setItem('roomCode', data.roomCode)
    localStorage.setItem('displayName', data.displayName)
  }

  function clearAuth() {
    token.value = ''
    memberId.value = ''
    roomId.value = ''
    roomCode.value = ''
    displayName.value = ''

    localStorage.removeItem('token')
    localStorage.removeItem('memberId')
    localStorage.removeItem('roomId')
    localStorage.removeItem('roomCode')
    localStorage.removeItem('displayName')
  }

  return {
    token,
    memberId,
    roomId,
    roomCode,
    displayName,
    isLoggedIn,
    setAuth,
    clearAuth,
  }
})

import { ref, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'

export function useWebSocket() {
  const authStore = useAuthStore()
  const socket = ref(null)
  const isConnected = ref(false)
  const messageHandlers = new Map()
  let intentionalClose = false

  function connect() {
    if (!authStore.isLoggedIn || !authStore.authToken) {
      console.warn('WebSocket 连接失败: 缺少 authToken')
      return
    }

    // 关闭已有连接
    if (socket.value) {
      socket.value.close()
    }

    const wsHost = window.location.hostname
    const wsUrl = `ws://${wsHost}:8081/ws/room/${authStore.roomCode}?token=${authStore.authToken}`
    console.log('WebSocket 正在连接:', wsUrl)
    socket.value = new WebSocket(wsUrl)
    intentionalClose = false

    socket.value.onopen = () => {
      isConnected.value = true
      console.log('WebSocket 连接成功')
    }

    socket.value.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)
        const handler = messageHandlers.get(message.type)
        if (handler) {
          handler(message.data)
        }
      } catch (error) {
        console.error('解析 WebSocket 消息失败:', error)
      }
    }

    socket.value.onclose = () => {
      isConnected.value = false
      console.log('WebSocket 连接关闭')
      // 非主动关闭时自动重连
      if (!intentionalClose) {
        setTimeout(connect, 3000)
      }
    }

    socket.value.onerror = (error) => {
      console.error('WebSocket 错误:', error)
    }
  }

  function send(type, data) {
    if (socket.value && socket.value.readyState === WebSocket.OPEN) {
      socket.value.send(JSON.stringify({ type, data }))
    }
  }

  function on(type, handler) {
    messageHandlers.set(type, handler)
  }

  function off(type) {
    messageHandlers.delete(type)
  }

  function close() {
    intentionalClose = true
    if (socket.value) {
      socket.value.close()
      socket.value = null
    }
  }

  onUnmounted(() => {
    close()
  })

  return {
    isConnected,
    send,
    on,
    off,
    connect,
    close,
  }
}

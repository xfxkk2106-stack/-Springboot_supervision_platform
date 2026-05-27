import { ref, computed, onMounted, onUnmounted } from 'vue'

export function useCountdown(deadline) {
  const now = ref(new Date())
  let timer = null

  const deadlineTime = computed(() => {
    if (!deadline.value) return null
    const today = new Date()
    const [hours, minutes] = deadline.value.split(':')
    return new Date(today.getFullYear(), today.getMonth(), today.getDate(), hours, minutes)
  })

  const isExpired = computed(() => {
    if (!deadlineTime.value) return false
    return now.value >= deadlineTime.value
  })

  const remaining = computed(() => {
    if (!deadlineTime.value || isExpired.value) {
      return { hours: 0, minutes: 0, seconds: 0 }
    }
    const diff = deadlineTime.value - now.value
    const hours = Math.floor(diff / (1000 * 60 * 60))
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
    const seconds = Math.floor((diff % (1000 * 60)) / 1000)
    return { hours, minutes, seconds }
  })

  const formatted = computed(() => {
    const { hours, minutes, seconds } = remaining.value
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  })

  onMounted(() => {
    timer = setInterval(() => {
      now.value = new Date()
    }, 1000)
  })

  onUnmounted(() => {
    if (timer) {
      clearInterval(timer)
    }
  })

  return {
    isExpired,
    remaining,
    formatted,
  }
}

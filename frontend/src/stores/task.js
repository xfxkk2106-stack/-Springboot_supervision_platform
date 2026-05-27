import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getTodayTasks } from '@/api/task'

export const useTaskStore = defineStore('task', () => {
  const tasks = ref([])
  const loading = ref(false)

  const completedTasks = computed(() => tasks.value.filter(t => t.isCompleted))
  const pendingTasks = computed(() => tasks.value.filter(t => !t.isCompleted))
  const progress = computed(() => {
    if (tasks.value.length === 0) return 0
    return Math.round((completedTasks.value.length / tasks.value.length) * 100)
  })

  // 按科目分组
  const tasksBySubject = computed(() => {
    const groups = {}
    tasks.value.forEach(task => {
      if (!groups[task.subject]) {
        groups[task.subject] = []
      }
      groups[task.subject].push(task)
    })
    return groups
  })

  async function fetchTodayTasks() {
    loading.value = true
    try {
      const res = await getTodayTasks()
      tasks.value = res.data
    } catch (error) {
      console.error('获取今日任务失败:', error)
    } finally {
      loading.value = false
    }
  }

  function updateTask(taskId, updates) {
    const task = tasks.value.find(t => t.id === taskId)
    if (task) {
      Object.assign(task, updates)
    }
  }

  function addTask(task) {
    tasks.value.push(task)
  }

  function removeTask(taskId) {
    tasks.value = tasks.value.filter(t => t.id !== taskId)
  }

  return {
    tasks,
    loading,
    completedTasks,
    pendingTasks,
    progress,
    tasksBySubject,
    fetchTodayTasks,
    updateTask,
    addTask,
    removeTask,
  }
})

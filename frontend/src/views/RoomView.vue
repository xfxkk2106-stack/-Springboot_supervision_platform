<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import { useWebSocket } from '@/composables/useWebSocket'
import { getMyTodayTasks, getMemberTodayTasks, getMemberHistoryTasks, createTask, completeTask, deleteTask, requestLeave, cancelLeave, getRoomStatus, getPlanStatus } from '@/api/task'
import { uploadEvidence, getTaskEvidence, deleteEvidence } from '@/api/evidence'
import { getTomorrowPlan, createTomorrowPlan, getMemberTomorrowPlan } from '@/api/review'
import { dissolveRoom, checkAdmin, leaveRoom } from '@/api/room'
import { verifyToken, logout, generateAuthCode } from '@/api/auth'
import { copyToClipboard } from '@/utils/clipboard'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const roomStore = useRoomStore()
const { on, send, connect, close } = useWebSocket()

const roomCode = route.params.roomCode

// 管理员相关
const isAdmin = ref(false)

// 授权码相关
const showAuthCodeDialog = ref(false)
const authCode = ref('')
const authCodeLoading = ref(false)

// 任务相关
const showTaskDialog = ref(false)
const taskContentInputs = ref(['']) // 学习内容输入框列表
const selectedSubject = ref('')

// 科目管理
const defaultSubjects = ['数学', '英语', '语文', '物理', '化学', '编程', '其他']
const subjects = ref(JSON.parse(localStorage.getItem('subjects') || 'null') || defaultSubjects)
const showSubjectDialog = ref(false)
const newSubjectInput = ref('')

function saveSubjects() {
  localStorage.setItem('subjects', JSON.stringify(subjects.value))
}
function addSubject() {
  const name = newSubjectInput.value.trim()
  if (!name) return
  if (subjects.value.includes(name)) {
    ElMessage.warning('科目已存在')
    return
  }
  subjects.value.push(name)
  newSubjectInput.value = ''
  saveSubjects()
}
function removeSubject(index) {
  subjects.value.splice(index, 1)
  saveSubjects()
}

// 证据上传
const showEvidenceDialog = ref(false)
const currentTaskId = ref(null)
const evidenceFiles = ref([])
const evidenceList = ref([])

// 明日计划
const showTomorrowDialog = ref(false)
const tomorrowPlans = ref([])
// 多科目组：[{id, subject, contentInputs: ['']}]
const tomorrowGroups = ref([{ id: 1, subject: '', contentInputs: [''] }])

// 我的今日任务
const myTasks = ref([])

// 查看成员任务
const showMemberTasksDialog = ref(false)
const selectedMember = ref(null)
const memberTasks = ref([])
const memberTomorrowPlans = ref([])

// 历史记录
const showHistoryDialog = ref(false)
const historyMember = ref(null)
const historyTasks = ref([])

// 加载状态
const loading = ref(false)

// 请假相关
const isOnLeave = ref(false)
const roomStatus = ref(null)
const planStatus = ref(null)

// 我的任务按科目分组
const myTasksBySubject = computed(() => {
  const groups = {}
  myTasks.value.forEach(task => {
    if (!groups[task.subject]) {
      groups[task.subject] = []
    }
    groups[task.subject].push(task)
  })
  return groups
})

// 明日计划按科目分组
const tomorrowBySubject = computed(() => {
  const groups = {}
  tomorrowPlans.value.forEach(plan => {
    if (!groups[plan.subject]) {
      groups[plan.subject] = []
    }
    groups[plan.subject].push(plan)
  })
  return groups
})

// 成员任务按科目分组
const memberTasksBySubject = computed(() => {
  const groups = {}
  memberTasks.value.forEach(task => {
    if (!groups[task.subject]) {
      groups[task.subject] = []
    }
    groups[task.subject].push(task)
  })
  return groups
})

// 成员明日计划按科目分组
const memberTomorrowBySubject = computed(() => {
  const groups = {}
  memberTomorrowPlans.value.forEach(plan => {
    if (!groups[plan.subject]) {
      groups[plan.subject] = []
    }
    groups[plan.subject].push(plan)
  })
  return groups
})

// 我的任务进度
const myProgress = computed(() => {
  if (myTasks.value.length === 0) return 0
  const completed = myTasks.value.filter(t => t.isCompleted).length
  return Math.round((completed / myTasks.value.length) * 100)
})

// 成员任务进度
const memberProgress = computed(() => {
  if (memberTasks.value.length === 0) return 0
  const completed = memberTasks.value.filter(t => t.isCompleted).length
  return Math.round((completed / memberTasks.value.length) * 100)
})

// 是否可以制定明日计划（请假用户也需要其他人完成才能制定）
const canCreateTomorrow = computed(() => {
  if (!planStatus.value) return false
  return planStatus.value.canCreatePlan
})

// 是否被锁定（有成员未完成，包括自己）
const isLocked = computed(() => {
  if (!planStatus.value) return true  // 未获取到状态时默认锁定
  // 自己未完成且未请假，或者其他成员未完成
  const myselfDone = planStatus.value.myTasksAllDone || isOnLeave.value
  return !myselfDone || !planStatus.value.allOthersCompleted
})

// 是否存在计划任务（fromPlan=1），锁定后不可添加/删除
const hasPlanTasks = computed(() => {
  return myTasks.value.some(t => t.fromPlan === 1)
})

// 是否为首日（joinedAt 的日期等于今天）
const isFirstDay = computed(() => {
  if (!planStatus.value) return false
  return planStatus.value.isFirstDay === true
})

// 是否可以添加今日任务（首日且无计划任务时才可添加）
const canAddTask = computed(() => {
  if (isOnLeave.value) return false
  if (hasPlanTasks.value) return false
  if (!isFirstDay.value) return false
  return true
})

// 制定计划按钮文案
const planButtonLabel = computed(() => {
  return '制定明日计划'
})


onMounted(async () => {
  // 先验证身份，获取 authToken（刷新页面后内存中的 authToken 会丢失）
  try {
    const verifyRes = await verifyToken()
    if (verifyRes.data) {
      authStore.setAuth(verifyRes.data)
    }
  } catch (error) {
    // 验证失败，跳转首页
    authStore.clearAuth()
    router.push('/')
    return
  }

  // 并行初始化，互不阻塞
  await Promise.allSettled([
    roomStore.fetchRoomInfo(roomCode),
    roomStore.fetchMembers(roomCode),
    fetchMyTasks(),
    fetchRoomStatus(),
    fetchPlanStatus(),
    fetchTomorrowPlans(),
  ])

  // 连接 WebSocket（authToken 已就绪）
  connect()

  // 检查房间是否已注销
  if (roomStore.roomInfo && roomStore.roomInfo.status !== 1) {
    ElMessageBox.alert(
      '房间已被管理员注销，您将被退出房间。',
      '房间已注销',
      {
        confirmButtonText: '确定',
        type: 'warning',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
      }
    ).then(() => {
      authStore.clearAuth()
      router.push('/')
    })
    return
  }

  // 检查是否为管理员
  isAdmin.value = authStore.isAdmin

  // WebSocket 事件监听
  on('member_online', (data) => {
    const exists = roomStore.members.find(m => m.id === data.memberId)
    if (exists) {
      roomStore.updateMemberStatus(data.memberId, true)
    } else {
      // 新成员加入，先直接添加到列表（立即可见），再刷新获取完整数据
      roomStore.addMember({
        id: data.memberId,
        displayName: data.displayName,
        roomId: data.roomId,
        isAdmin: data.isAdmin || 0,
        isOnline: 1,
      })
      roomStore.fetchMembers(roomCode)
      // 刷新房间完成状态，更新"有成员未完成今日任务"列表
      fetchRoomStatus()
    }
    ElMessage.info(`${data.displayName} 上线了`)
  })

  on('member_offline', (data) => {
    roomStore.updateMemberStatus(data.memberId, false)
  })

  on('member_left', (data) => {
    roomStore.removeMember(data.memberId)
    ElMessage.info(`${data.displayName} 已退出房间`)
    // 使用广播中的 roomStatus 即时更新房间完成状态
    if (data?.roomStatus) {
      roomStatus.value = data.roomStatus
      const myStatus = data.roomStatus.members?.find(m => m.memberId === authStore.memberId)
      if (myStatus) {
        isOnLeave.value = myStatus.isOnLeave
      }
    } else {
      fetchRoomStatus()
    }
  })

  on('room_online_members', (data) => {
    // 收到房间内已在线成员列表，更新状态
    if (Array.isArray(data)) {
      data.forEach(m => {
        roomStore.updateMemberStatus(m.memberId, true)
      })
    }
  })

  on('task_completed', (data) => {
    fetchMyTasks()
    // 优先使用 WebSocket 广播中携带的房间状态（即时更新，无需额外 REST 请求）
    if (data?.roomStatus) {
      roomStatus.value = data.roomStatus
      const myStatus = data.roomStatus.members?.find(m => m.memberId === authStore.memberId)
      if (myStatus) {
        isOnLeave.value = myStatus.isOnLeave
      }
    } else {
      fetchRoomStatus()
    }
    fetchPlanStatus()
    fetchTomorrowPlans()
  })

  on('member_leave_changed', (data) => {
    fetchMyTasks()
    // 优先使用 WebSocket 广播中携带的房间状态（即时更新，无需额外 REST 请求）
    if (data?.roomStatus) {
      roomStatus.value = data.roomStatus
      const myStatus = data.roomStatus.members?.find(m => m.memberId === authStore.memberId)
      if (myStatus) {
        isOnLeave.value = myStatus.isOnLeave
      }
    } else {
      fetchRoomStatus()
    }
    fetchPlanStatus()
    fetchTomorrowPlans()
  })

  on('tomorrow_converted', (data) => {
    fetchMyTasks()
    fetchRoomStatus()
    fetchPlanStatus()
    fetchTomorrowPlans()
    ElMessage.info('计划已转为今日任务')
  })

  on('task_created', (data) => {
    fetchMyTasks()
    fetchRoomStatus()
    fetchPlanStatus()
    fetchTomorrowPlans()
  })

  on('task_deleted', (data) => {
    fetchMyTasks()
    // 优先使用 WebSocket 广播中携带的房间状态（即时更新，无需额外 REST 请求）
    if (data?.roomStatus) {
      roomStatus.value = data.roomStatus
      const myStatus = data.roomStatus.members?.find(m => m.memberId === authStore.memberId)
      if (myStatus) {
        isOnLeave.value = myStatus.isOnLeave
      }
    } else {
      fetchRoomStatus()
    }
    fetchPlanStatus()
    fetchTomorrowPlans()
  })

  on('evidence_reviewed', (data) => {
    // 只对证据上传者显示通知
    if (data.memberId === authStore.memberId) {
      ElMessage.info(`您的学习证据已${data.result === 1 ? '通过' : '被驳回'}`)
    }
    fetchMyTasks()
    // 使用广播中的 roomStatus 即时更新（审核通过会自动完成任务，需刷新房间状态）
    if (data?.roomStatus) {
      roomStatus.value = data.roomStatus
      const myStatus = data.roomStatus.members?.find(m => m.memberId === authStore.memberId)
      if (myStatus) {
        isOnLeave.value = myStatus.isOnLeave
      }
    } else {
      fetchRoomStatus()
    }
    fetchPlanStatus()
    fetchTomorrowPlans()
  })

  on('room_dissolved', () => {
    ElMessageBox.alert(
      '房间已被管理员注销，您将被退出房间。',
      '房间已注销',
      {
        confirmButtonText: '确定',
        type: 'warning',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
      }
    ).then(() => {
      authStore.clearAuth()
      router.push('/')
    }).catch(() => {
      authStore.clearAuth()
      router.push('/')
    })
  })

  // 被踢出房间
  on('member_kicked', (data) => {
    const reason = data?.reason || '未填写明日计划'
    // 先关闭 WebSocket 连接，防止自动重连
    close()
    ElMessageBox.alert(
      `您因${reason}被踢出房间。`,
      '已被踢出',
      {
        confirmButtonText: '确定',
        type: 'warning',
        showClose: false,
        closeOnClickModal: false,
        closeOnPressEscape: false,
      }
    ).then(() => {
      authStore.clearAuth()
      router.push('/')
    }).catch(() => {
      authStore.clearAuth()
      router.push('/')
    })
  })
})

// 获取我的今日任务
async function fetchMyTasks() {
  try {
    const res = await getMyTodayTasks()
    myTasks.value = res.data || []
    // 如果有任务，说明不在请假状态
    if (myTasks.value.length > 0) {
      isOnLeave.value = false
    }
  } catch (error) {
    myTasks.value = []
  }
}

// 获取房间完成状态
async function fetchRoomStatus() {
  try {
    console.log('[fetchRoomStatus] 开始请求 /api/task/room-status')
    const res = await getRoomStatus()
    console.log('[fetchRoomStatus] 响应:', res)
    roomStatus.value = res.data
    // 检查自己是否请假
    const myStatus = roomStatus.value?.members?.find(m => m.memberId === authStore.memberId)
    if (myStatus) {
      isOnLeave.value = myStatus.isOnLeave
    }
  } catch (error) {
    console.error('[fetchRoomStatus] 失败:', error)
    roomStatus.value = null
  }
}

// 获取计划状态
async function fetchPlanStatus() {
  try {
    const res = await getPlanStatus()
    planStatus.value = res.data
  } catch (error) {
    planStatus.value = null
  }
}

// 获取明日计划列表
async function fetchTomorrowPlans() {
  try {
    const res = await getTomorrowPlan()
    tomorrowPlans.value = res.data || []
  } catch {
    tomorrowPlans.value = []
  }
}

// 请假
async function handleRequestLeave() {
  try {
    await ElMessageBox.confirm('确定请假？', '请假确认', {
      confirmButtonText: '确认请假',
      cancelButtonText: '取消',
      type: 'warning',
    })
    loading.value = true
    await requestLeave()
    isOnLeave.value = true
    await fetchRoomStatus()
    ElMessage.success('已请假')
  } catch (error) {
    if (error !== 'cancel') {
      // 错误已在拦截器中处理
    }
  } finally {
    loading.value = false
  }
}

// 取消请假
async function handleCancelLeave() {
  try {
    loading.value = true
    await cancelLeave()
    isOnLeave.value = false
    await fetchMyTasks()
    await fetchRoomStatus()
    ElMessage.success('已取消请假')
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

// 查看成员任务
async function viewMemberTasks(member) {
  selectedMember.value = member
  showMemberTasksDialog.value = true
  try {
    const [tasksRes, plansRes] = await Promise.all([
      getMemberTodayTasks(member.id),
      getMemberTomorrowPlan(member.id)
    ])
    memberTasks.value = tasksRes.data || []
    memberTomorrowPlans.value = plansRes.data || []
  } catch (error) {
    memberTasks.value = []
    memberTomorrowPlans.value = []
  }
}

// 查看成员历史计划
function viewMemberPlans() {
  if (!selectedMember.value) return
  showMemberTasksDialog.value = false
  router.push(`/room/${roomCode}/plan/${selectedMember.value.id}?name=${encodeURIComponent(selectedMember.value.displayName)}`)
}

// 查看成员历史记录
async function viewMemberHistory() {
  if (!selectedMember.value) return
  historyMember.value = selectedMember.value
  showMemberTasksDialog.value = false
  showHistoryDialog.value = true
  try {
    const res = await getMemberHistoryTasks(selectedMember.value.id, { days: 30 })
    historyTasks.value = res.data || []
  } catch (error) {
    historyTasks.value = []
  }
}

// 历史任务按日期分组
const historyByDate = computed(() => {
  const groups = {}
  for (const task of historyTasks.value) {
    const date = task.taskDate
    if (!groups[date]) {
      groups[date] = []
    }
    groups[date].push(task)
  }
  return groups
})

// 输入框 DOM 引用（用于聚焦）
const inputRefs = []
function setInputRef(index, el) {
  if (el) inputRefs[index] = el
}

// 输入框内容变化时
function onContentInput(index) {
  const val = taskContentInputs.value[index]
  const list = [...taskContentInputs.value]
  const isLast = index === list.length - 1

  if (!val.trim()) {
    // 清空了内容：将下方所有项上移一位
    for (let i = index; i < list.length - 1; i++) {
      list[i] = list[i + 1]
    }
    // 移除末尾多余空框
    while (list.length > 1 && !list[list.length - 1].trim()) {
      list.pop()
    }
    // 末尾有内容时，补一个空框方便继续添加
    if (list[list.length - 1].trim()) {
      list.push('')
    }
    taskContentInputs.value = list
    // 聚焦到最后一个输入框
    nextTick(() => {
      const lastIdx = taskContentInputs.value.length - 1
      const inputEl = inputRefs[lastIdx]
      if (inputEl) {
        const inner = inputEl.$el ? inputEl.$el.querySelector('input') : inputEl
        if (inner) {
          inner.focus()
          inner.setSelectionRange(inner.value.length, inner.value.length)
        }
      }
    })
  } else if (isLast) {
    // 当前框有内容且是最后一个，添加新空白框
    taskContentInputs.value = [...list, '']
  }
}
// 删除输入框
function removeContentInput(index) {
  if (taskContentInputs.value.length <= 1) return
  // 用新数组替换，确保 Vue 响应式正确更新所有输入框的值
  const newList = taskContentInputs.value.filter((_, i) => i !== index)
  // 清理末尾空输入框
  while (newList.length > 1 && !newList[newList.length - 1].trim()) {
    newList.pop()
  }
  taskContentInputs.value = newList.length > 0 ? newList : ['']
}

// 创建任务（批量提交）
async function handleCreateTask() {
  if (!selectedSubject.value) {
    ElMessage.warning('请选择科目')
    return
  }
  // 过滤掉末尾空内容
  const contents = [...taskContentInputs.value]
  while (contents.length > 0 && !contents[contents.length - 1].trim()) {
    contents.pop()
  }
  if (contents.length === 0) {
    ElMessage.warning('请至少输入一个学习计划')
    return
  }
  loading.value = true
  try {
    for (const content of contents) {
      const res = await createTask({
        subject: selectedSubject.value,
        taskContent: content.trim(),
      })
      myTasks.value.push(res.data)
    }
    ElMessage.success(`成功创建 ${contents.length} 个任务`)
    showTaskDialog.value = false
    selectedSubject.value = ''
    taskContentInputs.value = ['']
    fetchPlanStatus()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

// 打开证据上传对话框
async function openEvidenceDialog(taskId) {
  currentTaskId.value = taskId
  evidenceFiles.value = []
  try {
    const res = await getTaskEvidence(taskId)
    evidenceList.value = res.data || []
  } catch (error) {
    evidenceList.value = []
  }
  showEvidenceDialog.value = true
}

// 上传证据
async function handleUploadEvidence() {
  if (evidenceFiles.value.length === 0) {
    ElMessage.warning('请选择至少一张图片')
    return
  }
  loading.value = true
  try {
    const formData = new FormData()
    formData.append('taskId', currentTaskId.value)
    evidenceFiles.value.forEach(file => {
      formData.append('files', file.raw)
    })
    await uploadEvidence(formData)
    ElMessage.success('证据上传成功')
    // 刷新证据列表
    const res = await getTaskEvidence(currentTaskId.value)
    evidenceList.value = res.data || []
    evidenceFiles.value = []
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

// 删除证据
async function handleDeleteEvidence(evidenceId) {
  try {
    await ElMessageBox.confirm('确认删除此证据图片？', '警告', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteEvidence(evidenceId)
    evidenceList.value = evidenceList.value.filter(e => e.id !== evidenceId)
    ElMessage.success('证据已删除')
  } catch (error) {
    if (error !== 'cancel') {
      // 错误已在拦截器中处理
    }
  }
}

// 标记任务完成
async function handleCompleteTask(taskId) {
  try {
    await ElMessageBox.confirm('确认标记此任务为已完成？', '确认', {
      confirmButtonText: '确认完成',
      cancelButtonText: '取消',
      type: 'success',
    })
    await completeTask(taskId)
    // 更新本地状态
    const task = myTasks.value.find(t => t.id === taskId)
    if (task) {
      task.isCompleted = 1
    }
    ElMessage.success('任务已完成')
    send('task_completed', { taskId })
    // 刷新房间状态，实时解锁明日计划按钮
    await fetchRoomStatus()
  } catch (error) {
    if (error !== 'cancel') {
      // 错误已在拦截器中处理
    }
  }
}

// 删除任务
async function handleDeleteTask(taskId) {
  try {
    await ElMessageBox.confirm('确认删除此任务？', '警告', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteTask(taskId)
    myTasks.value = myTasks.value.filter(t => t.id !== taskId)
    ElMessage.success('任务已删除')
    // 刷新房间状态，实时更新任务数量
    await fetchRoomStatus()
  } catch (error) {
    if (error !== 'cancel') {
      // 错误已在拦截器中处理
    }
  }
}

// 提交明日计划
async function handleCreateTomorrow() {
  // 收集所有有效计划
  const plans = []
  for (const group of tomorrowGroups.value) {
    if (!group.subject) continue
    const contents = group.contentInputs.filter(c => c.trim())
    for (const c of contents) {
      plans.push({ subject: group.subject, taskContent: c.trim() })
    }
  }
  if (plans.length === 0) {
    ElMessage.warning('请至少填写一个学习计划')
    return
  }
  loading.value = true
  try {
    await createTomorrowPlan({ plans })
    ElMessage.success('计划保存成功')
    showTomorrowDialog.value = false
    tomorrowGroups.value = [{ id: 1, subject: '', contentInputs: [''] }]
    await fetchPlanStatus()
    await fetchTomorrowPlans()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

// 打开明日计划对话框
async function openTomorrowDialog() {
  showTomorrowDialog.value = true
  try {
    const res = await getTomorrowPlan()
    const plans = res.data || []
    if (plans.length > 0) {
      // 按科目分组加载已有计划
      const groups = {}
      plans.forEach(p => {
        if (!groups[p.subject]) groups[p.subject] = []
        groups[p.subject].push(p.taskContent || '')
      })
      let id = 0
      tomorrowGroups.value = Object.entries(groups).map(([subject, contents]) => ({
        id: ++id,
        subject,
        contentInputs: [...contents, ''],
      }))
    } else {
      tomorrowGroups.value = [{ id: 1, subject: '', contentInputs: [''] }]
    }
  } catch {
    tomorrowGroups.value = [{ id: 1, subject: '', contentInputs: [''] }]
  }
}

// 明日计划：科目组操作
function addTomorrowGroup() {
  const maxId = Math.max(0, ...tomorrowGroups.value.map(g => g.id))
  tomorrowGroups.value.push({ id: maxId + 1, subject: '', contentInputs: [''] })
}

function removeTomorrowGroup(groupId) {
  if (tomorrowGroups.value.length <= 1) return
  tomorrowGroups.value = tomorrowGroups.value.filter(g => g.id !== groupId)
}

// 内容输入框变化（自动扩展）
function onGroupContentInput(groupId, index) {
  const group = tomorrowGroups.value.find(g => g.id === groupId)
  if (!group) return
  const val = group.contentInputs[index]
  const list = [...group.contentInputs]
  const isLast = index === list.length - 1

  if (!val.trim()) {
    // 删除空行（保留最后一行）
    for (let i = index; i < list.length - 1; i++) {
      list[i] = list[i + 1]
    }
    while (list.length > 1 && !list[list.length - 1].trim()) {
      list.pop()
    }
    if (list[list.length - 1].trim()) {
      list.push('')
    }
    group.contentInputs = list
  } else if (isLast) {
    group.contentInputs = [...list, '']
  }
}

// 删除内容输入框
function removeGroupContentInput(groupId, index) {
  const group = tomorrowGroups.value.find(g => g.id === groupId)
  if (!group || group.contentInputs.length <= 1) return
  const newList = group.contentInputs.filter((_, i) => i !== index)
  while (newList.length > 1 && !newList[newList.length - 1].trim()) {
    newList.pop()
  }
  group.contentInputs = newList.length > 0 ? newList : ['']
}

// 复制邀请码
async function copyInviteCode() {
  const success = await copyToClipboard(roomCode)
  if (success) {
    ElMessage.success('邀请码已复制')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

// 复制邀请链接
async function copyInviteLink() {
  const link = `${window.location.origin}?invite=${roomCode}`
  const success = await copyToClipboard(link)
  if (success) {
    ElMessage.success('邀请链接已复制')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

// 注销房间（管理员）
async function handleDissolveRoom() {
  try {
    await ElMessageBox.confirm(
      '注销房间后，所有成员将被踢出，此操作不可撤销！',
      '注销房间',
      {
        confirmButtonText: '确认注销',
        cancelButtonText: '取消',
        type: 'error',
      }
    )
    await dissolveRoom()
    ElMessage.success('房间已注销')
    try { await logout() } catch (e) { /* 忽略 */ }
    authStore.clearAuth()
    router.push('/')
  } catch (error) {
    if (error !== 'cancel') {
      // 错误已在拦截器中处理
    }
  }
}

// 跳转到审核页面
function goReview() {
  router.push(`/room/${roomCode}/review`)
}

// 跳转到计划管理
function goPlan() {
  router.push(`/room/${roomCode}/plan`)
}

// 退出房间（非管理员）
function handleLogout() {
  ElMessageBox.confirm('确认退出房间？', '提示', {
    confirmButtonText: '确认退出',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    try {
      await leaveRoom()
      ElMessage.success('已退出房间')
    } catch (e) {
      console.error('退出房间API失败:', e)
    }
    try { await logout() } catch (e) { /* 忽略 */ }
    close()
    authStore.clearAuth()
    router.push('/')
  }).catch(() => {})
}

// 获取授权码
async function handleGenerateAuthCode() {
  authCodeLoading.value = true
  try {
    const res = await generateAuthCode()
    authCode.value = res.data.code
    showAuthCodeDialog.value = true
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    authCodeLoading.value = false
  }
}

// 复制授权码
async function copyAuthCode() {
  const success = await copyToClipboard(authCode.value)
  if (success) {
    ElMessage.success('授权码已复制')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 page-bg">
    <!-- 顶部导航 -->
    <header class="glass-card mx-2 sm:mx-4 mt-2 sm:mt-4 px-3 sm:px-6 py-3 sm:py-4 sticky top-2 sm:top-4 z-50">
      <!-- 移动端：紧凑单行布局 -->
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2 sm:gap-4 min-w-0">
          <h1 class="text-base sm:text-xl font-bold gradient-text whitespace-nowrap">互相监督</h1>
          <el-tag type="primary" effect="plain" class="!rounded-lg cursor-pointer !text-xs sm:!text-sm" @click="copyInviteCode">
            {{ roomCode }}
          </el-tag>
          <el-tag v-if="isAdmin" type="warning" effect="plain" class="!rounded-lg !text-xs sm:!text-sm desktop-only">
            管理员
          </el-tag>
        </div>

        <div class="flex items-center gap-1 sm:gap-4">
          <!-- 邀请按钮 -->
          <el-dropdown>
            <el-button type="primary" plain class="!rounded-xl !px-2 sm:!px-4">
              <el-icon><Share /></el-icon>
              <span class="desktop-only ml-1">邀请</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="copyInviteCode">
                  <el-icon><CopyDocument /></el-icon> 复制邀请码
                </el-dropdown-item>
                <el-dropdown-item @click="copyInviteLink">
                  <el-icon><Link /></el-icon> 复制邀请链接
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>

          <!-- 用户菜单 -->
          <el-dropdown>
            <el-button class="!rounded-xl !px-2 sm:!px-4">
              <el-icon><User /></el-icon>
              <span class="desktop-only ml-1">{{ authStore.displayName }}</span>
              <el-icon class="ml-1"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goPlan">
                  <el-icon><Calendar /></el-icon> 计划管理
                </el-dropdown-item>
                <el-dropdown-item @click="goReview">
                  <el-icon><Checked /></el-icon> 证据审核
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleGenerateAuthCode">
                  <el-icon><Key /></el-icon> 获取授权码
                </el-dropdown-item>
                <el-dropdown-item v-if="isAdmin" divided @click="handleDissolveRoom">
                  <el-icon><Delete /></el-icon> 注销房间
                </el-dropdown-item>
                <el-dropdown-item v-if="!isAdmin" divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon> 退出房间
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="max-w-7xl mx-auto px-2 sm:px-4 py-4 sm:py-6">
      <div class="grid grid-cols-1 lg:grid-cols-3 gap-4 sm:gap-6">
        <!-- 左侧：房间成员 -->
        <div class="lg:col-span-1">
          <!-- 移动端：成员横向滚动 -->
          <div class="glass-card p-4 sm:p-6 mobile-card">
            <div class="flex items-center justify-between mb-3 sm:mb-4">
              <h2 class="text-base sm:text-lg font-semibold text-gray-800">房间成员</h2>
              <el-tag type="info" effect="plain" class="!rounded-lg">
                {{ roomStore.memberCount }} 人
              </el-tag>
            </div>

            <!-- 移动端横向滚动 -->
            <div class="flex lg:hidden overflow-x-auto gap-3 pb-2 -mx-1 px-1 scrollbar-hide">
              <div
                v-for="member in roomStore.members"
                :key="member.id"
                @click="viewMemberTasks(member)"
                class="flex-shrink-0 flex flex-col items-center p-3 rounded-xl bg-white/50 cursor-pointer min-w-[80px]"
              >
                <div class="relative mb-1">
                  <div class="w-12 h-12 rounded-full bg-gradient-to-br from-primary-400 to-secondary-400 flex items-center justify-center text-white font-bold">
                    {{ member.displayName.charAt(0) }}
                  </div>
                </div>
                <div class="text-xs font-medium text-gray-700 text-center truncate max-w-[60px]">
                  {{ member.displayName }}
                </div>
                <el-tag v-if="member.isAdmin" size="small" type="warning" class="!rounded-md !text-[10px] mt-1">
                  管理员
                </el-tag>
              </div>
            </div>

            <!-- 桌面端纵向列表 -->
            <div class="hidden lg:block space-y-3">
              <div
                v-for="member in roomStore.members"
                :key="member.id"
                @click="viewMemberTasks(member)"
                class="flex items-center p-3 rounded-xl bg-white/50 hover:bg-white/80 cursor-pointer hover-lift"
              >
                <div>
                  <div class="w-10 h-10 rounded-full bg-gradient-to-br from-primary-400 to-secondary-400 flex items-center justify-center text-white font-bold">
                    {{ member.displayName.charAt(0) }}
                  </div>
                </div>
                <div class="ml-3 flex-1">
                  <div class="font-medium text-gray-800 flex items-center">
                    {{ member.displayName }}
                    <el-tag v-if="member.isAdmin" size="small" type="warning" class="ml-2 !rounded-md">
                      管理员
                    </el-tag>
                    <el-tag v-if="member.id === authStore.memberId" size="small" type="primary" class="ml-2 !rounded-md">
                      我
                    </el-tag>
                  </div>
                </div>
                <el-icon class="text-gray-400"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>

          <!-- 快捷操作（桌面端显示） -->
          <div class="glass-card p-6 mt-4 desktop-only">
            <h2 class="text-lg font-semibold text-gray-800 mb-4">快捷操作</h2>
            <div class="space-y-3">
              <el-button
                v-if="isFirstDay && !hasPlanTasks"
                type="primary"
                class="w-full !rounded-xl !h-12"
                @click="showTaskDialog = true"
                :disabled="!canAddTask"
              >
                <el-icon class="mr-2"><Plus /></el-icon>
                添加今日任务
              </el-button>
              <el-tooltip
                :content="isLocked ? '有成员未完成今日任务，无法制定明日计划' : ''"
                :disabled="!isLocked"
                placement="top"
              >
                <el-button
                  type="success"
                  class="w-full !rounded-xl !h-12"
                  @click="openTomorrowDialog"
                  :disabled="!canCreateTomorrow"
                >
                  <el-icon class="mr-2"><Calendar /></el-icon>
                  {{ planButtonLabel }}
                </el-button>
              </el-tooltip>
              <el-button
                type="warning"
                class="w-full !rounded-xl !h-12"
                @click="goReview"
              >
                <el-icon class="mr-2"><Checked /></el-icon>
                审核证据
              </el-button>
            </div>
          </div>
        </div>

        <!-- 右侧：我的今日任务 -->
        <div class="lg:col-span-2">
          <!-- 进度概览 -->
          <div class="glass-card p-4 sm:p-6 mb-4 sm:mb-6 mobile-card">
            <div class="flex items-center justify-between mb-3 sm:mb-4">
              <h2 class="text-base sm:text-lg font-semibold text-gray-800">我的今日进度</h2>
              <div class="text-2xl sm:text-3xl font-bold gradient-text">{{ myProgress }}%</div>
            </div>
            <el-progress
              :percentage="myProgress"
              :stroke-width="10"
              :show-text="false"
              class="custom-progress"
            />
            <div class="flex justify-between mt-2 sm:mt-3 text-xs sm:text-sm text-gray-500">
              <span>已完成 {{ myTasks.filter(t => t.isCompleted).length }} 项</span>
              <span>共 {{ myTasks.length }} 项</span>
            </div>
          </div>

          <!-- 房间成员状态 -->
          <div v-if="roomStatus" class="glass-card p-4 sm:p-6 mb-4 sm:mb-6 border-2 border-warning-200 bg-warning-50/50">
            <div class="flex items-center mb-3">
              <el-icon class="text-xl text-warning-500 mr-2"><WarningFilled /></el-icon>
              <h3 class="font-semibold text-gray-800">{{ isLocked ? '有成员未完成今日任务' : '全员已完成' }}</h3>
            </div>
            <p v-if="isLocked" class="text-sm text-gray-500 mb-4">全员完成后才能{{ planButtonLabel }}</p>
            <div class="space-y-2">
              <div
                v-for="m in roomStatus.members"
                :key="m.memberId"
                class="flex items-center justify-between p-2 rounded-lg bg-white"
              >
                <div class="flex items-center">
                  <div
                    :class="[
                      'w-8 h-8 rounded-full flex items-center justify-center text-white text-sm font-bold mr-2',
                      m.allDone
                        ? 'bg-gradient-to-br from-success-400 to-success-500'
                        : 'bg-gradient-to-br from-gray-300 to-gray-400'
                    ]"
                  >
                    {{ m.displayName?.charAt(0) || '?' }}
                  </div>
                  <span class="text-sm text-gray-700">{{ m.displayName }}</span>
                </div>
                <div>
                  <el-tag v-if="m.isOnLeave" size="small" type="warning" effect="plain" class="!rounded-md">
                    已请假
                  </el-tag>
                  <el-tag v-else-if="m.allDone" size="small" type="success" effect="plain" class="!rounded-md">
                    已完成
                  </el-tag>
                  <el-tag v-else size="small" type="info" effect="plain" class="!rounded-md">
                    {{ m.completedTasks }}/{{ m.totalTasks }}
                  </el-tag>
                </div>
              </div>
            </div>
          </div>

          <!-- 移动端快捷操作按钮 -->
          <div class="flex lg:hidden gap-2 mb-4">
            <el-button v-if="isFirstDay && !hasPlanTasks" type="primary" class="flex-1 !rounded-xl !h-11" @click="showTaskDialog = true" :disabled="!canAddTask">
              <el-icon class="mr-1"><Plus /></el-icon> 添加今日任务
            </el-button>
            <el-tooltip
              :content="isLocked ? '有成员未完成今日任务，无法制定明日计划' : ''"
              :disabled="!isLocked"
              placement="top"
            >
              <el-button type="success" class="flex-1 !rounded-xl !h-11" @click="openTomorrowDialog" :disabled="!canCreateTomorrow">
                <el-icon class="mr-1"><Calendar /></el-icon> {{ planButtonLabel }}
              </el-button>
            </el-tooltip>
            <el-button type="warning" class="flex-1 !rounded-xl !h-11" @click="goReview">
              <el-icon class="mr-1"><Checked /></el-icon> 审核
            </el-button>
          </div>

          <!-- 明日计划 -->
          <div v-if="tomorrowPlans.length > 0" class="glass-card p-4 sm:p-6 mobile-card mb-4">
            <div class="flex items-center justify-between mb-4 sm:mb-6">
              <h2 class="text-base sm:text-lg font-semibold text-gray-800">
                <el-icon class="mr-1 text-primary-500"><Calendar /></el-icon>明日计划
              </h2>
              <el-button
                type="primary"
                size="small"
                @click="openTomorrowDialog"
                :disabled="!canCreateTomorrow"
                class="!rounded-lg"
              >
                <el-icon class="mr-1"><Edit /></el-icon> 修改
              </el-button>
            </div>

            <div class="space-y-4">
              <div
                v-for="(plans, subject) in tomorrowBySubject"
                :key="subject"
                class="animate-fade-in"
              >
                <div class="flex items-center mb-2">
                  <div class="w-2 h-2 rounded-full bg-primary-400 mr-2"></div>
                  <h3 class="font-semibold text-gray-700 text-sm">{{ subject }}</h3>
                  <el-tag size="small" type="info" class="ml-2 !rounded-md">{{ plans.length }}项</el-tag>
                </div>
                <div class="space-y-1 ml-2 sm:ml-4">
                  <div
                    v-for="plan in plans"
                    :key="plan.id"
                    class="flex items-center p-2 sm:p-3 rounded-lg bg-primary-50/50 border border-primary-100"
                  >
                    <span class="text-xs sm:text-sm text-gray-700">{{ plan.taskContent }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 我的任务列表 -->
          <div class="glass-card p-4 sm:p-6 mobile-card">
            <div class="flex items-center justify-between mb-4 sm:mb-6">
              <h2 class="text-base sm:text-lg font-semibold text-gray-800">我的学习计划</h2>
              <div class="flex gap-2">
                <el-button
                  v-if="!isOnLeave"
                  size="small"
                  @click="handleRequestLeave"
                  :loading="loading"
                  class="!rounded-lg"
                >
                  请假
                </el-button>
                <el-button
                  v-if="isFirstDay && !hasPlanTasks"
                  type="primary"
                  size="small"
                  @click="showTaskDialog = true"
                  :disabled="!canAddTask"
                  class="!rounded-lg"
                >
                  <el-icon class="mr-1"><Plus /></el-icon>
                  添加
                </el-button>
              </div>
            </div>

            <!-- 请假状态 -->
            <div v-if="isOnLeave" class="text-center py-12">
              <div class="w-20 h-20 mx-auto mb-4 rounded-full bg-warning-50 flex items-center justify-center">
                <el-icon class="text-4xl text-warning-400"><Calendar /></el-icon>
              </div>
              <p class="text-gray-600 font-medium mb-2">今日已请假</p>
              <p class="text-gray-400 text-sm mb-4">请假后今日无学习计划，不影响其他成员</p>
              <el-button type="warning" @click="handleCancelLeave" :loading="loading" class="!rounded-xl">
                取消请假
              </el-button>
            </div>

            <!-- 空状态 -->
            <div v-else-if="myTasks.length === 0" class="text-center py-12">
              <div class="w-20 h-20 mx-auto mb-4 rounded-full bg-gray-100 flex items-center justify-center">
                <el-icon class="text-4xl text-gray-300"><Document /></el-icon>
              </div>
              <p class="text-gray-400 mb-4">还没有学习计划</p>
              <div class="flex gap-3 justify-center">
                <el-button v-if="isFirstDay" type="primary" @click="showTaskDialog = true" :disabled="!canAddTask" class="!rounded-xl">
                  添加任务
                </el-button>
                <el-button v-if="!isOnLeave" @click="handleRequestLeave" :loading="loading" class="!rounded-xl">
                  今日请假
                </el-button>
              </div>
            </div>

            <!-- 按科目分组显示 -->
            <div v-else class="space-y-6">
              <div
                v-for="(tasks, subject) in myTasksBySubject"
                :key="subject"
                class="animate-fade-in"
              >
                <div class="flex items-center mb-3">
                  <div class="w-2 h-2 rounded-full bg-primary-500 mr-2"></div>
                  <h3 class="font-semibold text-gray-700">{{ subject }}</h3>
                  <el-tag size="small" type="info" class="ml-2 !rounded-md">
                    {{ tasks.filter(t => t.isCompleted).length }}/{{ tasks.length }}
                  </el-tag>
                </div>

                <div class="space-y-2 ml-2 sm:ml-4">
                  <div
                    v-for="task in tasks"
                    :key="task.id"
                    :class="[
                      'flex items-center p-3 sm:p-4 rounded-xl',
                      task.isCompleted
                        ? 'bg-success-50 border border-success-200'
                        : 'bg-white hover-lift border border-gray-100'
                    ]"
                  >
                    <div class="flex-1 min-w-0">
                      <div :class="['text-xs sm:text-sm break-words', task.isCompleted === 1 ? 'line-through text-gray-400' : 'text-gray-700']">
                        {{ task.taskContent }}
                      </div>
                      <div v-if="task.fromPlan === 1" class="text-[10px] text-gray-400 mt-0.5">计划任务</div>
                    </div>

                    <div class="flex items-center gap-1 sm:gap-2 ml-2 flex-shrink-0">
                      <el-button
                        size="small"
                        type="primary"
                        @click="openEvidenceDialog(task.id)"
                        class="!rounded-lg !px-2 sm:!px-3"
                      >
                        <el-icon class="sm:mr-1"><Camera /></el-icon>
                        <span class="hidden sm:inline">证据</span>
                      </el-button>
                      <el-button
                        v-if="!task.isCompleted && task.fromPlan !== 1 && !hasPlanTasks"
                        size="small"
                        type="danger"
                        @click="handleDeleteTask(task.id)"
                        class="!rounded-lg !px-2"
                      >
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 添加任务对话框 -->
    <el-dialog
      v-model="showTaskDialog"
      title="添加今日任务"
      width="500px"
      class="mobile-dialog"
      @open="selectedSubject = ''; taskContentInputs = ['']"
    >
      <div class="space-y-4">
        <!-- 科目选择 -->
        <div>
          <div class="flex items-center justify-between mb-2">
            <label class="text-sm font-medium text-gray-700">科目名称</label>
            <el-button
              type="primary"
              link
              size="small"
              @click="showSubjectDialog = true"
            >
              <el-icon class="mr-1"><Setting /></el-icon>
              管理科目
            </el-button>
          </div>
          <el-select
            v-model="selectedSubject"
            placeholder="请选择科目"
            size="large"
            class="w-full"
            filterable
            allow-create
          >
            <el-option
              v-for="s in subjects"
              :key="s"
              :label="s"
              :value="s"
            />
          </el-select>
        </div>

        <!-- 学习内容输入框列表 -->
        <div>
          <label class="text-sm font-medium text-gray-700 mb-2 block">学习计划内容</label>
          <div class="space-y-2">
            <div
              v-for="(content, index) in taskContentInputs"
              :key="index"
              class="flex items-center gap-2"
            >
              <el-input
                :ref="(el) => setInputRef(index, el)"
                v-model="taskContentInputs[index]"
                :placeholder="index === 0 ? '输入学习计划内容...' : `计划 ${index + 1}`"
                size="large"
                @input="onContentInput(index)"
              >
                <template #prefix>
                  <span class="text-xs text-gray-400 font-medium">{{ index + 1 }}</span>
                </template>
              </el-input>
              <el-button
                v-if="taskContentInputs.length > 1 && (index === 0 || taskContentInputs[index - 1].trim())"
                type="danger"
                @click="removeContentInput(index)"
                class="!rounded-lg !px-3 flex-shrink-0"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          <p class="text-xs text-gray-400 mt-2">填写完成后会自动出现新的输入框</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="showTaskDialog = false" class="!flex-1 sm:!flex-none">取消</el-button>
        <el-button
          type="primary"
          :loading="loading"
          @click="handleCreateTask"
          class="!flex-1 sm:!flex-none"
        >
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 科目管理对话框 -->
    <el-dialog
      v-model="showSubjectDialog"
      title="管理科目"
      width="400px"
      class="mobile-dialog"
    >
      <div class="space-y-4">
        <div class="flex gap-2">
          <el-input
            v-model="newSubjectInput"
            placeholder="输入新科目名称"
            size="large"
            @keyup.enter="addSubject"
          />
          <el-button type="primary" @click="addSubject" class="!rounded-xl flex-shrink-0">
            添加
          </el-button>
        </div>
        <div class="space-y-2">
          <div
            v-for="(s, index) in subjects"
            :key="s"
            class="flex items-center justify-between p-3 bg-gray-50 rounded-xl"
          >
            <span class="text-gray-700">{{ s }}</span>
            <el-button
              size="small"
              type="danger"
              @click="removeSubject(index)"
              class="!rounded-lg !px-2"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <el-alert
          title="科目数据保存在浏览器本地，清除缓存后需重新设置"
          type="info"
          :closable="false"
          show-icon
          class="!rounded-xl"
        />
      </div>
      <template #footer>
        <el-button type="primary" @click="showSubjectDialog = false" class="!rounded-xl">
          完成
        </el-button>
      </template>
    </el-dialog>

    <!-- 证据上传对话框 -->
    <el-dialog
      v-model="showEvidenceDialog"
      title="学习证据"
      width="600px"
      class="mobile-dialog"
    >
      <div class="space-y-4 sm:space-y-6">
        <!-- 已有证据 -->
        <div v-if="evidenceList.length > 0">
          <h4 class="text-sm font-medium text-gray-700 mb-3">已上传的证据</h4>
          <div class="grid grid-cols-2 sm:grid-cols-3 gap-2 sm:gap-3">
            <div
              v-for="evidence in evidenceList"
              :key="evidence.id"
              class="relative group"
            >
              <!-- 待审核状态的删除角标 -->
              <div
                v-if="evidence.status === 0"
                @click.stop="handleDeleteEvidence(evidence.id)"
                class="absolute top-1 right-1 z-10 w-6 h-6 bg-red-500 rounded-full flex items-center justify-center cursor-pointer shadow-md hover:bg-red-600 transition-colors"
              >
                <el-icon class="text-white text-xs"><Close /></el-icon>
              </div>
              <img
                :src="evidence.imageUrl"
                class="w-full h-24 sm:h-32 object-cover rounded-xl"
                :alt="'证据图片'"
              />
              <div class="absolute inset-0 bg-black/50 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center gap-2">
                <el-tag
                  :type="evidence.status === 1 ? 'success' : evidence.status === 2 ? 'danger' : 'warning'"
                  effect="dark"
                  class="!rounded-lg"
                >
                  {{ evidence.status === 1 ? '已通过' : evidence.status === 2 ? '已驳回' : '待审核' }}
                </el-tag>
                <el-button
                  v-if="evidence.status === 0"
                  size="small"
                  type="danger"
                  @click.stop="handleDeleteEvidence(evidence.id)"
                  class="!rounded-lg"
                >
                  <el-icon><Delete /></el-icon> 删除
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 上传新证据 -->
        <div>
          <h4 class="text-sm font-medium text-gray-700 mb-3">上传新证据</h4>
          <el-upload
            v-model:file-list="evidenceFiles"
            action="#"
            list-type="picture-card"
            :auto-upload="false"
            multiple
            accept="image/*"
            class="evidence-upload"
          >
            <el-icon class="text-2xl text-gray-400"><Plus /></el-icon>
          </el-upload>
        </div>
      </div>
      <template #footer>
        <el-button @click="showEvidenceDialog = false" class="!flex-1 sm:!flex-none">关闭</el-button>
        <el-button
          type="primary"
          :loading="loading"
          :disabled="evidenceFiles.length === 0"
          @click="handleUploadEvidence"
          class="!flex-1 sm:!flex-none"
        >
          上传证据
        </el-button>
      </template>
    </el-dialog>

    <!-- 明日计划对话框 -->
    <el-dialog
      v-model="showTomorrowDialog"
      :title="planButtonLabel"
      width="560px"
      class="mobile-dialog"
    >
      <div class="space-y-4">
        <el-alert
          title="请慎重安排任务，0 点过后即生效，计划生效后将不可更改"
          type="warning"
          :closable="false"
          show-icon
          class="!rounded-xl"
        />

        <!-- 科目组列表 -->
        <div
          v-for="(group, gIdx) in tomorrowGroups"
          :key="group.id"
          class="p-4 rounded-xl border border-gray-200 bg-gray-50/50 space-y-3 relative"
        >
          <!-- 删除组按钮 -->
          <el-button
            v-if="tomorrowGroups.length > 1"
            type="danger"
            text
            size="small"
            @click="removeTomorrowGroup(group.id)"
            class="absolute top-2 right-2"
          >
            <el-icon><Delete /></el-icon>
          </el-button>

          <div class="text-xs text-gray-400 font-medium">科目 {{ gIdx + 1 }}</div>

          <!-- 科目选择 -->
          <div>
            <div class="flex items-center justify-between mb-1">
              <label class="text-sm font-medium text-gray-700">科目名称</label>
              <el-button
                type="primary"
                link
                size="small"
                @click="showSubjectDialog = true"
              >
                <el-icon class="mr-1"><Setting /></el-icon>
                管理科目
              </el-button>
            </div>
            <el-select
              v-model="group.subject"
              placeholder="请选择科目"
              size="large"
              class="w-full"
              filterable
              allow-create
            >
              <el-option
                v-for="s in subjects"
                :key="s"
                :label="s"
                :value="s"
              />
            </el-select>
          </div>

          <!-- 学习内容输入框 -->
          <div>
            <label class="text-sm font-medium text-gray-700 mb-1 block">学习计划内容</label>
            <div class="space-y-2">
              <div
                v-for="(content, cIdx) in group.contentInputs"
                :key="cIdx"
                class="flex items-center gap-2"
              >
                <el-input
                  v-model="group.contentInputs[cIdx]"
                  :placeholder="cIdx === 0 ? '输入学习计划内容...' : `计划 ${cIdx + 1}`"
                  size="large"
                  @input="onGroupContentInput(group.id, cIdx)"
                >
                  <template #prefix>
                    <span class="text-xs text-gray-400 font-medium">{{ cIdx + 1 }}</span>
                  </template>
                </el-input>
                <el-button
                  v-if="group.contentInputs.length > 1 && (cIdx === 0 || group.contentInputs[cIdx - 1].trim())"
                  type="danger"
                  @click="removeGroupContentInput(group.id, cIdx)"
                  class="!rounded-lg !px-3 flex-shrink-0"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 添加科目组按钮 -->
        <el-button
          type="primary"
          text
          class="w-full !border !border-dashed !border-primary-300 !rounded-xl !h-12"
          @click="addTomorrowGroup"
        >
          <el-icon class="mr-1"><Plus /></el-icon>
          添加科目
        </el-button>
      </div>
      <template #footer>
        <el-button @click="showTomorrowDialog = false" class="!flex-1 sm:!flex-none">取消</el-button>
        <el-button
          type="primary"
          :loading="loading"
          @click="handleCreateTomorrow"
          class="!flex-1 sm:!flex-none"
        >
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看成员任务对话框 -->
    <el-dialog
      v-model="showMemberTasksDialog"
      :title="`${selectedMember?.displayName} 的学习计划`"
      width="600px"
      class="mobile-dialog"
    >
      <div v-if="selectedMember" class="space-y-4 sm:space-y-6">
        <!-- 成员信息 -->
        <div class="flex items-center p-3 sm:p-4 bg-gradient-to-r from-primary-50 to-secondary-50 rounded-xl">
          <div class="w-10 h-10 sm:w-12 sm:h-12 rounded-full bg-gradient-to-br from-primary-400 to-secondary-400 flex items-center justify-center text-white font-bold text-base sm:text-lg">
            {{ selectedMember.displayName.charAt(0) }}
          </div>
          <div class="ml-3 sm:ml-4">
            <div class="font-semibold text-gray-800 text-sm sm:text-base">{{ selectedMember.displayName }}</div>
          </div>
          <div class="ml-auto text-right">
            <div class="text-xl sm:text-2xl font-bold gradient-text">{{ memberProgress }}%</div>
            <div class="text-xs text-gray-500">完成率</div>
          </div>
        </div>

        <!-- 进度条 -->
        <el-progress
          :percentage="memberProgress"
          :stroke-width="8"
          :show-text="false"
          class="custom-progress"
        />

        <!-- 任务列表 -->
        <div v-if="memberTasks.length === 0" class="text-center py-8">
          <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-gray-100 flex items-center justify-center">
            <el-icon class="text-3xl text-gray-300"><Document /></el-icon>
          </div>
          <p class="text-gray-400">还没有学习计划</p>
        </div>

        <div v-else class="space-y-4">
          <div
            v-for="(tasks, subject) in memberTasksBySubject"
            :key="subject"
          >
            <div class="flex items-center mb-2">
              <div class="w-2 h-2 rounded-full bg-primary-500 mr-2"></div>
              <h4 class="font-semibold text-gray-700 text-sm sm:text-base">{{ subject }}</h4>
              <el-tag size="small" type="info" class="ml-2 !rounded-md">
                {{ tasks.filter(t => t.isCompleted).length }}/{{ tasks.length }}
              </el-tag>
            </div>

            <div class="space-y-2 ml-2 sm:ml-4">
              <div
                v-for="task in tasks"
                :key="task.id"
                :class="[
                  'flex items-center p-2 sm:p-3 rounded-lg',
                  task.isCompleted ? 'bg-success-50' : 'bg-gray-50'
                ]"
              >
                <el-icon
                  :class="[
                    'mr-2 flex-shrink-0',
                    task.isCompleted ? 'text-success-500' : 'text-gray-300'
                  ]"
                >
                  <CircleCheck v-if="task.isCompleted" />
                  <Circle v-else />
                </el-icon>
                <span :class="['text-xs sm:text-sm break-words', task.isCompleted ? 'line-through text-gray-400' : 'text-gray-700']">
                  {{ task.taskContent }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 明日计划 -->
        <div v-if="memberTomorrowPlans.length > 0" class="mt-4 pt-4 border-t border-gray-200">
          <h3 class="text-sm font-semibold text-gray-600 mb-3">
            <el-icon class="mr-1 text-primary-500"><Calendar /></el-icon>明日计划
          </h3>
          <div class="space-y-3">
            <div
              v-for="(plans, subject) in memberTomorrowBySubject"
              :key="subject"
            >
              <div class="flex items-center mb-1">
                <div class="w-2 h-2 rounded-full bg-primary-400 mr-2"></div>
                <h4 class="font-medium text-gray-700 text-xs">{{ subject }}</h4>
                <el-tag size="small" type="info" class="ml-2 !rounded-md">{{ plans.length }}项</el-tag>
              </div>
              <div class="space-y-1 ml-2 sm:ml-4">
                <div
                  v-for="plan in plans"
                  :key="plan.id"
                  class="flex items-center p-2 rounded-lg bg-primary-50/50 border border-primary-100"
                >
                  <span class="text-xs text-gray-700">{{ plan.taskContent }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showMemberTasksDialog = false">关闭</el-button>
        <el-button type="success" @click="viewMemberHistory">
          <el-icon class="mr-1"><Clock /></el-icon> 历史记录
        </el-button>
        <el-button type="primary" @click="viewMemberPlans">
          <el-icon class="mr-1"><Calendar /></el-icon> 长期计划
        </el-button>
      </template>
    </el-dialog>

    <!-- 历史记录对话框 -->
    <el-dialog
      v-model="showHistoryDialog"
      :title="`${historyMember?.displayName} 的历史记录`"
      width="650px"
      class="mobile-dialog"
    >
      <div v-if="historyTasks.length === 0" class="text-center py-8">
        <div class="w-16 h-16 mx-auto mb-4 rounded-full bg-gray-100 flex items-center justify-center">
          <el-icon class="text-3xl text-gray-300"><Clock /></el-icon>
        </div>
        <p class="text-gray-400">暂无历史记录</p>
      </div>

      <div v-else class="max-h-[60vh] overflow-y-auto space-y-4 pr-1">
        <div
          v-for="(tasks, date) in historyByDate"
          :key="date"
          class="glass-card p-4"
        >
          <div class="flex items-center justify-between mb-3">
            <div class="font-semibold text-gray-700">{{ date }}</div>
            <el-tag size="small" type="info" class="!rounded-md">
              {{ tasks.filter(t => t.isCompleted).length }}/{{ tasks.length }} 完成
            </el-tag>
          </div>
          <div class="space-y-2">
            <div
              v-for="task in tasks"
              :key="task.id"
              :class="[
                'flex items-center p-2 sm:p-3 rounded-lg',
                task.isCompleted ? 'bg-success-50' : 'bg-gray-50'
              ]"
            >
              <el-icon
                :class="[
                  'mr-2 flex-shrink-0',
                  task.isCompleted ? 'text-success-500' : 'text-gray-300'
                ]"
              >
                <CircleCheck v-if="task.isCompleted" />
                <Circle v-else />
              </el-icon>
              <div class="flex-1 min-w-0">
                <span :class="['text-xs sm:text-sm break-words', task.isCompleted ? 'line-through text-gray-400' : 'text-gray-700']">
                  {{ task.taskContent }}
                </span>
                <span v-if="task.subject" class="text-xs text-gray-400 ml-2">[{{ task.subject }}]</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="showHistoryDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 授权码弹窗 -->
    <el-dialog
      v-model="showAuthCodeDialog"
      title="房间授权码"
      width="480px"
      class="mobile-dialog"
    >
      <div class="space-y-4">
        <el-alert type="warning" :closable="false" show-icon>
          <template #title>
            <span class="font-semibold">妥善保管授权码，不要泄露给他人！</span>
          </template>
          <template #default>
            <p class="text-sm mt-1">获得授权码的人，可以如同你本人亲临房间，直接处理一切。如需作废旧码，重新获取即可。</p>
          </template>
        </el-alert>

        <div class="bg-gray-50 rounded-xl p-4 text-center">
          <p class="text-gray-500 text-sm mb-2">您的授权码</p>
          <div class="text-lg sm:text-xl font-mono font-bold gradient-text break-all select-all">
            {{ authCode }}
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" plain @click="copyAuthCode" class="!rounded-xl">
          <el-icon class="mr-1"><CopyDocument /></el-icon> 复制授权码
        </el-button>
        <el-button @click="showAuthCodeDialog = false" class="!rounded-xl">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.custom-progress :deep(.el-progress-bar__outer) {
  border-radius: 10px;
  background: linear-gradient(90deg, #e0e7ff 0%, #ede9fe 100%);
}

.custom-progress :deep(.el-progress-bar__inner) {
  border-radius: 10px;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
}

.evidence-upload :deep(.el-upload--picture-card) {
  border-radius: 12px;
  border: 2px dashed #d1d5db;
  transition: all 0.3s ease;
}

.evidence-upload :deep(.el-upload--picture-card:hover) {
  border-color: #667eea;
  background: #f0f1fe;
}

.evidence-upload :deep(.el-upload-list__item) {
  border-radius: 12px;
}

/* 隐藏滚动条 */
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}
</style>

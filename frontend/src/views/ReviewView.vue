<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTodayTasks } from '@/api/task'
import { getTaskEvidence, reviewEvidence } from '@/api/evidence'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const roomCode = route.params.roomCode

const pendingReviews = ref([])
const loading = ref(false)
const currentEvidence = ref(null)
const showReviewDialog = ref(false)
const reviewForm = ref({
  result: 1,
  comment: '',
})

onMounted(async () => {
  await fetchPendingReviews()
})

async function fetchPendingReviews() {
  loading.value = true
  try {
    const tasksRes = await getTodayTasks()
    const tasks = tasksRes.data || []

    const allEvidence = []
    for (const task of tasks) {
      // 排除自己的任务（使用宽松比较，因为类型可能不一致）
      if (task.memberId == authStore.memberId) continue
      const evidenceRes = await getTaskEvidence(task.id)
      const evidence = evidenceRes.data || []
      evidence.forEach(e => {
        if (e.status === 0) { // 待审核
          allEvidence.push({
            ...e,
            taskContent: task.taskContent,
            subject: task.subject,
            displayName: task.displayName,
          })
        }
      })
    }
    pendingReviews.value = allEvidence
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function openReviewDialog(evidence) {
  currentEvidence.value = evidence
  reviewForm.value = { result: 1, comment: '' }
  showReviewDialog.value = true
}

async function handleReview() {
  loading.value = true
  try {
    await reviewEvidence(currentEvidence.value.id, reviewForm.value)
    ElMessage.success(reviewForm.value.result === 1 ? '已通过' : '已驳回')
    showReviewDialog.value = false
    await fetchPendingReviews()
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push(`/room/${roomCode}`)
}
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
    <!-- 顶部导航 -->
    <header class="glass-card mx-2 sm:mx-4 mt-2 sm:mt-4 px-3 sm:px-6 py-3 sm:py-4 sticky top-2 sm:top-4 z-50">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2 sm:gap-4">
          <el-button @click="goBack" class="!rounded-xl !px-2 sm:!px-4">
            <el-icon><Back /></el-icon>
            <span class="hidden sm:inline ml-1">返回</span>
          </el-button>
          <h1 class="text-base sm:text-xl font-bold gradient-text">证据审核</h1>
        </div>
        <el-tag type="warning" effect="plain" class="!rounded-lg">
          待审核: {{ pendingReviews.length }}
        </el-tag>
      </div>
    </header>

    <main class="max-w-5xl mx-auto px-2 sm:px-4 py-4 sm:py-6">
      <!-- 加载状态 -->
      <div v-if="loading" class="text-center py-12">
        <div class="loading-spinner mx-auto"></div>
        <p class="text-gray-400 mt-4">加载中...</p>
      </div>

      <!-- 空状态 -->
      <div v-else-if="pendingReviews.length === 0" class="glass-card p-12 text-center">
        <div class="w-20 h-20 mx-auto mb-4 rounded-full bg-success-50 flex items-center justify-center">
          <el-icon class="text-4xl text-success-400"><CircleCheck /></el-icon>
        </div>
        <p class="text-gray-600 font-medium mb-2">所有证据已审核完毕</p>
        <p class="text-gray-400 text-sm mb-6">暂时没有需要审核的学习证据</p>
        <el-button type="primary" @click="goBack" class="!rounded-xl">
          返回房间
        </el-button>
      </div>

      <!-- 证据列表 -->
      <div v-else class="space-y-3 sm:space-y-4">
        <div
          v-for="evidence in pendingReviews"
          :key="evidence.id"
          class="glass-card p-4 sm:p-5 hover-lift animate-fade-in"
        >
          <!-- 移动端：垂直布局 -->
          <div class="flex flex-col sm:flex-row sm:items-start gap-3 sm:gap-4">
            <!-- 证据图片 -->
            <div class="w-full sm:w-32 h-48 sm:h-32 rounded-xl overflow-hidden flex-shrink-0">
              <img
                :src="evidence.imageUrl"
                :alt="'学习证据'"
                class="w-full h-full object-cover"
              />
            </div>

            <!-- 证据信息 -->
            <div class="flex-1">
              <div class="flex items-center mb-2">
                <div class="w-8 h-8 rounded-full bg-gradient-to-br from-primary-400 to-secondary-400 flex items-center justify-center text-white text-sm font-bold mr-2">
                  {{ evidence.displayName?.charAt(0) || '?' }}
                </div>
                <span class="font-medium text-gray-700 text-sm sm:text-base">{{ evidence.displayName }}</span>
                <el-tag size="small" type="info" class="ml-2 !rounded-md">
                  {{ evidence.subject }}
                </el-tag>
              </div>

              <p class="text-gray-600 text-xs sm:text-sm mb-3">{{ evidence.taskContent }}</p>

              <div class="flex items-center justify-between">
                <span class="text-xs text-gray-400">
                  {{ new Date(evidence.uploadedAt).toLocaleString() }}
                </span>

                <el-button
                  type="primary"
                  @click="openReviewDialog(evidence)"
                  class="!rounded-xl"
                >
                  <el-icon class="mr-1"><Checked /></el-icon>
                  审核
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 审核对话框 -->
    <el-dialog
      v-model="showReviewDialog"
      title="审核学习证据"
      width="500px"
      class="mobile-dialog"
    >
      <div v-if="currentEvidence" class="space-y-4">
        <!-- 证据预览 -->
        <div class="rounded-xl overflow-hidden">
          <img
            :src="currentEvidence.imageUrl"
            :alt="'学习证据'"
            class="w-full h-64 object-contain bg-gray-100"
          />
        </div>

        <div class="p-4 bg-gray-50 rounded-xl">
          <div class="flex items-center mb-2">
            <span class="text-sm font-medium text-gray-700">{{ currentEvidence.displayName }}</span>
            <el-tag size="small" type="info" class="ml-2 !rounded-md">{{ currentEvidence.subject }}</el-tag>
          </div>
          <p class="text-sm text-gray-600">{{ currentEvidence.taskContent }}</p>
        </div>

        <!-- 审核选项 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-3">审核结果</label>
          <div class="flex space-x-4">
            <div
              @click="reviewForm.result = 1"
              :class="[
                'flex-1 p-4 rounded-xl cursor-pointer transition-all duration-300 border-2 text-center',
                reviewForm.result === 1
                  ? 'border-success-500 bg-success-50'
                  : 'border-gray-200 hover:border-gray-300'
              ]"
            >
              <el-icon class="text-2xl text-success-500 mb-1"><CircleCheck /></el-icon>
              <div class="font-medium text-gray-700">通过</div>
            </div>
            <div
              @click="reviewForm.result = 2"
              :class="[
                'flex-1 p-4 rounded-xl cursor-pointer transition-all duration-300 border-2 text-center',
                reviewForm.result === 2
                  ? 'border-danger-500 bg-danger-50'
                  : 'border-gray-200 hover:border-gray-300'
              ]"
            >
              <el-icon class="text-2xl text-danger-500 mb-1"><CircleClose /></el-icon>
              <div class="font-medium text-gray-700">驳回</div>
            </div>
          </div>
        </div>

        <!-- 审核备注 -->
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">审核备注（选填）</label>
          <el-input
            v-model="reviewForm.comment"
            type="textarea"
            :rows="3"
            :placeholder="reviewForm.result === 1 ? '可以添加鼓励的话...' : '请说明驳回原因...'"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="showReviewDialog = false">取消</el-button>
        <el-button
          :type="reviewForm.result === 1 ? 'success' : 'danger'"
          :loading="loading"
          @click="handleReview"
        >
          {{ reviewForm.result === 1 ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>


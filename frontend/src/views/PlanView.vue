<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPlanList, getMemberPlanList, createPlan, updatePlan, deletePlan } from '@/api/plan'

const route = useRoute()
const router = useRouter()
const roomCode = route.params.roomCode

// 判断是查看他人计划还是自己的计划
const viewMemberId = route.params.memberId
const memberName = route.query.name
const isViewOther = computed(() => !!viewMemberId)

const plans = ref([])
const loading = ref(false)
const activeType = ref('ALL')
const showCreateDialog = ref(false)

const planForm = ref({
  planType: 'WEEK',
  title: '',
  targetDate: '',
})

const planTypes = [
  { value: 'YEAR', label: '年目标', icon: 'Flag', color: 'from-red-400 to-pink-500' },
  { value: 'QUARTER', label: '季目标', icon: 'TrendCharts', color: 'from-orange-400 to-yellow-500' },
  { value: 'MONTH', label: '月目标', icon: 'Calendar', color: 'from-green-400 to-emerald-500' },
  { value: 'WEEK', label: '周目标', icon: 'Timer', color: 'from-blue-400 to-indigo-500' },
]

const filteredPlans = ref([])

onMounted(async () => {
  await fetchPlans()
})

async function fetchPlans() {
  loading.value = true
  try {
    const params = { type: activeType.value === 'ALL' ? undefined : activeType.value }
    const res = isViewOther.value
      ? await getMemberPlanList(viewMemberId, params)
      : await getPlanList(params)
    plans.value = res.data || []
    filteredPlans.value = plans.value
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function filterByType(type) {
  activeType.value = type
  if (type === 'ALL') {
    filteredPlans.value = plans.value
  } else {
    filteredPlans.value = plans.value.filter(p => p.planType === type)
  }
}

async function handleCreatePlan() {
  if (!planForm.value.title.trim()) {
    ElMessage.warning('请输入计划标题')
    return
  }
  if (!planForm.value.targetDate) {
    ElMessage.warning('请选择目标日期')
    return
  }
  loading.value = true
  try {
    const res = await createPlan(planForm.value)
    plans.value.unshift(res.data)
    showCreateDialog.value = false
    planForm.value = { planType: 'WEEK', title: '', targetDate: '' }
    ElMessage.success('计划创建成功')
    filterByType(activeType.value)
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

async function handleUpdatePlan(plan) {
  try {
    await updatePlan(plan.id, { status: plan.status === 0 ? 1 : 0 })
    plan.status = plan.status === 0 ? 1 : 0
    ElMessage.success(plan.status === 1 ? '计划已完成' : '计划已重新开始')
  } catch (error) {
    // 错误已在拦截器中处理
  }
}

async function handleDeletePlan(id) {
  try {
    await deletePlan(id)
    plans.value = plans.value.filter(p => p.id !== id)
    filterByType(activeType.value)
    ElMessage.success('计划已删除')
  } catch (error) {
    // 错误已在拦截器中处理
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
          <h1 class="text-base sm:text-xl font-bold gradient-text">{{ isViewOther ? `${memberName} 的计划` : '计划管理' }}</h1>
        </div>
        <el-button v-if="!isViewOther" type="primary" @click="showCreateDialog = true" class="!rounded-xl !px-3 sm:!px-4">
          <el-icon class="mr-1"><Plus /></el-icon>
          <span class="hidden sm:inline">创建计划</span>
        </el-button>
      </div>
    </header>

    <main class="max-w-5xl mx-auto px-2 sm:px-4 py-4 sm:py-6">
      <!-- 计划类型筛选 -->
      <div class="glass-card p-3 sm:p-4 mb-4 sm:mb-6">
        <div class="flex overflow-x-auto gap-2 sm:gap-3 pb-1 scrollbar-hide">
          <button
            @click="filterByType('ALL')"
            :class="[
              'px-3 sm:px-4 py-2 rounded-xl text-xs sm:text-sm font-medium transition-all duration-300 whitespace-nowrap flex-shrink-0',
              activeType === 'ALL'
                ? 'bg-gradient-to-r from-primary-500 to-secondary-500 text-white shadow-lg'
                : 'bg-white text-gray-600 hover:bg-gray-50'
            ]"
          >
            全部
          </button>
          <button
            v-for="type in planTypes"
            :key="type.value"
            @click="filterByType(type.value)"
            :class="[
              'px-3 sm:px-4 py-2 rounded-xl text-xs sm:text-sm font-medium transition-all duration-300 whitespace-nowrap flex-shrink-0',
              activeType === type.value
                ? `bg-gradient-to-r ${type.color} text-white shadow-lg`
                : 'bg-white text-gray-600 hover:bg-gray-50'
            ]"
          >
            {{ type.label }}
          </button>
        </div>
      </div>

      <!-- 计划列表 -->
      <div v-if="loading" class="text-center py-12">
        <div class="loading-spinner mx-auto"></div>
        <p class="text-gray-400 mt-4">加载中...</p>
      </div>

      <div v-else-if="filteredPlans.length === 0" class="glass-card p-12 text-center">
        <div class="w-20 h-20 mx-auto mb-4 rounded-full bg-gray-100 flex items-center justify-center">
          <el-icon class="text-4xl text-gray-300"><Calendar /></el-icon>
        </div>
        <p class="text-gray-400 mb-4">{{ isViewOther ? '该成员还没有学习计划' : '还没有学习计划' }}</p>
        <el-button v-if="!isViewOther" type="primary" @click="showCreateDialog = true" class="!rounded-xl">
          创建第一个计划
        </el-button>
      </div>

      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-3 sm:gap-4">
        <div
          v-for="plan in filteredPlans"
          :key="plan.id"
          class="glass-card p-4 sm:p-5 hover-lift animate-fade-in"
        >
          <div class="flex items-start justify-between mb-3">
            <div class="flex items-center">
              <div
                :class="[
                  'w-10 h-10 rounded-xl flex items-center justify-center text-white mr-3',
                  `bg-gradient-to-br ${planTypes.find(t => t.value === plan.planType)?.color || 'from-gray-400 to-gray-500'}`
                ]"
              >
                <el-icon>
                  <component :is="planTypes.find(t => t.value === plan.planType)?.icon || 'Document'" />
                </el-icon>
              </div>
              <div>
                <el-tag
                  :type="plan.planType === 'YEAR' ? 'danger' : plan.planType === 'QUARTER' ? 'warning' : plan.planType === 'MONTH' ? 'success' : 'primary'"
                  effect="plain"
                  class="!rounded-md mb-1"
                >
                  {{ planTypes.find(t => t.value === plan.planType)?.label }}
                </el-tag>
                <div class="text-sm text-gray-500">
                  目标日期: {{ plan.targetDate }}
                </div>
              </div>
            </div>

            <el-dropdown v-if="!isViewOther">
              <el-button class="!rounded-lg">
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleUpdatePlan(plan)">
                    <el-icon><Check /></el-icon>
                    {{ plan.status === 0 ? '标记完成' : '重新开始' }}
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleDeletePlan(plan.id)" divided>
                    <el-icon><Delete /></el-icon>
                    删除计划
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>

          <h3 :class="['font-semibold text-lg mb-2', plan.status === 1 ? 'text-gray-400 line-through' : 'text-gray-800']">
            {{ plan.title }}
          </h3>

          <div class="flex items-center justify-between">
            <el-tag
              :type="plan.status === 1 ? 'success' : 'info'"
              effect="plain"
              class="!rounded-md"
            >
              {{ plan.status === 1 ? '已完成' : '进行中' }}
            </el-tag>
            <span class="text-xs text-gray-400">
              {{ new Date(plan.createdAt).toLocaleDateString() }}
            </span>
          </div>
        </div>
      </div>
    </main>

    <!-- 创建计划对话框 -->
    <el-dialog
      v-model="showCreateDialog"
      title="创建学习计划"
      width="500px"
      class="mobile-dialog"
    >
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-3">计划类型</label>
          <div class="grid grid-cols-2 gap-3">
            <div
              v-for="type in planTypes"
              :key="type.value"
              @click="planForm.planType = type.value"
              :class="[
                'p-4 rounded-xl cursor-pointer transition-all duration-300 border-2',
                planForm.planType === type.value
                  ? 'border-primary-500 bg-primary-50'
                  : 'border-gray-200 hover:border-gray-300'
              ]"
            >
              <div class="flex items-center">
                <div
                  :class="[
                    'w-8 h-8 rounded-lg flex items-center justify-center text-white mr-2',
                    `bg-gradient-to-br ${type.color}`
                  ]"
                >
                  <el-icon size="16">
                    <component :is="type.icon" />
                  </el-icon>
                </div>
                <span class="font-medium text-gray-700">{{ type.label }}</span>
              </div>
            </div>
          </div>
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">计划标题</label>
          <el-input
            v-model="planForm.title"
            placeholder="例如：掌握 Vue 3 核心概念"
            size="large"
          />
        </div>

        <div>
          <label class="block text-sm font-medium text-gray-700 mb-2">目标日期</label>
          <el-date-picker
            v-model="planForm.targetDate"
            type="date"
            placeholder="选择目标日期"
            size="large"
            class="w-full"
            value-format="YYYY-MM-DD"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleCreatePlan">
          创建计划
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}
</style>

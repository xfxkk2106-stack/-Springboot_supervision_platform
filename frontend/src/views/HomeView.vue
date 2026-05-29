<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createRoom, joinRoom } from '@/api/room'
import { verifyToken, useAuthCode } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { validateRoomCode } from '@/utils/roomCode'
import { copyToClipboard } from '@/utils/clipboard'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const activeTab = ref('join')
const checkingAuth = ref(true)

// 加入房间表单
const joinForm = reactive({
  displayName: '',
})

// 创建房间表单
const createForm = reactive({
  displayName: '',
})

const loading = ref(false)
const showInviteDialog = ref(false)
const inviteCode = ref('')

// 授权码相关
const showAuthCodeDialog = ref(false)
const authCodeInput = ref('')
const authCodeLoading = ref(false)

// 检查 URL 中是否有邀请码
const urlInviteCode = route.query.invite
if (urlInviteCode) {
  activeTab.value = 'join'
  inviteCode.value = urlInviteCode.toUpperCase()
}

// 页面加载时通过 Cookie 验证用户身份
onMounted(async () => {
  try {
    const res = await verifyToken()
    if (res.data) {
      // 用户已认证
      authStore.setAuth(res.data)
      if (res.data.inRoom && res.data.roomCode) {
        // 自动跳转到房间
        router.replace(`/room/${res.data.roomCode}`)
        return
      }
    }
  } catch (error) {
    // 未认证或 token 失效，显示首页
    authStore.clearAuth()
  }
  checkingAuth.value = false
})

// 加入房间
async function handleJoin() {
  if (!validateRoomCode(inviteCode.value)) {
    ElMessage.warning('请输入7位邀请码')
    return
  }
  if (!joinForm.displayName.trim()) {
    ElMessage.warning('请输入您的自定义名称')
    return
  }

  loading.value = true
  try {
    const res = await joinRoom({
      roomCode: inviteCode.value.toUpperCase(),
      displayName: joinForm.displayName.trim(),
    })
    authStore.setAuth(res.data)
    ElMessage.success('加入房间成功')
    router.push(`/room/${res.data.roomCode}`)
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

// 创建房间
async function handleCreate() {
  if (!createForm.displayName.trim()) {
    ElMessage.warning('请输入您的自定义名称')
    return
  }

  loading.value = true
  try {
    const res = await createRoom({
      displayName: createForm.displayName.trim(),
    })
    authStore.setAuth(res.data)
    // 显示邀请码
    inviteCode.value = res.data.roomCode
    showInviteDialog.value = true
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

// 复制邀请码
async function copyInviteCode() {
  const success = await copyToClipboard(inviteCode.value)
  if (success) {
    ElMessage.success('邀请码已复制')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

// 复制邀请链接
async function copyInviteLink() {
  const link = `${window.location.origin}?invite=${inviteCode.value}`
  const success = await copyToClipboard(link)
  if (success) {
    ElMessage.success('邀请链接已复制')
  } else {
    ElMessage.error('复制失败，请手动复制')
  }
}

// 进入房间
function enterRoom() {
  showInviteDialog.value = false
  router.push(`/room/${inviteCode.value}`)
}

// 使用授权码
async function handleUseAuthCode() {
  if (!authCodeInput.value.trim()) {
    ElMessage.warning('请输入授权码')
    return
  }

  authCodeLoading.value = true
  try {
    const res = await useAuthCode(authCodeInput.value.trim())
    authStore.setAuth(res.data)
    ElMessage.success('授权码验证成功')
    showAuthCodeDialog.value = false
    router.push(`/room/${res.data.roomCode}`)
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    authCodeLoading.value = false
  }
}
</script>

<template>
  <!-- 检查认证状态中 -->
  <div v-if="checkingAuth" class="min-h-screen flex items-center justify-center">
    <div class="text-center">
      <div class="loading-spinner mx-auto mb-4"></div>
      <p class="text-gray-400">正在加载...</p>
    </div>
  </div>

  <!-- 主页面 -->
  <div v-else class="min-h-screen flex items-center justify-center p-4 relative overflow-hidden">
    <!-- 背景装饰 -->
    <div class="absolute inset-0 overflow-hidden">
      <div class="absolute -top-40 -right-40 w-80 h-80 bg-purple-300 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-float"></div>
      <div class="absolute -bottom-40 -left-40 w-80 h-80 bg-yellow-300 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-float" style="animation-delay: 2s;"></div>
      <div class="absolute top-40 left-40 w-80 h-80 bg-pink-300 rounded-full mix-blend-multiply filter blur-xl opacity-70 animate-float" style="animation-delay: 4s;"></div>
    </div>

    <!-- 主卡片 -->
    <div class="relative z-10 w-full max-w-md">
      <!-- 标题 -->
      <div class="text-center mb-6 sm:mb-8 animate-fade-in">
        <h1 class="text-3xl sm:text-4xl font-bold gradient-text mb-2">互相监督平台</h1>
        <p class="text-gray-500 text-base sm:text-lg">一起学习，共同进步</p>
      </div>

      <!-- 选项卡 -->
      <div class="glass-card p-4 sm:p-6 animate-slide-up">
        <div class="flex bg-gray-100 rounded-2xl p-1 mb-4 sm:mb-6">
          <button
            @click="activeTab = 'join'"
            :class="[
              'flex-1 py-2.5 sm:py-3 rounded-xl text-xs sm:text-sm font-semibold transition-all duration-300',
              activeTab === 'join'
                ? 'bg-white text-gray-800 shadow-lg'
                : 'text-gray-500 hover:text-gray-700'
            ]"
          >
            加入房间
          </button>
          <button
            @click="activeTab = 'create'"
            :class="[
              'flex-1 py-2.5 sm:py-3 rounded-xl text-xs sm:text-sm font-semibold transition-all duration-300',
              activeTab === 'create'
                ? 'bg-white text-gray-800 shadow-lg'
                : 'text-gray-500 hover:text-gray-700'
            ]"
          >
            创建房间
          </button>
        </div>

        <!-- 加入房间表单 -->
        <div v-if="activeTab === 'join'" class="space-y-3 sm:space-y-4 animate-fade-in">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">邀请码</label>
            <el-input
              v-model="inviteCode"
              placeholder="请输入7位邀请码"
              size="large"
              maxlength="7"
              class="custom-input"
            >
              <template #prefix>
                <el-icon><Key /></el-icon>
              </template>
            </el-input>
          </div>

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">您的名称</label>
            <el-input
              v-model="joinForm.displayName"
              placeholder="请输入您在房间中显示的名称"
              size="large"
              maxlength="20"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </div>

          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleJoin"
            class="w-full !h-11 sm:!h-12 !text-sm sm:!text-base !font-semibold !rounded-xl"
          >
            加入房间
          </el-button>
        </div>

        <!-- 创建房间表单 -->
        <div v-else class="space-y-3 sm:space-y-4 animate-fade-in">
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-2">您的名称</label>
            <el-input
              v-model="createForm.displayName"
              placeholder="请输入您在房间中显示的名称"
              size="large"
              maxlength="20"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </div>

          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleCreate"
            class="w-full !h-11 sm:!h-12 !text-sm sm:!text-base !font-semibold !rounded-xl"
          >
            创建房间
          </el-button>
        </div>

        <!-- 授权码入口 -->
        <div class="mt-4 pt-4 border-t border-gray-100 text-center">
          <el-button link type="primary" @click="showAuthCodeDialog = true">
            使用授权码加入
          </el-button>
        </div>
      </div>

      <!-- 底部提示 -->
      <p class="text-center text-gray-400 text-xs sm:text-sm mt-4 sm:mt-6 animate-fade-in">
        互相监督，让学习更有动力
      </p>
    </div>

    <!-- 邀请码弹窗 -->
    <el-dialog
      v-model="showInviteDialog"
      title="房间创建成功"
      width="450px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      class="mobile-dialog"
    >
      <div class="text-center space-y-4 sm:space-y-6">
        <div class="w-14 h-14 sm:w-16 sm:h-16 mx-auto rounded-full bg-success-50 flex items-center justify-center">
          <el-icon class="text-2xl sm:text-3xl text-success-500"><CircleCheck /></el-icon>
        </div>

        <div>
          <p class="text-gray-500 mb-2 text-sm sm:text-base">您的邀请码</p>
          <div class="text-3xl sm:text-4xl font-bold font-mono tracking-widest gradient-text">
            {{ inviteCode }}
          </div>
        </div>

        <div class="flex gap-2 sm:gap-3">
          <el-button
            type="primary"
            plain
            @click="copyInviteCode"
            class="flex-1 !rounded-xl"
          >
            <el-icon class="mr-1"><CopyDocument /></el-icon>
            复制邀请码
          </el-button>
          <el-button
            type="success"
            plain
            @click="copyInviteLink"
            class="flex-1 !rounded-xl"
          >
            <el-icon class="mr-1"><Link /></el-icon>
            复制链接
          </el-button>
        </div>

        <el-alert
          title="分享邀请码给好友，即可一起加入房间学习"
          type="info"
          :closable="false"
          show-icon
          class="!rounded-xl"
        />
      </div>
      <template #footer>
        <el-button type="primary" @click="enterRoom" class="!rounded-xl w-full sm:w-auto">
          进入房间
        </el-button>
      </template>
    </el-dialog>

    <!-- 授权码弹窗 -->
    <el-dialog
      v-model="showAuthCodeDialog"
      title="使用授权码加入"
      width="450px"
      class="mobile-dialog"
    >
      <div class="space-y-4">
        <p class="text-gray-500 text-sm">输入房间成员分享的授权码，即可直接加入房间。</p>
        <el-input
          v-model="authCodeInput"
          placeholder="请输入授权码"
          size="large"
          class="custom-input"
        >
          <template #prefix>
            <el-icon><Key /></el-icon>
          </template>
        </el-input>
      </div>
      <template #footer>
        <el-button @click="showAuthCodeDialog = false" class="!rounded-xl">取消</el-button>
        <el-button type="primary" :loading="authCodeLoading" @click="handleUseAuthCode" class="!rounded-xl">
          确认加入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.custom-input :deep(.el-input__wrapper) {
  border-radius: 12px;
}
</style>

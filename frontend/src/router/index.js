import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/room/:roomCode',
      name: 'room',
      component: () => import('@/views/RoomView.vue'),
      props: true,
      meta: { requiresAuth: true },
    },
    {
      path: '/room/:roomCode/plan',
      name: 'plan',
      component: () => import('@/views/PlanView.vue'),
      props: true,
      meta: { requiresAuth: true },
    },
    {
      path: '/room/:roomCode/plan/:memberId',
      name: 'memberPlan',
      component: () => import('@/views/PlanView.vue'),
      props: true,
      meta: { requiresAuth: true },
    },
    {
      path: '/room/:roomCode/review',
      name: 'review',
      component: () => import('@/views/ReviewView.vue'),
      props: true,
      meta: { requiresAuth: true },
    },
  ],
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const roomCode = localStorage.getItem('roomCode')

  // 需要认证的页面
  if (to.meta.requiresAuth) {
    if (!token) {
      // 未登录，跳转到首页
      next('/')
      return
    }

    // 检查是否访问的是当前房间的页面
    if (to.params.roomCode && roomCode && to.params.roomCode.toUpperCase() !== roomCode.toUpperCase()) {
      // 访问的不是当前房间，跳转到首页
      next('/')
      return
    }
  }

  // 已登录用户访问首页时，由 HomeView 处理跳转
  next()
})

export default router

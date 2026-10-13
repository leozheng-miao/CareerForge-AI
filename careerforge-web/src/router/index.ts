import { createRouter, createWebHistory } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import AuthLayout from '@/layouts/AuthLayout.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'

import { ROUTE_PATHS } from './paths'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: PublicLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: '水水面｜技术面试练习' },
        },
        {
          path: 'terms',
          name: 'terms',
          component: () => import('@/views/TermsView.vue'),
          meta: { title: '用户协议｜水水面' },
        },
        {
          path: 'privacy',
          name: 'privacy',
          component: () => import('@/views/PrivacyView.vue'),
          meta: { title: '隐私政策｜水水面' },
        },
      ],
    },
    {
      path: '/login',
      component: AuthLayout,
      children: [
        {
          path: '',
          name: 'login',
          component: () => import('@/views/auth/LoginView.vue'),
          meta: { title: '登录｜水水面', guestOnly: true },
        },
      ],
    },
    {
      path: '/register',
      component: AuthLayout,
      children: [
        {
          path: '',
          name: 'register',
          component: () => import('@/views/auth/RegisterView.vue'),
          meta: { title: '注册｜水水面', guestOnly: true },
        },
      ],
    },
    {
      path: '/app',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'dashboard',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'target-role',
          name: 'target-role',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'skill-gaps',
          name: 'skill-gaps',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'memories',
          name: 'memories',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'training-plans',
          name: 'training-plans',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'training-plans/:planId',
          name: 'training-plan-detail',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'coach',
          name: 'coach',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'coach/:sessionId',
          name: 'coach-session',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'evidence',
          name: 'evidence',
          redirect: ROUTE_PATHS.interviews,
          meta: { requiresAuth: true },
        },
        {
          path: 'interviews',
          name: 'interviews',
          component: () => import('@/views/app/InterviewsView.vue'),
          meta: { title: '开始面试', requiresAuth: true },
        },
        {
          path: 'interviews/comprehensive/setup',
          name: 'comprehensive-interview-setup',
          component: () => import('@/views/app/ComprehensiveInterviewSetupView.vue'),
          meta: { title: '综合面试设置', requiresAuth: true, immersive: true },
        },
        {
          path: 'interviews/:interviewId/ready',
          name: 'interview-ready',
          component: () => import('@/views/app/InterviewReadyView.vue'),
          meta: { title: '面试就绪', requiresAuth: true, immersive: true },
        },
        {
          path: 'interviews/:interviewId/session',
          name: 'interview-session',
          component: () => import('@/views/app/InterviewDetailView.vue'),
          meta: { title: '快速面试', requiresAuth: true, immersive: true },
        },
        {
          path: 'interviews/:interviewId/report',
          name: 'interview-report',
          component: () => import('@/views/app/InterviewReportView.vue'),
          meta: { title: '面试报告', requiresAuth: true },
        },
        {
          path: 'interview-records',
          name: 'interview-records',
          component: () => import('@/views/app/InterviewRecordsView.vue'),
          meta: { title: '面试记录', requiresAuth: true },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面未找到｜水水面' },
    },
  ],
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }

    if (to.hash) {
      return {
        el: to.hash,
        top: 88,
        behavior: 'smooth',
      }
    }

    return { top: 0 }
  },
})

router.afterEach((to) => {
  document.title = typeof to.meta.title === 'string'
    ? to.meta.title
    : '水水面'
})

export default router

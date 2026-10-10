import { createRouter, createWebHistory } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import PublicLayout from '@/layouts/PublicLayout.vue'

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
          meta: { title: 'CareerForge-AI｜证据驱动职业训练' },
        },
        {
          path: 'login',
          name: 'login',
          component: () => import('@/views/auth/LoginView.vue'),
          meta: { title: '登录｜CareerForge-AI', guestOnly: true },
        },
        {
          path: 'register',
          name: 'register',
          component: () => import('@/views/auth/RegisterView.vue'),
          meta: { title: '注册｜CareerForge-AI', guestOnly: true },
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
          component: () => import('@/views/app/DashboardView.vue'),
          meta: { title: '成长总览', requiresAuth: true },
        },
        {
          path: 'target-role',
          name: 'target-role',
          component: () => import('@/views/app/TargetRoleView.vue'),
          meta: { title: '目标岗位', requiresAuth: true },
        },
        {
          path: 'skill-gaps',
          name: 'skill-gaps',
          component: () => import('@/views/app/SkillGapsView.vue'),
          meta: { title: '能力差距', requiresAuth: true },
        },
        {
          path: 'memories',
          name: 'memories',
          component: () => import('@/views/app/MemoriesView.vue'),
          meta: { title: '长期记忆', requiresAuth: true },
        },
        {
          path: 'training-plans',
          name: 'training-plans',
          component: () => import('@/views/app/TrainingPlansView.vue'),
          meta: { title: '训练计划', requiresAuth: true },
        },
        {
          path: 'training-plans/:planId',
          name: 'training-plan-detail',
          component: () => import('@/views/app/TrainingPlanDetailView.vue'),
          meta: { title: '训练计划详情', requiresAuth: true },
        },
        {
          path: 'coach',
          name: 'coach',
          component: () => import('@/views/app/CoachSessionsView.vue'),
          meta: { title: 'Career Coach', requiresAuth: true },
        },
        {
          path: 'coach/:sessionId',
          name: 'coach-session',
          component: () => import('@/views/app/CoachSessionDetailView.vue'),
          meta: { title: 'Career Coach 对话', requiresAuth: true },
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/app/KnowledgeView.vue'),
          meta: { title: '知识问答', requiresAuth: true },
        },
        {
          path: 'evidence',
          name: 'evidence',
          component: () => import('@/views/app/EvidenceView.vue'),
          meta: { title: '个人证据', requiresAuth: true },
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
          meta: { title: '综合面试设置', requiresAuth: true },
        },
        {
          path: 'interviews/:interviewId/ready',
          name: 'interview-ready',
          component: () => import('@/views/app/InterviewReadyView.vue'),
          meta: { title: '面试就绪', requiresAuth: true },
        },
        {
          path: 'interviews/:interviewId/session',
          name: 'interview-session',
          component: () => import('@/views/app/InterviewDetailView.vue'),
          meta: { title: '快速面试', requiresAuth: true },
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
    : 'CareerForge-AI'
})

export default router

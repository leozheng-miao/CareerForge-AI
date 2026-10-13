<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  DownOutlined,
  HistoryOutlined,
  LogoutOutlined,
  MenuOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { Avatar, Button, Drawer } from 'ant-design-vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import BrandLogo from '@/components/brand/BrandLogo.vue'
import { useAuthStore } from '@/features/auth/store/auth-store'
import { useInterviewFlowStore } from '@/features/interview/store/interview-flow-store'
import { ROUTE_PATHS } from '@/router/paths'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const interviewStore = useInterviewFlowStore()
const mobileNavigationOpen = ref(false)
const loggingOut = ref(false)
const immersive = computed(() => route.meta.immersive === true)
const HEARTBEAT_INTERVAL_MS = 10_000
let heartbeatTimer: number | undefined

const navigation = [
  { label: '开始面试', to: ROUTE_PATHS.interviews, routeName: 'interviews', icon: RobotOutlined },
  {
    label: '面试记录',
    to: ROUTE_PATHS.interviewRecords,
    routeName: 'interview-records',
    icon: HistoryOutlined,
  },
] as const

const pageTitle = computed(() => String(route.meta.title ?? '面试中心'))
const accountName = computed(() => authStore.currentUser?.displayName || '当前账户')

function isActive(path: string) {
  if (path === ROUTE_PATHS.app) {
    return route.path === path
  }

  return route.path === path || route.path.startsWith(`${path}/`)
}

function closeMobileNavigation() {
  mobileNavigationOpen.value = false
}

async function logout() {
  loggingOut.value = true
  try {
    try {
      await interviewStore.pauseActiveBeforeLogout()
    } catch {
      // Redis活动租约仍会在退出后将运行中的面试安全收敛为暂停。
    }
    await authStore.logout()
  } catch {
    // 即使服务端退出暂时失败，本地会话也已由 auth store 清除。
  } finally {
    loggingOut.value = false
    await router.replace(ROUTE_PATHS.login)
  }
}

function isRouteAvailable(routeName: string) {
  return router.hasRoute(routeName)
}

function synchronizeHeartbeat() {
  window.clearInterval(heartbeatTimer)
  heartbeatTimer = undefined
  if (
    interviewStore.session?.stage !== 'INTERVIEWING' ||
    interviewStore.timer?.timerState !== 'RUNNING' ||
    interviewStore.waiting
  ) {
    return
  }
  heartbeatTimer = window.setInterval(() => {
    void interviewStore.heartbeat().catch(() => undefined)
  }, HEARTBEAT_INTERVAL_MS)
}

function pauseForPageExit() {
  interviewStore.pauseForPageExit()
}

const stopHeartbeatWatch = watch(
  () => [
    interviewStore.session?.stage,
    interviewStore.timer?.timerState,
    interviewStore.waiting,
  ],
  synchronizeHeartbeat,
  { immediate: true },
)

onMounted(() => window.addEventListener('pagehide', pauseForPageExit))
onBeforeUnmount(() => {
  window.removeEventListener('pagehide', pauseForPageExit)
  window.clearInterval(heartbeatTimer)
  stopHeartbeatWatch()
})
</script>

<template>
  <div class="app-layout" :class="{ 'app-layout--immersive': immersive }">
    <aside v-if="!immersive" class="app-sidebar" aria-label="面试中心导航">
      <div class="app-sidebar__brand">
        <BrandLogo />
      </div>
      <nav class="app-navigation">
        <template v-for="item in navigation" :key="item.to">
          <RouterLink
            v-if="isRouteAvailable(item.routeName)"
            class="app-navigation__item"
            :class="{ 'app-navigation__item--active': isActive(item.to) }"
            :to="item.to"
          >
            <component :is="item.icon" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </RouterLink>
          <span
            v-else
            class="app-navigation__item app-navigation__item--disabled"
            aria-disabled="true"
          >
            <component :is="item.icon" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </span>
        </template>
      </nav>
    </aside>

    <div class="app-layout__body">
      <header v-if="!immersive" class="app-header">
        <Button
          class="app-header__menu-button"
          type="text"
          aria-label="打开面试中心导航"
          @click="mobileNavigationOpen = true"
        >
          <MenuOutlined />
        </Button>
        <h1 class="app-header__title">{{ pageTitle }}</h1>
        <details class="app-account-menu">
          <summary aria-label="打开账户菜单">
            <Avatar class="app-header__avatar" :aria-label="accountName">
              <template #icon><UserOutlined /></template>
            </Avatar>
            <span class="app-header__account-name">{{ accountName }}</span>
            <DownOutlined class="app-account-menu__chevron" aria-hidden="true" />
          </summary>
          <div class="app-account-menu__panel">
            <div class="app-account-menu__identity">
              <strong>{{ accountName }}</strong>
              <span>{{ authStore.currentUser?.email || '已登录账户' }}</span>
            </div>
            <Button
              type="text"
              danger
              block
              aria-label="退出登录"
              :loading="loggingOut"
              @click="logout"
            >
              <LogoutOutlined />
              退出登录
            </Button>
          </div>
        </details>
      </header>

      <main class="app-content" :class="{ 'app-content--immersive': immersive }">
        <slot>
          <RouterView />
        </slot>
      </main>
    </div>

    <Drawer
      v-if="!immersive"
      v-model:open="mobileNavigationOpen"
      placement="left"
      :width="280"
      :closable="false"
      :body-style="{ padding: 0 }"
      title="面试中心导航"
    >
      <div class="app-drawer__brand">
        <BrandLogo />
      </div>
      <nav class="app-navigation" aria-label="移动端面试中心导航">
        <template v-for="item in navigation" :key="item.to">
          <RouterLink
            v-if="isRouteAvailable(item.routeName)"
            class="app-navigation__item"
            :class="{ 'app-navigation__item--active': isActive(item.to) }"
            :to="item.to"
            @click="closeMobileNavigation"
          >
            <component :is="item.icon" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </RouterLink>
          <span
            v-else
            class="app-navigation__item app-navigation__item--disabled"
            aria-disabled="true"
          >
            <component :is="item.icon" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </span>
        </template>
      </nav>
    </Drawer>
  </div>
</template>

<style scoped>
.app-layout {
  display: grid;
  min-height: 100vh;
  grid-template-columns: var(--cf-app-sidebar-width) minmax(0, 1fr);
  background: var(--cf-color-background);
}

.app-layout--immersive {
  display: block;
}

.app-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
  border-right: 1px solid var(--cf-color-border);
  background: var(--cf-color-surface);
}

.app-sidebar__brand,
.app-drawer__brand {
  display: flex;
  min-height: var(--cf-app-header-height);
  align-items: center;
  padding: 0 var(--cf-space-6);
  border-bottom: 1px solid var(--cf-color-border);
}

.app-navigation {
  display: grid;
  gap: var(--cf-space-1);
  padding: var(--cf-space-4) var(--cf-space-3);
}

.app-navigation__item {
  display: flex;
  min-height: 44px;
  align-items: center;
  gap: var(--cf-space-3);
  padding: 0 var(--cf-space-3);
  border-radius: var(--cf-radius-control);
  color: var(--cf-color-text-secondary);
  font-weight: 550;
  transition:
    color var(--cf-motion-fast) ease,
    background var(--cf-motion-fast) ease;
}

.app-navigation__item:hover {
  background: var(--ssm-color-brand-soft);
  color: var(--cf-color-primary);
}

.app-navigation__item--active {
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
  font-weight: 650;
}

.app-navigation__item--disabled {
  color: var(--ssm-color-text-muted);
  cursor: not-allowed;
  opacity: 0.58;
}

.app-layout__body {
  min-width: 0;
}

.app-header {
  position: sticky;
  z-index: 90;
  top: 0;
  display: flex;
  min-height: var(--cf-app-header-height);
  align-items: center;
  gap: var(--cf-space-3);
  padding: 0 var(--cf-space-8);
  border-bottom: 1px solid var(--cf-color-border);
  background: var(--ssm-color-surface);
}

.app-header__title {
  flex: 1;
  margin: 0;
  font-size: 18px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.app-header__avatar {
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
}

.app-header__account-name {
  max-width: 180px;
  overflow: hidden;
  color: var(--cf-color-text-secondary);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-account-menu {
  position: relative;
}

.app-account-menu > summary {
  display: flex;
  min-height: 42px;
  align-items: center;
  gap: var(--cf-space-2);
  padding: 4px 8px;
  border-radius: var(--ssm-radius-control);
  cursor: pointer;
  list-style: none;
  transition: background var(--ssm-motion-fast) ease;
}

.app-account-menu > summary::-webkit-details-marker {
  display: none;
}

.app-account-menu > summary:hover,
.app-account-menu[open] > summary {
  background: var(--ssm-color-brand-soft);
}

.app-account-menu > summary:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--ssm-color-brand) 30%, transparent);
  outline-offset: 2px;
}

.app-account-menu__chevron {
  color: var(--ssm-color-text-secondary);
  font-size: 11px;
  transition: transform var(--ssm-motion-fast) ease;
}

.app-account-menu[open] .app-account-menu__chevron {
  transform: rotate(180deg);
}

.app-account-menu__panel {
  position: absolute;
  z-index: 110;
  top: calc(100% + 8px);
  right: 0;
  width: 260px;
  padding: 10px;
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-card);
  background: var(--ssm-color-surface);
  box-shadow: var(--ssm-shadow-panel);
}

.app-account-menu__identity {
  display: grid;
  gap: 4px;
  padding: 10px 12px 14px;
  border-bottom: 1px solid var(--ssm-color-line);
  margin-bottom: 6px;
}

.app-account-menu__identity strong,
.app-account-menu__identity span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-account-menu__identity span {
  color: var(--ssm-color-text-secondary);
  font-size: 13px;
}

.app-header__menu-button {
  display: none;
}

.app-content {
  width: min(calc(100% - 64px), var(--cf-content-width));
  min-height: calc(100vh - var(--cf-app-header-height));
  margin-inline: auto;
  padding: var(--cf-space-8) 0 var(--cf-space-12);
}

.app-content--immersive {
  width: 100%;
  min-height: 100vh;
  padding: var(--cf-space-6)
    max(var(--cf-space-6), calc((100vw - var(--cf-content-width)) / 2));
}

@media (max-width: 1023px) {
  .app-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .app-sidebar {
    display: none;
  }

  .app-header__menu-button {
    display: inline-flex;
  }
}

@media (max-width: 767px) {
  .app-header {
    padding: 0 var(--cf-space-4);
  }

  .app-content {
    width: min(calc(100% - 32px), var(--cf-content-width));
    padding-top: var(--cf-space-6);
  }

  .app-content--immersive {
    width: 100%;
    padding: var(--cf-space-4);
  }

  .app-header__account-name {
    display: none;
  }
}
</style>

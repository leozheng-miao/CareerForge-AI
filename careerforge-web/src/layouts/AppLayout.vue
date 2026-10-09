<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  BookOutlined,
  BulbOutlined,
  DatabaseOutlined,
  FileDoneOutlined,
  HomeOutlined,
  MenuOutlined,
  MessageOutlined,
  ReadOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import { Avatar, Button, Drawer } from 'ant-design-vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import BrandLogo from '@/components/brand/BrandLogo.vue'
import { ROUTE_PATHS } from '@/router/paths'

const route = useRoute()
const router = useRouter()
const mobileNavigationOpen = ref(false)

const navigation = [
  { label: '成长总览', to: ROUTE_PATHS.app, routeName: 'dashboard', icon: HomeOutlined },
  { label: '目标岗位', to: ROUTE_PATHS.targetRole, routeName: 'target-role', icon: ReadOutlined },
  { label: '能力差距', to: ROUTE_PATHS.skillGaps, routeName: 'skill-gaps', icon: BulbOutlined },
  {
    label: '训练计划',
    to: ROUTE_PATHS.trainingPlans,
    routeName: 'training-plans',
    icon: FileDoneOutlined,
  },
  { label: 'Career Coach', to: ROUTE_PATHS.coach, routeName: 'coach', icon: MessageOutlined },
  { label: '知识问答', to: ROUTE_PATHS.knowledge, routeName: 'knowledge', icon: BookOutlined },
  { label: '长期记忆', to: ROUTE_PATHS.memories, routeName: 'memories', icon: DatabaseOutlined },
  { label: '个人证据', to: ROUTE_PATHS.evidence, routeName: 'evidence', icon: UserOutlined },
  { label: '开始面试', to: ROUTE_PATHS.interviews, routeName: 'interviews', icon: RobotOutlined },
  { label: '面试记录', to: ROUTE_PATHS.interviewRecords, routeName: 'interview-records', icon: RobotOutlined },
] as const

const pageTitle = computed(() => String(route.meta.title ?? '成长工作台'))

function isActive(path: string) {
  if (path === ROUTE_PATHS.app) {
    return route.path === path
  }

  return route.path === path || route.path.startsWith(`${path}/`)
}

function closeMobileNavigation() {
  mobileNavigationOpen.value = false
}

function isRouteAvailable(routeName: string) {
  return router.hasRoute(routeName)
}
</script>

<template>
  <div class="app-layout">
    <aside class="app-sidebar" aria-label="工作台导航">
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
      <header class="app-header">
        <Button
          class="app-header__menu-button"
          type="text"
          aria-label="打开工作台导航"
          @click="mobileNavigationOpen = true"
        >
          <MenuOutlined />
        </Button>
        <h1 class="app-header__title">{{ pageTitle }}</h1>
        <Avatar class="app-header__avatar" aria-label="当前账户">
          <template #icon><UserOutlined /></template>
        </Avatar>
      </header>

      <main class="app-content">
        <slot>
          <RouterView />
        </slot>
      </main>
    </div>

    <Drawer
      v-model:open="mobileNavigationOpen"
      placement="left"
      :width="280"
      :closable="false"
      :body-style="{ padding: 0 }"
      title="工作台导航"
    >
      <div class="app-drawer__brand">
        <BrandLogo />
      </div>
      <nav class="app-navigation" aria-label="移动端工作台导航">
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
  background: #f8fafc;
  color: var(--cf-color-primary);
}

.app-navigation__item--active {
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
  font-weight: 650;
}

.app-navigation__item--disabled {
  color: #a8b1c1;
  cursor: not-allowed;
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
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
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

.app-header__menu-button {
  display: none;
}

.app-content {
  width: min(calc(100% - 64px), var(--cf-content-width));
  min-height: calc(100vh - var(--cf-app-header-height));
  margin-inline: auto;
  padding: var(--cf-space-8) 0 var(--cf-space-12);
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
}
</style>

<script setup lang="ts">
import { ref } from 'vue'
import { CloseOutlined, MenuOutlined } from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'
import { RouterLink, RouterView } from 'vue-router'

import BrandLogo from '@/components/brand/BrandLogo.vue'
import { ROUTE_PATHS } from '@/router/paths'

const mobileMenuOpen = ref(false)

const navigation = [
  { label: '产品能力', hash: '#capabilities' },
  { label: '成长路径', hash: '#growth-path' },
  { label: '模拟面试', hash: '#interview' },
  { label: '关于我们', hash: '#about' },
] as const

function closeMobileMenu() {
  mobileMenuOpen.value = false
}
</script>

<template>
  <div class="public-layout">
    <header class="public-header">
      <div class="cf-container public-header__inner">
        <BrandLogo />

        <nav class="public-nav" aria-label="主要导航">
          <RouterLink
            v-for="item in navigation"
            :key="item.hash"
            class="public-nav__link"
            :to="{ path: ROUTE_PATHS.home, hash: item.hash }"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="public-header__actions">
          <RouterLink class="public-header__login" :to="ROUTE_PATHS.login">登录</RouterLink>
          <RouterLink class="public-header__start" :to="ROUTE_PATHS.register">免费开始</RouterLink>
        </div>

        <Button
          class="public-header__menu-button"
          type="text"
          :aria-expanded="mobileMenuOpen"
          aria-controls="public-mobile-menu"
          aria-label="切换导航菜单"
          @click="mobileMenuOpen = !mobileMenuOpen"
        >
          <CloseOutlined v-if="mobileMenuOpen" />
          <MenuOutlined v-else />
        </Button>
      </div>

      <nav
        v-if="mobileMenuOpen"
        id="public-mobile-menu"
        class="public-mobile-nav"
        aria-label="移动端导航"
      >
        <RouterLink
          v-for="item in navigation"
          :key="item.hash"
          class="public-mobile-nav__link"
          :to="{ path: ROUTE_PATHS.home, hash: item.hash }"
          @click="closeMobileMenu"
        >
          {{ item.label }}
        </RouterLink>
        <div class="public-mobile-nav__actions">
          <RouterLink :to="ROUTE_PATHS.login" @click="closeMobileMenu">登录</RouterLink>
          <RouterLink
            class="public-header__start"
            :to="ROUTE_PATHS.register"
            @click="closeMobileMenu"
          >
            免费开始
          </RouterLink>
        </div>
      </nav>
    </header>

    <main class="public-layout__main">
      <slot>
        <RouterView />
      </slot>
    </main>

    <footer id="about" class="public-footer">
      <div class="cf-container public-footer__inner">
        <div>
          <BrandLogo />
          <p class="public-footer__description">面向 AI Agent 岗位的证据驱动职业训练平台</p>
        </div>
        <nav class="public-footer__links" aria-label="页脚导航">
          <RouterLink :to="{ path: ROUTE_PATHS.home, hash: '#capabilities' }">产品能力</RouterLink>
          <RouterLink :to="{ path: ROUTE_PATHS.home, hash: '#growth-path' }">成长路径</RouterLink>
          <RouterLink :to="{ path: ROUTE_PATHS.home, hash: '#interview' }">模拟面试</RouterLink>
        </nav>
        <div class="public-footer__legal">
          <span>© {{ new Date().getFullYear() }} CareerForge-AI</span>
          <span>用户协议</span>
          <span>隐私政策</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.public-layout {
  min-height: 100vh;
  background: var(--cf-color-surface);
}

.public-header {
  position: sticky;
  z-index: 100;
  top: 0;
  min-height: var(--cf-public-header-height);
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(14px);
}

.public-header__inner {
  display: grid;
  min-height: var(--cf-public-header-height);
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--cf-space-8);
}

.public-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--cf-space-8);
}

.public-nav__link,
.public-header__login {
  color: var(--cf-color-text-secondary);
  font-weight: 550;
  transition: color var(--cf-motion-fast) ease;
}

.public-nav__link:hover,
.public-header__login:hover {
  color: var(--cf-color-primary);
}

.public-header__actions {
  display: flex;
  align-items: center;
  gap: var(--cf-space-6);
}

.public-header__start {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  padding: 0 22px;
  border-radius: var(--cf-radius-control);
  background: var(--cf-color-accent);
  color: #422006;
  font-weight: 700;
  transition:
    background var(--cf-motion-fast) ease,
    transform var(--cf-motion-fast) ease;
}

.public-header__start:hover {
  background: #fbbf24;
  color: #422006;
  transform: translateY(-1px);
}

.public-header__menu-button,
.public-mobile-nav {
  display: none;
}

.public-layout__main {
  min-height: calc(100vh - var(--cf-public-header-height));
}

.public-footer {
  border-top: 1px solid var(--cf-color-border);
  background: var(--cf-color-surface);
  padding: var(--cf-space-12) 0 var(--cf-space-8);
}

.public-footer__inner {
  display: grid;
  grid-template-columns: 1.3fr 1fr auto;
  align-items: start;
  gap: var(--cf-space-8);
}

.public-footer__description {
  margin: var(--cf-space-3) 0 0;
  color: var(--cf-color-text-muted);
}

.public-footer__links {
  display: flex;
  gap: var(--cf-space-6);
  color: var(--cf-color-text-secondary);
}

.public-footer__links a:hover {
  color: var(--cf-color-primary);
}

.public-footer__legal {
  display: grid;
  justify-items: end;
  gap: var(--cf-space-2);
  color: var(--cf-color-text-muted);
  font-size: 13px;
}

@media (max-width: 900px) {
  .public-header__inner {
    grid-template-columns: 1fr auto;
  }

  .public-nav,
  .public-header__actions {
    display: none;
  }

  .public-header__menu-button {
    display: inline-flex;
    width: 42px;
    height: 42px;
    align-items: center;
    justify-content: center;
    font-size: 20px;
  }

  .public-mobile-nav {
    display: grid;
    gap: var(--cf-space-2);
    padding: var(--cf-space-3) var(--cf-space-4) var(--cf-space-6);
    border-top: 1px solid var(--cf-color-border);
    background: var(--cf-color-surface);
  }

  .public-mobile-nav__link {
    padding: var(--cf-space-3) var(--cf-space-2);
    border-radius: var(--cf-radius-control);
    color: var(--cf-color-text-secondary);
    font-weight: 600;
  }

  .public-mobile-nav__link:hover {
    background: var(--cf-color-primary-soft);
    color: var(--cf-color-primary);
  }

  .public-mobile-nav__actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: center;
    gap: var(--cf-space-3);
    padding-top: var(--cf-space-3);
    text-align: center;
  }

  .public-footer__inner {
    grid-template-columns: 1fr 1fr;
  }

  .public-footer__legal {
    grid-column: 1 / -1;
    grid-auto-flow: column;
    justify-content: start;
    justify-items: start;
  }
}

@media (max-width: 600px) {
  .public-footer__inner {
    grid-template-columns: 1fr;
  }

  .public-footer__links {
    flex-wrap: wrap;
  }

  .public-footer__legal {
    grid-column: auto;
    grid-auto-flow: row;
  }
}
</style>

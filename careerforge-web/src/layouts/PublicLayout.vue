<script setup lang="ts">
import { ref } from 'vue'
import { CloseOutlined, MenuOutlined } from '@ant-design/icons-vue'
import { Button } from 'ant-design-vue'
import { RouterLink, RouterView } from 'vue-router'

import BrandLogo from '@/components/brand/BrandLogo.vue'
import { ROUTE_PATHS } from '@/router/paths'

const mobileMenuOpen = ref(false)

const navigation = [
  { label: '面试模式', to: { path: ROUTE_PATHS.home, hash: '#modes' } },
  { label: '怎么进行', to: { path: ROUTE_PATHS.home, hash: '#process' } },
  { label: '面试记录', to: ROUTE_PATHS.interviewRecords },
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
            :key="item.label"
            class="public-nav__link"
            :to="item.to"
          >
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="public-header__actions">
          <RouterLink class="public-header__login" :to="ROUTE_PATHS.login">登录</RouterLink>
          <RouterLink class="public-header__start" :to="ROUTE_PATHS.register">开始面试</RouterLink>
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
          :key="item.label"
          class="public-mobile-nav__link"
          :to="item.to"
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
            开始面试
          </RouterLink>
        </div>
      </nav>
    </header>

    <main class="public-layout__main">
      <slot>
        <RouterView />
      </slot>
    </main>

    <footer class="public-footer">
      <div class="cf-container public-footer__inner">
        <div>
          <BrandLogo />
          <p class="public-footer__description">面试前，先认真面一次。</p>
        </div>
        <nav class="public-footer__links" aria-label="页脚导航">
          <RouterLink :to="ROUTE_PATHS.terms">用户协议</RouterLink>
          <RouterLink :to="ROUTE_PATHS.privacy">隐私政策</RouterLink>
          <RouterLink :to="ROUTE_PATHS.login">登录</RouterLink>
        </nav>
        <div class="public-footer__legal">
          <span>© {{ new Date().getFullYear() }} 水水面</span>
          <span>Java 后端 · AI Agent 开发</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.public-layout {
  min-height: 100vh;
  background: var(--ssm-color-paper);
}

.public-header {
  position: sticky;
  z-index: 100;
  top: 0;
  min-height: var(--cf-public-header-height);
  border-bottom: 1px solid var(--ssm-color-line);
  background: rgba(247, 245, 240, 0.96);
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
  color: var(--ssm-color-text-secondary);
  font-weight: 500;
  transition: color var(--ssm-motion-fast) ease;
}

.public-nav__link:hover,
.public-header__login:hover {
  color: var(--ssm-color-brand);
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
  background: var(--ssm-color-brand);
  color: #fff;
  font-weight: 650;
  transition: background var(--ssm-motion-fast) ease;
}

.public-header__start:hover {
  background: var(--ssm-color-brand-hover);
  color: #fff;
}

.public-header__menu-button,
.public-mobile-nav {
  display: none;
}

.public-layout__main {
  min-height: calc(100vh - var(--cf-public-header-height));
}

.public-footer {
  border-top: 1px solid var(--ssm-color-line);
  background: var(--ssm-color-paper);
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
  color: var(--ssm-color-text-secondary);
}

.public-footer__links {
  display: flex;
  gap: var(--cf-space-6);
  color: var(--ssm-color-text-secondary);
}

.public-footer__links a:hover {
  color: var(--ssm-color-brand);
}

.public-footer__legal {
  display: grid;
  justify-items: end;
  gap: var(--cf-space-2);
  color: var(--ssm-color-text-secondary);
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
    border-top: 1px solid var(--ssm-color-line);
    background: var(--ssm-color-paper);
  }

  .public-mobile-nav__link {
    padding: var(--cf-space-3) var(--cf-space-2);
    border-radius: var(--cf-radius-control);
    color: var(--ssm-color-text-secondary);
    font-weight: 600;
  }

  .public-mobile-nav__link:hover {
    background: var(--ssm-color-brand-soft);
    color: var(--ssm-color-brand);
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

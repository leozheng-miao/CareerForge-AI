<script setup lang="ts">
import { App as AntApplication, ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

import { careerForgeTheme } from '@/theme/ant-design'
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="careerForgeTheme">
    <AntApplication class="careerforge-app">
      <RouterView />
    </AntApplication>
  </ConfigProvider>
</template>

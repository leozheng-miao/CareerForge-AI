<script setup lang="ts">
import { App as AntApplication, ConfigProvider } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

import { shuishuiMianTheme } from '@/theme/ant-design'
</script>

<template>
  <ConfigProvider :locale="zhCN" :theme="shuishuiMianTheme">
    <AntApplication class="shuishuimian-app">
      <RouterView />
    </AntApplication>
  </ConfigProvider>
</template>

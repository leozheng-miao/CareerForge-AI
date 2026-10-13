import type { ConfigProviderProps } from 'ant-design-vue'

export const shuishuiMianTheme: ConfigProviderProps['theme'] = {
  token: {
    colorPrimary: '#2f78aa',
    colorPrimaryHover: '#27668f',
    colorInfo: '#2f78aa',
    colorSuccess: '#287a55',
    colorWarning: '#b87925',
    colorError: '#b33a3a',
    colorText: '#171a1c',
    colorTextSecondary: '#5d6267',
    colorBgBase: '#f7f5f0',
    colorBgContainer: '#fcfbf8',
    colorBorder: '#d8d5ce',
    borderRadius: 8,
    borderRadiusLG: 12,
    controlHeight: 40,
    fontFamily:
      '"PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    fontSize: 14,
    fontWeightStrong: 600,
    boxShadowSecondary: '0 12px 32px rgba(23, 26, 28, 0.07)',
    motionDurationFast: '0.12s',
    motionDurationMid: '0.16s',
    motionDurationSlow: '0.2s',
  },
  components: {
    Button: {
      borderRadius: 8,
      controlHeight: 40,
    },
    Card: {
      borderRadiusLG: 12,
    },
    Input: {
      borderRadius: 8,
      controlHeight: 40,
    },
  },
}

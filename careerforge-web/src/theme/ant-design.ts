import type { ConfigProviderProps } from 'ant-design-vue'

export const careerForgeTheme: ConfigProviderProps['theme'] = {
  token: {
    colorPrimary: '#4f46e5',
    colorPrimaryHover: '#4338ca',
    colorInfo: '#4f46e5',
    colorSuccess: '#16a34a',
    colorWarning: '#d97706',
    colorError: '#dc2626',
    colorText: '#0f172a',
    colorTextSecondary: '#475569',
    colorBgBase: '#f8fafc',
    colorBgContainer: '#ffffff',
    colorBorder: '#e2e8f0',
    borderRadius: 8,
    borderRadiusLG: 12,
    controlHeight: 40,
    fontFamily:
      'Inter, "PingFang SC", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
    fontSize: 14,
    fontWeightStrong: 600,
    boxShadowSecondary: '0 12px 32px rgba(15, 23, 42, 0.08)',
    motionDurationFast: '0.15s',
    motionDurationMid: '0.2s',
    motionDurationSlow: '0.24s',
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

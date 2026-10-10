<script setup lang="ts">
import { computed, ref } from 'vue'
import {
  AimOutlined,
  ArrowRightOutlined,
  BarChartOutlined,
  BookOutlined,
  CheckCircleFilled,
  CodeOutlined,
  CompassOutlined,
  FileSearchOutlined,
  MessageOutlined,
  ReadOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
  SolutionOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import { RouterLink } from 'vue-router'

import { ROUTE_PATHS } from '@/router/paths'

const interviewMode = ref<'TARGETED_MOCK' | 'GAP_DRILL'>('TARGETED_MOCK')

const capabilityCards = [
  {
    number: '01',
    title: '岗位能力诊断',
    description: '输入目标 JD，看清岗位要求、已有能力和真实差距。',
    icon: FileSearchOutlined,
    facts: ['结构化岗位要求', '关联个人证据', '生成差距快照'],
    meta: 'JD 解析 · Target Role · Skill Gap',
    action: '从 JD 开始诊断',
    to: ROUTE_PATHS.register,
  },
  {
    number: '02',
    title: '个性化成长训练',
    description: '根据差距和时间约束生成计划，并持续向 Career Coach 复盘。',
    icon: CompassOutlined,
    facts: ['确认训练计划', '完成针对性任务', '沉淀可验证证据'],
    meta: 'Training Plan · Career Coach · Memory',
    action: '查看训练方式',
    to: { path: ROUTE_PATHS.home, hash: '#growth-path' },
  },
  {
    number: '03',
    title: '多 Agent 模拟面试',
    description: '围绕目标岗位或能力短板连续追问，并获得多维复盘。',
    icon: TeamOutlined,
    facts: ['岗位定向或差距专项', '四角色协同评审', '形成可执行复盘'],
    meta: 'TARGETED_MOCK · GAP_DRILL · Report',
    action: '体验模拟面试',
    to: { path: ROUTE_PATHS.home, hash: '#interview' },
  },
] as const

const growthSteps = [
  {
    title: '定目标',
    description: '粘贴目标岗位或真实 JD',
    outcome: 'Target Role',
    icon: AimOutlined,
  },
  {
    title: '找差距',
    description: '关联经历与项目证据',
    outcome: 'Skill Gap',
    icon: BarChartOutlined,
  },
  {
    title: '做训练',
    description: '完成针对性实战任务',
    outcome: '个人技能证据',
    icon: CodeOutlined,
  },
  {
    title: '接受辅导',
    description: '与 Career Coach 持续复盘',
    outcome: '下一步训练计划',
    icon: MessageOutlined,
  },
  {
    title: '面试与复盘',
    description: '进行目标面试或短板专项',
    outcome: '多维 Report',
    icon: SolutionOutlined,
  },
] as const

const preview = computed(() =>
  interviewMode.value === 'TARGETED_MOCK'
    ? {
        label: '目标岗位面试',
        title: 'AI Agent 应用开发 · 系统设计面试',
        question: '你会如何为一个需要工具调用的 Agent 设计可靠的执行流程？',
        followUp: '如果外部工具连续失败，你会如何避免 Agent 陷入循环？',
        focus: ['架构合理性', '工具调用风险', '失败恢复边界'],
      }
    : {
        label: '能力短板专项',
        title: '可靠性工程 · GAP_DRILL',
        question: '如何设计一个可重试、可追踪且不会重复写入的 Agent 工具？',
        followUp: '请说明幂等键、超时与人工接管分别在哪一层生效。',
        focus: ['幂等设计', '超时与重试', '人工接管条件'],
      },
)

const faqItems = [
  {
    question: 'CareerForge-AI 会自动替我确认能力或长期记忆吗？',
    answer:
      '不会。模型可以提出候选结论，但长期记忆、训练计划和面试报告中的关键确认都由用户显式决定。',
  },
  {
    question: '为什么强调“证据驱动”？',
    answer:
      '岗位判断、训练建议和面试追问都应回到可追溯的项目经历、练习产出与系统记录，避免只有漂亮但无法验证的描述。',
  },
  {
    question: '它是刷题网站吗？',
    answer:
      '不是。平台围绕你的目标岗位与真实能力差距组织诊断、训练、辅导和面试，让每一轮反馈更新下一步行动。',
  },
] as const
</script>

<template>
  <div class="home-page">
    <section class="hero-section" aria-labelledby="hero-title">
      <div class="cf-container hero-section__inner">
        <p class="section-eyebrow">为 Java 开发者打造的 AI Agent 转型路径</p>
        <h1 id="hero-title">从看懂目标岗位，到练成能拿 Offer 的能力</h1>
        <p class="hero-section__lead">
          输入目标岗位或 JD，获得可追溯的能力诊断、训练计划和模拟面试反馈。
        </p>

        <div class="hero-section__actions" aria-label="开始使用 CareerForge-AI">
          <RouterLink class="cf-action cf-action--primary" :to="ROUTE_PATHS.register">
            从一份 JD 开始职业诊断
            <ArrowRightOutlined />
          </RouterLink>
          <RouterLink
            class="cf-action cf-action--secondary"
            :to="{ path: ROUTE_PATHS.home, hash: '#interview' }"
          >
            <RobotOutlined />
            体验 AI 模拟面试
          </RouterLink>
        </div>

        <p class="hero-section__trust">
          <SafetyCertificateOutlined />
          每条建议都有依据，每次练习都有下一步
        </p>
        <div class="hero-section__tags" aria-label="适合的职业方向">
          <span>AI Agent 应用开发</span>
          <span>Java 后端转型</span>
          <span>大模型应用工程师</span>
        </div>
      </div>
    </section>

    <section
      id="capabilities"
      class="section-block capability-section"
      aria-labelledby="capability-title"
    >
      <div class="cf-container">
        <header class="section-heading">
          <p class="section-eyebrow">三条核心业务线</p>
          <h2 id="capability-title">从发现差距，到形成能被验证的岗位能力</h2>
          <p>诊断决定练什么，训练沉淀能力证据，模拟面试检验是否真正掌握。</p>
        </header>

        <div class="capability-grid">
          <article v-for="card in capabilityCards" :key="card.number" class="capability-card">
            <div class="capability-card__topline">
              <span class="capability-card__number">{{ card.number }}</span>
              <component :is="card.icon" class="capability-card__icon" />
            </div>
            <h3>{{ card.title }}</h3>
            <p>{{ card.description }}</p>
            <ul>
              <li v-for="fact in card.facts" :key="fact">
                <CheckCircleFilled />
                <span>{{ fact }}</span>
              </li>
            </ul>
            <p class="capability-card__meta">{{ card.meta }}</p>
            <RouterLink class="capability-card__link" :to="card.to">
              {{ card.action }}
              <ArrowRightOutlined />
            </RouterLink>
          </article>
        </div>

        <div class="convergence-line" aria-label="产品闭环">
          <span><AimOutlined />诊断定位差距</span>
          <ArrowRightOutlined class="convergence-line__arrow" />
          <span><CodeOutlined />训练形成证据</span>
          <ArrowRightOutlined class="convergence-line__arrow" />
          <span><SafetyCertificateOutlined />面试验证能力</span>
        </div>
      </div>
    </section>

    <section id="growth-path" class="section-block growth-section" aria-labelledby="growth-title">
      <div class="cf-container">
        <header class="section-heading">
          <p class="section-eyebrow">五步职业成长路径</p>
          <h2 id="growth-title">知道下一步做什么，也知道练完得到什么</h2>
          <p>每一步都有明确行动、可保存的产出，并成为下一步判断的依据。</p>
        </header>

        <ol class="growth-path">
          <li v-for="(step, index) in growthSteps" :key="step.title" class="growth-step">
            <div class="growth-step__icon"><component :is="step.icon" /></div>
            <span class="growth-step__number">{{ index + 1 }}</span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
            <strong>得到：{{ step.outcome }}</strong>
          </li>
        </ol>

        <p class="growth-section__loop">
          <CompassOutlined />
          面试反馈回到能力诊断，自动更新下一轮成长重点。
        </p>
      </div>
    </section>

    <section
      id="interview"
      class="section-block interview-section"
      aria-labelledby="interview-title"
    >
      <div class="cf-container">
        <header class="section-heading">
          <p class="section-eyebrow">多 Agent 模拟面试</p>
          <h2 id="interview-title">不是泛泛地练题，而是围绕你的目标岗位持续追问</h2>
          <p>可针对完整岗位进行目标面试，或围绕能力短板进行专项训练。</p>
        </header>

        <div class="interview-tabs" role="tablist" aria-label="模拟面试模式">
          <button
            id="targeted-mock-tab"
            type="button"
            role="tab"
            :aria-selected="interviewMode === 'TARGETED_MOCK'"
            aria-controls="interview-preview-panel"
            :class="{ 'interview-tabs__button--active': interviewMode === 'TARGETED_MOCK' }"
            @click="interviewMode = 'TARGETED_MOCK'"
          >
            TARGETED_MOCK · 目标岗位面试
          </button>
          <button
            id="gap-drill-tab"
            type="button"
            role="tab"
            :aria-selected="interviewMode === 'GAP_DRILL'"
            aria-controls="interview-preview-panel"
            :class="{ 'interview-tabs__button--active': interviewMode === 'GAP_DRILL' }"
            @click="interviewMode = 'GAP_DRILL'"
          >
            GAP_DRILL · 能力短板专项
          </button>
        </div>

        <article
          id="interview-preview-panel"
          class="interview-preview"
          role="tabpanel"
          :aria-labelledby="
            interviewMode === 'TARGETED_MOCK' ? 'targeted-mock-tab' : 'gap-drill-tab'
          "
        >
          <header class="interview-preview__header">
            <span><RobotOutlined />{{ preview.title }}</span>
            <span class="interview-preview__sample">产品预览 · 示例</span>
          </header>
          <div class="interview-preview__content">
            <div class="interview-conversation">
              <p class="interview-conversation__role">主面试官 · {{ preview.label }}</p>
              <div class="interview-conversation__question">{{ preview.question }}</div>
              <div class="interview-conversation__answer">
                我会把流程拆成规划、执行和校验三个阶段，并为每次工具调用设置超时、幂等键和结果验证。
              </div>
              <p class="interview-conversation__role">深挖面试官</p>
              <div
                class="interview-conversation__question interview-conversation__question--follow"
              >
                {{ preview.followUp }}
              </div>
              <div class="interview-conversation__composer">
                <span>说出你的思路…</span>
                <SendOutlined />
              </div>
            </div>

            <aside class="interview-feedback" aria-label="本轮反馈示例">
              <p class="interview-feedback__title">本轮进度</p>
              <div class="interview-feedback__steps">
                <span class="is-complete"><CheckCircleFilled />提问</span>
                <span class="is-active">2 回答</span>
                <span>3 评审</span>
                <span>4 复盘</span>
              </div>
              <p class="interview-feedback__title">四角色协同</p>
              <ul class="interview-feedback__roles">
                <li><RobotOutlined />主面试官 · 控制节奏</li>
                <li><MessageOutlined />深挖面试官 · 追问细节</li>
                <li><SafetyCertificateOutlined />证据核验员 · 检查依据</li>
                <li><TeamOutlined />能力评审官 · 多维反馈</li>
              </ul>
              <p class="interview-feedback__title">即时能力反馈</p>
              <ul class="interview-feedback__focus">
                <li v-for="item in preview.focus" :key="item"><CheckCircleFilled />{{ item }}</li>
              </ul>
            </aside>
          </div>
        </article>
      </div>
    </section>

    <section class="trust-section" aria-labelledby="trust-title">
      <div class="cf-container trust-section__grid">
        <div>
          <p class="section-eyebrow">可信能力贯穿全程</p>
          <h2 id="trust-title">建议来自证据，进步沉淀为长期记忆</h2>
          <p>个人证据库和长期记忆不是独立功能岛，而是诊断、训练与面试共同使用的可信基础。</p>
        </div>
        <ul>
          <li>
            <BookOutlined /><span><strong>来源可追溯</strong>岗位判断与知识回答保留来源。</span>
          </li>
          <li>
            <SafetyCertificateOutlined /><span
              ><strong>用户做确认</strong>模型不能直接确认长期能力画像。</span
            >
          </li>
          <li>
            <ReadOutlined /><span><strong>结果可复盘</strong>训练和面试反馈更新下一步行动。</span>
          </li>
        </ul>
      </div>
    </section>

    <section class="section-block faq-section" aria-labelledby="faq-title">
      <div class="cf-container faq-section__grid">
        <header>
          <p class="section-eyebrow">常见问题</p>
          <h2 id="faq-title">开始前，你可能还想知道</h2>
        </header>
        <div class="faq-list">
          <details v-for="(item, index) in faqItems" :key="item.question" :open="index === 0">
            <summary>{{ item.question }}</summary>
            <p>{{ item.answer }}</p>
          </details>
        </div>
      </div>
    </section>

    <section class="final-cta" aria-labelledby="final-cta-title">
      <div class="cf-container final-cta__inner">
        <div>
          <p class="section-eyebrow">把目标岗位变成今天可以开始的行动</p>
          <h2 id="final-cta-title">先看清差距，再按自己的时间一步步练成</h2>
        </div>
        <div class="final-cta__actions">
          <RouterLink class="cf-action cf-action--primary" :to="ROUTE_PATHS.register">
            免费开始
            <ArrowRightOutlined />
          </RouterLink>
          <RouterLink class="cf-action cf-action--secondary" :to="ROUTE_PATHS.login">
            已有账号，登录
          </RouterLink>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  overflow: hidden;
  background: var(--cf-color-surface);
}

.hero-section {
  border-bottom: 1px solid #eef2ff;
  background: #fbfdff;
}

.hero-section__inner {
  display: flex;
  min-height: 570px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-block: 86px 76px;
  text-align: center;
}

.section-eyebrow {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: var(--cf-space-2);
  margin: 0 0 var(--cf-space-6);
  padding: 7px 16px;
  border: 1px solid #dbe4ff;
  border-radius: 999px;
  background: #f7f9ff;
  color: var(--cf-color-primary);
  font-size: 15px;
  font-weight: 700;
}

.hero-section h1 {
  max-width: 1120px;
  margin: 0;
  color: #0b1740;
  font-size: clamp(38px, 4.1vw, 58px);
  font-weight: 760;
  letter-spacing: -0.045em;
  line-height: 1.18;
}

.hero-section__lead {
  max-width: 780px;
  margin: var(--cf-space-6) 0 0;
  color: var(--cf-color-text-muted);
  font-size: 19px;
  line-height: 1.7;
}

.hero-section__actions,
.final-cta__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--cf-space-4);
  margin-top: var(--cf-space-8);
}

.cf-action {
  display: inline-flex;
  min-height: 50px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 0 24px;
  border: 1px solid transparent;
  border-radius: var(--cf-radius-control);
  font-size: 16px;
  font-weight: 700;
  transition:
    border-color var(--cf-motion-fast) ease,
    background var(--cf-motion-fast) ease,
    transform var(--cf-motion-fast) ease;
}

.cf-action:hover {
  transform: translateY(-1px);
}

.cf-action--primary {
  background: var(--cf-color-primary);
  color: #fff;
}

.cf-action--primary:hover {
  background: var(--cf-color-primary-hover);
  color: #fff;
}

.cf-action--secondary {
  border-color: #aebcff;
  background: #fff;
  color: var(--cf-color-primary);
}

.cf-action--secondary:hover {
  border-color: var(--cf-color-primary);
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary-hover);
}

.hero-section__trust {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: var(--cf-space-6) 0 0;
  color: var(--cf-color-text-secondary);
  font-size: 14px;
}

.hero-section__tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--cf-space-3);
  margin-top: var(--cf-space-5, 20px);
}

.hero-section__tags span {
  padding: 7px 14px;
  border-radius: 999px;
  background: #f1f5f9;
  color: var(--cf-color-text-secondary);
  font-size: 13px;
  font-weight: 600;
}

.section-block {
  padding: 92px 0;
}

.section-heading {
  display: flex;
  max-width: 880px;
  flex-direction: column;
  align-items: center;
  margin: 0 auto 48px;
  text-align: center;
}

.section-heading .section-eyebrow {
  margin-bottom: var(--cf-space-4);
}

.section-heading h2,
.trust-section h2,
.faq-section h2,
.final-cta h2 {
  margin: 0;
  color: #0b1740;
  font-size: clamp(30px, 3vw, 42px);
  letter-spacing: -0.035em;
  line-height: 1.25;
}

.section-heading > p:last-child,
.trust-section__grid > div > p:last-child {
  margin: var(--cf-space-4) 0 0;
  color: var(--cf-color-text-muted);
  font-size: 17px;
  line-height: 1.7;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--cf-space-4);
}

.capability-card {
  display: flex;
  min-height: 430px;
  flex-direction: column;
  padding: 28px;
  border: 1px solid #dce4f5;
  border-radius: var(--cf-radius-card);
  background: #fff;
  box-shadow: 0 1px 0 rgba(79, 70, 229, 0.03);
  transition:
    border-color var(--cf-motion-normal) ease,
    box-shadow var(--cf-motion-normal) ease,
    transform var(--cf-motion-normal) ease;
}

.capability-card:hover {
  border-color: #bac7ff;
  box-shadow: var(--cf-shadow-card);
  transform: translateY(-3px);
}

.capability-card__topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.capability-card__number {
  display: inline-flex;
  width: 42px;
  height: 42px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
  font-weight: 700;
}

.capability-card__icon {
  color: #8291e9;
  font-size: 32px;
}

.capability-card h3 {
  margin: 30px 0 10px;
  color: #0b1740;
  font-size: 25px;
}

.capability-card > p {
  margin: 0;
  color: var(--cf-color-text-muted);
  line-height: 1.7;
}

.capability-card ul {
  display: grid;
  gap: var(--cf-space-3);
  margin: 28px 0;
  padding: 0;
  list-style: none;
}

.capability-card li {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--cf-color-text-secondary);
}

.capability-card li :deep(svg) {
  color: var(--cf-color-success);
}

.capability-card .capability-card__meta {
  margin-top: auto;
  padding-top: var(--cf-space-4);
  border-top: 1px solid #edf0f7;
  color: #697595;
  font-size: 13px;
}

.capability-card__link {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-top: var(--cf-space-5, 20px);
  color: var(--cf-color-primary);
  font-weight: 700;
}

.capability-card__link:hover {
  color: var(--cf-color-primary-hover);
}

.convergence-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--cf-space-6);
  margin-top: 42px;
  color: #23304f;
  font-weight: 650;
}

.convergence-line span {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.convergence-line span :deep(svg) {
  color: var(--cf-color-primary);
  font-size: 22px;
}

.convergence-line__arrow {
  color: #a7b3d7;
}

.growth-section {
  border-block: 1px solid #eef2ff;
  background: #fbfcff;
}

.growth-path {
  display: grid;
  margin: 0;
  padding: 38px 26px;
  border: 1px solid #dbe4f6;
  border-radius: var(--cf-radius-panel);
  background: #fff;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  list-style: none;
}

.growth-step {
  position: relative;
  display: flex;
  align-items: center;
  padding: 0 14px;
  text-align: center;
  flex-direction: column;
}

.growth-step:not(:last-child)::after {
  position: absolute;
  top: 37px;
  right: -18%;
  width: 36%;
  border-top: 2px dashed #c7d2fe;
  content: '';
}

.growth-step__icon {
  display: flex;
  width: 72px;
  height: 72px;
  align-items: center;
  justify-content: center;
  border: 1px solid #cdd7ff;
  border-radius: 50%;
  background: #f8faff;
  color: var(--cf-color-primary);
  font-size: 32px;
}

.growth-step:last-child .growth-step__icon,
.growth-step:last-child .growth-step__number {
  border-color: #fde68a;
  background: #fffbeb;
  color: var(--cf-color-accent-hover);
}

.growth-step__number {
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  margin-top: var(--cf-space-3);
  border-radius: 50%;
  background: var(--cf-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
}

.growth-step h3 {
  margin: var(--cf-space-4) 0 var(--cf-space-2);
  color: #0b1740;
  font-size: 19px;
}

.growth-step p {
  min-height: 42px;
  margin: 0;
  color: var(--cf-color-text-muted);
  font-size: 13px;
}

.growth-step strong {
  margin-top: var(--cf-space-4);
  padding: 8px 11px;
  border-radius: 8px;
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
  font-size: 13px;
  font-weight: 650;
}

.growth-section__loop {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin: 30px 0 0;
  color: var(--cf-color-text-secondary);
}

.growth-section__loop :deep(svg) {
  color: var(--cf-color-primary);
}

.interview-section {
  background: #fff;
}

.interview-tabs {
  display: grid;
  max-width: 620px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  margin: 0 auto 24px;
  padding: 4px;
  border: 1px solid #d6def2;
  border-radius: 10px;
  background: #f8fafc;
}

.interview-tabs__button {
  min-height: 44px;
  padding: 0 14px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--cf-color-text-secondary);
  cursor: pointer;
  font-weight: 650;
}

.interview-tabs__button--active {
  background: var(--cf-color-primary);
  color: #fff;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.18);
}

.interview-preview {
  overflow: hidden;
  max-width: 1080px;
  margin: 0 auto;
  border: 1px solid #dce3f0;
  border-radius: var(--cf-radius-panel);
  background: #fff;
  box-shadow: var(--cf-shadow-panel);
}

.interview-preview__header {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: var(--cf-space-4);
  padding: 0 24px;
  border-bottom: 1px solid #e9edf5;
  color: #16213f;
  font-weight: 700;
}

.interview-preview__header > span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.interview-preview__sample {
  color: var(--cf-color-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.interview-preview__content {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(280px, 0.8fr);
}

.interview-conversation {
  display: grid;
  gap: 12px;
  padding: 26px;
}

.interview-conversation__role {
  margin: 0;
  color: var(--cf-color-primary);
  font-size: 13px;
  font-weight: 700;
}

.interview-conversation__question,
.interview-conversation__answer {
  max-width: 88%;
  padding: 18px 20px;
  border: 1px solid #d8e0fb;
  border-radius: 8px;
  background: #fbfcff;
  color: #16213f;
  font-size: 16px;
  line-height: 1.7;
}

.interview-conversation__answer {
  justify-self: end;
  border-color: #e0e7ff;
  background: var(--cf-color-primary-soft);
}

.interview-conversation__question--follow {
  margin-bottom: 10px;
}

.interview-conversation__composer {
  display: flex;
  min-height: 50px;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px 0 18px;
  border: 1px solid #d8dfec;
  border-radius: 9px;
  color: #94a3b8;
}

.interview-conversation__composer :deep(svg) {
  color: var(--cf-color-primary);
  font-size: 20px;
}

.interview-feedback {
  padding: 26px 22px;
  border-left: 1px solid #e9edf5;
  background: #fcfdff;
}

.interview-feedback__title {
  margin: 0 0 12px;
  color: #1e293b;
  font-size: 13px;
  font-weight: 750;
}

.interview-feedback__title:not(:first-child) {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #e8edf6;
}

.interview-feedback__steps {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  color: #94a3b8;
  font-size: 11px;
}

.interview-feedback__steps span {
  display: flex;
  min-height: 36px;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  background: #f1f5f9;
}

.interview-feedback__steps .is-complete {
  background: #ecfdf5;
  color: var(--cf-color-success);
}

.interview-feedback__steps .is-active {
  background: var(--cf-color-primary-soft);
  color: var(--cf-color-primary);
  font-weight: 700;
}

.interview-feedback__roles,
.interview-feedback__focus {
  display: grid;
  gap: 11px;
  margin: 0;
  padding: 0;
  color: var(--cf-color-text-secondary);
  font-size: 12px;
  list-style: none;
}

.interview-feedback__roles li,
.interview-feedback__focus li {
  display: flex;
  align-items: center;
  gap: 9px;
}

.interview-feedback__roles li :deep(svg) {
  color: var(--cf-color-primary);
  font-size: 16px;
}

.interview-feedback__focus li :deep(svg) {
  color: var(--cf-color-success);
}

.trust-section {
  padding: 72px 0;
  border-block: 1px solid #f4ead5;
  background: var(--cf-color-background-warm);
}

.trust-section__grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(400px, 0.9fr);
  align-items: center;
  gap: 80px;
}

.trust-section .section-eyebrow,
.faq-section .section-eyebrow,
.final-cta .section-eyebrow {
  margin-bottom: var(--cf-space-4);
}

.trust-section ul {
  display: grid;
  gap: var(--cf-space-4);
  margin: 0;
  padding: 0;
  list-style: none;
}

.trust-section li {
  display: grid;
  grid-template-columns: 42px 1fr;
  align-items: center;
  gap: var(--cf-space-4);
  padding: 16px 18px;
  border: 1px solid #ece5d7;
  border-radius: 10px;
  background: #fff;
  color: var(--cf-color-text-secondary);
}

.trust-section li > :deep(svg) {
  color: var(--cf-color-primary);
  font-size: 26px;
}

.trust-section li span {
  display: grid;
  gap: 2px;
}

.trust-section li strong {
  color: #1e293b;
}

.faq-section__grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.7fr) minmax(0, 1.3fr);
  gap: 80px;
}

.faq-list {
  border-top: 1px solid #dfe5ef;
}

.faq-list details {
  border-bottom: 1px solid #dfe5ef;
}

.faq-list summary {
  padding: 22px 4px;
  color: #18223f;
  cursor: pointer;
  font-weight: 700;
}

.faq-list p {
  margin: -6px 0 22px;
  padding-right: 28px;
  color: var(--cf-color-text-muted);
  line-height: 1.75;
}

.final-cta {
  padding: 64px 0;
  border-top: 1px solid #f2e7d4;
  background: #fffaf0;
}

.final-cta__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 48px;
}

.final-cta h2 {
  max-width: 720px;
  font-size: clamp(28px, 2.6vw, 38px);
}

.final-cta__actions {
  flex: 0 0 auto;
  margin-top: 0;
}

@media (max-width: 1023px) {
  .capability-grid {
    grid-template-columns: 1fr;
  }

  .capability-card {
    min-height: 0;
  }

  .growth-path {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 32px 16px;
  }

  .growth-step:last-child {
    grid-column: 1 / -1;
  }

  .growth-step::after {
    display: none;
  }

  .interview-preview__content {
    grid-template-columns: 1fr;
  }

  .interview-feedback {
    border-top: 1px solid #e9edf5;
    border-left: 0;
  }

  .trust-section__grid,
  .faq-section__grid {
    grid-template-columns: 1fr;
    gap: 40px;
  }

  .final-cta__inner {
    flex-direction: column;
    align-items: flex-start;
  }

  .final-cta__actions {
    justify-content: flex-start;
  }
}

@media (max-width: 767px) {
  .hero-section__inner {
    min-height: auto;
    padding-block: 64px 56px;
  }

  .hero-section h1 {
    font-size: 38px;
  }

  .hero-section__lead {
    font-size: 17px;
  }

  .section-block {
    padding: 68px 0;
  }

  .section-heading {
    align-items: flex-start;
    margin-bottom: 36px;
    text-align: left;
  }

  .section-heading h2,
  .trust-section h2,
  .faq-section h2,
  .final-cta h2 {
    font-size: 30px;
  }

  .capability-card {
    padding: 22px;
  }

  .convergence-line {
    align-items: stretch;
    flex-direction: column;
    gap: var(--cf-space-3);
  }

  .convergence-line span {
    padding: 14px 16px;
    border: 1px solid #e1e7f2;
    border-radius: 9px;
  }

  .convergence-line__arrow {
    display: none;
  }

  .growth-path {
    grid-template-columns: 1fr;
    padding: 28px 18px;
  }

  .growth-step:last-child {
    grid-column: auto;
  }

  .growth-step p {
    min-height: 0;
  }

  .growth-section__loop {
    align-items: flex-start;
    text-align: left;
  }

  .interview-tabs {
    grid-template-columns: 1fr;
  }

  .interview-preview__header {
    align-items: flex-start;
    flex-direction: column;
    padding-block: 16px;
  }

  .interview-conversation,
  .interview-feedback {
    padding: 20px;
  }

  .interview-conversation__question,
  .interview-conversation__answer {
    max-width: 100%;
  }

  .trust-section {
    padding: 58px 0;
  }

  .final-cta__actions,
  .hero-section__actions {
    width: 100%;
    flex-direction: column;
  }

  .cf-action {
    width: 100%;
  }
}

@media (max-width: 479px) {
  .hero-section h1 {
    font-size: 34px;
  }

  .section-eyebrow {
    padding-inline: 12px;
    font-size: 13px;
  }

  .interview-feedback__steps {
    grid-template-columns: repeat(2, 1fr);
  }

  .trust-section li {
    grid-template-columns: 34px 1fr;
    padding: 14px;
  }
}
</style>

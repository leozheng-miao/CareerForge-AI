<script setup lang="ts">
import {
  ArrowRightOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
  PauseOutlined,
  SendOutlined,
} from '@ant-design/icons-vue'
import { RouterLink } from 'vue-router'

import { ROUTE_PATHS } from '@/router/paths'

const modes = [
  {
    title: '快速面试',
    meta: '60 分钟 · 8 个考查目标',
    description: '不需要准备材料，直接检验 AI Agent 开发的通用能力。',
  },
  {
    title: '综合面试',
    meta: '结合岗位与真实经历',
    description: '确认岗位和个人材料后，完成一场更贴近目标岗位的面试。',
  },
] as const

const processSteps = [
  {
    number: '01',
    title: '选一种面试',
    description: '直接开始快速面试，或先填写岗位和个人材料。',
  },
  {
    number: '02',
    title: '按题认真作答',
    description: '回答提交后保存并冻结；面试会根据回答决定是否继续追问。',
  },
  {
    number: '03',
    title: '拿到完整复盘',
    description: '结束后异步生成报告，保留总结、改进项和完整问答。',
  },
] as const

const comparisonRows = [
  { label: '开始前', quick: '无需上传材料', comprehensive: '填写岗位，可选上传简历' },
  { label: '时长', quick: '固定 60 分钟', comprehensive: '60 或 90 分钟' },
  { label: '考查方式', quick: '8 个固定考查目标', comprehensive: '6 个岗位相关主题' },
] as const

const faqItems = [
  {
    question: '切换到其他浏览器标签页会暂停吗？',
    answer: '不会。只有主动点击暂停、关闭或离开页面、退出登录时，系统才会进入暂停收敛流程。',
  },
  {
    question: '时间结束会直接打断回答吗？',
    answer: '不会。时间用尽后仍可答完当前题，提交后面试正常结束，不再进入下一题。',
  },
  {
    question: '回答和个人材料会进入公共题库吗？',
    answer: '不会。公共题库只由管理员维护，用户提交的岗位材料、简历和回答不会写入公共题库。',
  },
  {
    question: '报告需要一直等在页面上吗？',
    answer: '不需要。面试结束后报告在后台生成，你可以先离开，稍后从面试记录中查看。',
  },
] as const
</script>

<template>
  <div class="home-page">
    <section class="home-hero" aria-labelledby="home-hero-title">
      <div class="ssm-container home-hero__grid">
        <div class="home-hero__copy">
          <p class="home-kicker">Java 后端 · AI Agent 开发</p>
          <h1 id="home-hero-title">面试前，<br>先认真面一次。</h1>
          <p class="home-hero__lead">会根据回答继续追问的技术面试。</p>
          <div class="home-hero__actions">
            <RouterLink class="home-button home-button--primary" :to="ROUTE_PATHS.register">
              开始面试
            </RouterLink>
            <RouterLink
              class="home-text-link"
              :to="{ path: ROUTE_PATHS.home, hash: '#process' }"
            >
              看看怎么进行
              <ArrowRightOutlined aria-hidden="true" />
            </RouterLink>
          </div>
        </div>

        <aside class="interview-preview" aria-label="正式面试界面示例">
          <div class="interview-preview__rail">
            <strong>水水面</strong>
            <span class="interview-preview__rail-item interview-preview__rail-item--active">
              快速面试
            </span>
            <span class="interview-preview__rail-item">综合面试</span>
            <span class="interview-preview__rail-divider"></span>
            <span class="interview-preview__rail-item">
              <FileTextOutlined aria-hidden="true" />
              面试记录
            </span>
          </div>
          <div class="interview-preview__body">
            <header class="interview-preview__topbar">
              <strong>快速面试</strong>
              <div>
                <span><ClockCircleOutlined aria-hidden="true" />60:00</span>
                <span>3 / 8</span>
                <span class="interview-preview__progress" aria-label="考查进度 3/8">
                  <i></i>
                </span>
              </div>
            </header>
            <div class="interview-preview__question">
              <span>当前问题 3</span>
              <h2>如果 Agent 任务执行到一半失败，你会怎样保证它能够继续？</h2>
            </div>
            <div class="interview-preview__answer">
              <span>请结合你的实际项目经验作答…</span>
              <small>0 / 12000</small>
            </div>
            <div class="interview-preview__actions">
              <button type="button" disabled><PauseOutlined />暂停</button>
              <button type="button" disabled>跳过</button>
              <button type="button" class="interview-preview__submit" disabled>
                <SendOutlined />
                提交回答
              </button>
            </div>
            <p class="interview-preview__note">下一题会根据这次回答决定是否继续追问</p>
          </div>
        </aside>
      </div>

      <div id="modes" class="ssm-container home-modes" aria-label="面试模式">
        <RouterLink
          v-for="mode in modes"
          :key="mode.title"
          class="home-mode-entry"
          :to="ROUTE_PATHS.register"
        >
          <h2>{{ mode.title }}</h2>
          <div>
            <strong>{{ mode.meta }}</strong>
            <span>{{ mode.description }}</span>
          </div>
          <ArrowRightOutlined aria-hidden="true" />
        </RouterLink>
      </div>
    </section>

    <section id="process" class="home-section home-process" aria-labelledby="process-title">
      <div class="ssm-container">
        <header class="home-section__heading">
          <p>怎么进行</p>
          <h2 id="process-title">从开始到复盘，只有必要的步骤。</h2>
        </header>
        <ol class="home-process__list">
          <li v-for="step in processSteps" :key="step.number">
            <span>{{ step.number }}</span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.description }}</p>
          </li>
        </ol>
      </div>
    </section>

    <section id="report" class="home-section home-report" aria-labelledby="report-title">
      <div class="ssm-container home-report__grid">
        <div class="home-section__heading">
          <p>面试报告</p>
          <h2 id="report-title">复盘具体回答，不给你一个虚构分数。</h2>
          <span>
            报告会整理实际问答、做得好的地方和优先改进项。没有覆盖到的内容只标记为未评估。
          </span>
        </div>
        <article class="report-preview" aria-label="面试报告结构示例">
          <header>
            <p>快速面试报告</p>
            <span>报告内容示例</span>
          </header>
          <section>
            <small>总结</small>
            <h3>先看这场面试暴露出的关键问题</h3>
            <p>报告只基于本场已提交回答，不使用没有依据的能力标签。</p>
          </section>
          <section class="report-preview__columns">
            <div>
              <small>做得好的地方</small>
              <p>说明具体观点，并引用对应回答。</p>
            </div>
            <div>
              <small>优先改进项</small>
              <p>指出缺失信息和下一次可以补充的内容。</p>
            </div>
          </section>
        </article>
      </div>
    </section>

    <section class="home-section home-comparison" aria-labelledby="comparison-title">
      <div class="ssm-container">
        <header class="home-section__heading">
          <p>两种选择</p>
          <h2 id="comparison-title">看你现在想准备到哪一步。</h2>
        </header>
        <div class="home-comparison__table" role="table" aria-label="快速面试与综合面试对比">
          <div class="home-comparison__row home-comparison__row--head" role="row">
            <span role="columnheader"></span>
            <strong role="columnheader">快速面试</strong>
            <strong role="columnheader">综合面试</strong>
          </div>
          <div v-for="row in comparisonRows" :key="row.label" class="home-comparison__row" role="row">
            <span role="rowheader">{{ row.label }}</span>
            <p role="cell">{{ row.quick }}</p>
            <p role="cell">{{ row.comprehensive }}</p>
          </div>
        </div>
      </div>
    </section>

    <section class="home-section home-faq" aria-labelledby="faq-title">
      <div class="ssm-container home-faq__grid">
        <header class="home-section__heading">
          <p>开始前</p>
          <h2 id="faq-title">你可能还想知道。</h2>
        </header>
        <div class="home-faq__list">
          <details v-for="item in faqItems" :key="item.question">
            <summary>{{ item.question }}</summary>
            <p>{{ item.answer }}</p>
          </details>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  background: var(--ssm-color-paper);
  color: var(--ssm-color-ink);
}

.home-hero {
  padding: 92px 0 0;
}

.home-hero__grid {
  display: grid;
  min-height: 610px;
  grid-template-columns: minmax(360px, 0.78fr) minmax(600px, 1.22fr);
  align-items: center;
  gap: clamp(48px, 6vw, 92px);
}

.home-kicker,
.home-section__heading > p {
  margin: 0 0 22px;
  color: var(--ssm-color-brand);
  font-size: 13px;
  font-weight: 650;
  letter-spacing: 0.08em;
}

.home-hero h1,
.home-section__heading h2 {
  margin: 0;
  font-family: var(--ssm-font-serif);
  font-weight: 600;
  letter-spacing: -0.055em;
}

.home-hero h1 {
  font-size: clamp(58px, 6.1vw, 88px);
  line-height: 1.12;
}

.home-hero__lead {
  margin: 28px 0 0;
  color: var(--ssm-color-text-secondary);
  font-size: clamp(20px, 2vw, 28px);
  line-height: 1.5;
}

.home-hero__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 28px;
  margin-top: 40px;
}

.home-button {
  display: inline-flex;
  min-height: 52px;
  align-items: center;
  justify-content: center;
  padding: 0 28px;
  border-radius: var(--ssm-radius-control);
  font-weight: 650;
}

.home-button--primary {
  background: var(--ssm-color-brand);
  color: #fff;
}

.home-button--primary:hover {
  background: var(--ssm-color-brand-hover);
  color: #fff;
}

.home-text-link {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 5px;
  border-bottom: 1px solid var(--ssm-color-brand);
  color: var(--ssm-color-ink);
  font-weight: 550;
}

.interview-preview {
  display: grid;
  min-height: 500px;
  grid-template-columns: 150px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-panel);
  background: var(--ssm-color-surface);
  box-shadow: 0 24px 70px rgba(23, 26, 28, 0.06);
}

.interview-preview__rail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 28px 14px;
  border-right: 1px solid var(--ssm-color-line);
}

.interview-preview__rail > strong {
  margin: 0 10px 14px;
  font-family: var(--ssm-font-serif);
  font-size: 20px;
}

.interview-preview__rail-item {
  display: flex;
  min-height: 40px;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  color: var(--ssm-color-text-secondary);
  font-size: 14px;
}

.interview-preview__rail-item--active {
  border-radius: 6px;
  background: var(--ssm-color-brand-soft);
  color: var(--ssm-color-brand);
}

.interview-preview__rail-divider {
  height: 1px;
  margin: 10px;
  background: var(--ssm-color-line);
}

.interview-preview__body {
  min-width: 0;
  padding: 28px;
}

.interview-preview__topbar,
.interview-preview__topbar > div,
.interview-preview__actions {
  display: flex;
  align-items: center;
}

.interview-preview__topbar {
  justify-content: space-between;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--ssm-color-line);
}

.interview-preview__topbar > div {
  gap: 18px;
  color: var(--ssm-color-text-secondary);
  font-size: 13px;
}

.interview-preview__topbar span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.interview-preview__progress {
  width: 96px;
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e1dd;
}

.interview-preview__progress i {
  width: 37.5%;
  height: 100%;
  border-radius: inherit;
  background: var(--ssm-color-brand);
}

.interview-preview__question {
  padding: 34px 0 24px;
}

.interview-preview__question > span {
  color: var(--ssm-color-text-secondary);
  font-size: 13px;
}

.interview-preview__question h2 {
  margin: 12px 0 0;
  font-size: clamp(20px, 1.7vw, 27px);
  line-height: 1.5;
}

.interview-preview__answer {
  position: relative;
  min-height: 160px;
  padding: 18px;
  border: 1px solid #c9c7c0;
  border-radius: 8px;
  color: #8a8d90;
  font-size: 14px;
}

.interview-preview__answer small {
  position: absolute;
  right: 16px;
  bottom: 14px;
  color: var(--ssm-color-text-secondary);
}

.interview-preview__actions {
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}

.interview-preview__actions button {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 0 18px;
  border: 1px solid var(--ssm-color-line);
  border-radius: 7px;
  background: var(--ssm-color-surface);
  color: var(--ssm-color-ink);
  opacity: 1;
}

.interview-preview__actions .interview-preview__submit {
  min-width: 154px;
  border-color: var(--ssm-color-brand);
  background: var(--ssm-color-brand);
  color: #fff;
}

.interview-preview__note {
  margin: 16px 0 0;
  padding: 12px 14px;
  border-left: 3px solid var(--ssm-color-warning);
  background: #f7efe3;
  color: #8d5e20;
  font-size: 13px;
}

.home-modes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 54px;
  border-top: 1px solid var(--ssm-color-line);
  border-bottom: 1px solid var(--ssm-color-line);
}

.home-mode-entry {
  display: grid;
  min-height: 112px;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 30px;
  padding: 24px 28px;
}

.home-mode-entry:first-child {
  border-right: 1px solid var(--ssm-color-line);
}

.home-mode-entry:hover {
  background: rgba(232, 241, 246, 0.55);
}

.home-mode-entry h2 {
  margin: 0;
  font-family: var(--ssm-font-serif);
  font-size: 26px;
  font-weight: 600;
}

.home-mode-entry > div {
  display: grid;
  gap: 6px;
  padding-left: 28px;
  border-left: 1px solid var(--ssm-color-line);
}

.home-mode-entry strong {
  font-size: 16px;
  font-weight: 550;
}

.home-mode-entry span {
  color: var(--ssm-color-text-secondary);
  font-size: 13px;
}

.home-section {
  padding: 118px 0;
}

.home-section + .home-section {
  border-top: 1px solid var(--ssm-color-line);
}

.home-section__heading {
  max-width: 680px;
}

.home-section__heading h2 {
  font-size: clamp(40px, 4.5vw, 64px);
  line-height: 1.2;
}

.home-section__heading > span {
  display: block;
  margin-top: 24px;
  color: var(--ssm-color-text-secondary);
  font-size: 17px;
  line-height: 1.8;
}

.home-process__list {
  display: grid;
  margin: 66px 0 0;
  padding: 0;
  border-top: 1px solid var(--ssm-color-line);
  list-style: none;
}

.home-process__list li {
  display: grid;
  grid-template-columns: 90px minmax(220px, 0.65fr) minmax(0, 1fr);
  gap: 30px;
  align-items: baseline;
  padding: 28px 0;
  border-bottom: 1px solid var(--ssm-color-line);
}

.home-process__list li > span {
  color: var(--ssm-color-brand);
  font-variant-numeric: tabular-nums;
  font-size: 13px;
}

.home-process__list h3,
.home-process__list p {
  margin: 0;
}

.home-process__list h3 {
  font-size: 22px;
}

.home-process__list p {
  color: var(--ssm-color-text-secondary);
  line-height: 1.8;
}

.home-report {
  background: var(--ssm-color-surface);
}

.home-report__grid {
  display: grid;
  grid-template-columns: minmax(0, 0.82fr) minmax(520px, 1.18fr);
  align-items: center;
  gap: clamp(60px, 8vw, 120px);
}

.report-preview {
  border: 1px solid var(--ssm-color-line);
  background: var(--ssm-color-paper);
}

.report-preview > header,
.report-preview > section {
  padding: 24px 28px;
}

.report-preview > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--ssm-color-line);
}

.report-preview > header p,
.report-preview section p,
.report-preview section h3 {
  margin: 0;
}

.report-preview > header p {
  font-family: var(--ssm-font-serif);
  font-size: 22px;
  font-weight: 600;
}

.report-preview > header span,
.report-preview small {
  color: var(--ssm-color-text-secondary);
  font-size: 12px;
}

.report-preview section h3 {
  margin-top: 12px;
  font-size: 22px;
}

.report-preview section p {
  margin-top: 10px;
  color: var(--ssm-color-text-secondary);
  line-height: 1.75;
}

.report-preview__columns {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0;
  border-top: 1px solid var(--ssm-color-line);
}

.report-preview__columns > div {
  padding-right: 24px;
}

.report-preview__columns > div + div {
  padding-left: 24px;
  border-left: 1px solid var(--ssm-color-line);
}

.home-comparison__table {
  margin-top: 66px;
  border-top: 1px solid var(--ssm-color-line);
}

.home-comparison__row {
  display: grid;
  grid-template-columns: 180px 1fr 1fr;
  border-bottom: 1px solid var(--ssm-color-line);
}

.home-comparison__row > * {
  margin: 0;
  padding: 22px 24px;
}

.home-comparison__row > * + * {
  border-left: 1px solid var(--ssm-color-line);
}

.home-comparison__row > span,
.home-comparison__row p {
  color: var(--ssm-color-text-secondary);
}

.home-comparison__row--head {
  background: var(--ssm-color-surface);
}

.home-faq__grid {
  display: grid;
  grid-template-columns: minmax(280px, 0.7fr) minmax(0, 1.3fr);
  gap: clamp(70px, 10vw, 150px);
}

.home-faq__list {
  border-top: 1px solid var(--ssm-color-line);
}

.home-faq details {
  border-bottom: 1px solid var(--ssm-color-line);
}

.home-faq summary {
  padding: 24px 4px;
  cursor: pointer;
  font-size: 17px;
  font-weight: 550;
}

.home-faq details p {
  margin: -4px 4px 24px;
  color: var(--ssm-color-text-secondary);
  line-height: 1.8;
}

@media (max-width: 1120px) {
  .home-hero__grid {
    grid-template-columns: 1fr;
  }

  .home-hero__copy {
    max-width: 700px;
  }

  .home-report__grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .home-hero {
    padding-top: 64px;
  }

  .home-hero__grid {
    min-height: auto;
  }

  .home-hero h1 {
    font-size: clamp(50px, 15vw, 68px);
  }

  .interview-preview {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .interview-preview__rail {
    display: none;
  }

  .interview-preview__body {
    padding: 20px;
  }

  .interview-preview__topbar {
    align-items: flex-start;
    gap: 14px;
  }

  .interview-preview__topbar > div {
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 10px;
  }

  .interview-preview__progress {
    display: none;
  }

  .interview-preview__actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .interview-preview__actions .interview-preview__submit {
    grid-column: 1 / -1;
  }

  .home-modes {
    grid-template-columns: 1fr;
  }

  .home-mode-entry:first-child {
    border-right: 0;
    border-bottom: 1px solid var(--ssm-color-line);
  }

  .home-mode-entry {
    grid-template-columns: 1fr auto;
    gap: 14px;
    padding-inline: 4px;
  }

  .home-mode-entry > div {
    grid-column: 1 / -1;
    grid-row: 2;
    padding: 0;
    border: 0;
  }

  .home-section {
    padding: 84px 0;
  }

  .home-process__list li {
    grid-template-columns: 50px 1fr;
    gap: 18px;
  }

  .home-process__list p {
    grid-column: 2;
  }

  .report-preview__columns {
    grid-template-columns: 1fr;
  }

  .report-preview__columns > div + div {
    margin-top: 20px;
    padding: 20px 0 0;
    border-top: 1px solid var(--ssm-color-line);
    border-left: 0;
  }

  .home-comparison__table {
    overflow-x: auto;
  }

  .home-comparison__row {
    min-width: 620px;
  }

  .home-faq__grid {
    grid-template-columns: 1fr;
    gap: 48px;
  }
}
</style>

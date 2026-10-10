<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

import SafeMarkdown from '@/components/content/SafeMarkdown.vue'

const props = withDefaults(
  defineProps<{
    content?: string
    play?: boolean
  }>(),
  {
    content: '',
    play: false,
  },
)

const emit = defineEmits<{
  complete: []
  progress: []
}>()

const visibleContent = ref('')
const typing = ref(false)
let timer: number | undefined
let sequence = 0

watch(
  () => [props.content, props.play] as const,
  ([content, play]) => start(content, play),
  { immediate: true },
)

onBeforeUnmount(stop)

function start(content: string, play: boolean) {
  stop()
  const currentSequence = ++sequence
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  if (!play || reducedMotion || !content) {
    visibleContent.value = content
    typing.value = false
    if (play) {
      queueMicrotask(() => {
        if (currentSequence === sequence) emit('complete')
      })
    }
    return
  }

  const characters = Array.from(content)
  const charactersPerFrame = Math.max(1, Math.ceil(characters.length / 180))
  let visibleCount = 0
  visibleContent.value = ''
  typing.value = true

  const tick = () => {
    if (currentSequence !== sequence) return
    visibleCount = Math.min(visibleCount + charactersPerFrame, characters.length)
    visibleContent.value = characters.slice(0, visibleCount).join('')
    emit('progress')

    if (visibleCount >= characters.length) {
      typing.value = false
      timer = undefined
      emit('complete')
      return
    }
    timer = window.setTimeout(tick, 24)
  }

  tick()
}

function stop() {
  sequence += 1
  if (timer !== undefined) window.clearTimeout(timer)
  timer = undefined
}
</script>

<template>
  <div class="typewriter-markdown" :aria-busy="typing">
    <SafeMarkdown :content="visibleContent" />
    <span v-if="typing" class="typewriter-markdown__cursor" aria-hidden="true" />
  </div>
</template>

<style scoped>
.typewriter-markdown__cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 3px;
  border-radius: 1px;
  background: var(--cf-color-primary);
  vertical-align: -0.12em;
  animation: cursor-blink 0.8s steps(1, end) infinite;
}

@keyframes cursor-blink {
  50% {
    opacity: 0;
  }
}
</style>

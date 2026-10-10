import { beforeEach, describe, expect, it } from 'vitest'

import { renderSafeMarkdown } from '@/shared/markdown/render-markdown'
import { clearSseResumeEventId, readResumeEventId } from '@/shared/sse/sse-client'

describe('safe markdown and SSE resume boundary', () => {
  beforeEach(() => sessionStorage.clear())

  it('renders useful markdown while removing executable HTML', () => {
    const html = renderSafeMarkdown(
      '[official](https://example.com)\n\n`code`\n\n<script>alert(1)</script>',
    )
    expect(html).toContain('rel="noopener noreferrer nofollow"')
    expect(html).toContain('<code>code</code>')
    expect(html).not.toContain('<script')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
  })

  it('does not allow javascript links', () => {
    const html = renderSafeMarkdown('[bad](javascript:alert(1))')
    expect(html).not.toContain('href="javascript:')
  })

  it('isolates and clears resume cursors by caller-supplied actor/resource key', () => {
    sessionStorage.setItem('careerforge:sse:coach:user-a:run-1', '1724200000000-0')
    expect(readResumeEventId('coach:user-a:run-1')).toBe('1724200000000-0')
    expect(readResumeEventId('coach:user-b:run-1')).toBeNull()
    clearSseResumeEventId('coach:user-a:run-1')
    expect(readResumeEventId('coach:user-a:run-1')).toBeNull()
  })
})

import { describe, expect, it } from 'vitest'
import * as compatibility from '@/features/interview/api/interview-api'
import * as current from '@/features/interview/api/interview-flow-api'

describe('interview API compatibility export', () => {
  it('does not retain the legacy mock-interview implementation', () => {
    expect(compatibility.createQuickInterview).toBe(current.createQuickInterview)
    expect('createInterview' in compatibility).toBe(false)
  })
})

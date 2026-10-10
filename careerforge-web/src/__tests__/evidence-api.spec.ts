/* oxlint-disable vitest/require-mock-type-parameters */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const generated = vi.hoisted(() => ({
  get: vi.fn(),
  update: vi.fn(),
  revoke: vi.fn(),
  create: vi.fn(),
  list: vi.fn(),
}))

vi.mock('@/shared/api/generated/careerforge', () => ({
  getPersonalEvidenceArtifact: generated.get,
  updatePersonalEvidenceArtifact: generated.update,
  revokePersonalEvidenceArtifact: generated.revoke,
  createPersonalEvidenceArtifact: generated.create,
  listPersonalEvidenceArtifacts: generated.list,
}))

import {
  createEvidence,
  EvidenceChangedError,
  updateReviewedEvidence,
} from '@/features/evidence/api/evidence-api'

describe('personal evidence workflow', () => {
  beforeEach(() => vi.clearAllMocks())

  it('trims text evidence and never models a file upload', async () => {
    generated.create.mockResolvedValue({ code: 0, data: { artifactId: 'a1', artifactVersion: 1 } })
    await createEvidence({ type: 'RESUME', sourceName: ' resume.md ', rawContent: ' # Java ' })
    expect(generated.create).toHaveBeenCalledWith({
      type: 'RESUME',
      sourceName: 'resume.md',
      rawContent: '# Java',
    })
  })

  it('rereads the active version before creating an immutable update', async () => {
    generated.get.mockResolvedValue({ code: 0, data: { artifactId: 'a1', artifactVersion: 2 } })
    generated.update.mockResolvedValue({ code: 0, data: { artifactId: 'a1', artifactVersion: 3 } })
    await updateReviewedEvidence({ artifactId: 'a1', artifactVersion: 2 }, 'Project', 'Evidence')
    expect(generated.update).toHaveBeenCalledWith('a1', {
      expectedVersion: 2,
      sourceName: 'Project',
      rawContent: 'Evidence',
    })
  })

  it('blocks a stale evidence update', async () => {
    generated.get.mockResolvedValue({ code: 0, data: { artifactId: 'a1', artifactVersion: 3 } })
    await expect(
      updateReviewedEvidence({ artifactId: 'a1', artifactVersion: 2 }, 'Project', 'Evidence'),
    ).rejects.toBeInstanceOf(EvidenceChangedError)
    expect(generated.update).not.toHaveBeenCalled()
  })
})

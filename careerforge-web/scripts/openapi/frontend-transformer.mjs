const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'options', 'head', 'trace']

export const FRONTEND_OPERATIONS = [
  ['GET', '/api/coaching-runs', 'listCoachingRuns'],
  ['GET', '/api/coaching-runs/{runId}', 'getCoachingRun'],
  ['GET', '/api/coaching-sessions', 'listCoachingSessions'],
  ['GET', '/api/coaching-sessions/{sessionId}', 'getCoachingSession'],
  ['GET', '/api/coaching-sessions/{sessionId}/turns', 'listCoachingTurns'],
  ['GET', '/api/me', 'getCurrentUser'],
  ['GET', '/api/memories/confirmed', 'getConfirmedMemoryProfile'],
  ['GET', '/api/memories/confirmed-skills', 'getConfirmedSkillProfile'],
  ['GET', '/api/memories/pending', 'listPendingMemoryCandidates'],
  ['GET', '/api/interviews', 'listInterviews'],
  ['GET', '/api/interviews/resumable', 'getResumableInterview'],
  ['GET', '/api/interviews/{interviewId}', 'getInterview'],
  ['GET', '/api/interviews/{interviewId}/question', 'getInterviewCurrentQuestion'],
  ['GET', '/api/interviews/{interviewId}/history', 'getInterviewHistory'],
  ['GET', '/api/interviews/{interviewId}/report', 'getInterviewReport'],
  ['GET', '/api/personal-evidence-artifacts', 'listPersonalEvidenceArtifacts'],
  ['GET', '/api/personal-evidence-artifacts/{artifactId}', 'getPersonalEvidenceArtifact'],
  ['GET', '/api/skill-gap-snapshots', 'listSkillGapSnapshots'],
  ['GET', '/api/skill-gap-snapshots/{snapshotId}', 'getSkillGapSnapshot'],
  ['GET', '/api/target-role-drafts/{draftId}', 'getTargetRoleDraft'],
  ['GET', '/api/target-roles/{targetRoleId}', 'getTargetRole'],
  ['GET', '/api/target-roles/latest', 'getLatestTargetRole'],
  ['GET', '/api/training-plans', 'listTrainingPlans'],
  ['GET', '/api/training-plans/{planId}', 'getTrainingPlan'],
  ['POST', '/api/auth/login', 'login'],
  ['POST', '/api/auth/logout', 'logout'],
  ['POST', '/api/auth/refresh', 'refreshSession'],
  ['POST', '/api/auth/register', 'register'],
  ['POST', '/api/coaching-runs', 'createCoachingRun'],
  ['POST', '/api/coaching-sessions', 'createCoachingSession'],
  [
    'POST',
    '/api/coaching-sessions/{sessionId}/memory-candidate-extractions',
    'extractMemoryCandidates',
  ],
  ['POST', '/api/knowledge/rag/query', 'queryCareerKnowledge'],
  ['POST', '/api/memories/{existingMemoryId}/replace', 'confirmMemoryReplacement'],
  ['POST', '/api/memories/{memoryId}/confirm', 'confirmMemory'],
  ['POST', '/api/memories/{memoryId}/reject', 'rejectMemory'],
  ['POST', '/api/memories/{memoryId}/revoke', 'revokeMemory'],
  ['POST', '/api/interviews/quick', 'createQuickInterview'],
  ['POST', '/api/interviews/{interviewId}/enter', 'enterInterview'],
  ['POST', '/api/interviews/{interviewId}/heartbeat', 'heartbeatInterview'],
  ['POST', '/api/interviews/{interviewId}/pause', 'pauseInterview'],
  ['POST', '/api/interviews/{interviewId}/resume', 'resumeInterview'],
  ['POST', '/api/interviews/{interviewId}/finish', 'finishInterview'],
  ['POST', '/api/interviews/{interviewId}/retry', 'retryInterviewReport'],
  ['PUT', '/api/interviews/{interviewId}/questions/{questionId}/draft', 'saveInterviewDraft'],
  ['POST', '/api/interviews/{interviewId}/questions/{questionId}/submit', 'submitInterviewAnswer'],
  ['POST', '/api/interviews/{interviewId}/questions/{questionId}/skip', 'skipInterviewQuestion'],
  ['POST', '/api/personal-evidence-artifacts', 'createPersonalEvidenceArtifact'],
  [
    'POST',
    '/api/personal-evidence-artifacts/{artifactId}/revoke',
    'revokePersonalEvidenceArtifact',
  ],
  [
    'POST',
    '/api/personal-evidence-artifacts/{artifactId}/versions',
    'updatePersonalEvidenceArtifact',
  ],
  ['POST', '/api/skill-gap-snapshots', 'generateSkillGapSnapshot'],
  ['POST', '/api/target-role-drafts', 'createTargetRoleDraft'],
  ['POST', '/api/target-role-drafts/{draftId}/confirm', 'confirmTargetRoleDraft'],
  ['POST', '/api/training-plans', 'generateTrainingPlan'],
  ['POST', '/api/training-plans/{planId}/cancel', 'cancelTrainingPlan'],
  ['POST', '/api/training-plans/{planId}/complete', 'completeTrainingPlan'],
  ['POST', '/api/training-plans/{planId}/confirm', 'confirmTrainingPlan'],
  ['POST', '/api/training-plans/{planId}/items/{itemId}/complete', 'completeTrainingPlanItem'],
  ['POST', '/api/training-plans/{planId}/items/{itemId}/start', 'startTrainingPlanItem'],
]

const operationKey = (method, path) => `${method.toUpperCase()} ${path}`

const allowedOperations = new Map(
  FRONTEND_OPERATIONS.map(([method, path, operationId]) => [
    operationKey(method, path),
    operationId,
  ]),
)

if (allowedOperations.size !== FRONTEND_OPERATIONS.length) {
  throw new Error('Frontend OpenAPI whitelist contains duplicate method and path entries.')
}

export default function frontendTransformer(document) {
  const seenOperations = new Set()

  for (const [path, pathItem] of Object.entries(document.paths ?? {})) {
    if (!pathItem || typeof pathItem !== 'object') {
      delete document.paths[path]
      continue
    }

    for (const method of HTTP_METHODS) {
      const operation = pathItem[method]
      if (!operation) {
        continue
      }

      const key = operationKey(method, path)
      const operationId = allowedOperations.get(key)
      if (!operationId) {
        delete pathItem[method]
        continue
      }

      operation.operationId = operationId
      operation.tags = ['frontend']
      seenOperations.add(key)
    }

    const hasAllowedOperation = HTTP_METHODS.some((method) => pathItem[method])
    if (!hasAllowedOperation) {
      delete document.paths[path]
    }
  }

  const missingOperations = [...allowedOperations.keys()].filter((key) => !seenOperations.has(key))
  if (missingOperations.length > 0) {
    throw new Error(
      `Frontend OpenAPI whitelist is out of sync. Missing operations: ${missingOperations.join(', ')}`,
    )
  }

  if (seenOperations.size !== FRONTEND_OPERATIONS.length) {
    throw new Error(
      `Expected ${FRONTEND_OPERATIONS.length} frontend operations, found ${seenOperations.size}.`,
    )
  }

  return document
}

import assert from 'node:assert/strict'
import test from 'node:test'

import frontendTransformer, { FRONTEND_OPERATIONS } from './frontend-transformer.mjs'

const buildDocument = () => {
  const paths = {}

  for (const [method, path] of FRONTEND_OPERATIONS) {
    paths[path] ??= {}
    paths[path][method.toLowerCase()] = {
      operationId: 'unstableGeneratedName',
      tags: ['backend-controller'],
    }
  }

  return { openapi: '3.1.0', info: { title: 'test', version: '1' }, paths }
}

test('keeps only the frozen frontend REST operations with stable names', () => {
  const document = buildDocument()
  document.paths['/health'] = { get: { operationId: 'health' } }
  document.paths['/api/mock-interviews/{interviewId}/events'] = {
    get: { operationId: 'events' },
  }

  const transformed = frontendTransformer(document)
  const operations = Object.entries(transformed.paths).flatMap(([path, pathItem]) =>
    Object.entries(pathItem)
      .filter(([method]) => ['get', 'post', 'put'].includes(method))
      .map(([method, operation]) => ({ method: method.toUpperCase(), path, operation })),
  )

  assert.equal(operations.length, FRONTEND_OPERATIONS.length)
  assert.equal(transformed.paths['/health'], undefined)
  assert.equal(transformed.paths['/api/mock-interviews/{interviewId}/events'], undefined)

  for (const [method, path, operationId] of FRONTEND_OPERATIONS) {
    assert.equal(transformed.paths[path][method.toLowerCase()].operationId, operationId)
    assert.deepEqual(transformed.paths[path][method.toLowerCase()].tags, ['frontend'])
  }
})

test('fails generation when a frozen frontend operation disappears', () => {
  const document = buildDocument()
  delete document.paths['/api/me'].get

  assert.throws(() => frontendTransformer(document), /Missing operations: GET \/api\/me/)
})

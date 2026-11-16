import assert from 'node:assert/strict'
import test from 'node:test'
import { ApiError, requestJson } from '../src/apiClient.js'

function installFetch(t, implementation) {
  const originalFetch = globalThis.fetch
  globalThis.fetch = implementation
  t.after(() => {
    globalThis.fetch = originalFetch
  })
}

test('returns a successful response only when it matches the supplied contract', async (t) => {
  installFetch(t, async () => new Response('{"ok":true}', { status: 200 }))

  const result = await requestJson('/api/test', undefined, undefined, (value) => value?.ok === true)

  assert.deepEqual(result, { ok: true })
})

test('reports malformed success JSON and contract mismatches explicitly', async (t) => {
  let responseBody = '{bad json'
  installFetch(t, async () => new Response(responseBody, { status: 200 }))
  await assert.rejects(
    requestJson('/api/test', undefined, undefined, () => true),
    (error) => error instanceof ApiError && error.code === 'INVALID_RESPONSE',
  )

  responseBody = '{"unexpected":true}'
  await assert.rejects(
    requestJson('/api/test', undefined, undefined, () => false),
    (error) => error instanceof ApiError && error.code === 'INVALID_RESPONSE',
  )
})

test('preserves API validation errors and maps unavailable service errors', async (t) => {
  let responseStatus = 400
  let responseBody = { code: 'INVALID_REQUEST', message: 'area 값이 올바르지 않습니다.', field: 'area' }
  installFetch(t, async () => new Response(JSON.stringify(responseBody), { status: responseStatus }))
  await assert.rejects(
    requestJson('/api/test', undefined, undefined, () => true),
    (error) => error instanceof ApiError
      && error.code === 'INVALID_REQUEST'
      && error.field === 'area'
      && error.message === 'area 값이 올바르지 않습니다.',
  )

  responseStatus = 503
  responseBody = { code: 'LLM_UNAVAILABLE' }
  await assert.rejects(
    requestJson('/api/test', undefined, undefined, () => true),
    (error) => error instanceof ApiError && error.code === 'LLM_UNAVAILABLE',
  )
})

test('maps network failures to a user-facing network error', async (t) => {
  installFetch(t, async () => {
    throw new TypeError('fetch failed')
  })

  await assert.rejects(
    requestJson('/api/test', undefined, undefined, () => true),
    (error) => error instanceof ApiError && error.code === 'NETWORK_ERROR',
  )
})

test('maps request timeout to a user-facing timeout error', async (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  installFetch(t, async (_path, init) => new Promise((_resolve, reject) => {
    init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true })
  }))

  const pending = requestJson('/api/test', undefined, undefined, () => true)
  const rejected = assert.rejects(
    pending,
    (error) => error instanceof ApiError && error.code === 'TIMEOUT',
  )
  t.mock.timers.tick(60_000)
  await rejected
})

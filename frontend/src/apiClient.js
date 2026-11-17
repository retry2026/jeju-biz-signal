const REQUEST_TIMEOUT_MS = 60_000

function isRecord(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export class ApiError extends Error {
  constructor(message, code, field) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.field = field
  }
}

function getErrorMessage(code, body) {
  if (code === 'INVALID_REQUEST') {
    return typeof body.message === 'string' ? body.message : '입력 내용을 확인해 주세요.'
  }
  if (code === 'LLM_UNAVAILABLE') {
    return '답변 서비스를 잠시 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.'
  }
  return '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

async function getErrorBody(response) {
  try {
    const body = await response.json()
    return isRecord(body) ? body : {}
  } catch {
    return {}
  }
}

export async function requestJson(path, init, externalSignal, isValidResponse) {
  const controller = new AbortController()
  let timedOut = false
  const abortRequest = () => controller.abort()
  if (externalSignal?.aborted) controller.abort()
  else externalSignal?.addEventListener('abort', abortRequest, { once: true })
  const timeoutId = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, REQUEST_TIMEOUT_MS)

  try {
    const response = await fetch(path, {
      ...init,
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
    })

    if (!response.ok) {
      const body = await getErrorBody(response)
      const code = typeof body.code === 'string' ? body.code : `HTTP_${response.status}`
      const field = typeof body.field === 'string' ? body.field : undefined
      throw new ApiError(getErrorMessage(code, body), code, field)
    }

    let body
    try {
      body = await response.json()
    } catch {
      throw new ApiError('서버 응답 형식이 올바르지 않습니다. 다시 시도해 주세요.', 'INVALID_RESPONSE')
    }
    if (!isValidResponse(body)) {
      throw new ApiError('서버 응답 형식이 올바르지 않습니다. 다시 시도해 주세요.', 'INVALID_RESPONSE')
    }
    return body
  } catch (error) {
    if (timedOut) {
      throw new ApiError('응답이 60초 안에 도착하지 않았습니다. 잠시 후 다시 시도해 주세요.', 'TIMEOUT')
    }
    if (error instanceof ApiError) throw error
    if (error instanceof TypeError) {
      throw new ApiError('서버에 연결할 수 없습니다. 연결 상태를 확인한 뒤 다시 시도해 주세요.', 'NETWORK_ERROR')
    }
    throw error
  } finally {
    clearTimeout(timeoutId)
    externalSignal?.removeEventListener('abort', abortRequest)
  }
}

import type { ChatRequest, ChatResponse, MetaResponse } from './types'
import { isChatResponse, isMetaResponse } from './apiContracts.js'
import { requestJson } from './apiClient.js'

export type {
  Area,
  ChatChart,
  ChatRequest,
  ChatResponse,
  ChatSection,
  ChartType,
  Evidence,
  Industry,
  MetaResponse,
  ResponseType,
  UserType,
} from './types'

export { ApiError } from './apiClient.js'

export function getMeta() {
  return requestJson<MetaResponse>('/api/meta', undefined, undefined, isMetaResponse)
}

export function sendChat(payload: ChatRequest, signal?: AbortSignal) {
  return requestJson<ChatResponse>('/api/chat', {
    method: 'POST',
    body: JSON.stringify(payload),
  }, signal, isChatResponse)
}

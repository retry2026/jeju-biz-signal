import type { ChatResponse, MetaResponse } from './types'

export function isMetaResponse(value: unknown): value is MetaResponse
export function isChatResponse(value: unknown): value is ChatResponse

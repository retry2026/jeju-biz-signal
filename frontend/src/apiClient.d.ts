export class ApiError extends Error {
  readonly code: string
  readonly field?: string

  constructor(message: string, code: string, field?: string)
}

export function requestJson<T>(
  path: string,
  init: RequestInit | undefined,
  externalSignal: AbortSignal | undefined,
  isValidResponse: (value: unknown) => value is T,
): Promise<T>

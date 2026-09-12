import type { ApiErrorBody } from './types'

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly body: ApiErrorBody | null

  constructor(status: number, body: ApiErrorBody | null) {
    super(body?.message ?? `HTTP ${status}`)
    this.status = status
    this.code = body?.code ?? 'UNKNOWN'
    this.body = body
  }
}

async function parseError(response: Response): Promise<never> {
  let body: ApiErrorBody | null = null
  try {
    body = (await response.json()) as ApiErrorBody
  } catch {
    // non-JSON error body
  }
  throw new ApiError(response.status, body)
}

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(path)
  if (!response.ok) await parseError(response)
  return response.json() as Promise<T>
}

export async function apiSend<T>(
  method: 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<T | null> {
  const response = await fetch(path, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) await parseError(response)
  if (response.status === 204) return null
  return response.json() as Promise<T>
}

/** Multipart POST of one or more files under the `files` field. */
export async function apiUploadMany<T>(path: string, files: File[]): Promise<T> {
  const form = new FormData()
  for (const file of files) form.append('files', file)
  const response = await fetch(path, { method: 'POST', body: form })
  if (!response.ok) await parseError(response)
  return response.json() as Promise<T>
}

export async function apiUpload<T>(path: string, file: File): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(path, { method: 'PUT', body: form })
  if (!response.ok) await parseError(response)
  return response.json() as Promise<T>
}

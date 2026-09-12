import i18n from '../i18n'
import { ApiError } from '../api/client'

/**
 * Turns any failure into a localized message. The backend sends a stable `code` with every
 * error; anything without a translation falls back to a generic message rather than leaking
 * server text at the user.
 */
export function messageForError(error: unknown): string {
  if (error instanceof ApiError) {
    const key = `errors.${error.code}`
    const translated = i18n.t(key)
    if (translated !== key) return translated
    return i18n.t('errors.UNKNOWN')
  }
  // fetch() rejects with a TypeError when the network/server is unreachable.
  if (error instanceof TypeError) return i18n.t('errors.NETWORK')
  return i18n.t('errors.UNKNOWN')
}

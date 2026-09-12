import { describe, expect, it, beforeEach } from 'vitest'
import { ApiError } from '../api/client'
import { messageForError } from './apiErrors'
import i18n from '../i18n'

describe('messageForError', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('zh-CN')
  })

  it('maps a known backend code to a localized message', () => {
    expect(messageForError(new ApiError(400, { status: 400, code: 'DUPLICATE_DATE', message: 'x' })))
      .toBe('这一天已经有记录了')
  })

  it('falls back to a generic message for an unknown code rather than server text', () => {
    const error = new ApiError(500, { status: 500, code: 'SOMETHING_NEW', message: 'stack trace leak' })
    expect(messageForError(error)).toBe('出错了，请重试')
  })

  it('recognises an unreachable server', () => {
    expect(messageForError(new TypeError('Failed to fetch'))).toBe('无法连接到服务器，请检查网络')
  })

  it('translates with the active language', async () => {
    await i18n.changeLanguage('en')
    expect(messageForError(new ApiError(413, { status: 413, code: 'FILE_TOO_LARGE', message: 'x' })))
      .toContain('too large')
  })
})

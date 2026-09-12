import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { VoiceRecorder } from './VoiceRecorder'
import { renderApp } from '../../test/render'

/** A MediaRecorder stand-in whose lifecycle the test drives by hand. */
class FakeMediaRecorder {
  static isTypeSupported = (type: string) => type === 'audio/webm;codecs=opus'
  static last: FakeMediaRecorder | null = null

  ondataavailable: ((event: { data: Blob }) => void) | null = null
  onstop: (() => void) | null = null
  readonly mimeType: string
  readonly stream: MediaStream
  started = false

  constructor(stream: MediaStream, options?: { mimeType?: string }) {
    this.stream = stream
    this.mimeType = options?.mimeType ?? 'audio/webm'
    FakeMediaRecorder.last = this
  }

  start() {
    this.started = true
  }

  stop() {
    this.ondataavailable?.({ data: new Blob(['voice-bytes'], { type: 'audio/webm' }) })
    this.onstop?.()
  }
}

function stubMediaApis({ denied = false } = {}) {
  const tracks = [{ stop: vi.fn() }]
  const stream = { getTracks: () => tracks } as unknown as MediaStream
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
  vi.stubGlobal('navigator', {
    ...navigator,
    mediaDevices: {
      getUserMedia: denied
        ? vi.fn().mockRejectedValue(new Error('NotAllowedError'))
        : vi.fn().mockResolvedValue(stream),
    },
  })
  return { tracks }
}

afterEach(() => {
  vi.unstubAllGlobals()
  FakeMediaRecorder.last = null
})

describe('VoiceRecorder', () => {
  it('records and hands back a file with a clean audio type', async () => {
    stubMediaApis()
    const onRecorded = vi.fn()
    renderApp(<VoiceRecorder onRecorded={onRecorded} />)

    await userEvent.click(screen.getByRole('button', { name: /录音/ }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('录音中'))

    await userEvent.click(screen.getByRole('button', { name: '停止' }))

    await waitFor(() => expect(onRecorded).toHaveBeenCalled())
    const file: File = onRecorded.mock.calls[0][0]
    // The codecs parameter must be stripped, or the server refuses the upload.
    expect(file.type).toBe('audio/webm')
    expect(file.name).toMatch(/^voice-.*\.weba$/)
    expect(file.size).toBeGreaterThan(0)
  })

  it('prefers a type the browser says it supports', async () => {
    stubMediaApis()
    renderApp(<VoiceRecorder onRecorded={vi.fn()} />)
    await userEvent.click(screen.getByRole('button', { name: /录音/ }))

    await waitFor(() => expect(FakeMediaRecorder.last?.started).toBe(true))
    expect(FakeMediaRecorder.last?.mimeType).toBe('audio/webm;codecs=opus')
  })

  it('releases the microphone when recording stops', async () => {
    const { tracks } = stubMediaApis()
    renderApp(<VoiceRecorder onRecorded={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: /录音/ }))
    await waitFor(() => expect(screen.getByRole('status')).toBeInTheDocument())
    await userEvent.click(screen.getByRole('button', { name: '停止' }))

    await waitFor(() => expect(tracks[0].stop).toHaveBeenCalled())
  })

  it('reports a refused microphone instead of hanging', async () => {
    stubMediaApis({ denied: true })
    const onRecorded = vi.fn()
    renderApp(<VoiceRecorder onRecorded={onRecorded} />)

    await userEvent.click(screen.getByRole('button', { name: /录音/ }))

    expect(await screen.findByRole('alert')).toHaveTextContent('无法使用麦克风')
    expect(onRecorded).not.toHaveBeenCalled()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  // Over plain http on a LAN address there is no getUserMedia at all.
  it('explains itself when the browser cannot record', () => {
    vi.stubGlobal('navigator', { ...navigator, mediaDevices: undefined })
    renderApp(<VoiceRecorder onRecorded={vi.fn()} />)

    expect(screen.getByText(/需要 HTTPS 或 localhost/)).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })
})

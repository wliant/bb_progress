import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { showToast } from '../../lib/toast'

/** In preference order; the first the browser can actually produce wins. */
const CANDIDATE_TYPES = [
  'audio/webm;codecs=opus',
  'audio/webm',
  'audio/mp4',
  'audio/ogg;codecs=opus',
]

const EXTENSION: Record<string, string> = {
  'audio/webm': 'weba',
  'audio/mp4': 'm4a',
  'audio/ogg': 'ogg',
  'audio/mpeg': 'mp3',
}

/**
 * getUserMedia only exists in a secure context, so recording works on https and on
 * localhost but not when the app is reached over plain http on a LAN address.
 */
export function recordingSupported(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    navigator.mediaDevices?.getUserMedia !== undefined &&
    typeof MediaRecorder !== 'undefined'
  )
}

function pickMimeType(): string | undefined {
  return CANDIDATE_TYPES.find((type) => MediaRecorder.isTypeSupported?.(type))
}

function baseType(mimeType: string): string {
  return mimeType.split(';')[0].trim().toLowerCase()
}

export function VoiceRecorder({ onRecorded }: { onRecorded: (file: File) => void }) {
  const { t } = useTranslation()
  const [recording, setRecording] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const recorder = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])
  const ticker = useRef<ReturnType<typeof setInterval> | null>(null)

  // Never leave the microphone open if the dialog closes mid-recording.
  useEffect(() => {
    return () => {
      if (ticker.current) clearInterval(ticker.current)
      recorder.current?.stream.getTracks().forEach((track) => track.stop())
    }
  }, [])

  if (!recordingSupported()) {
    return <p className="mt-1 text-xs text-slate-400">{t('media.recordUnavailable')}</p>
  }

  async function start() {
    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    } catch {
      showToast(t('media.microphoneDenied'))
      return
    }

    const mimeType = pickMimeType()
    const instance = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
    chunks.current = []
    instance.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.current.push(event.data)
    }
    instance.onstop = () => {
      stream.getTracks().forEach((track) => track.stop())
      const type = baseType(instance.mimeType || mimeType || 'audio/webm')
      const blob = new Blob(chunks.current, { type })
      if (blob.size > 0) {
        const stamp = new Date().toISOString().replace(/[:.]/g, '-')
        onRecorded(new File([blob], `voice-${stamp}.${EXTENSION[type] ?? 'weba'}`, { type }))
      }
    }

    recorder.current = instance
    instance.start()
    setRecording(true)
    setSeconds(0)
    ticker.current = setInterval(() => setSeconds((value) => value + 1), 1000)
  }

  function stop() {
    if (ticker.current) clearInterval(ticker.current)
    ticker.current = null
    recorder.current?.stop()
    setRecording(false)
  }

  if (recording) {
    return (
      <div className="mt-2 flex items-center gap-3 rounded-lg bg-rose-50 px-3 py-2">
        <span aria-hidden className="h-2 w-2 animate-pulse rounded-full bg-rose-600" />
        <span className="text-sm text-rose-700" role="status">
          {t('media.recording')} {formatDuration(seconds)}
        </span>
        <button
          type="button"
          onClick={stop}
          className="ml-auto rounded-lg bg-rose-500 px-3 py-1 text-sm font-semibold text-white"
        >
          {t('media.stopRecording')}
        </button>
      </div>
    )
  }

  return (
    <button
      type="button"
      onClick={() => void start()}
      className="text-sm font-medium text-rose-600 hover:underline"
    >
      🎤 {t('media.record')}
    </button>
  )
}

function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60)
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`
}

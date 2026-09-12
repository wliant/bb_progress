import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import type { Media, MediaKind } from '../../api/types'

export const KIND_ICON: Record<MediaKind, string> = {
  PHOTO: '🖼️',
  VIDEO: '🎬',
  AUDIO: '🎤',
}

/** Everything the browser can play back; the server refuses anything else. */
export const ACCEPTED_TYPES =
  'image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm,' +
  'audio/mpeg,audio/mp4,audio/aac,audio/wav,audio/ogg,audio/webm'

export interface PendingMedia {
  file: File
  previewUrl: string
}

interface Props {
  media: Media[]
  onAdd: (files: File[]) => void
  onRemove: (mediaId: string) => void
  /** Files chosen but not yet uploaded, shown alongside the saved ones. */
  pending?: PendingMedia[]
  onRemovePending?: (index: number) => void
  busy?: boolean
}

/**
 * One picker for both care-log entries and milestones: a strip of what is attached, each with a
 * remove control, and one button to add more. Photos preview; video and voice show their kind,
 * because the app has no transcoder to pull a frame out of them.
 */
export function MediaPicker({ media, onAdd, onRemove, pending = [], onRemovePending, busy }: Props) {
  const { t } = useTranslation()
  const input = useRef<HTMLInputElement>(null)

  function onChange(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (files.length > 0) onAdd(files)
  }

  return (
    <div>
      <p className="mb-2 text-sm font-medium text-slate-700">{t('media.attachments')}</p>

      {(media.length > 0 || pending.length > 0) && (
        <ul className="mb-2 grid grid-cols-3 gap-2 sm:grid-cols-4">
          {media.map((item) => (
            <li key={item.id} className="relative">
              <Tile kind={item.kind} thumbnailUrl={item.thumbnailUrl} />
              <button
                type="button"
                onClick={() => onRemove(item.id)}
                aria-label={t('media.remove')}
                className="absolute -right-1 -top-1 flex h-6 w-6 items-center justify-center rounded-full bg-slate-800/80 text-xs text-white"
              >
                ✕
              </button>
            </li>
          ))}
          {pending.map((item, index) => (
            <li key={`pending-${index}`} className="relative">
              <Tile
                kind={item.file.type.startsWith('video') ? 'VIDEO'
                  : item.file.type.startsWith('audio') ? 'AUDIO' : 'PHOTO'}
                thumbnailUrl={item.file.type.startsWith('image') ? item.previewUrl : null}
                dimmed
              />
              {onRemovePending && (
                <button
                  type="button"
                  onClick={() => onRemovePending(index)}
                  aria-label={t('media.remove')}
                  className="absolute -right-1 -top-1 flex h-6 w-6 items-center justify-center rounded-full bg-slate-800/80 text-xs text-white"
                >
                  ✕
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <input
        ref={input}
        type="file"
        multiple
        accept={ACCEPTED_TYPES}
        className="hidden"
        onChange={onChange}
        data-testid="media-input"
      />
      <button
        type="button"
        onClick={() => input.current?.click()}
        disabled={busy}
        className="text-sm font-medium text-rose-600 hover:underline disabled:opacity-50"
      >
        {busy ? t('media.uploading') : t('media.add')}
      </button>
      <p className="mt-1 text-xs text-slate-400">{t('media.hint')}</p>
      {pending.length > 0 && (
        <p className="mt-1 text-xs text-slate-400">{t('media.pending', { count: pending.length })}</p>
      )}
    </div>
  )
}

function Tile({
  kind,
  thumbnailUrl,
  dimmed,
}: {
  kind: MediaKind
  thumbnailUrl: string | null
  dimmed?: boolean
}) {
  const { t } = useTranslation()
  if (thumbnailUrl) {
    return (
      <img
        src={thumbnailUrl}
        alt={t(`media.kind.${kind}`)}
        className={`aspect-square w-full rounded-lg bg-slate-100 object-cover ${dimmed ? 'opacity-60' : ''}`}
      />
    )
  }
  return (
    <div
      className={`flex aspect-square w-full flex-col items-center justify-center gap-1 rounded-lg bg-slate-100 ${dimmed ? 'opacity-60' : ''}`}
    >
      <span aria-hidden className="text-2xl">{KIND_ICON[kind]}</span>
      <span className="text-[10px] text-slate-500">{t(`media.kind.${kind}`)}</span>
    </div>
  )
}

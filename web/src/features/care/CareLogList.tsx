import { useTranslation } from 'react-i18next'
import type { CareLog, CareType, Media } from '../../api/types'
import { KIND_ICON } from '../media/MediaPicker'
import { timeSgt } from '../../lib/dates'

export const CARE_ICONS: Record<CareType, string> = {
  FEEDING: '🍼',
  SLEEP: '😴',
  DIAPER: '🧷',
}

interface Props {
  logs: CareLog[]
  onSelect: (log: CareLog) => void
}

/** Each row opens the edit dialog — that is how a one-tap entry gets its note. */
/** The first attachment as a tile, with a count when there are more. */
function MediaBadge({ media }: { media: Media[] }) {
  const { t } = useTranslation()
  const first = media[0]
  return (
    <span className="relative shrink-0">
      {first.thumbnailUrl ? (
        <img src={first.thumbnailUrl} alt={t(`media.kind.${first.kind}`)}
          className="h-10 w-10 rounded-md object-cover" />
      ) : (
        <span className="flex h-10 w-10 items-center justify-center rounded-md bg-slate-100 text-lg"
          aria-label={t(`media.kind.${first.kind}`)} role="img">
          {KIND_ICON[first.kind]}
        </span>
      )}
      {media.length > 1 && (
        <span className="absolute -right-1 -top-1 rounded-full bg-slate-800 px-1.5 text-[10px] text-white">
          {media.length}
        </span>
      )}
    </span>
  )
}

export function CareLogList({ logs, onSelect }: Props) {
  const { t } = useTranslation()

  return (
    <ul className="divide-y divide-slate-100">
      {logs.map((log) => (
        <li key={log.id}>
          <button
            type="button"
            onClick={() => onSelect(log)}
            className="flex w-full items-center gap-3 py-3 text-left text-sm hover:bg-slate-50"
          >
            <span aria-hidden className="text-xl">{CARE_ICONS[log.type]}</span>
            <span className="w-14 shrink-0 font-mono text-slate-700">{timeSgt(log.loggedAt)}</span>
            <span className="flex-1 text-slate-600">
              {t(`care.${log.type}`)}
              {log.note && <span className="text-slate-400"> · {log.note}</span>}
            </span>
            {log.media.length > 0 && <MediaBadge media={log.media} />}
            <span aria-hidden className="text-slate-300">›</span>
          </button>
        </li>
      ))}
    </ul>
  )
}

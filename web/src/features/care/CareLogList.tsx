import { useTranslation } from 'react-i18next'
import type { CareLog, CareType } from '../../api/types'
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
            <span aria-hidden className="text-slate-300">›</span>
          </button>
        </li>
      ))}
    </ul>
  )
}

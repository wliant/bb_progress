import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useCreateCareLog } from '../api/hooks'
import type { CareType } from '../api/types'

const TYPES: { type: CareType; icon: string; color: string }[] = [
  { type: 'FEEDING', icon: '🍼', color: 'bg-amber-100 text-amber-800 active:bg-amber-200' },
  { type: 'SLEEP', icon: '😴', color: 'bg-indigo-100 text-indigo-800 active:bg-indigo-200' },
  { type: 'DIAPER', icon: '🧷', color: 'bg-emerald-100 text-emerald-800 active:bg-emerald-200' },
]

/**
 * One-tap care logging. Without `loggedAt` the server stamps the entry "now"; the care page passes one
 * to backfill a past day. `disabled` blocks taps while that time is incomplete.
 */
export function QuickCareButtons({ loggedAt, disabled = false }: { loggedAt?: string; disabled?: boolean } = {}) {
  const { t } = useTranslation()
  const createLog = useCreateCareLog()
  const [toast, setToast] = useState<string | null>(null)

  function log(type: CareType) {
    createLog.mutate(
      { type, loggedAt },
      {
        onSuccess: () => {
          setToast(t('care.logged', { type: t(`care.${type}`) }))
          setTimeout(() => setToast(null), 2500)
        },
      },
    )
  }

  return (
    <div>
      <div className="grid grid-cols-3 gap-3">
        {TYPES.map(({ type, icon, color }) => (
          <button
            key={type}
            type="button"
            onClick={() => log(type)}
            disabled={disabled || createLog.isPending}
            className={`flex flex-col items-center gap-1 rounded-2xl py-4 text-sm font-semibold shadow-sm transition disabled:opacity-50 ${color}`}
          >
            <span className="text-3xl" aria-hidden>{icon}</span>
            {t(`care.${type}`)}
          </button>
        ))}
      </div>
      {toast && (
        <div role="status" className="mt-3 rounded-lg bg-slate-800 px-4 py-2 text-center text-sm text-white">
          {toast}
        </div>
      )}
    </div>
  )
}

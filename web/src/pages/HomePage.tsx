import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useCareLogs } from '../api/hooks'
import { QuickCareButtons } from '../components/QuickCareButtons'
import { RequireBaby } from '../components/RequireBaby'
import { CareLogList } from '../features/care/CareLogList'
import { CareLogEditDialog } from '../features/care/CareLogEditDialog'
import { ageParts, todaySgt } from '../lib/dates'
import type { Baby, CareLog, CareType } from '../api/types'

const RECENT_LIMIT = 5

export function HomePage() {
  return <RequireBaby>{(baby) => <HomeContent baby={baby} />}</RequireBaby>
}

function HomeContent({ baby }: { baby: Baby }) {
  const { t } = useTranslation()
  const today = todaySgt()
  const { data: logs = [] } = useCareLogs(today)
  const [editing, setEditing] = useState<CareLog | null>(null)

  const age = ageParts(baby.dateOfBirth, today)
  const counts: Record<CareType, number> = { FEEDING: 0, SLEEP: 0, DIAPER: 0 }
  for (const log of logs) counts[log.type] += 1
  const recent = logs.slice(0, RECENT_LIMIT)

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <section className="flex items-center gap-4 rounded-2xl bg-white p-6 shadow-sm">
        {baby.hasPhoto ? (
          <img
            src={`/api/baby/photo?v=${baby.photoVersion}`}
            alt={baby.name}
            className="h-16 w-16 rounded-full object-cover"
          />
        ) : (
          <span className="flex h-16 w-16 items-center justify-center rounded-full bg-rose-100 text-3xl" aria-hidden>
            👶
          </span>
        )}
        <div>
          <h2 className="text-xl font-bold text-slate-800">{baby.name}</h2>
          <p className="text-sm text-slate-500">
            {t('home.age')}: {t('profile.ageLabel', { months: age.months, days: age.days })}
          </p>
          <p className="text-xs text-slate-400">
            {baby.dateOfBirth}
            {baby.timeOfBirth && ` ${baby.timeOfBirth.slice(0, 5)}`}
          </p>
        </div>
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-sm">
        <h3 className="mb-3 font-semibold text-slate-700">{t('home.quickActions')}</h3>
        <QuickCareButtons />
        <p className="mt-4 text-sm text-slate-500">
          {t('care.todayCounts')}: 🍼 {counts.FEEDING} · 😴 {counts.SLEEP} · 🧷 {counts.DIAPER}
        </p>
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-sm">
        <div className="mb-1 flex items-baseline justify-between">
          <h3 className="font-semibold text-slate-700">{t('home.recent')}</h3>
          <Link to="/care" className="text-sm font-medium text-rose-600 hover:underline">
            {t('home.viewAll')}
          </Link>
        </div>
        {recent.length === 0 ? (
          <p className="py-2 text-sm text-slate-500">{t('care.noLogs')}</p>
        ) : (
          <>
            <p className="mb-1 text-xs text-slate-400">{t('home.tapToEdit')}</p>
            <CareLogList logs={recent} onSelect={setEditing} />
          </>
        )}
      </section>

      {editing && <CareLogEditDialog log={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

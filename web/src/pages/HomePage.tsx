import { useTranslation } from 'react-i18next'
import { useCareLogs } from '../api/hooks'
import { QuickCareButtons } from '../components/QuickCareButtons'
import { RequireBaby } from '../components/RequireBaby'
import { ageParts, todaySgt } from '../lib/dates'
import type { Baby, CareType } from '../api/types'

export function HomePage() {
  return <RequireBaby>{(baby) => <HomeContent baby={baby} />}</RequireBaby>
}

function HomeContent({ baby }: { baby: Baby }) {
  const { t } = useTranslation()
  const today = todaySgt()
  const { data: logs } = useCareLogs(today)

  const age = ageParts(baby.dateOfBirth, today)
  const counts: Record<CareType, number> = { FEEDING: 0, SLEEP: 0, DIAPER: 0 }
  for (const log of logs ?? []) counts[log.type] += 1

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
        </div>
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-sm">
        <h3 className="mb-3 font-semibold text-slate-700">{t('home.quickActions')}</h3>
        <QuickCareButtons />
        <p className="mt-4 text-sm text-slate-500">
          {t('care.todayCounts')}: 🍼 {counts.FEEDING} · 😴 {counts.SLEEP} · 🧷 {counts.DIAPER}
        </p>
      </section>
    </div>
  )
}

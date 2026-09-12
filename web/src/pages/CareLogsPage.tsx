import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useCareLogs } from '../api/hooks'
import { QuickCareButtons } from '../components/QuickCareButtons'
import { ErrorState } from '../components/ErrorState'
import { RequireBaby } from '../components/RequireBaby'
import { CareLogList } from '../features/care/CareLogList'
import { CareLogEditDialog } from '../features/care/CareLogEditDialog'
import type { CareLog, CareType } from '../api/types'
import { todaySgt } from '../lib/dates'

export function CareLogsPage() {
  return <RequireBaby>{() => <CareLogsContent />}</RequireBaby>
}

function CareLogsContent() {
  const { t } = useTranslation()
  const [date, setDate] = useState(todaySgt())
  const { data: logs = [], isLoading, isError, error, refetch } = useCareLogs(date)
  const [editing, setEditing] = useState<CareLog | null>(null)

  const counts: Record<CareType, number> = { FEEDING: 0, SLEEP: 0, DIAPER: 0 }
  for (const log of logs) counts[log.type] += 1

  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-5">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-xl font-bold text-slate-800">{t('care.title')}</h2>
        <input
          type="date"
          max={todaySgt()}
          value={date}
          onChange={(e) => setDate(e.target.value)}
          className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
          aria-label={t('common.date')}
        />
      </div>

      {date === todaySgt() && (
        <section className="rounded-2xl bg-white p-5 shadow-sm">
          <h3 className="mb-3 text-sm font-semibold text-slate-700">{t('care.quickLog')}</h3>
          <QuickCareButtons />
        </section>
      )}

      {isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <section className="rounded-2xl bg-white p-5 shadow-sm">
          <p className="mb-3 text-sm text-slate-500">
            {t('care.todayCounts')}: 🍼 {counts.FEEDING} · 😴 {counts.SLEEP} · 🧷 {counts.DIAPER}
          </p>
          {isLoading ? (
            <p className="text-sm text-slate-500">{t('common.loading')}</p>
          ) : logs.length === 0 ? (
            <p className="text-sm text-slate-500">{t('care.noLogs')}</p>
          ) : (
            <CareLogList logs={logs} onSelect={setEditing} />
          )}
        </section>
      )}

      {editing && <CareLogEditDialog log={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

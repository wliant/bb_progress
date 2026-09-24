import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useCareLogs } from '../api/hooks'
import { QuickCareButtons } from '../components/QuickCareButtons'
import { ErrorState } from '../components/ErrorState'
import { RequireBaby } from '../components/RequireBaby'
import { CareLogList } from '../features/care/CareLogList'
import { CareLogEditDialog } from '../features/care/CareLogEditDialog'
import type { CareType } from '../api/types'
import { timeSgt, todaySgt } from '../lib/dates'

export function CareLogsPage() {
  return <RequireBaby>{() => <CareLogsContent />}</RequireBaby>
}

function CareLogsContent() {
  const { t } = useTranslation()
  // ?date= lets the photo gallery link straight to the day an entry belongs to.
  const [searchParams] = useSearchParams()
  const [date, setDate] = useState(searchParams.get('date') ?? todaySgt())
  const { data: logs = [], isLoading, isError, error, refetch } = useCareLogs(date)
  const [editingId, setEditingId] = useState<string | null>(null)
  // Time of day for backfilling a past date; today logs "now" and ignores it.
  const [time, setTime] = useState(() => timeSgt(new Date().toISOString()))
  const isToday = date === todaySgt()

  // Derived, not held in state: an upload inside the dialog must be reflected there.
  const editing = logs.find((log) => log.id === editingId) ?? null

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

      <section className="rounded-2xl bg-white p-5 shadow-sm">
        <h3 className="mb-3 text-sm font-semibold text-slate-700">
          {isToday ? t('care.quickLog') : t('care.quickLogOn', { date })}
        </h3>
        {!isToday && (
          <label className="mb-3 flex items-center gap-2 text-sm text-slate-600">
            {t('care.time')}
            <input
              type="time"
              value={time}
              onChange={(e) => setTime(e.target.value)}
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
            />
          </label>
        )}
        <QuickCareButtons
          loggedAt={isToday ? undefined : `${date}T${time}:00+08:00`}
          disabled={!isToday && time === ''}
        />
      </section>

      {isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <section className="rounded-2xl bg-white p-5 shadow-sm">
          <p className="mb-3 text-sm text-slate-500">
            {isToday ? t('care.todayCounts') : date}: 🍼 {counts.FEEDING} · 😴 {counts.SLEEP} · 🧷 {counts.DIAPER}
          </p>
          {isLoading ? (
            <p className="text-sm text-slate-500">{t('common.loading')}</p>
          ) : logs.length === 0 ? (
            <p className="text-sm text-slate-500">{t('care.noLogs')}</p>
          ) : (
            <CareLogList logs={logs} onSelect={(log) => setEditingId(log.id)} />
          )}
        </section>
      )}

      {editing && <CareLogEditDialog log={editing} onClose={() => setEditingId(null)} />}
    </div>
  )
}

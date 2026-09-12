import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useCareLogs, useDeleteCareLog, useUpdateCareLog } from '../api/hooks'
import { QuickCareButtons } from '../components/QuickCareButtons'
import { ErrorState } from '../components/ErrorState'
import { RequireBaby } from '../components/RequireBaby'
import type { CareLog, CareType } from '../api/types'
import { timeSgt, todaySgt } from '../lib/dates'

const ICONS: Record<CareType, string> = { FEEDING: '🍼', SLEEP: '😴', DIAPER: '🧷' }

export function CareLogsPage() {
  return <RequireBaby>{() => <CareLogsContent />}</RequireBaby>
}

function CareLogsContent() {
  const { t } = useTranslation()
  const [date, setDate] = useState(todaySgt())
  const { data: logs = [], isLoading, isError, error, refetch } = useCareLogs(date)
  const deleteLog = useDeleteCareLog()
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
            <ul className="divide-y divide-slate-100">
              {logs.map((log) => (
                <li key={log.id} className="flex items-center gap-3 py-3 text-sm">
                  <span aria-hidden className="text-xl">{ICONS[log.type]}</span>
                  <span className="w-14 font-mono text-slate-700">{timeSgt(log.loggedAt)}</span>
                  <span className="flex-1 text-slate-600">
                    {t(`care.${log.type}`)}
                    {log.note && <span className="text-slate-400"> · {log.note}</span>}
                  </span>
                  <button type="button" onClick={() => setEditing(log)} className="text-rose-600 hover:underline">
                    {t('common.edit')}
                  </button>
                  <button
                    type="button"
                    onClick={() => deleteLog.mutate(log.id)}
                    className="text-slate-400 hover:text-red-600"
                  >
                    {t('common.delete')}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {editing && <EditDialog log={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function EditDialog({ log, onClose }: { log: CareLog; onClose: () => void }) {
  const { t } = useTranslation()
  const updateLog = useUpdateCareLog()
  // The API serializes loggedAt in SGT (+08:00); take the local part for the input.
  const [time, setTime] = useState(log.loggedAt.slice(0, 16))
  const [note, setNote] = useState(log.note ?? '')

  function save() {
    updateLog.mutate(
      { id: log.id, loggedAt: `${time}:00+08:00`, note: note || undefined },
      { onSuccess: onClose },
    )
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div className="w-full max-w-md rounded-t-2xl bg-white p-6 sm:rounded-2xl" onClick={(e) => e.stopPropagation()}>
        <h3 className="mb-4 font-semibold text-slate-800">
          {ICONS[log.type]} {t(`care.${log.type}`)}
        </h3>
        <label className="mb-3 flex flex-col gap-1 text-sm font-medium text-slate-700">
          {t('care.time')}
          <input
            type="datetime-local"
            required
            value={time}
            onChange={(e) => setTime(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2"
          />
        </label>
        <label className="mb-4 flex flex-col gap-1 text-sm font-medium text-slate-700">
          {t('common.note')}{t('common.optional')}
          <input
            maxLength={500}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2"
          />
        </label>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={save}
            disabled={updateLog.isPending || time === ''}
            className="flex-1 rounded-xl bg-rose-500 py-2.5 font-semibold text-white shadow hover:bg-rose-600 disabled:opacity-50"
          >
            {t('common.save')}
          </button>
          <button type="button" onClick={onClose} className="flex-1 rounded-xl border border-slate-300 py-2.5 font-semibold text-slate-600">
            {t('common.cancel')}
          </button>
        </div>
      </div>
    </div>
  )
}

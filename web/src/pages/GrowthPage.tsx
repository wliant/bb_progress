import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import {
  useBaby,
  useDeleteGrowthRecord,
  useGrowthRecords,
  useGrowthStandards,
  useSaveGrowthRecord,
} from '../api/hooks'
import { ApiError } from '../api/client'
import type { GrowthMeasure, GrowthRecord } from '../api/types'
import { GrowthChart } from '../features/growth/GrowthChart'
import { todaySgt } from '../lib/dates'

const MEASURES: { key: GrowthMeasure; labelKey: string; unitKey: string }[] = [
  { key: 'WEIGHT', labelKey: 'growth.weight', unitKey: 'growth.weightUnit' },
  { key: 'HEIGHT', labelKey: 'growth.height', unitKey: 'growth.heightUnit' },
  { key: 'HEAD_CIRCUMFERENCE', labelKey: 'growth.headCircumference', unitKey: 'growth.headUnit' },
]

interface FormState {
  id?: string
  measuredOn: string
  weightKg: string
  heightCm: string
  headCircumferenceCm: string
  note: string
}

const emptyForm = (): FormState => ({
  measuredOn: todaySgt(),
  weightKg: '',
  heightCm: '',
  headCircumferenceCm: '',
  note: '',
})

export function GrowthPage() {
  const { t } = useTranslation()
  const { data: baby, isLoading: babyLoading } = useBaby()
  const { data: records = [] } = useGrowthRecords()
  const [measure, setMeasure] = useState<GrowthMeasure>('WEIGHT')
  const { data: standards } = useGrowthStandards(baby?.gender, measure)
  const saveRecord = useSaveGrowthRecord()
  const deleteRecord = useDeleteGrowthRecord()

  const [form, setForm] = useState<FormState | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (babyLoading) return <p className="text-slate-500">{t('common.loading')}</p>
  if (!baby) {
    return (
      <p className="text-slate-600">
        {t('profile.notCreated')}{' '}
        <Link className="font-medium text-rose-600 hover:underline" to="/profile">
          {t('profile.create')}
        </Link>
      </p>
    )
  }

  const unit = t(MEASURES.find((m) => m.key === measure)!.unitKey)

  function startEdit(record: GrowthRecord) {
    setForm({
      id: record.id,
      measuredOn: record.measuredOn,
      weightKg: record.weightKg?.toString() ?? '',
      heightCm: record.heightCm?.toString() ?? '',
      headCircumferenceCm: record.headCircumferenceCm?.toString() ?? '',
      note: record.note ?? '',
    })
    setError(null)
  }

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!form) return
    const input = {
      measuredOn: form.measuredOn,
      weightKg: form.weightKg === '' ? null : Number(form.weightKg),
      heightCm: form.heightCm === '' ? null : Number(form.heightCm),
      headCircumferenceCm: form.headCircumferenceCm === '' ? null : Number(form.headCircumferenceCm),
      note: form.note || null,
    }
    if (input.weightKg == null && input.heightCm == null && input.headCircumferenceCm == null) {
      setError(t('growth.atLeastOne'))
      return
    }
    setError(null)
    saveRecord.mutate(
      { id: form.id, input },
      {
        onSuccess: () => setForm(null),
        onError: (err) =>
          setError(
            err instanceof ApiError && err.code === 'DUPLICATE_DATE'
              ? t('growth.duplicateDate')
              : t('common.error'),
          ),
      },
    )
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-slate-800">{t('growth.title')}</h2>
        <button
          type="button"
          onClick={() => { setForm(emptyForm()); setError(null) }}
          className="rounded-xl bg-rose-500 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-rose-600"
        >
          {t('growth.addRecord')}
        </button>
      </div>

      <div className="flex gap-2" role="tablist">
        {MEASURES.map((m) => (
          <button
            key={m.key}
            role="tab"
            aria-selected={measure === m.key}
            type="button"
            onClick={() => setMeasure(m.key)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium ${
              measure === m.key ? 'bg-rose-500 text-white' : 'bg-white text-slate-600 shadow-sm'
            }`}
          >
            {t(m.labelKey)}
          </button>
        ))}
      </div>

      <section className="rounded-2xl bg-white p-4 shadow-sm">
        <GrowthChart
          measure={measure}
          records={records}
          standards={standards}
          dateOfBirth={baby.dateOfBirth}
          unit={unit}
        />
      </section>

      {form && (
        <form onSubmit={onSubmit} className="flex flex-col gap-3 rounded-2xl bg-white p-6 shadow-sm">
          <h3 className="font-semibold text-slate-700">
            {form.id ? t('growth.editRecord') : t('growth.addRecord')}
          </h3>
          <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
            {t('growth.measuredOn')}
            <input
              required
              type="date"
              max={todaySgt()}
              value={form.measuredOn}
              onChange={(e) => setForm({ ...form, measuredOn: e.target.value })}
              className="rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
              {t('growth.weight')} ({t('growth.weightUnit')})
              <input
                type="number" step="0.01" min="0.3" max="40"
                value={form.weightKg}
                onChange={(e) => setForm({ ...form, weightKg: e.target.value })}
                className="rounded-lg border border-slate-300 px-3 py-2"
              />
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
              {t('growth.height')} ({t('growth.heightUnit')})
              <input
                type="number" step="0.1" min="20" max="150"
                value={form.heightCm}
                onChange={(e) => setForm({ ...form, heightCm: e.target.value })}
                className="rounded-lg border border-slate-300 px-3 py-2"
              />
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
              {t('growth.headCircumference')} ({t('growth.headUnit')})
              <input
                type="number" step="0.1" min="20" max="70"
                value={form.headCircumferenceCm}
                onChange={(e) => setForm({ ...form, headCircumferenceCm: e.target.value })}
                className="rounded-lg border border-slate-300 px-3 py-2"
              />
            </label>
          </div>
          <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
            {t('common.note')}{t('common.optional')}
            <input
              maxLength={500}
              value={form.note}
              onChange={(e) => setForm({ ...form, note: e.target.value })}
              className="rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
          {error && <p className="text-sm text-red-600">{error}</p>}
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={saveRecord.isPending}
              className="flex-1 rounded-xl bg-rose-500 py-2.5 font-semibold text-white shadow hover:bg-rose-600 disabled:opacity-50"
            >
              {t('common.save')}
            </button>
            <button
              type="button"
              onClick={() => setForm(null)}
              className="flex-1 rounded-xl border border-slate-300 py-2.5 font-semibold text-slate-600"
            >
              {t('common.cancel')}
            </button>
          </div>
        </form>
      )}

      <section className="rounded-2xl bg-white p-4 shadow-sm">
        <h3 className="mb-2 px-2 font-semibold text-slate-700">{t('growth.records')}</h3>
        {records.length === 0 ? (
          <p className="px-2 pb-2 text-sm text-slate-500">{t('growth.noRecords')}</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {[...records].reverse().map((record) => (
              <li key={record.id} className="flex items-center gap-3 px-2 py-3 text-sm">
                <span className="w-24 shrink-0 font-medium text-slate-700">{record.measuredOn}</span>
                <span className="flex-1 text-slate-600">
                  {record.weightKg != null && `${record.weightKg} ${t('growth.weightUnit')}  `}
                  {record.heightCm != null && `${record.heightCm} ${t('growth.heightUnit')}  `}
                  {record.headCircumferenceCm != null && `${t('growth.headCircumference')} ${record.headCircumferenceCm} ${t('growth.headUnit')}`}
                  {record.note && <span className="text-slate-400"> · {record.note}</span>}
                </span>
                <button
                  type="button"
                  onClick={() => startEdit(record)}
                  className="text-rose-600 hover:underline"
                >
                  {t('common.edit')}
                </button>
                <button
                  type="button"
                  onClick={() => deleteRecord.mutate(record.id)}
                  className="text-slate-400 hover:text-red-600"
                >
                  {t('common.delete')}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

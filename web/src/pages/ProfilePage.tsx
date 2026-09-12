import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useBaby, useSaveBaby, useUploadBabyPhoto } from '../api/hooks'
import { ErrorState } from '../components/ErrorState'
import type { Gender } from '../api/types'
import { todaySgt } from '../lib/dates'

interface FormState {
  name: string
  dateOfBirth: string
  timeOfBirth: string
  gender: Gender
  birthWeightKg: string
  birthLengthCm: string
  birthHeadCircumferenceCm: string
}

const EMPTY: FormState = {
  name: '',
  dateOfBirth: '',
  timeOfBirth: '',
  gender: 'FEMALE',
  birthWeightKg: '',
  birthLengthCm: '',
  birthHeadCircumferenceCm: '',
}

const numberOrNull = (value: string) => (value === '' ? null : Number(value))

export function ProfilePage() {
  const { t } = useTranslation()
  const { data: baby, isLoading, isError, error, refetch } = useBaby()
  const saveBaby = useSaveBaby()
  const uploadPhoto = useUploadBabyPhoto()
  const fileInput = useRef<HTMLInputElement>(null)

  const [form, setForm] = useState<FormState>(EMPTY)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (baby) {
      setForm({
        name: baby.name,
        dateOfBirth: baby.dateOfBirth,
        // The API sends ISO local time ("14:30:00"); <input type="time"> wants HH:mm.
        timeOfBirth: baby.timeOfBirth?.slice(0, 5) ?? '',
        gender: baby.gender,
        birthWeightKg: baby.birthWeightKg?.toString() ?? '',
        birthLengthCm: baby.birthLengthCm?.toString() ?? '',
        birthHeadCircumferenceCm: baby.birthHeadCircumferenceCm?.toString() ?? '',
      })
    }
  }, [baby])

  function set<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSaved(false)
    // Failures surface as a toast via the shared mutation error handler.
    saveBaby.mutate(
      {
        name: form.name,
        dateOfBirth: form.dateOfBirth,
        timeOfBirth: form.timeOfBirth || null,
        gender: form.gender,
        birthWeightKg: numberOrNull(form.birthWeightKg),
        birthLengthCm: numberOrNull(form.birthLengthCm),
        birthHeadCircumferenceCm: numberOrNull(form.birthHeadCircumferenceCm),
      },
      {
        onSuccess: () => {
          setSaved(true)
          setTimeout(() => setSaved(false), 2500)
        },
      },
    )
  }

  function onPhotoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) uploadPhoto.mutate(file)
    e.target.value = ''
  }

  if (isLoading) return <p className="text-slate-500">{t('common.loading')}</p>
  if (isError) return <ErrorState error={error} onRetry={() => void refetch()} />

  return (
    <div className="mx-auto max-w-md">
      <h2 className="mb-4 text-xl font-bold text-slate-800">{t('profile.title')}</h2>

      <form onSubmit={onSubmit} className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm">
        {baby && (
          <div className="flex flex-col items-center gap-2">
            {baby.hasPhoto ? (
              <img
                src={`/api/baby/photo?v=${baby.photoVersion}`}
                alt={baby.name}
                className="h-24 w-24 rounded-full object-cover"
              />
            ) : (
              <span className="flex h-24 w-24 items-center justify-center rounded-full bg-rose-100 text-4xl" aria-hidden>
                👶
              </span>
            )}
            <input
              ref={fileInput}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
              onChange={onPhotoChange}
              data-testid="photo-input"
            />
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              className="text-sm font-medium text-rose-600 hover:underline"
              disabled={uploadPhoto.isPending}
            >
              {t('profile.uploadPhoto')}
            </button>
          </div>
        )}

        <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
          {t('profile.name')}
          <input
            required
            maxLength={100}
            value={form.name}
            onChange={(e) => set('name', e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-base"
          />
        </label>

        <div className="grid grid-cols-2 gap-3">
          <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
            {t('profile.dateOfBirth')}
            <input
              required
              type="date"
              max={todaySgt()}
              value={form.dateOfBirth}
              onChange={(e) => set('dateOfBirth', e.target.value)}
              className="rounded-lg border border-slate-300 px-3 py-2 text-base"
            />
          </label>
          <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
            {t('profile.timeOfBirth')}
            <input
              type="time"
              value={form.timeOfBirth}
              onChange={(e) => set('timeOfBirth', e.target.value)}
              className="rounded-lg border border-slate-300 px-3 py-2 text-base"
            />
          </label>
        </div>

        <fieldset className="flex flex-col gap-1 text-sm font-medium text-slate-700">
          <legend>{t('profile.gender')}</legend>
          <div className="mt-1 flex gap-3">
            {(['FEMALE', 'MALE'] as Gender[]).map((g) => (
              <label
                key={g}
                className={`flex-1 cursor-pointer rounded-lg border px-3 py-2 text-center ${
                  form.gender === g ? 'border-rose-500 bg-rose-50 text-rose-700' : 'border-slate-300'
                }`}
              >
                <input
                  type="radio"
                  name="gender"
                  value={g}
                  checked={form.gender === g}
                  onChange={() => set('gender', g)}
                  className="sr-only"
                />
                {g === 'FEMALE' ? t('profile.female') : t('profile.male')}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset className="rounded-xl border border-slate-200 p-4">
          <legend className="px-1 text-sm font-medium text-slate-700">
            {t('profile.birthMeasurements')}
          </legend>
          <p className="mb-3 text-xs text-slate-400">{t('profile.birthMeasurementsHint')}</p>
          <div className="grid grid-cols-3 gap-3">
            <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
              {t('growth.weight')} ({t('growth.weightUnit')})
              <input
                type="number" step="0.01" min="0.3" max="40"
                value={form.birthWeightKg}
                onChange={(e) => set('birthWeightKg', e.target.value)}
                className="rounded-lg border border-slate-300 px-2 py-2 text-base"
              />
            </label>
            <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
              {t('profile.birthLength')} ({t('growth.heightUnit')})
              <input
                type="number" step="0.1" min="20" max="150"
                value={form.birthLengthCm}
                onChange={(e) => set('birthLengthCm', e.target.value)}
                className="rounded-lg border border-slate-300 px-2 py-2 text-base"
              />
            </label>
            <label className="flex flex-col gap-1 text-xs font-medium text-slate-600">
              {t('growth.headCircumference')} ({t('growth.headUnit')})
              <input
                type="number" step="0.1" min="20" max="70"
                value={form.birthHeadCircumferenceCm}
                onChange={(e) => set('birthHeadCircumferenceCm', e.target.value)}
                className="rounded-lg border border-slate-300 px-2 py-2 text-base"
              />
            </label>
          </div>
        </fieldset>

        {saved && <p className="text-sm text-emerald-600">{t('profile.saved')}</p>}

        <button
          type="submit"
          disabled={saveBaby.isPending}
          className="rounded-xl bg-rose-500 py-3 font-semibold text-white shadow hover:bg-rose-600 disabled:opacity-50"
        >
          {t('common.save')}
        </button>
      </form>
    </div>
  )
}

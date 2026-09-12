import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useBaby, useSaveBaby, useUploadBabyPhoto } from '../api/hooks'
import { ApiError } from '../api/client'
import type { Gender } from '../api/types'
import { todaySgt } from '../lib/dates'

export function ProfilePage() {
  const { t } = useTranslation()
  const { data: baby, isLoading } = useBaby()
  const saveBaby = useSaveBaby()
  const uploadPhoto = useUploadBabyPhoto()
  const fileInput = useRef<HTMLInputElement>(null)

  const [name, setName] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [gender, setGender] = useState<Gender>('FEMALE')
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [photoVersion, setPhotoVersion] = useState(0)

  useEffect(() => {
    if (baby) {
      setName(baby.name)
      setDateOfBirth(baby.dateOfBirth)
      setGender(baby.gender)
    }
  }, [baby])

  function onSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    setSaved(false)
    saveBaby.mutate(
      { name, dateOfBirth, gender },
      {
        onSuccess: () => {
          setSaved(true)
          setTimeout(() => setSaved(false), 2500)
        },
        onError: () => setError(t('common.error')),
      },
    )
  }

  function onPhotoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setError(null)
    uploadPhoto.mutate(file, {
      onSuccess: () => setPhotoVersion((v) => v + 1),
      onError: (err) =>
        setError(
          err instanceof ApiError && err.code === 'UNSUPPORTED_PHOTO_TYPE'
            ? t('profile.photoTypeError')
            : t('common.error'),
        ),
    })
  }

  if (isLoading) return <p className="text-slate-500">{t('common.loading')}</p>

  return (
    <div className="mx-auto max-w-md">
      <h2 className="mb-4 text-xl font-bold text-slate-800">{t('profile.title')}</h2>

      <form onSubmit={onSubmit} className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm">
        {baby && (
          <div className="flex flex-col items-center gap-2">
            {baby.hasPhoto ? (
              <img
                src={`/api/baby/photo?v=${photoVersion}`}
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
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-base"
          />
        </label>

        <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
          {t('profile.dateOfBirth')}
          <input
            required
            type="date"
            max={todaySgt()}
            value={dateOfBirth}
            onChange={(e) => setDateOfBirth(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2 text-base"
          />
        </label>

        <fieldset className="flex flex-col gap-1 text-sm font-medium text-slate-700">
          <legend>{t('profile.gender')}</legend>
          <div className="mt-1 flex gap-3">
            {(['FEMALE', 'MALE'] as Gender[]).map((g) => (
              <label
                key={g}
                className={`flex-1 cursor-pointer rounded-lg border px-3 py-2 text-center ${
                  gender === g ? 'border-rose-500 bg-rose-50 text-rose-700' : 'border-slate-300'
                }`}
              >
                <input
                  type="radio"
                  name="gender"
                  value={g}
                  checked={gender === g}
                  onChange={() => setGender(g)}
                  className="sr-only"
                />
                {g === 'FEMALE' ? t('profile.female') : t('profile.male')}
              </label>
            ))}
          </div>
        </fieldset>

        {error && <p className="text-sm text-red-600">{error}</p>}
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

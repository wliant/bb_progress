import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useDeleteCareLog,
  useRemoveCareLogPhoto,
  useUpdateCareLog,
  useUploadCareLogPhoto,
} from '../../api/hooks'
import type { CareLog } from '../../api/types'
import { CARE_ICONS } from './CareLogList'

/** The dialog reads its entry from the list so an upload shows up without reopening. */
export function CareLogEditDialog({ log, onClose }: { log: CareLog; onClose: () => void }) {
  const { t } = useTranslation()
  const updateLog = useUpdateCareLog()
  const deleteLog = useDeleteCareLog()
  const uploadPhoto = useUploadCareLogPhoto()
  const removePhoto = useRemoveCareLogPhoto()
  const fileInput = useRef<HTMLInputElement>(null)

  // The API serializes loggedAt in SGT (+08:00); take the local part for the input.
  const [time, setTime] = useState(log.loggedAt.slice(0, 16))
  const [note, setNote] = useState(log.note ?? '')

  function save() {
    updateLog.mutate(
      { id: log.id, loggedAt: `${time}:00+08:00`, note: note || undefined },
      { onSuccess: onClose },
    )
  }

  function remove() {
    deleteLog.mutate(log.id, { onSuccess: onClose })
  }

  function onPhotoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) uploadPhoto.mutate({ id: log.id, file })
    e.target.value = ''
  }

  const busy = updateLog.isPending || deleteLog.isPending

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div
        className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-t-2xl bg-white p-6 sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="mb-4 font-semibold text-slate-800">
          {CARE_ICONS[log.type]} {t(`care.${log.type}`)}
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
            placeholder={t(`care.notePlaceholder.${log.type}`)}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2"
          />
        </label>

        <div className="mb-4">
          <p className="mb-2 text-sm font-medium text-slate-700">{t('care.photo')}</p>
          {log.hasPhoto && (
            <img
              src={`/api/care-logs/${log.id}/photo?v=${log.photoVersion}`}
              alt=""
              className="mb-2 max-h-56 w-full rounded-lg object-cover"
            />
          )}
          <input
            ref={fileInput}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            className="hidden"
            onChange={onPhotoChange}
            data-testid="care-photo-input"
          />
          <div className="flex items-center gap-4">
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              disabled={uploadPhoto.isPending}
              className="text-sm font-medium text-rose-600 hover:underline disabled:opacity-50"
            >
              {uploadPhoto.isPending
                ? t('care.uploading')
                : log.hasPhoto
                  ? t('care.replacePhoto')
                  : t('care.addPhoto')}
            </button>
            {log.hasPhoto && (
              <button
                type="button"
                onClick={() => removePhoto.mutate(log.id)}
                disabled={removePhoto.isPending}
                className="text-sm text-slate-400 hover:text-red-600 disabled:opacity-50"
              >
                {t('care.removePhoto')}
              </button>
            )}
          </div>
          <p className="mt-1 text-xs text-slate-400">{t('care.photoHint')}</p>
        </div>

        <div className="flex gap-3">
          <button
            type="button"
            onClick={save}
            disabled={busy || time === ''}
            className="flex-1 rounded-xl bg-rose-500 py-2.5 font-semibold text-white shadow hover:bg-rose-600 disabled:opacity-50"
          >
            {t('common.save')}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-300 px-4 py-2.5 font-semibold text-slate-600"
          >
            {t('common.cancel')}
          </button>
          <button
            type="button"
            onClick={remove}
            disabled={busy}
            className="rounded-xl border border-red-200 px-4 py-2.5 font-semibold text-red-600 hover:bg-red-50 disabled:opacity-50"
          >
            {t('common.delete')}
          </button>
        </div>
      </div>
    </div>
  )
}

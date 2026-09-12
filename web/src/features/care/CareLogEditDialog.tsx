import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useAddCareLogMedia,
  useDeleteCareLog,
  useDeleteMedia,
  useUpdateCareLog,
} from '../../api/hooks'
import type { CareLog } from '../../api/types'
import { MediaPicker } from '../media/MediaPicker'
import { CARE_ICONS } from './CareLogList'

/** The dialog reads its entry from the list so an upload shows up without reopening. */
export function CareLogEditDialog({ log, onClose }: { log: CareLog; onClose: () => void }) {
  const { t } = useTranslation()
  const updateLog = useUpdateCareLog()
  const deleteLog = useDeleteCareLog()
  const addMedia = useAddCareLogMedia()
  const removeMedia = useDeleteMedia()

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
          {/* The entry already exists, so attachments upload straight away. */}
          <MediaPicker
            media={log.media}
            onAdd={(files) => addMedia.mutate({ id: log.id, files })}
            onRemove={(mediaId) => removeMedia.mutate(mediaId)}
            busy={addMedia.isPending}
          />
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

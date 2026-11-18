import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useAddHeadShapeMedia,
  useDeleteHeadShapeEntry,
  useDeleteMedia,
  useUpdateHeadShapeEntry,
} from '../../api/hooks'
import type { HeadShapeEntry } from '../../api/types'
import { MediaPicker } from '../media/MediaPicker'

/** The dialog reads its entry from the list so an upload shows up without reopening. */
export function HeadShapeEntryDialog({
  entry,
  onClose,
}: {
  entry: HeadShapeEntry
  onClose: () => void
}) {
  const { t } = useTranslation()
  const updateEntry = useUpdateHeadShapeEntry()
  const deleteEntry = useDeleteHeadShapeEntry()
  const addMedia = useAddHeadShapeMedia()
  const removeMedia = useDeleteMedia()

  const [note, setNote] = useState(entry.note ?? '')

  const busy = updateEntry.isPending || deleteEntry.isPending

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
        <h3 className="mb-4 font-semibold text-slate-800">🧠 {entry.recordedOn}</h3>

        <label className="mb-4 flex flex-col gap-1 text-sm font-medium text-slate-700">
          {t('common.note')}{t('common.optional')}
          <input
            maxLength={500}
            placeholder={t('headShape.notePlaceholder')}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2"
          />
        </label>

        <div className="mb-4">
          {/* The entry already exists, so photos upload straight away. */}
          <MediaPicker
            photoOnly
            media={entry.media}
            onAdd={(files) => addMedia.mutate({ id: entry.id, files })}
            onRemove={(mediaId) => removeMedia.mutate(mediaId)}
            busy={addMedia.isPending}
          />
        </div>

        <div className="flex gap-3">
          <button
            type="button"
            disabled={busy}
            onClick={() =>
              updateEntry.mutate(
                { id: entry.id, recordedOn: entry.recordedOn, note: note || undefined },
                { onSuccess: onClose },
              )
            }
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
            disabled={busy}
            onClick={() => deleteEntry.mutate(entry.id, { onSuccess: onClose })}
            className="rounded-xl border border-red-200 px-4 py-2.5 font-semibold text-red-600 hover:bg-red-50 disabled:opacity-50"
          >
            {t('common.delete')}
          </button>
        </div>
      </div>
    </div>
  )
}

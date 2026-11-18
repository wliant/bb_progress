import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useAddHeadShapeMedia,
  useDeleteMedia,
  useHeadShapeEntries,
  useSaveHeadShapeEntry,
} from '../api/hooks'
import type { Baby, HeadShapeEntry, Media } from '../api/types'
import { ErrorState } from '../components/ErrorState'
import { RequireBaby } from '../components/RequireBaby'
import { HeadShapeEntryDialog } from '../features/headshape/HeadShapeEntryDialog'
import { todaySgt } from '../lib/dates'

export function HeadShapePage() {
  return <RequireBaby>{(baby) => <HeadShapeContent baby={baby} />}</RequireBaby>
}

function HeadShapeContent({ baby }: { baby: Baby }) {
  const { t } = useTranslation()
  const { data: entries = [], isLoading, isError, error, refetch } = useHeadShapeEntries()
  const saveEntry = useSaveHeadShapeEntry()
  const addMedia = useAddHeadShapeMedia()
  const [date, setDate] = useState(todaySgt())
  const [editingId, setEditingId] = useState<string | null>(null)
  const [viewing, setViewing] = useState<{ entryId: string; mediaId: string } | null>(null)

  // Derived from the list, not held in state: an upload inside the dialog has to show up
  // without the dialog being reopened.
  const editing = entries.find((entry) => entry.id === editingId) ?? null
  const viewingEntry = entries.find((entry) => entry.id === viewing?.entryId) ?? null
  const viewingMedia = viewingEntry?.media.find((item) => item.id === viewing?.mediaId) ?? null

  /** Pick a day, pick the photos — the entry is created on the way through if it is new. */
  function upload(files: File[]) {
    saveEntry.mutate(
      { recordedOn: date },
      { onSuccess: (entry) => entry && addMedia.mutate({ id: entry.id, files }) },
    )
  }

  if (isLoading) return <p className="text-slate-500">{t('common.loading')}</p>
  if (isError) return <ErrorState error={error} onRetry={() => void refetch()} />

  const busy = saveEntry.isPending || addMedia.isPending

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <div>
        <h2 className="text-xl font-bold text-slate-800">{t('headShape.title')}</h2>
        <p className="mt-1 text-sm text-slate-500">{t('headShape.subtitle')}</p>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-2xl bg-white p-4 shadow-sm">
        <label className="flex flex-col gap-1 text-sm font-medium text-slate-700">
          {t('headShape.date')}
          <input
            type="date"
            value={date}
            min={baby.dateOfBirth}
            max={todaySgt()}
            onChange={(e) => setDate(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2"
          />
        </label>
        <label className="rounded-xl bg-rose-500 px-4 py-2.5 font-semibold text-white shadow hover:bg-rose-600 has-[:disabled]:opacity-50">
          {busy ? t('media.uploading') : t('headShape.addPhotos')}
          <input
            type="file"
            multiple
            accept="image/jpeg,image/png,image/webp"
            disabled={busy}
            className="hidden"
            data-testid="head-shape-input"
            onChange={(e) => {
              const files = Array.from(e.target.files ?? [])
              e.target.value = ''
              if (files.length > 0) upload(files)
            }}
          />
        </label>
      </div>

      {entries.length === 0 ? (
        <div className="rounded-2xl bg-white p-8 text-center shadow-sm">
          <p className="mb-3 text-4xl" aria-hidden>
            🧠
          </p>
          <p className="text-slate-600">{t('headShape.empty')}</p>
          <p className="mt-2 text-sm text-slate-400">{t('headShape.emptyHint')}</p>
        </div>
      ) : (
        entries.map((entry) => (
          <EntryCard
            key={entry.id}
            entry={entry}
            onEdit={() => setEditingId(entry.id)}
            onOpenPhoto={(mediaId) => setViewing({ entryId: entry.id, mediaId })}
          />
        ))
      )}

      {editing && <HeadShapeEntryDialog entry={editing} onClose={() => setEditingId(null)} />}
      {viewingEntry && viewingMedia && (
        <PhotoViewer
          entry={viewingEntry}
          media={viewingMedia}
          onClose={() => setViewing(null)}
        />
      )}
    </div>
  )
}

function EntryCard({
  entry,
  onEdit,
  onOpenPhoto,
}: {
  entry: HeadShapeEntry
  onEdit: () => void
  onOpenPhoto: (mediaId: string) => void
}) {
  const { t } = useTranslation()

  return (
    <section className="rounded-2xl bg-white p-4 shadow-sm">
      <button
        type="button"
        onClick={onEdit}
        aria-label={t('headShape.editDay', { date: entry.recordedOn })}
        className="mb-3 flex w-full items-baseline justify-between px-1 text-left"
      >
        <span className="text-sm font-semibold text-slate-600">{entry.recordedOn}</span>
        <span className="text-xs text-slate-400">
          {t('headShape.photoCount', { count: entry.media.length })}
        </span>
      </button>
      {entry.note && <p className="mb-3 px-1 text-sm text-slate-500">{entry.note}</p>}
      {entry.media.length === 0 ? (
        <p className="px-1 text-sm text-slate-400">{t('headShape.noPhotos')}</p>
      ) : (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
          {entry.media.map((item) => (
            <li key={item.id}>
              <button
                type="button"
                onClick={() => onOpenPhoto(item.id)}
                className="block w-full overflow-hidden rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-400"
              >
                <img
                  src={item.thumbnailUrl ?? item.url}
                  alt={t('headShape.photoAlt', { date: entry.recordedOn })}
                  loading="lazy"
                  className="aspect-square w-full bg-slate-100 object-cover transition hover:opacity-90"
                />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function PhotoViewer({
  entry,
  media,
  onClose,
}: {
  entry: HeadShapeEntry
  media: Media
  onClose: () => void
}) {
  const { t } = useTranslation()
  const removeMedia = useDeleteMedia()

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div
        className="flex max-h-full w-full max-w-2xl flex-col gap-3"
        onClick={(e) => e.stopPropagation()}
      >
        <img
          src={media.url}
          alt={t('headShape.photoAlt', { date: entry.recordedOn })}
          className="max-h-[70vh] w-full rounded-xl object-contain"
        />
        <div className="rounded-xl bg-white p-4">
          <p className="font-medium text-slate-800">{entry.recordedOn}</p>
          {entry.note && <p className="mt-0.5 text-sm text-slate-500">{entry.note}</p>}
          <div className="mt-3 flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-600"
            >
              {t('common.cancel')}
            </button>
            <button
              type="button"
              disabled={removeMedia.isPending}
              onClick={() => removeMedia.mutate(media.id, { onSuccess: onClose })}
              className="rounded-xl border border-red-200 px-4 py-2 text-sm font-semibold text-red-600 hover:bg-red-50 disabled:opacity-50"
            >
              {t('media.remove')}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

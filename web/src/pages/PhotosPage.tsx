import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { usePhotos } from '../api/hooks'
import { ErrorState } from '../components/ErrorState'
import { RequireBaby } from '../components/RequireBaby'
import type { GalleryPhoto } from '../api/types'
import { timeSgt } from '../lib/dates'

export function PhotosPage() {
  return <RequireBaby>{() => <PhotosContent />}</RequireBaby>
}

/** Photos keep the date of the entry they belong to; the profile photo has none. */
function groupByDate(photos: GalleryPhoto[]): [string | null, GalleryPhoto[]][] {
  const groups: [string | null, GalleryPhoto[]][] = []
  for (const photo of photos) {
    const last = groups[groups.length - 1]
    if (last && last[0] === photo.takenOn) {
      last[1].push(photo)
    } else {
      groups.push([photo.takenOn, [photo]])
    }
  }
  return groups
}

function PhotosContent() {
  const { t } = useTranslation()
  const caption = useCaption()
  const { data: photos = [], isLoading, isError, error, refetch } = usePhotos()
  const [openId, setOpenId] = useState<string | null>(null)

  if (isLoading) return <p className="text-slate-500">{t('common.loading')}</p>
  if (isError) return <ErrorState error={error} onRetry={() => void refetch()} />

  const open = photos.find((photo) => photo.id === openId) ?? null

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <div className="flex items-baseline justify-between">
        <h2 className="text-xl font-bold text-slate-800">{t('photos.title')}</h2>
        {photos.length > 0 && (
          <span className="text-sm text-slate-500">{t('photos.count', { count: photos.length })}</span>
        )}
      </div>

      {photos.length === 0 ? (
        <div className="rounded-2xl bg-white p-8 text-center shadow-sm">
          <p className="mb-3 text-4xl" aria-hidden>
            📷
          </p>
          <p className="text-slate-600">{t('photos.empty')}</p>
          <p className="mt-2 text-sm text-slate-400">{t('photos.emptyHint')}</p>
        </div>
      ) : (
        groupByDate(photos).map(([date, group]) => (
          <section key={date ?? 'profile'} className="rounded-2xl bg-white p-4 shadow-sm">
            <h3 className="mb-3 px-1 text-sm font-semibold text-slate-600">
              {date ?? t('photos.profilePhoto')}
            </h3>
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
              {group.map((photo) => (
                <li key={photo.id}>
                  <button
                    type="button"
                    onClick={() => setOpenId(photo.id)}
                    className="block w-full overflow-hidden rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-400"
                  >
                    <img
                      src={photo.thumbnailUrl}
                      alt={caption(photo)}
                      loading="lazy"
                      className="aspect-square w-full bg-slate-100 object-cover transition hover:opacity-90"
                    />
                  </button>
                  <p className="mt-1 truncate px-0.5 text-xs text-slate-500">{caption(photo)}</p>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}

      {open && <PhotoViewer photo={open} onClose={() => setOpenId(null)} />}
    </div>
  )
}

/** Milestone titles arrive bilingual from the API, so the caption follows the switcher. */
function useCaption() {
  const { t, i18n } = useTranslation()
  return (photo: GalleryPhoto, withNote = true): string => {
    if (photo.source === 'PROFILE') return t('photos.profilePhoto')
    if (photo.source === 'MILESTONE') {
      return (i18n.language === 'zh-CN' ? photo.titleZh : photo.titleEn) ?? ''
    }
    const type = photo.careType ? t(`care.${photo.careType}`) : ''
    return withNote && photo.note ? `${type} · ${photo.note}` : type
  }
}

function PhotoViewer({ photo, onClose }: { photo: GalleryPhoto; onClose: () => void }) {
  const { t } = useTranslation()
  const caption = useCaption()(photo, false)

  const target =
    photo.source === 'PROFILE'
      ? '/profile'
      : photo.source === 'MILESTONE'
        ? '/milestones'
        : `/care?date=${photo.takenOn}`

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
          src={photo.url}
          alt={caption}
          className="max-h-[70vh] w-full rounded-xl object-contain"
        />
        <div className="rounded-xl bg-white p-4">
          <p className="font-medium text-slate-800">{caption}</p>
          {photo.note && <p className="mt-0.5 text-sm text-slate-500">{photo.note}</p>}
          <p className="mt-1 text-xs text-slate-400">
            {photo.takenOn
              ? `${photo.takenOn}${photo.takenAt ? ` ${timeSgt(photo.takenAt)}` : ''}`
              : t('photos.noDate')}
          </p>
          <div className="mt-3 flex gap-3">
            <Link
              to={target}
              className="rounded-xl bg-rose-500 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-rose-600"
            >
              {t('photos.openEntry')}
            </Link>
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-600"
            >
              {t('common.cancel')}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

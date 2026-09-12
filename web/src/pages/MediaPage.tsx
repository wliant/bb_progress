import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMediaGallery } from '../api/hooks'
import { ErrorState } from '../components/ErrorState'
import { RequireBaby } from '../components/RequireBaby'
import { KIND_ICON } from '../features/media/MediaPicker'
import type { GalleryItem } from '../api/types'
import { timeSgt } from '../lib/dates'

export function MediaPage() {
  return <RequireBaby>{() => <MediaContent />}</RequireBaby>
}

/** Items keep the date of the entry they belong to; the profile photo has none. */
function groupByDate(items: GalleryItem[]): [string | null, GalleryItem[]][] {
  const groups: [string | null, GalleryItem[]][] = []
  for (const item of items) {
    const last = groups[groups.length - 1]
    if (last && last[0] === item.takenOn) {
      last[1].push(item)
    } else {
      groups.push([item.takenOn, [item]])
    }
  }
  return groups
}

function MediaContent() {
  const { t } = useTranslation()
  const caption = useCaption()
  const { data: items = [], isLoading, isError, error, refetch } = useMediaGallery()
  const [openId, setOpenId] = useState<string | null>(null)

  if (isLoading) return <p className="text-slate-500">{t('common.loading')}</p>
  if (isError) return <ErrorState error={error} onRetry={() => void refetch()} />

  const open = items.find((item) => item.id === openId) ?? null

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <div className="flex items-baseline justify-between">
        <h2 className="text-xl font-bold text-slate-800">{t('media.title')}</h2>
        {items.length > 0 && (
          <span className="text-sm text-slate-500">{t('media.count', { count: items.length })}</span>
        )}
      </div>

      {items.length === 0 ? (
        <div className="rounded-2xl bg-white p-8 text-center shadow-sm">
          <p className="mb-3 text-4xl" aria-hidden>
            🎞️
          </p>
          <p className="text-slate-600">{t('media.empty')}</p>
          <p className="mt-2 text-sm text-slate-400">{t('media.emptyHint')}</p>
        </div>
      ) : (
        groupByDate(items).map(([date, group]) => (
          <section key={date ?? 'profile'} className="rounded-2xl bg-white p-4 shadow-sm">
            <h3 className="mb-3 px-1 text-sm font-semibold text-slate-600">
              {date ?? t('media.profilePhoto')}
            </h3>
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4">
              {group.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => setOpenId(item.id)}
                    className="relative block w-full overflow-hidden rounded-xl focus:outline-none focus:ring-2 focus:ring-rose-400"
                  >
                    {item.thumbnailUrl ? (
                      <img
                        src={item.thumbnailUrl}
                        alt={caption(item)}
                        loading="lazy"
                        className="aspect-square w-full bg-slate-100 object-cover transition hover:opacity-90"
                      />
                    ) : (
                      <span className="flex aspect-square w-full flex-col items-center justify-center gap-1 bg-slate-100">
                        <span aria-hidden className="text-3xl">{KIND_ICON[item.kind]}</span>
                        <span className="text-[11px] text-slate-500">{t(`media.kind.${item.kind}`)}</span>
                      </span>
                    )}
                    {item.kind !== 'PHOTO' && (
                      <span
                        aria-hidden
                        className="absolute bottom-1 right-1 rounded bg-black/60 px-1 text-[10px] text-white"
                      >
                        {KIND_ICON[item.kind]}
                      </span>
                    )}
                  </button>
                  <p className="mt-1 truncate px-0.5 text-xs text-slate-500">{caption(item)}</p>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}

      {open && <MediaViewer item={open} onClose={() => setOpenId(null)} />}
    </div>
  )
}

/** Milestone titles arrive bilingual from the API, so the caption follows the switcher. */
function useCaption() {
  const { t, i18n } = useTranslation()
  return (item: GalleryItem, withNote = true): string => {
    if (item.source === 'PROFILE') return t('media.profilePhoto')
    if (item.source === 'MILESTONE') {
      return (i18n.language === 'zh-CN' ? item.titleZh : item.titleEn) ?? ''
    }
    const type = item.careType ? t(`care.${item.careType}`) : ''
    return withNote && item.note ? `${type} · ${item.note}` : type
  }
}

function MediaViewer({ item, onClose }: { item: GalleryItem; onClose: () => void }) {
  const { t } = useTranslation()
  const caption = useCaption()(item, false)

  const target =
    item.source === 'PROFILE'
      ? '/profile'
      : item.source === 'MILESTONE'
        ? '/milestones'
        : `/care?date=${item.takenOn}`

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
        {/* Video and voice play inline with the browser's own controls. */}
        {item.kind === 'PHOTO' && (
          <img src={item.url} alt={caption} className="max-h-[70vh] w-full rounded-xl object-contain" />
        )}
        {item.kind === 'VIDEO' && (
          <video src={item.url} controls playsInline className="max-h-[70vh] w-full rounded-xl bg-black" />
        )}
        {item.kind === 'AUDIO' && (
          <div className="rounded-xl bg-white p-6 text-center">
            <p className="mb-3 text-4xl" aria-hidden>🎤</p>
            <audio src={item.url} controls className="w-full" />
          </div>
        )}

        <div className="rounded-xl bg-white p-4">
          <p className="font-medium text-slate-800">{caption}</p>
          <p className="text-xs text-slate-400">{t(`media.kind.${item.kind}`)}</p>
          {item.note && <p className="mt-0.5 text-sm text-slate-500">{item.note}</p>}
          <p className="mt-1 text-xs text-slate-400">
            {item.takenOn
              ? `${item.takenOn}${item.takenAt ? ` ${timeSgt(item.takenAt)}` : ''}`
              : t('media.noDate')}
          </p>
          <div className="mt-3 flex gap-3">
            <Link
              to={target}
              className="rounded-xl bg-rose-500 px-4 py-2 text-sm font-semibold text-white shadow hover:bg-rose-600"
            >
              {t('media.openEntry')}
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

import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useMilestones,
  useRemoveMilestoneAchievement,
  useSetMilestoneAchievement,
  useUploadMilestonePhoto,
} from '../api/hooks'
import type { Milestone } from '../api/types'
import { todaySgt } from '../lib/dates'

export function MilestonesPage() {
  const { t, i18n } = useTranslation()
  const { data: groups = [], isLoading } = useMilestones()
  const [openGroup, setOpenGroup] = useState<number | null>(null)
  const [dialog, setDialog] = useState<Milestone | null>(null)

  const title = (m: Milestone) => (i18n.language === 'zh-CN' ? m.titleZh : m.titleEn)

  if (isLoading) return <p className="text-slate-500">{t('common.loading')}</p>

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-3">
      <h2 className="text-xl font-bold text-slate-800">{t('milestones.title')}</h2>

      {groups.map((group) => {
        const done = group.milestones.filter((m) => m.achievement).length
        const open = openGroup === group.ageMonths
        return (
          <section key={group.ageMonths} className="rounded-2xl bg-white shadow-sm">
            <button
              type="button"
              onClick={() => setOpenGroup(open ? null : group.ageMonths)}
              className="flex w-full items-center justify-between px-5 py-4 text-left"
              aria-expanded={open}
            >
              <span className="font-semibold text-slate-800">
                {t('milestones.ageGroup', { months: group.ageMonths })}
              </span>
              <span className={`text-sm ${done === group.milestones.length ? 'text-emerald-600' : 'text-slate-500'}`}>
                {t('milestones.progress', { done, total: group.milestones.length })}
              </span>
            </button>
            {open && (
              <ul className="divide-y divide-slate-100 border-t border-slate-100">
                {group.milestones.map((milestone) => (
                  <li key={milestone.id}>
                    <button
                      type="button"
                      onClick={() => setDialog(milestone)}
                      className="flex w-full items-start gap-3 px-5 py-3 text-left hover:bg-slate-50"
                    >
                      <span
                        aria-hidden
                        className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border text-xs ${
                          milestone.achievement
                            ? 'border-emerald-500 bg-emerald-500 text-white'
                            : 'border-slate-300 text-transparent'
                        }`}
                      >
                        ✓
                      </span>
                      <span className="flex-1">
                        <span className={`block text-sm ${milestone.achievement ? 'text-slate-400' : 'text-slate-700'}`}>
                          {title(milestone)}
                        </span>
                        <span className="mt-0.5 block text-xs text-slate-400">
                          {t(`milestones.category.${milestone.category}`)}
                          {milestone.achievement && ` · ${t('milestones.achievedOn')} ${milestone.achievement.achievedOn}`}
                        </span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )
      })}

      {dialog && <MilestoneDialog milestone={dialog} onClose={() => setDialog(null)} titleOf={title} />}
    </div>
  )
}

function MilestoneDialog({
  milestone,
  onClose,
  titleOf,
}: {
  milestone: Milestone
  onClose: () => void
  titleOf: (m: Milestone) => string
}) {
  const { t } = useTranslation()
  const setAchievement = useSetMilestoneAchievement()
  const removeAchievement = useRemoveMilestoneAchievement()
  const uploadPhoto = useUploadMilestonePhoto()
  const fileInput = useRef<HTMLInputElement>(null)

  const [achievedOn, setAchievedOn] = useState(milestone.achievement?.achievedOn ?? todaySgt())
  const [note, setNote] = useState(milestone.achievement?.note ?? '')

  function save() {
    setAchievement.mutate(
      { id: milestone.id, achievedOn, note: note || undefined },
      { onSuccess: onClose },
    )
  }

  function unmark() {
    removeAchievement.mutate(milestone.id, { onSuccess: onClose })
  }

  function onPhotoChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) uploadPhoto.mutate({ id: milestone.id, file })
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      onClick={onClose}
    >
      <div
        className="w-full max-w-md rounded-t-2xl bg-white p-6 sm:rounded-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="mb-1 font-semibold text-slate-800">{titleOf(milestone)}</h3>
        <p className="mb-4 text-xs text-slate-400">{t(`milestones.category.${milestone.category}`)}</p>

        <label className="mb-3 flex flex-col gap-1 text-sm font-medium text-slate-700">
          {t('milestones.achievedOn')}
          <input
            type="date"
            max={todaySgt()}
            value={achievedOn}
            onChange={(e) => setAchievedOn(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2"
          />
        </label>

        <label className="mb-3 flex flex-col gap-1 text-sm font-medium text-slate-700">
          {t('common.note')}{t('common.optional')}
          <input
            maxLength={500}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            className="rounded-lg border border-slate-300 px-3 py-2"
          />
        </label>

        {milestone.achievement && (
          <div className="mb-3">
            {milestone.achievement.hasPhoto && (
              <img
                src={`/api/milestones/${milestone.id}/achievement/photo`}
                alt=""
                className="mb-2 h-32 w-full rounded-lg object-cover"
              />
            )}
            <input
              ref={fileInput}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
              onChange={onPhotoChange}
            />
            <button
              type="button"
              onClick={() => fileInput.current?.click()}
              disabled={uploadPhoto.isPending}
              className="text-sm font-medium text-rose-600 hover:underline"
            >
              {t('milestones.uploadPhoto')}
            </button>
          </div>
        )}

        <div className="flex gap-3">
          <button
            type="button"
            onClick={save}
            disabled={setAchievement.isPending}
            className="flex-1 rounded-xl bg-rose-500 py-2.5 font-semibold text-white shadow hover:bg-rose-600 disabled:opacity-50"
          >
            {milestone.achievement ? t('common.save') : t('milestones.markAchieved')}
          </button>
          {milestone.achievement && (
            <button
              type="button"
              onClick={unmark}
              disabled={removeAchievement.isPending}
              className="rounded-xl border border-slate-300 px-4 py-2.5 font-semibold text-slate-600"
            >
              {t('milestones.unmark')}
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-slate-300 px-4 py-2.5 font-semibold text-slate-600"
          >
            {t('common.cancel')}
          </button>
        </div>
      </div>
    </div>
  )
}

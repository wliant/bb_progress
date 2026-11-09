import { useTranslation } from 'react-i18next'
import type { CareLog, DiaperCondition, FeedMethod } from '../../api/types'

const FEED_METHODS: FeedMethod[] = ['BREAST', 'BOTTLE', 'BOTH']
const CONDITIONS: DiaperCondition[] = ['CLEAN', 'SLIGHTLY_DIRTY', 'DIRTY', 'VERY_DIRTY']

/** The detail an entry of this type carries while it is being edited. */
export interface DetailDraft {
  method: FeedMethod | ''
  amountMl: string
  durationMin: string
  startedAt: string
  endedAt: string
  condition: DiaperCondition | ''
}

/** Seeds the draft from an entry; the inputs are strings so a field can be left blank. */
export function detailDraftFrom(log: CareLog): DetailDraft {
  return {
    method: log.method ?? '',
    amountMl: log.amountMl?.toString() ?? '',
    durationMin: log.durationMin?.toString() ?? '',
    // The API serializes in SGT (+08:00), so the local part is already the SGT wall clock.
    startedAt: log.startedAt?.slice(0, 16) ?? '',
    endedAt: log.endedAt?.slice(0, 16) ?? '',
    condition: log.condition ?? '',
  }
}

/**
 * The detail as the API expects it, carrying only the fields this type owns — sending
 * another type's field is rejected with CARE_DETAIL_TYPE_MISMATCH.
 */
export function detailPayload(type: CareLog['type'], draft: DetailDraft) {
  const num = (v: string) => (v.trim() === '' ? null : Number(v))
  switch (type) {
    case 'FEEDING':
      return {
        method: draft.method || null,
        amountMl: num(draft.amountMl),
        durationMin: num(draft.durationMin),
      }
    case 'SLEEP':
      return {
        startedAt: draft.startedAt ? `${draft.startedAt}:00+08:00` : null,
        endedAt: draft.endedAt ? `${draft.endedAt}:00+08:00` : null,
      }
    case 'DIAPER':
      return { condition: draft.condition || null }
  }
}

const inputClass = 'rounded-lg border border-slate-300 px-3 py-2'
const labelClass = 'flex flex-col gap-1 text-sm font-medium text-slate-700'

function Choices<T extends string>({
  label,
  options,
  value,
  onChange,
  labelFor,
}: {
  label: string
  options: readonly T[]
  value: T | ''
  onChange: (next: T | '') => void
  labelFor: (option: T) => string
}) {
  return (
    <fieldset className="mb-3">
      <legend className="mb-1.5 text-sm font-medium text-slate-700">{label}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <button
            key={option}
            type="button"
            aria-pressed={value === option}
            // Tapping the active choice clears it: detail is optional, and a quick-logged
            // entry must be able to go back to having none.
            onClick={() => onChange(value === option ? '' : option)}
            className={`rounded-xl border px-3 py-2 text-sm font-medium ${
              value === option
                ? 'border-rose-500 bg-rose-50 text-rose-700'
                : 'border-slate-300 text-slate-600 hover:bg-slate-50'
            }`}
          >
            {labelFor(option)}
          </button>
        ))}
      </div>
    </fieldset>
  )
}

/** The fields for one care type. Feeding shows ml and minutes together, per spec 08. */
export function CareDetailFields({
  type,
  draft,
  onChange,
}: {
  type: CareLog['type']
  draft: DetailDraft
  onChange: (next: DetailDraft) => void
}) {
  const { t } = useTranslation()
  const set = <K extends keyof DetailDraft>(key: K, value: DetailDraft[K]) =>
    onChange({ ...draft, [key]: value })

  if (type === 'FEEDING') {
    // Minutes only make sense when the breast was involved; a bottle has no side time.
    const showMinutes = draft.method === 'BREAST' || draft.method === 'BOTH'
    return (
      <>
        <Choices
          label={t('care.method.label')}
          options={FEED_METHODS}
          value={draft.method}
          onChange={(v) => onChange({ ...draft, method: v, durationMin: v === 'BOTTLE' ? '' : draft.durationMin })}
          labelFor={(m) => t(`care.method.${m}`)}
        />
        <label className={`mb-3 ${labelClass}`}>
          {t('care.amountMl')}
          <input
            type="number"
            inputMode="numeric"
            min={1}
            max={500}
            value={draft.amountMl}
            onChange={(e) => set('amountMl', e.target.value)}
            className={inputClass}
          />
        </label>
        {showMinutes && (
          <label className={`mb-3 ${labelClass}`}>
            {t('care.durationMin')}
            <input
              type="number"
              inputMode="numeric"
              min={1}
              max={180}
              value={draft.durationMin}
              onChange={(e) => set('durationMin', e.target.value)}
              className={inputClass}
            />
          </label>
        )}
      </>
    )
  }

  if (type === 'SLEEP') {
    return (
      <>
        <label className={`mb-3 ${labelClass}`}>
          {t('care.startedAt')}
          <input
            type="datetime-local"
            value={draft.startedAt}
            onChange={(e) => set('startedAt', e.target.value)}
            className={inputClass}
          />
        </label>
        <label className={`mb-3 ${labelClass}`}>
          {t('care.endedAt')}
          {t('common.optional')}
          <input
            type="datetime-local"
            value={draft.endedAt}
            onChange={(e) => set('endedAt', e.target.value)}
            className={inputClass}
          />
        </label>
      </>
    )
  }

  return (
    <Choices
      label={t('care.condition.label')}
      options={CONDITIONS}
      value={draft.condition}
      onChange={(v) => set('condition', v)}
      labelFor={(c) => t(`care.condition.${c}`)}
    />
  )
}

import { useTranslation } from 'react-i18next'
import { useNewbornAssessment } from '../../api/hooks'
import type { GrowthMeasure } from '../../api/types'

const UNIT_KEY: Record<GrowthMeasure, string> = {
  WEIGHT: 'growth.weightUnit',
  HEIGHT: 'growth.heightUnit',
  HEAD_CIRCUMFERENCE: 'growth.headUnit',
}

const LABEL_KEY: Record<GrowthMeasure, string> = {
  WEIGHT: 'growth.weight',
  HEIGHT: 'profile.birthLength',
  HEAD_CIRCUMFERENCE: 'growth.headCircumference',
}

export function formatGestationalAge(days: number): string {
  return `${Math.floor(days / 7)}+${days % 7}`
}

/**
 * Size at birth answers a different question from the growth curves — how the baby compared
 * with others born at the same gestation — so it gets its own panel rather than being forced
 * onto an axis of age since birth.
 */
export function NewbornAssessmentCard() {
  const { t } = useTranslation()
  const { data: assessment, isLoading } = useNewbornAssessment()

  if (isLoading || !assessment) return null

  return (
    <section className="rounded-2xl bg-white p-5 shadow-sm" data-testid="newborn-assessment">
      <div className="mb-1 flex items-baseline justify-between gap-3">
        <h3 className="font-semibold text-slate-700">{t('newborn.title')}</h3>
        <span className="text-sm text-slate-500">
          {t('newborn.atGestation', { ga: formatGestationalAge(assessment.gestationalAgeDays) })}
        </span>
      </div>

      {!assessment.covered ? (
        <p className="py-2 text-sm text-slate-500">{t('newborn.outOfRange')}</p>
      ) : (
        <>
          <ul className="divide-y divide-slate-100">
            {assessment.assessments.map((item) => (
              <li key={item.measure} className="flex items-center gap-3 py-3 text-sm">
                <span className="w-24 shrink-0 font-medium text-slate-700">
                  {t(LABEL_KEY[item.measure])}
                </span>
                <span className="w-24 shrink-0 text-slate-600">
                  {item.value} {t(UNIT_KEY[item.measure])}
                </span>
                <span className="flex-1 text-slate-700">
                  {t('newborn.centile', { centile: item.centile })}
                </span>
                <span className="font-mono text-xs text-slate-400">
                  z {item.zScore > 0 ? '+' : ''}{item.zScore.toFixed(2)}
                </span>
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-slate-400">
            {t('newborn.source', { standard: assessment.standard })}
          </p>
          <p className="mt-1 text-xs text-slate-400">{t('newborn.disclaimer')}</p>
        </>
      )}
    </section>
  )
}

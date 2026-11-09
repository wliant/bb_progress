import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useCareAggregates } from '../api/hooks'
import { ErrorState } from '../components/ErrorState'
import { RequireBaby } from '../components/RequireBaby'
import { CareTabs } from '../features/care/CareTabs'
import { DiaperChart, IntakeChart, SimpleBarChart } from '../features/care/CareTrendCharts'
import { SERIES } from '../features/care/careChartPalette'
import { formatMinutes } from '../lib/careFormat'
import { todaySgt } from '../lib/dates'

const RANGES = [7, 30, 90, 'all'] as const
type Range = (typeof RANGES)[number]

/** "all" is bounded by the API's 400-day window, which outlives any single baby's record. */
function startFor(range: Range, today: string): string {
  const days = range === 'all' ? 399 : range - 1
  const start = new Date(`${today}T00:00:00Z`)
  start.setUTCDate(start.getUTCDate() - days)
  return start.toISOString().slice(0, 10)
}

export function CareTrendsPage() {
  return <RequireBaby>{() => <CareTrendsContent />}</RequireBaby>
}

function CareTrendsContent() {
  const { t } = useTranslation()
  const [range, setRange] = useState<Range>(30)
  const [showTable, setShowTable] = useState(false)
  const today = todaySgt()
  const { data, isLoading, isError, error, refetch } = useCareAggregates(startFor(range, today), today)

  const summary = data?.summary
  const days = data?.days ?? []
  const openSleeps = days.reduce((total, day) => total + day.openSleeps, 0)

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4">
      <h2 className="text-xl font-bold text-slate-800">{t('care.title')}</h2>
      <CareTabs />

      {/* Filters in one row above the charts. */}
      <div className="flex flex-wrap gap-2" role="group" aria-label={t('care.trends.title')}>
        {RANGES.map((option) => (
          <button
            key={option}
            type="button"
            aria-pressed={range === option}
            onClick={() => setRange(option)}
            className={`rounded-xl border px-3 py-1.5 text-sm font-medium ${
              range === option
                ? 'border-rose-500 bg-rose-50 text-rose-700'
                : 'border-slate-300 text-slate-600 hover:bg-slate-50'
            }`}
          >
            {t(`care.trends.range.${option}`)}
          </button>
        ))}
      </div>

      {isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : isLoading || !summary ? (
        <div className="space-y-4" aria-busy="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-48 animate-pulse rounded-2xl bg-white shadow-sm" />
          ))}
        </div>
      ) : summary.feedsTotal === 0 && summary.diaperTotal === 0 && summary.sleepMinutes === 0 ? (
        <p className="rounded-2xl bg-white p-8 text-center text-sm text-slate-500 shadow-sm">
          {t('care.trends.noData')}
        </p>
      ) : (
        <>
          <section className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {/* The total never appears without the coverage that qualifies it. */}
            <Tile
              label={t('care.trends.totalIntake')}
              value={`${summary.amountMl.toLocaleString()} ml`}
              hint={t('care.trends.coverage', {
                measured: summary.feedsWithAmount,
                total: summary.feedsTotal,
                pct: summary.coveragePct,
              })}
            />
            <Tile
              label={t('care.trends.avgIntake')}
              value={`${summary.avgAmountMlPerDay} ml`}
              hint={t('care.trends.perDayOver', { days: summary.daysWithData })}
            />
            <Tile
              label={t('care.trends.avgFeeds')}
              value={summary.avgFeedsPerDay.toString()}
              hint={t('care.trends.perDayOver', { days: summary.daysWithData })}
            />
            <Tile
              label={t('care.trends.totalChanges')}
              value={summary.diaperTotal.toString()}
              hint={t('care.trends.perDayOver', { days: summary.daysWithData })}
            />
          </section>

          <IntakeChart days={days} />
          <SimpleBarChart
            days={days}
            dataKey="feedsTotal"
            title={t('care.trends.feeds')}
            color={SERIES.feeds}
          />
          <SimpleBarChart
            days={days}
            dataKey="breastMinutes"
            title={t('care.trends.breastMinutes')}
            color={SERIES.breastMinutes}
            unit=" min"
          />
          <SimpleBarChart
            days={days}
            dataKey="sleepMinutes"
            title={t('care.trends.sleep')}
            color={SERIES.sleep}
            formatValue={(value) => formatMinutes(t, value)}
          />
          {openSleeps > 0 && (
            <p className="text-xs text-slate-400">{t('care.trends.openSleeps', { count: openSleeps })}</p>
          )}
          <DiaperChart days={days} />

          {/* A table view so every figure is reachable without reading a color. */}
          <button
            type="button"
            onClick={() => setShowTable((shown) => !shown)}
            className="self-start text-sm font-medium text-rose-600 underline"
            aria-expanded={showTable}
          >
            {showTable ? t('care.trends.hideTable') : t('care.trends.showTable')}
          </button>
          {showTable && <DayTable days={days} />}
        </>
      )}
    </div>
  )
}

function Tile({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div className="rounded-2xl bg-white p-4 shadow-sm">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-0.5 text-lg font-bold text-slate-800">{value}</p>
      <p className="mt-0.5 text-[11px] leading-tight text-slate-400">{hint}</p>
    </div>
  )
}

function DayTable({ days }: { days: import('../api/types').DayAggregate[] }) {
  const { t } = useTranslation()
  return (
    <div className="overflow-x-auto rounded-2xl bg-white p-4 shadow-sm">
      <table className="w-full text-left text-xs">
        <thead className="text-slate-500">
          <tr>
            <th scope="col" className="py-1 pr-3">{t('common.date')}</th>
            <th scope="col" className="py-1 pr-3">{t('care.trends.intake')}</th>
            <th scope="col" className="py-1 pr-3">{t('care.trends.feeds')}</th>
            <th scope="col" className="py-1 pr-3">{t('care.trends.breastMinutes')}</th>
            <th scope="col" className="py-1 pr-3">{t('care.trends.sleep')}</th>
            <th scope="col" className="py-1">{t('care.trends.diapers')}</th>
          </tr>
        </thead>
        <tbody className="text-slate-700">
          {days.map((day) => (
            <tr key={day.date} className="border-t border-slate-100">
              <td className="py-1 pr-3 font-mono">{day.date}</td>
              <td className="py-1 pr-3">
                {day.amountMl} ml
                {day.partial && (
                  <span className="text-slate-400"> ({day.feedsWithAmount}/{day.feedsTotal})</span>
                )}
              </td>
              <td className="py-1 pr-3">{day.feedsTotal}</td>
              <td className="py-1 pr-3">{day.breastMinutes}</td>
              <td className="py-1 pr-3">{day.sleepMinutes}</td>
              <td className="py-1">{day.diaperTotal}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

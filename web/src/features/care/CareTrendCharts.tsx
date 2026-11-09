import { useTranslation } from 'react-i18next'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { DayAggregate } from '../../api/types'
import { AXIS, CONDITION_COLORS, GRID, PARTIAL_PATTERN_ID, SERIES } from './careChartPalette'

/** Day labels are already SGT calendar dates from the API; show them as DD/MM. */
function dayTick(date: string): string {
  const [, month, day] = date.split('-')
  return `${day}/${month}`
}

const CHART_HEIGHT = 200

function Frame({
  title,
  children,
  footnote,
  empty,
}: {
  title: string
  children: React.ReactNode
  footnote?: string
  /** True when every value in the series is zero — say so rather than show a blank grid. */
  empty?: boolean
}) {
  const { t } = useTranslation()
  return (
    <figure className="rounded-2xl bg-white p-4 shadow-sm">
      <figcaption className="mb-2 text-sm font-semibold text-slate-700">{title}</figcaption>
      {empty ? (
        <p className="py-10 text-center text-sm text-slate-400">{t('care.trends.noneInRange')}</p>
      ) : (
        children
      )}
      {footnote && !empty && <p className="mt-1 text-xs text-slate-400">{footnote}</p>}
    </figure>
  )
}

/** The 45° hatch that marks a day whose volume total is incomplete. */
function PartialHatch() {
  return (
    <defs>
      <pattern
        id={PARTIAL_PATTERN_ID}
        width={6}
        height={6}
        patternTransform="rotate(45)"
        patternUnits="userSpaceOnUse"
      >
        <rect width={6} height={6} fill={SERIES.intake} opacity={0.18} />
        <line x1={0} y1={0} x2={0} y2={6} stroke={SERIES.intake} strokeWidth={2.5} />
      </pattern>
    </defs>
  )
}

const axisProps = {
  stroke: AXIS,
  fontSize: 11,
  tickLine: false as const,
}

/**
 * Daily volume. Partial days — where some feed's amount was never measured — are hatched
 * rather than drawn solid, because their bar understates the day by an unknown margin.
 * The tooltip always names the coverage, so no figure here is ever read as a bare total.
 */
export function IntakeChart({ days }: { days: DayAggregate[] }) {
  const { t } = useTranslation()
  return (
    <Frame
      title={t('care.trends.intake')}
      footnote={t('care.trends.coverageNote')}
      empty={days.every((day) => day.amountMl === 0)}
    >
      {/* Hand-built legend: the partial entry has to show the hatch itself, which a
          solid color swatch cannot convey. */}
      <ul className="mb-1 flex flex-wrap gap-4 text-xs text-slate-500">
        <li className="flex items-center gap-1.5">
          <span aria-hidden className="h-3 w-3 rounded-sm" style={{ background: SERIES.intake }} />
          {t('care.trends.intakeMeasured')}
        </li>
        <li className="flex items-center gap-1.5">
          <span
            aria-hidden
            className="h-3 w-3 rounded-sm border"
            style={{
              borderColor: SERIES.intake,
              backgroundImage: `repeating-linear-gradient(45deg, ${SERIES.intake} 0 2px, transparent 2px 4px)`,
            }}
          />
          {t('care.trends.intakePartial')}
        </li>
      </ul>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <BarChart data={days} margin={{ top: 4, right: 8, bottom: 0, left: 4 }}>
          <PartialHatch />
          <CartesianGrid strokeDasharray="2 4" stroke={GRID} vertical={false} />
          <XAxis dataKey="date" tickFormatter={dayTick} {...axisProps} />
          <YAxis unit=" ml" {...axisProps} />
          <Tooltip
            content={({ active, payload }) => {
              if (!active || !payload?.length) return null
              const day = payload[0].payload as DayAggregate
              return (
                <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow">
                  <p className="font-semibold text-slate-700">{day.date}</p>
                  <p className="text-slate-600">
                    {t('care.trends.fromFeeds', {
                      ml: day.amountMl,
                      measured: day.feedsWithAmount,
                      total: day.feedsTotal,
                    })}
                  </p>
                </div>
              )
            }}
          />
          <Bar dataKey="amountMl" radius={[4, 4, 0, 0]} isAnimationActive={false}>
            {days.map((day) => (
              <Cell
                key={day.date}
                fill={day.partial ? `url(#${PARTIAL_PATTERN_ID})` : SERIES.intake}
                stroke={day.partial ? SERIES.intake : undefined}
                strokeWidth={day.partial ? 1 : 0}
              />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </Frame>
  )
}

/**
 * Feed count, as the honest companion to a partial volume: frequency is complete even on
 * the days whose volume is not. Its own chart rather than a second axis on the intake
 * chart — ml and a count share no scale.
 */
export function SimpleBarChart({
  days,
  dataKey,
  title,
  color,
  unit,
  formatValue,
}: {
  days: DayAggregate[]
  dataKey: keyof DayAggregate
  title: string
  color: string
  unit?: string
  formatValue?: (value: number) => string
}) {
  return (
    <Frame title={title} empty={days.every((day) => Number(day[dataKey]) === 0)}>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <BarChart data={days} margin={{ top: 4, right: 8, bottom: 0, left: 4 }}>
          <CartesianGrid strokeDasharray="2 4" stroke={GRID} vertical={false} />
          <XAxis dataKey="date" tickFormatter={dayTick} {...axisProps} />
          <YAxis unit={unit} {...axisProps} />
          <Tooltip
            labelFormatter={(date) => String(date)}
            formatter={(value) => [
              formatValue ? formatValue(Number(value)) : `${value}${unit ?? ''}`,
              title,
            ]}
            contentStyle={{ fontSize: 12, borderRadius: 8, borderColor: GRID }}
          />
          <Bar
            dataKey={dataKey}
            fill={color}
            radius={[4, 4, 0, 0]}
            isAnimationActive={false}
            name={title}
          />
        </BarChart>
      </ResponsiveContainer>
    </Frame>
  )
}

/** Stack order, and the i18n label each segment wears. */
const CONDITION_SEGMENTS = [
  { key: 'diaperClean', label: 'CLEAN' },
  { key: 'diaperSlightlyDirty', label: 'SLIGHTLY_DIRTY' },
  { key: 'diaperDirty', label: 'DIRTY' },
  { key: 'diaperVeryDirty', label: 'VERY_DIRTY' },
  { key: 'diaperUnknown', label: 'UNKNOWN' },
] as const

/** Stacked by condition, ordered clean → very dirty, with unrecorded last. */
export function DiaperChart({ days }: { days: DayAggregate[] }) {
  const { t } = useTranslation()
  return (
    <Frame
      title={t('care.trends.diapers')}
      empty={days.every((day) => day.diaperTotal === 0)}
    >
      {/* Hand-built so the legend keeps the ramp's order; Recharts reorders its own. */}
      <ul className="mb-1 flex flex-wrap gap-3 text-xs text-slate-500">
        {CONDITION_SEGMENTS.map(({ key, label }) => (
          <li key={key} className="flex items-center gap-1.5">
            <span
              aria-hidden
              className="h-3 w-3 rounded-sm"
              style={{ background: CONDITION_COLORS[key] }}
            />
            {t(`care.condition.${label}`)}
          </li>
        ))}
      </ul>
      <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
        <BarChart data={days} margin={{ top: 4, right: 8, bottom: 0, left: 4 }}>
          <CartesianGrid strokeDasharray="2 4" stroke={GRID} vertical={false} />
          <XAxis dataKey="date" tickFormatter={dayTick} {...axisProps} />
          <YAxis allowDecimals={false} {...axisProps} />
          <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8, borderColor: GRID }} />
          {CONDITION_SEGMENTS.map(({ key, label }, i) => (
            <Bar
              key={key}
              dataKey={key}
              stackId="diaper"
              name={t(`care.condition.${label}`)}
              fill={CONDITION_COLORS[key]}
              // A surface-colored gap between segments keeps the stack readable.
              stroke="#ffffff"
              strokeWidth={1}
              radius={i === CONDITION_SEGMENTS.length - 1 ? [4, 4, 0, 0] : undefined}
              isAnimationActive={false}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>
    </Frame>
  )
}


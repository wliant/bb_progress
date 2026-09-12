import { useTranslation } from 'react-i18next'
import {
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { GrowthRecord, GrowthStandards, GrowthMeasure } from '../../api/types'
import { ageInMonths } from '../../lib/dates'

const PERCENTILES = ['p3', 'p15', 'p50', 'p85', 'p97'] as const

const MEASURE_FIELD: Record<GrowthMeasure, keyof GrowthRecord> = {
  WEIGHT: 'weightKg',
  HEIGHT: 'heightCm',
  HEAD_CIRCUMFERENCE: 'headCircumferenceCm',
}

interface Props {
  measure: GrowthMeasure
  records: GrowthRecord[]
  standards: GrowthStandards | undefined
  dateOfBirth: string
  unit: string
}

export function GrowthChart({ measure, records, standards, dateOfBirth, unit }: Props) {
  const { t } = useTranslation()

  // One row per reference age with all percentile values, e.g. {ageMonths, p3, ..., p97}.
  const referenceRows = (standards?.curves.p50 ?? []).map((point, i) => {
    const row: Record<string, number> = { ageMonths: point.ageMonths }
    for (const p of PERCENTILES) row[p] = standards!.curves[p][i].value
    return row
  })

  const field = MEASURE_FIELD[measure]
  const babyPoints = records
    .filter((r) => r[field] != null)
    .map((r) => ({
      ageMonths: Number(ageInMonths(dateOfBirth, r.measuredOn).toFixed(2)),
      baby: Number(r[field]),
      measuredOn: r.measuredOn,
    }))

  return (
    <div data-testid={`growth-chart-${measure}`}>
      <ResponsiveContainer width="100%" height={320}>
        <ComposedChart margin={{ top: 8, right: 12, bottom: 4, left: -12 }}>
          <CartesianGrid strokeDasharray="2 4" stroke="#e2e8f0" />
          <XAxis
            dataKey="ageMonths"
            type="number"
            domain={[0, 36]}
            tickCount={13}
            label={{ value: t('growth.ageMonths'), position: 'insideBottom', offset: -2, fontSize: 12 }}
            fontSize={11}
          />
          <YAxis
            dataKey={undefined}
            type="number"
            domain={['auto', 'auto']}
            unit={` ${unit}`}
            fontSize={11}
          />
          <Tooltip
            formatter={(value, name) => [
              `${value ?? ''} ${unit}`,
              String(name) === 'baby' ? t('growth.baby') : String(name).toUpperCase(),
            ]}
            labelFormatter={(age) => `${t('growth.ageMonths')}: ${age}`}
          />
          {PERCENTILES.map((p) => (
            <Line
              key={p}
              data={referenceRows}
              dataKey={p}
              name={p.toUpperCase()}
              stroke={p === 'p50' ? '#94a3b8' : '#cbd5e1'}
              strokeDasharray={p === 'p50' ? undefined : '5 4'}
              strokeWidth={p === 'p50' ? 1.5 : 1}
              dot={false}
              isAnimationActive={false}
            />
          ))}
          <Scatter data={babyPoints} dataKey="baby" name="baby" fill="#e11d48" isAnimationActive={false} />
          <Line
            data={babyPoints}
            dataKey="baby"
            name="baby"
            stroke="#e11d48"
            strokeWidth={2.5}
            dot={{ r: 3, fill: '#e11d48' }}
            isAnimationActive={false}
            legendType="none"
          />
        </ComposedChart>
      </ResponsiveContainer>
      <p className="mt-1 text-xs text-slate-400">{t('growth.percentileNote')}</p>
    </div>
  )
}

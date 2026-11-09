/**
 * Chart colors for the care trends view.
 *
 * Every value below was run through the dataviz validator (six checks, light and dark
 * surfaces) rather than picked by eye. Change one and re-run it.
 *
 * Each trend is its own single-series chart, so these four hues are never adjacent to
 * one another; they are kept distinct anyway so a reader moving between charts is not
 * misled into reading two of them as the same measure. Rose matches the growth chart's
 * accent, which is already how "the baby's own data" reads in this app.
 */
export const SERIES = {
  intake: '#e11d48',
  feeds: '#0891b2',
  breastMinutes: '#7c3aed',
  sleep: '#0d9488',
  wet: '#2563eb',
} as const

/**
 * Diaper condition is ordered, not categorical, so it gets one sequential ramp running
 * light to dark with increasing dirtiness. Monotonic in lightness, and every adjacent
 * pair clears the CVD and normal-vision separation floors.
 */
export const CONDITION_COLORS = {
  diaperClean: '#d97706',
  diaperSlightlyDirty: '#be123c',
  diaperDirty: '#86198f',
  diaperVeryDirty: '#3b0764',
  /** Deliberately the one gray: an unrecorded condition is an absence, not a degree. */
  diaperUnknown: '#94a3b8',
} as const

/**
 * The reference band is context, not data. It is a recessive neutral so it never competes
 * with the bars in front of it -- a second saturated hue here would read as a series.
 */
export const REFERENCE = {
  band: '#64748b',
  bandOpacity: 0.1,
  line: '#64748b',
} as const

export const AXIS = '#94a3b8'
export const GRID = '#e2e8f0'

/** id of the 45° hatch that marks a partial day. Defined once per chart in <defs>. */
export const PARTIAL_PATTERN_ID = 'care-partial-hatch'

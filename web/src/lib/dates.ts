const SGT = 'Asia/Singapore'

/** Today's date (YYYY-MM-DD) in Singapore Time, regardless of the browser timezone. */
export function todaySgt(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: SGT }).format(new Date())
}

/** Time of day (HH:mm) in SGT for an ISO timestamp. */
export function timeSgt(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: SGT,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(iso))
}

/** Whole months and remaining days between a date of birth and a reference date. */
export function ageParts(dateOfBirth: string, onDate: string): { months: number; days: number } {
  const dob = new Date(dateOfBirth + 'T00:00:00')
  const ref = new Date(onDate + 'T00:00:00')
  let months =
    (ref.getFullYear() - dob.getFullYear()) * 12 + (ref.getMonth() - dob.getMonth())
  let anchor = addMonths(dob, months)
  if (anchor > ref) {
    months -= 1
    anchor = addMonths(dob, months)
  }
  const days = Math.round((ref.getTime() - anchor.getTime()) / 86_400_000)
  return { months: Math.max(0, months), days: Math.max(0, days) }
}

/** Age in (possibly fractional) months, for plotting growth data. */
export function ageInMonths(dateOfBirth: string, onDate: string): number {
  const dob = new Date(dateOfBirth + 'T00:00:00')
  const ref = new Date(onDate + 'T00:00:00')
  const days = (ref.getTime() - dob.getTime()) / 86_400_000
  return days / 30.4375
}

function addMonths(date: Date, months: number): Date {
  const result = new Date(date)
  result.setMonth(result.getMonth() + months)
  return result
}

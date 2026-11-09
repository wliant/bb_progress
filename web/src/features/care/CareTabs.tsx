import { NavLink } from 'react-router-dom'
import { useTranslation } from 'react-i18next'

/** Day view and trends are two readings of the same entries, so they sit side by side. */
export function CareTabs() {
  const { t } = useTranslation()
  const tabClass = ({ isActive }: { isActive: boolean }) =>
    `rounded-xl px-3 py-1.5 text-sm font-medium ${
      isActive ? 'bg-rose-100 text-rose-700' : 'text-slate-500 hover:bg-slate-100'
    }`
  return (
    <nav className="flex gap-1" aria-label={t('care.title')}>
      <NavLink to="/care" end className={tabClass}>
        {t('care.tabDay')}
      </NavLink>
      <NavLink to="/care/trends" className={tabClass}>
        {t('care.tabTrends')}
      </NavLink>
    </nav>
  )
}

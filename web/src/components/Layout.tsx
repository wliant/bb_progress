import { NavLink, Outlet } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { LanguageSwitcher } from './LanguageSwitcher'
import { ToastHost } from './ToastHost'

const NAV_ITEMS = [
  { to: '/', key: 'nav.home', icon: '🏠' },
  { to: '/growth', key: 'nav.growth', icon: '📈' },
  { to: '/milestones', key: 'nav.milestones', icon: '🏆' },
  { to: '/care', key: 'nav.care', icon: '🍼' },
  { to: '/media', key: 'nav.media', icon: '🎞️' },
  { to: '/profile', key: 'nav.profile', icon: '👶' },
] as const

export function Layout() {
  const { t } = useTranslation()

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
      isActive ? 'bg-rose-100 text-rose-700' : 'text-slate-600 hover:bg-slate-100'
    }`

  const tabClass = ({ isActive }: { isActive: boolean }) =>
    `flex min-w-0 flex-1 flex-col items-center gap-0.5 px-0.5 py-2 text-[11px] leading-tight ${
      isActive ? 'text-rose-600 font-semibold' : 'text-slate-500'
    }`

  return (
    <div className="min-h-screen bg-slate-50 md:flex">
      {/* Desktop sidebar */}
      <aside className="hidden md:flex md:w-56 md:flex-col md:border-r md:border-slate-200 md:bg-white md:p-4 md:gap-1">
        <h1 className="mb-4 px-3 text-lg font-bold text-rose-600">{t('app.title')}</h1>
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} className={linkClass} end={item.to === '/'}>
            <span aria-hidden>{item.icon}</span>
            {t(item.key)}
          </NavLink>
        ))}
        <div className="mt-auto px-3">
          <LanguageSwitcher />
        </div>
      </aside>

      <div className="flex min-h-screen flex-1 flex-col">
        {/* Mobile header */}
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 md:hidden">
          <h1 className="text-lg font-bold text-rose-600">{t('app.title')}</h1>
          <LanguageSwitcher />
        </header>

        <main className="flex-1 p-4 pb-20 md:p-8 md:pb-8">
          <Outlet />
        </main>

        {/* Mobile bottom tab bar */}
        <nav className="fixed inset-x-0 bottom-0 flex border-t border-slate-200 bg-white md:hidden">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.to} to={item.to} className={tabClass} end={item.to === '/'}>
              <span aria-hidden className="text-lg leading-none">{item.icon}</span>
              {t(item.key)}
            </NavLink>
          ))}
        </nav>
      </div>

      <ToastHost />
    </div>
  )
}

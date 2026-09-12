import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useBaby } from '../api/hooks'
import type { Baby } from '../api/types'
import { ErrorState } from './ErrorState'

/**
 * Every feature is recorded against the baby, so each page waits for the profile and
 * offers to create it rather than showing controls that would fail on use.
 */
export function RequireBaby({ children }: { children: (baby: Baby) => ReactNode }) {
  const { t } = useTranslation()
  const { data: baby, isLoading, isError, error, refetch } = useBaby()

  if (isLoading) return <p className="text-slate-500">{t('common.loading')}</p>
  if (isError) return <ErrorState error={error} onRetry={() => void refetch()} />

  if (!baby) {
    return (
      <div className="mx-auto max-w-md rounded-2xl bg-white p-8 text-center shadow-sm">
        <p className="mb-4 text-4xl" aria-hidden>
          👶
        </p>
        <p className="mb-6 text-slate-600">{t('profile.notCreated')}</p>
        <Link
          to="/profile"
          className="inline-block rounded-xl bg-rose-500 px-6 py-3 font-semibold text-white shadow hover:bg-rose-600"
        >
          {t('profile.create')}
        </Link>
      </div>
    )
  }

  return <>{children(baby)}</>
}

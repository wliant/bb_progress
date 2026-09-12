import { useTranslation } from 'react-i18next'
import { messageForError } from '../lib/apiErrors'

interface Props {
  error: unknown
  onRetry?: () => void
}

/** Shown in place of content when a query fails, so a failure never looks like "no data". */
export function ErrorState({ error, onRetry }: Props) {
  const { t } = useTranslation()

  return (
    <div role="alert" className="mx-auto max-w-md rounded-2xl bg-white p-8 text-center shadow-sm">
      <p className="mb-2 text-3xl" aria-hidden>
        ⚠️
      </p>
      <p className="mb-1 font-semibold text-slate-800">{t('common.loadFailed')}</p>
      <p className="mb-5 text-sm text-slate-500">{messageForError(error)}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="rounded-xl bg-rose-500 px-5 py-2.5 font-semibold text-white shadow hover:bg-rose-600"
        >
          {t('common.retry')}
        </button>
      )}
    </div>
  )
}

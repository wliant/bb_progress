import { useEffect, useState } from 'react'
import { subscribeToasts, type Toast } from '../lib/toast'

const VISIBLE_MS = 4000

export function ToastHost() {
  const [toasts, setToasts] = useState<Toast[]>([])

  useEffect(
    () =>
      subscribeToasts((toast) => {
        setToasts((current) => [...current, toast])
        setTimeout(
          () => setToasts((current) => current.filter((t) => t.id !== toast.id)),
          VISIBLE_MS,
        )
      }),
    [],
  )

  if (toasts.length === 0) return null

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-20 z-[60] flex flex-col items-center gap-2 px-4 md:bottom-6">
      {toasts.map((toast) => (
        <div
          key={toast.id}
          role="alert"
          className={`pointer-events-auto max-w-md rounded-xl px-4 py-2.5 text-sm text-white shadow-lg ${
            toast.kind === 'error' ? 'bg-red-600' : 'bg-emerald-600'
          }`}
        >
          {toast.message}
        </div>
      ))}
    </div>
  )
}

export type ToastKind = 'error' | 'success'

export interface Toast {
  id: number
  kind: ToastKind
  message: string
}

type Listener = (toast: Toast) => void

const listeners = new Set<Listener>()
let nextId = 1

/** Usable outside the React tree, which is where React Query reports mutation failures. */
export function showToast(message: string, kind: ToastKind = 'error') {
  const toast: Toast = { id: nextId++, kind, message }
  listeners.forEach((listener) => listener(toast))
}

export function subscribeToasts(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '../i18n'
import { ToastHost } from '../components/ToastHost'
import { messageForError } from '../lib/apiErrors'
import { showToast } from '../lib/toast'

/** Mirrors main.tsx so tests exercise the real error-surfacing wiring. */
export function renderApp(ui: ReactElement, { route = '/', language = 'zh-CN' } = {}) {
  void i18n.changeLanguage(language)
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
    mutationCache: new MutationCache({
      onError: (error) => showToast(messageForError(error)),
    }),
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        {ui}
        <ToastHost />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

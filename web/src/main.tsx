import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './i18n'
import './index.css'
import App from './App'
import { messageForError } from './lib/apiErrors'
import { showToast } from './lib/toast'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
  // One place to surface every failed write, so no action can fail silently.
  // Failed reads are rendered in place by each page instead.
  mutationCache: new MutationCache({
    onError: (error) => showToast(messageForError(error)),
  }),
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)

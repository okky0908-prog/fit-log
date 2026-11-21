import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { ToastProvider } from './components/Toast'
import { AuthProvider } from './lib/auth/AuthProvider'
import { createQueryClient } from './lib/queryClient'
import { routes } from './pages/routes'
import './index.css'

const router = createBrowserRouter(routes)

// アプリの入口。サーバーのデータ（TanStack Query）・お知らせ・ログインの状態・画面の切り替えの順に包む。
// お知らせは画面の切り替えの外に置き、画面を移ったあとも出し続ける
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={createQueryClient()}>
      <ToastProvider>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>,
)

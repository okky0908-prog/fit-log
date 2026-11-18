import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

// アプリの入口。画面（React Router）とログインの状態は、段階 1 で足す
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <h1>FIT LOG</h1>
  </StrictMode>,
)

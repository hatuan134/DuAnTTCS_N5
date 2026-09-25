import {
  Navigate,
  createBrowserRouter,
} from 'react-router-dom'

import AppLayout from '../layouts/AppLayout'
import {
  appRoutes,
  publicRoutes,
} from './featureRegistry'

function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <div className="text-center">
        <h1 className="text-5xl font-semibold text-slate-900">
          404
        </h1>

        <p className="mt-2 text-slate-500">
          Không tìm thấy trang.
        </p>
      </div>
    </div>
  )
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: (
      <Navigate
        to="/dashboard"
        replace
      />
    ),
  },

  ...publicRoutes,

  {
    element: <AppLayout />,
    children: appRoutes,
  },

  {
    path: '*',
    element: <NotFoundPage />,
  },
])
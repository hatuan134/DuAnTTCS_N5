import {
  Navigate,
  createBrowserRouter,
} from 'react-router-dom'

import AppLayout
  from '../components/layout/AppLayout'

import ProtectedRoute
  from './ProtectedRoute'

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

        <a
          href="/"
          className="mt-5 inline-block text-sm font-medium text-blue-600 hover:text-blue-700"
        >
          Quay lại trang chủ
        </a>
      </div>
    </div>
  )
}

export const router =
  createBrowserRouter([
    // Mở localhost:5173/
    // thì vào màn hình đăng nhập
    {
      path: '/',
      element: (
        <Navigate
          to="/login"
          replace
        />
      ),
    },

    // Các route public:
    // login, forgot-password,
    // reset-password...
    ...publicRoutes,

    // Các trang nghiệp vụ
    // đều phải đăng nhập
    {
      element: (
        <ProtectedRoute />
      ),

      children: [
        {
          element: (
            <AppLayout />
          ),

          children:
            appRoutes,
        },
      ],
    },

    {
      path: '*',
      element: (
        <NotFoundPage />
      ),
    },
  ])
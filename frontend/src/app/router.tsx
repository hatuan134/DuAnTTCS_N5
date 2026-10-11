import PublicLayout from '../components/public/PublicLayout'
import PageTransition from '../components/ui/PageTransition'
import {
  Outlet,
  Link,
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

        <Link
          to="/"
          className="mt-5 inline-block text-sm font-medium text-blue-600 hover:text-blue-700"
        >
          Quay lại trang chủ
        </Link>
      </div>
    </div>
  )
}

export const router =
  createBrowserRouter([
    { element: <PublicLayout />, children: publicRoutes.filter(route => route.path === '/' || route.path?.startsWith('/catalog')) },
    { element: <PageTransition><Outlet /></PageTransition>, children: publicRoutes.filter(route => route.path !== '/' && !route.path?.startsWith('/catalog')) },

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
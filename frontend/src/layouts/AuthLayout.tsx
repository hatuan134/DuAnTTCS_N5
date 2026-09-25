import type { ReactNode } from 'react'

import {
  BookOpen,
  CreditCard,
  ShieldCheck,
  UsersRound,
} from 'lucide-react'

interface AuthLayoutProps {
  children: ReactNode
}

const highlights = [
  {
    icon: UsersRound,
    title: 'Quản lý bạn đọc',
    description:
      'Quản lý hồ sơ và tài khoản bạn đọc tập trung.',
  },
  {
    icon: CreditCard,
    title: 'Thẻ thư viện',
    description:
      'Theo dõi trạng thái và thời hạn sử dụng thẻ.',
  },
  {
    icon: ShieldCheck,
    title: 'Bảo mật & phân quyền',
    description:
      'Kiểm soát truy cập theo từng vai trò trong hệ thống.',
  },
]

export default function AuthLayout({
  children,
}: AuthLayoutProps) {
  return (
    <div className="min-h-screen bg-slate-50 lg:grid lg:grid-cols-[1.05fr_0.95fr]">
      <section className="relative hidden min-h-screen overflow-hidden bg-blue-600 px-12 py-10 text-white lg:flex lg:flex-col">
        <div className="pointer-events-none absolute -right-32 -top-28 h-96 w-96 rounded-full border-[70px] border-white/5" />

        <div className="pointer-events-none absolute -bottom-40 -left-32 h-[480px] w-[480px] rounded-full border-[90px] border-white/5" />

        <div className="relative z-10 flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-white/15 backdrop-blur-sm">
            <BookOpen size={25} />
          </div>

          <div>
            <div className="text-xl font-semibold tracking-wide">
              LIBRA
            </div>

            <div className="text-sm text-blue-100">
              Library Management System
            </div>
          </div>
        </div>

        <div className="relative z-10 my-auto max-w-2xl py-12">
          <div className="inline-flex rounded-full border border-white/15 bg-white/10 px-3 py-1 text-xs font-medium text-blue-50">
            HỆ THỐNG QUẢN LÝ THƯ VIỆN
          </div>

          <h1 className="mt-6 max-w-xl text-4xl font-semibold leading-tight xl:text-5xl">
            Quản lý thư viện
            <br />
            đơn giản và hiệu quả hơn
          </h1>

          <p className="mt-5 max-w-xl text-base leading-7 text-blue-100">
            Một nền tảng thống nhất cho tài khoản,
            bạn đọc, thẻ thư viện, chính sách mượn
            và các nghiệp vụ quản lý thư viện.
          </p>

          <div className="mt-10 grid max-w-2xl gap-4">
            {highlights.map((item) => {
              const Icon = item.icon

              return (
                <div
                  key={item.title}
                  className="flex items-start gap-4 rounded-xl border border-white/10 bg-white/[0.08] p-4 backdrop-blur-sm"
                >
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white/10">
                    <Icon size={20} />
                  </div>

                  <div>
                    <div className="font-medium">
                      {item.title}
                    </div>

                    <div className="mt-1 text-sm leading-5 text-blue-100">
                      {item.description}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        <div className="relative z-10 flex items-center justify-between text-xs text-blue-100">
          <span>Nhóm 5 • Dự án TTCS</span>

          <span>LIBRA © 2026</span>
        </div>
      </section>

      <section className="flex min-h-screen items-center justify-center px-5 py-10 sm:px-8 lg:px-12">
        <div className="w-full max-w-[460px]">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-blue-600 text-white">
              <BookOpen size={23} />
            </div>

            <div>
              <div className="font-semibold text-slate-900">
                LIBRA
              </div>

              <div className="text-xs text-slate-500">
                Library Management System
              </div>
            </div>
          </div>

          {children}
        </div>
      </section>
    </div>
  )
}
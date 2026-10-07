import { Globe2, Mail, MessageCircle, Phone } from 'lucide-react'

const contactLinks = [
  {
    label: 'Facebook LIBRA',
    href: 'https://www.facebook.com/',
    icon: Globe2,
  },
  {
    label: 'Zalo LIBRA',
    href: 'https://zalo.me/',
    icon: MessageCircle,
  },
]

export default function PublicSiteFooter() {
  return (
    <footer className="mt-10 border-t border-slate-200 bg-slate-900 text-slate-300">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-9 sm:px-6 md:grid-cols-3 lg:px-8">
        <div>
          <p className="text-base font-bold text-white">LIBRA Library</p>
          <p className="mt-2 text-sm leading-6 text-slate-400">
            Hệ thống tra cứu và quản lý mượn / trả sách thư viện. Thông tin liên hệ bên dưới được dùng cho mục đích demo hệ thống.
          </p>
        </div>

        <div>
          <p className="text-sm font-semibold uppercase tracking-wide text-slate-200">Liên hệ hỗ trợ</p>
          <div className="mt-3 space-y-2 text-sm">
            <a href="tel:19001234" className="flex items-center gap-2 hover:text-white">
              <Phone size={16} /> Hotline: 1900 1234
            </a>
            <a href="mailto:hotro@libra-demo.vn" className="flex items-center gap-2 hover:text-white">
              <Mail size={16} /> hotro@libra-demo.vn
            </a>
          </div>
        </div>

        <div>
          <p className="text-sm font-semibold uppercase tracking-wide text-slate-200">Kênh liên hệ</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {contactLinks.map(({ label, href, icon: Icon }) => (
              <a
                key={label}
                href={href}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-medium text-slate-200 transition hover:border-slate-500 hover:bg-slate-800 hover:text-white"
              >
                <Icon size={16} /> {label}
              </a>
            ))}
          </div>
        </div>
      </div>
      <div className="border-t border-slate-800 px-4 py-4 text-center text-xs text-slate-500">
        © 2026 LIBRA Library Management System · Phiên bản trình diễn
      </div>
    </footer>
  )
}

import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { CreditCard, RefreshCw } from 'lucide-react'
import { getCurrentUser } from '../../core/auth/authStorage'
import { libraryCardService } from '../s1-04-library-card/libraryCardService'
import type { MyLibraryCard } from '../s1-04-library-card/libraryCardService'
import { date, status } from './format'
import PageHeader from '../../components/ui/PageHeader'
import FeedbackAlert from '../../components/ui/FeedbackAlert'

export default function MyLibraryCardPage() {
  const allowed = getCurrentUser()?.role === 'READER'
  const [data, setData] = useState<MyLibraryCard | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [version, setVersion] = useState(0)
  useEffect(() => {
    let active = true
    if (!allowed) return
    libraryCardService.getMine().then(value => { if (active) setData(value) }).catch(() => { if (active) { setData(null); setError('Không thể tải thẻ thư viện. Vui lòng thử lại.') } }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [allowed, version])
  if (!allowed) return <Navigate to="/catalog" replace />
  return <div><PageHeader title="Thẻ thư viện của tôi" description="Thông tin được lấy trực tiếp từ tài khoản đang đăng nhập." />
    <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />
    <button className="mb-4 flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2" disabled={loading} onClick={() => { setLoading(true); setError(''); setVersion(v => v + 1) }}><RefreshCw size={16} /> Tải lại</button>
    {loading ? <p role="status">Đang tải thẻ…</p> : data && <article className="max-w-xl rounded-3xl border border-blue-100 bg-white p-6 shadow-sm"><div className="mb-5 flex items-center gap-3 text-blue-700"><CreditCard size={30} /><h2 className="text-xl font-bold">LIBRA · Thẻ bạn đọc</h2></div><dl className="grid gap-4 sm:grid-cols-2">{[['Họ tên', data.fullName], ['Mã bạn đọc', data.memberCode], ['Hồ sơ', status(data.registrationStatus)], ['Trạng thái thẻ', status(data.cardStatus)], ['Mã thẻ', data.cardNumber || 'Chưa cấp'], ['Loại thẻ', data.cardTypeName || 'Chưa cấp'], ['Ngày cấp', date(data.issuedAt)], ['Hết hạn', date(data.expiresAt)]].map(([label, value]) => <div key={label}><dt className="text-sm text-slate-500">{label}</dt><dd className="mt-1 font-semibold text-slate-900">{value}</dd></div>)}</dl>{data.manualLock?.active && <p className="mt-4 text-red-700">Thẻ đang bị khóa. Liên hệ thư viện để được hỗ trợ.</p>}</article>}
  </div>
}

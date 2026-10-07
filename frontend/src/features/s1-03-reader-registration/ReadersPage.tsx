import { useEffect, useState } from 'react'
import {
  AlertCircle,
  CheckCircle2,
  Clock,
  ExternalLink,
  IdCard,
  Mail,
  Phone,
  RefreshCw,
  Search,
  UserPlus,
  Users,
  XCircle,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import PageHeader from '../../components/ui/PageHeader'
import StatusBadge from '../../components/ui/StatusBadge'
import { readerService } from './readerService'
import type { ReaderProfileResponse } from './readerService'

export default function ReadersPage() {
  const [readers, setReaders] = useState<ReaderProfileResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')

  const fetchReaders = async () => {
    setLoading(true)
    setError('')
    try {
      const data = await readerService.getAllReaders()
      setReaders(data)
    } catch {
      setError('Không thể tải danh sách bạn đọc. Vui lòng thử lại sau.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchReaders()
  }, [])

  const filteredReaders = readers.filter((reader) => {
    const matchesSearch =
      reader.fullName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      reader.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
      reader.memberCode.toLowerCase().includes(searchTerm.toLowerCase())

    const matchesStatus =
      statusFilter === 'ALL' || reader.registrationStatus === statusFilter

    return matchesSearch && matchesStatus
  })

  const pendingCount = readers.filter(
    (r) => r.registrationStatus === 'PENDING',
  ).length
  const approvedCount = readers.filter(
    (r) => r.registrationStatus === 'APPROVED',
  ).length
  const rejectedCount = readers.filter(
    (r) => r.registrationStatus === 'REJECTED',
  ).length

  return (
    <div className="space-y-6">
      <PageHeader
        title="Quản lý bạn đọc & tiếp nhận hồ sơ"
        description="Kiểm tra trùng lặp email, mã định danh và quản lý tiếp nhận hồ sơ đăng ký bạn đọc."
        action={(
          <>
            <Button type="button" variant="secondary" onClick={fetchReaders} loading={loading}>
              <RefreshCw size={16} />
              Làm mới
            </Button>
            <Link
              to="/register"
              target="_blank"
              className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-blue-600 bg-blue-600 px-4 text-sm font-semibold text-white shadow-sm transition hover:border-blue-700 hover:bg-blue-700"
            >
              <UserPlus size={16} />
              Mở cổng đăng ký
              <ExternalLink size={14} className="opacity-70" />
            </Link>
          </>
        )}
      />

      {/* Stats Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="flex items-center justify-between text-slate-500">
            <span className="text-xs font-semibold uppercase tracking-wider">
              Tổng bạn đọc
            </span>
            <Users size={18} className="text-blue-600" />
          </div>
          <p className="mt-2 text-2xl font-bold text-slate-900">
            {readers.length}
          </p>
        </div>

        <div className="rounded-2xl border border-amber-200 bg-amber-50/50 p-4 shadow-sm">
          <div className="flex items-center justify-between text-amber-700">
            <span className="text-xs font-semibold uppercase tracking-wider">
              Chờ duyệt
            </span>
            <Clock size={18} className="text-amber-600" />
          </div>
          <p className="mt-2 text-2xl font-bold text-amber-900">
            {pendingCount}
          </p>
        </div>

        <div className="rounded-2xl border border-emerald-200 bg-emerald-50/50 p-4 shadow-sm">
          <div className="flex items-center justify-between text-emerald-700">
            <span className="text-xs font-semibold uppercase tracking-wider">
              Đã duyệt
            </span>
            <CheckCircle2 size={18} className="text-emerald-600" />
          </div>
          <p className="mt-2 text-2xl font-bold text-emerald-900">
            {approvedCount}
          </p>
        </div>

        <div className="rounded-2xl border border-red-200 bg-red-50/50 p-4 shadow-sm">
          <div className="flex items-center justify-between text-red-700">
            <span className="text-xs font-semibold uppercase tracking-wider">
              Từ chối
            </span>
            <XCircle size={18} className="text-red-600" />
          </div>
          <p className="mt-2 text-2xl font-bold text-red-900">{rejectedCount}</p>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col sm:flex-row gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="relative flex-1">
          <Search
            size={18}
            className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
          />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Tìm kiếm theo họ tên, email, mã sinh viên / cán bộ..."
            className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm text-slate-900 outline-none transition focus:border-blue-500 focus:bg-white focus:ring-4 focus:ring-blue-100"
          />
        </div>

        <div className="flex items-center gap-2">
          {['ALL', 'PENDING', 'APPROVED', 'REJECTED'].map((st) => (
            <button
              key={st}
              onClick={() => setStatusFilter(st)}
              className={`rounded-xl px-3.5 py-2 text-xs font-semibold transition ${
                statusFilter === st
                  ? 'bg-blue-600 text-white shadow-sm'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {st === 'ALL'
                ? 'Tất cả'
                : st === 'PENDING'
                  ? 'Chờ duyệt'
                  : st === 'APPROVED'
                    ? 'Đã duyệt'
                    : 'Bị từ chối'}
            </button>
          ))}
        </div>
      </div>

      {/* Main Table */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        {loading ? (
          <div className="flex flex-col items-center justify-center p-12 text-slate-400">
            <RefreshCw size={28} className="animate-spin text-blue-600" />
            <p className="mt-3 text-sm font-medium">Đang tải danh sách hồ sơ...</p>
          </div>
        ) : error ? (
          <div className="flex items-center gap-3 p-6 text-red-600">
            <AlertCircle size={20} />
            <span className="text-sm">{error}</span>
          </div>
        ) : filteredReaders.length === 0 ? (
          <div className="p-12 text-center text-slate-500">
            <IdCard size={40} className="mx-auto text-slate-300" />
            <p className="mt-3 text-base font-semibold text-slate-800">
              Không tìm thấy hồ sơ bạn đọc nào
            </p>
            <p className="mt-1 text-sm text-slate-400">
              Thử tìm kiếm với từ khóa khác hoặc tạo hồ sơ đăng ký mới.
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50/75 text-xs font-semibold uppercase tracking-wider text-slate-500">
                  <th className="py-3.5 px-4">Mã định danh</th>
                  <th className="py-3.5 px-4">Họ và tên</th>
                  <th className="py-3.5 px-4">Loại thẻ</th>
                  <th className="py-3.5 px-4">Email liên hệ</th>
                  <th className="py-3.5 px-4">Số điện thoại</th>
                  <th className="py-3.5 px-4">Ngày nộp</th>
                  <th className="py-3.5 px-4">Trạng thái hồ sơ</th>
                  <th className="py-3.5 px-4 text-right">Tài khoản</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredReaders.map((reader) => (
                  <tr key={reader.userId} className="hover:bg-slate-50/50 transition">
                    <td className="py-3.5 px-4 font-mono font-bold text-blue-700">
                      {reader.memberCode}
                    </td>
                    <td className="py-3.5 px-4 font-semibold text-slate-900">
                      {reader.fullName}
                    </td>
                    <td className="py-3.5 px-4 text-slate-600">
                      {reader.cardTypeName || (
                        <span className="text-slate-400 italic">Chưa cấp thẻ</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4 text-slate-600">
                      <div className="flex items-center gap-1.5">
                        <Mail size={14} className="text-slate-400" />
                        {reader.email}
                      </div>
                    </td>
                    <td className="py-3.5 px-4 text-slate-600">
                      {reader.phone ? (
                        <div className="flex items-center gap-1.5">
                          <Phone size={14} className="text-slate-400" />
                          {reader.phone}
                        </div>
                      ) : (
                        <span className="text-slate-300 italic">Chưa có</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4 text-slate-500 text-xs">
                      {new Date(reader.submittedAt).toLocaleDateString('vi-VN')}
                    </td>
                    <td className="py-3.5 px-4">
                      <StatusBadge
                        status={reader.registrationStatus}
                        label={reader.registrationStatus === 'PENDING'
                          ? 'Chờ duyệt'
                          : reader.registrationStatus === 'APPROVED'
                            ? 'Đã duyệt'
                            : reader.registrationStatus === 'REJECTED'
                              ? 'Bị từ chối'
                              : reader.registrationStatus}
                      />
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      <StatusBadge status={reader.userStatus} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

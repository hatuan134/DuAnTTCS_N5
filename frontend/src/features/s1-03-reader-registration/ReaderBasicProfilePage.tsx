import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, RefreshCw } from 'lucide-react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import LoadingState from '../../components/ui/LoadingState'
import PageHeader from '../../components/ui/PageHeader'
import StatusBadge from '../../components/ui/StatusBadge'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { readerService } from './readerService'
import type { ReaderProfileResponse } from './readerService'

export default function ReaderBasicProfilePage() {
  const { readerId } = useParams()
  const id = Number(readerId)
  return <ReaderBasicProfile key={readerId} id={id} />
}

export function ReaderBasicProfile({ id }: { id: number }) {
  const [profile, setProfile] = useState<ReaderProfileResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)
  const [notice, setNotice] = useState('')
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    let active = true
    setProfile(null); setFailed(false); setNotice(''); setLoading(true)
    if (!Number.isSafeInteger(id) || id < 1) {
      setFailed(true); setNotice('Mã bạn đọc không hợp lệ.'); setLoading(false)
      return
    }
    readerService.getReaderById(id)
      .then((result) => {
        if (!active) return
        if (result.userId !== id) throw new Error('Hồ sơ trả về không khớp Bạn đọc đang xem.')
        setProfile(result)
      })
      .catch((error: unknown) => {
        if (!active) return
        setFailed(true)
        setNotice(getApiErrorMessage(error, 'Không tải được hồ sơ Bạn đọc. Vui lòng thử lại.'))
      })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [id, revision])

  return <div className="space-y-5">
    <Link to="/readers" className="inline-flex items-center gap-2 text-sm font-medium text-blue-700 hover:underline">
      <ArrowLeft size={16} /> Danh sách Bạn đọc
    </Link>
    <PageHeader title="Hồ sơ Bạn đọc" description="Thông tin đăng ký và trạng thái hồ sơ Bạn đọc."
      action={<Button type="button" variant="secondary" loading={loading} onClick={() => setRevision(v => v + 1)}>
        <RefreshCw size={16} /> Làm mới
      </Button>} />
    {notice && <FeedbackAlert message={notice} tone="error" onDismiss={() => setNotice('')} />}
    {loading && <LoadingState />}
    {!loading && failed && <Card className="p-5"><p role="status" className="text-sm text-slate-700">
      Chưa tải được hồ sơ Bạn đọc. Nhấn “Làm mới” để thử lại.
    </p></Card>}
    {!loading && !failed && profile && <Card className="p-5 sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h3 className="min-w-0 break-words text-lg font-semibold text-slate-900">{profile.fullName}</h3>
        <StatusBadge status={profile.userStatus} />
      </div>
      <dl className="mt-5 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
        <Field label="Mã Bạn đọc" value={profile.memberCode} />
        <Field label="Email" value={profile.email} />
        <Field label="Số điện thoại" value={profile.phone || 'Chưa có'} />
        <Field label="Ngày sinh" value={new Date(`${profile.dateOfBirth}T00:00:00+07:00`).toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })} />
        <Field label="Địa chỉ" value={profile.address || 'Chưa có'} />
        <Field label="Loại thẻ" value={profile.cardTypeName || 'Chưa cấp thẻ'} />
        <div><dt className="text-slate-500">Trạng thái hồ sơ</dt><dd className="mt-1"><StatusBadge status={profile.registrationStatus} /></dd></div>
        <Field label="Ngày đăng ký" value={formatLoanTimestamp(profile.submittedAt, true)} />
        {profile.rejectionReason && <Field label="Lý do từ chối hồ sơ" value={profile.rejectionReason} />}
      </dl>
    </Card>}
  </div>
}

function Field({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0"><dt className="text-slate-500">{label}</dt>
    <dd className="mt-1 break-words font-medium text-slate-900">{value}</dd></div>
}

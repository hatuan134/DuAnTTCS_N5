import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { librarySettingsService } from './librarySettingsService'
import type { OverdueFeeSetting } from './librarySettingsService'

export default function OverdueFeeSettingsPanel({ readOnly }: { readOnly: boolean }) {
  const [setting, setSetting] = useState<OverdueFeeSetting | null>(null)
  const [value, setValue] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const pending = useRef(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    librarySettingsService.getOverdueFee()
      .then(data => { if (active) { setSetting(data); setValue(data.dailyRateVnd === null ? '' : String(data.dailyRateVnd)) } })
      .catch(err => { if (active) { setSetting(null); setError(getApiErrorMessage(err, 'Không tải được mức phí trễ hạn.')) } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [revision])
  async function save(event: FormEvent) {
    event.preventDefault()
    if (readOnly || pending.current || loading || !setting) return
    if (!/^\d+$/.test(value.trim()) || !Number.isSafeInteger(Number(value)) || Number(value) > 1_000_000_000) {
      setError('Nhập số nguyên từ 0 đến 1.000.000.000 VND/ngày.'); return
    }
    pending.current = true; setSaving(true); setError(''); setNotice('')
    try {
      const data = await librarySettingsService.updateOverdueFee(Number(value))
      setSetting(data); setValue(String(data.dailyRateVnd))
      setNotice('Đã lưu mức phí trễ hạn. Các khoản đã ghi nhận giữ nguyên.')
    } catch (err) { setError(getApiErrorMessage(err, 'Không lưu được mức phí trễ hạn.')) }
    finally { pending.current = false; setSaving(false) }
  }
  return <section id="overdue-fee"><Card className="min-w-0 p-5">
    <h2 className="text-lg font-semibold text-slate-900">Mức phí trễ hạn</h2>
    <p className="mt-1 text-sm leading-6 text-slate-600">Áp dụng theo mức có hiệu lực khi xác nhận nhận trả. Tổng phí làm tròn đến 1.000 VND; đúng 500 đồng làm lên.</p>
    {loading && <p role="status" className="mt-3 text-sm text-blue-700">Đang tải mức phí…</p>}
    {error && <FeedbackAlert className="mt-3" tone="error" message={error} onDismiss={() => setError('')} />}
    {notice && <FeedbackAlert className="mt-3" tone="success" message={notice} onDismiss={() => setNotice('')} />}
    {!loading && setting && <>
      <p className="mt-3 text-sm">Mức hiện tại: <strong>{setting.dailyRateVnd === null ? 'Chưa khai báo' : `${formatVnd(setting.dailyRateVnd)}/ngày`}</strong></p>
      {setting.dailyRateVnd === null && <p className="mt-1 text-sm text-amber-800">Chưa thể nhận trả cuốn có ngày mở cửa trễ hạn. Cuốn không có ngày trễ tính phí vẫn nhận trả với 0 VND.</p>}
      {setting.updatedAt && <p className="mt-1 text-xs text-slate-500">Cập nhật: {formatLoanTimestamp(setting.updatedAt)}</p>}
      {!readOnly && <form noValidate onSubmit={event => { void save(event) }} className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end">
        <div className="w-full min-w-0 sm:max-w-sm"><Input id="overdue-daily-rate" label="Mức phí mỗi ngày (VND)" required inputMode="numeric" maxLength={10}
          value={value} disabled={saving} placeholder="Ví dụ: 5000" onChange={event => { setValue(event.target.value); setError(''); setNotice('') }} /></div>
        <Button type="submit" loading={saving} className="sm:mb-0.5">Lưu mức phí</Button>
      </form>}
      {readOnly && <p className="mt-2 text-xs text-slate-500">Thủ thư được xem mức phí. Liên hệ Quản lý thư viện để cập nhật.</p>}
      <p className="mt-3 text-xs leading-5 text-slate-500">Có thể khai báo 0 VND/ngày. Kết quả phí đã ghi nhận được giữ nguyên khi đổi mức phí hoặc lịch đóng cửa.</p>
    </>}
    {!loading && !setting && <Button type="button" variant="secondary" className="mt-3" onClick={() => setRevision(x => x + 1)}>Thử tải lại</Button>}
  </Card></section>
}

import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import ConfirmActionDialog from '../../components/ui/ConfirmActionDialog'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { vietnamDateKey } from '../s2-08-my-reservations/pickupLastDay'
import { formatPickupDate } from './pickupService'
import { readyEmailService, pickupEmailLabels, type PickupReminderReport } from './readyEmailService'

export default function PickupLastDayReminderPanel() {
  const [date, setDate] = useState(() => vietnamDateKey())
  const [report, setReport] = useState<PickupReminderReport | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [notice, setNotice] = useState<{ message: string; tone: 'error' | 'info' | 'warning' } | null>(null)
  const submitting = useRef(false)
  const mounted = useRef(true)
  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])

  async function execute(preview: boolean) {
    if (submitting.current) return
    if (!date) { setNotice({ tone: 'warning', message: 'Vui lòng chọn ngày kiểm tra.' }); return }
    submitting.current = true
    setBusy(true); setNotice(null)
    try {
      const data = preview ? await readyEmailService.previewLastDay(date) : await readyEmailService.runLastDay(date)
      if (mounted.current) {
        setReport(data)
        setNotice({ tone: data.entries.some((item) => item.notificationStatus === 'FAILED') ? 'warning' : 'info', message: data.message })
      }
    } catch (error) {
      if (mounted.current) { setReport(null); setNotice({ tone: 'error', message: getApiErrorMessage(error, 'Không chạy được lịch nhắc ngày cuối.') }) }
    } finally {
      submitting.current = false
      if (mounted.current) { setBusy(false); setConfirm(false) }
    }
  }

  return <Card className="mb-5 min-w-0 p-5">
    <h2 className="font-semibold text-slate-900">Lịch email nhắc ngày cuối</h2>
    <p className="mt-2 text-sm leading-6 text-slate-600">Tự chạy lúc {report?.sendTime || '07:00'} giờ Việt Nam mỗi ngày. Đơn đã nhận, đã hủy hoặc hết hạn không được gửi nhắc.</p>
    <div className="mt-4 flex flex-wrap items-end gap-3">
      <div className="min-w-0"><label htmlFor="pickup-reminder-date" className="mb-1 block text-sm font-medium text-slate-700">Ngày kiểm tra *</label>
        <input id="pickup-reminder-date" type="date" required value={date} disabled={busy}
          onChange={(event) => { setDate(event.target.value); setReport(null); setNotice(null) }}
          className="max-w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm" /></div>
      <Button type="button" variant="secondary" disabled={busy || !date} onClick={() => void execute(true)}>Xem trước theo ngày</Button>
      <Button type="button" disabled={busy || !date || date !== vietnamDateKey()} onClick={() => setConfirm(true)}>Chạy gửi thật hôm nay</Button>
    </div>
    <p className="mt-2 text-xs leading-5 text-slate-500">Xem trước dùng trạng thái hiện tại tại giờ lịch của ngày chọn, không gửi thư và không ghi lịch sử. Gửi thật chỉ dùng ngày hôm nay và kiểm tra lại dữ liệu lúc gửi.</p>
    {notice && <FeedbackAlert message={notice.message} tone={notice.tone} onDismiss={() => setNotice(null)} className="mt-3" />}
    {busy && <p role="status" className="mt-3 text-sm text-slate-600">Đang kiểm tra và xử lý lịch nhắc…</p>}
    {report && <div className="mt-4 min-w-0">
      <p className="text-sm text-slate-700">{report.preview ? 'Xem trước' : 'Kết quả chạy thật'} · Kiểm tra lúc {formatPickupDate(report.evaluatedAt)} · {report.entries.length} đơn có hạn nhận trong ngày · {report.eligibleCount} đơn đủ điều kiện tạo email mới.</p>
      {report.entries.length === 0 && <p className="mt-3 text-sm text-slate-500">Không có đơn với hạn nhận trong ngày đã chọn.</p>}
      <div className="mt-3 space-y-3">{report.entries.map((item) => <article key={item.reservationId} className="min-w-0 rounded-lg border border-slate-200 p-4 text-sm">
        <div className="flex flex-wrap justify-between gap-2"><Link className="min-w-0 flex-1 break-words font-semibold text-blue-700 hover:underline" to={`/reservations/ready-for-pickup/${item.reservationId}`}>Đơn #{item.reservationId} — {item.bookTitle}</Link>
          <span className="text-slate-600">{item.notificationStatus ? pickupEmailLabels[item.notificationStatus] || item.notificationStatus : item.eligible ? 'Đủ điều kiện' : 'Không gửi'}</span></div>
        <p className="mt-2 break-words">{item.readerName} · {item.recipientEmail || 'Chưa có email'}</p>
        <p className="mt-1">Hạn nhận: {formatPickupDate(item.pickupDeadline)}</p>
        <p className="mt-1 break-words">Quầy nhận: {item.pickupLocation || 'Chưa khai báo'}</p>
        <p className="mt-2 text-slate-600">{item.reason}</p>
        <details className="mt-2"><summary className="cursor-pointer font-medium text-blue-700">Xem nội dung email nhắc</summary>
          <p className="mt-2 break-words font-medium">{item.subject}</p>
          <iframe title={`Xem trước nhắc ngày cuối đơn #${item.reservationId}`} srcDoc={item.emailContent} sandbox="" className="mt-2 h-80 w-full rounded border border-slate-200 bg-white" />
        </details>
      </article>)}</div>
    </div>}
    {confirm && <ConfirmActionDialog danger={false} busy={busy} title="Chạy lịch nhắc hôm nay"
      description="Hệ thống sẽ gửi email thật cho các đơn còn Chờ nhận và còn hạn nhận hôm nay. Email đã có cho cùng đơn và hạn nhận không được tạo lại."
      confirmLabel="Chạy gửi thật" onCancel={() => { if (!busy) setConfirm(false) }} onConfirm={() => void execute(false)} />}
  </Card>
}

import { useCallback, useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import StatusBadge from '../../components/ui/StatusBadge'
import ConfirmActionDialog from '../../components/ui/ConfirmActionDialog'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatPickupDate } from './pickupService'
import { readyEmailService, pickupEmailLabels } from './readyEmailService'
import type { PickupEmailHistory } from './readyEmailService'

export default function ReadyPickupEmailPanel({ reservationId }: { reservationId: number }) {
  const [history, setHistory] = useState<PickupEmailHistory | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const [busy, setBusy] = useState(false)
  const [confirmMode, setConfirmMode] = useState<boolean | null>(null)
  const [notice, setNotice] = useState<{ message: string; tone: 'success' | 'error' | 'warning' | 'info' } | null>(null)
  const active = useRef(false)
  const fetching = useRef(false)
  const submitting = useRef(false)
  const load = useCallback(async (showNotice = true) => {
    if (fetching.current || submitting.current) return
    fetching.current = true
    try {
      const data = await readyEmailService.history(reservationId)
      if (active.current) { setHistory(data); setLoadFailed(false) }
    } catch (e) {
      if (active.current) {
        setLoadFailed(true)
        if (showNotice) setNotice({ tone: 'error', message: getApiErrorMessage(e, 'Không tải được lịch sử email nhận sách.') })
      }
    } finally {
      fetching.current = false
      if (active.current) setLoading(false)
    }
  }, [reservationId])
  useEffect(() => {
    active.current = true
    void load()
    return () => { active.current = false }
  }, [load])
  const latest = history?.notifications.find((item) => !item.notificationType || item.notificationType === 'READY_FOR_PICKUP')
  const waiting = latest?.status === 'PENDING' || latest?.status === 'RETRY' || latest?.status === 'SENDING'
  useEffect(() => {
    if (!history?.notifications.some((item) => ['PENDING', 'RETRY', 'SENDING'].includes(item.status)) || loadFailed) return
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void load(false) }, 2000)
    return () => window.clearInterval(timer)
  }, [history, loadFailed, load])
  const trigger = async (simulateFailure: boolean) => {
    if (submitting.current) return
    submitting.current = true
    setBusy(true); setNotice(null)
    try {
      const result = await readyEmailService.trigger(reservationId, simulateFailure)
      if (!active.current) return
      setHistory(result.history)
      const status = result.history.notifications.find((item) => !item.notificationType || item.notificationType === 'READY_FOR_PICKUP')?.status
      setNotice({ tone: status === 'FAILED' || status === 'SKIPPED' ? 'warning' : status === 'SENT' ? 'success' : 'info', message: result.message })
    } catch (e) {
      if (active.current) setNotice({ tone: 'error', message: getApiErrorMessage(e, 'Không chạy thử được email nhận sách.') })
    } finally {
      submitting.current = false
      if (active.current) { setBusy(false); setConfirmMode(null) }
    }
  }
  const canTrigger = !loading && !loadFailed && !busy && latest?.status !== 'SENDING' && waiting && history?.reservationStatus === 'READY_FOR_PICKUP'
  return <section className="mt-6 min-w-0 border-t border-slate-200 pt-5" aria-label="Lịch sử email đơn đặt giữ">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h3 className="font-semibold text-slate-900">Lịch sử email đơn đặt giữ</h3>
      <Button type="button" size="sm" variant="secondary" disabled={busy || loading} onClick={() => void load()}>Làm mới email</Button>
    </div>
    <p className="mt-2 break-words text-sm text-slate-700">Quầy nhận: <strong>{history?.pickupLocation || 'Chưa khai báo'}</strong></p>
    <p className="mt-1 text-xs leading-5 text-slate-500">Đã gửi nghĩa là SMTP đã chấp nhận thư. Thông báo sẵn sàng có hạn gửi 5 phút; nhắc ngày cuối được lập theo giờ buổi sáng đã khai báo và chỉ gửi khi đơn còn hiệu lực. Email hủy chỉ áp dụng cho sự kiện hệ thống hủy do quá hạn nhận, tối đa 3 lượt gửi khi lỗi tạm thời. Giờ hiển thị là giờ Việt Nam.</p>
    {notice && <FeedbackAlert message={notice.message} tone={notice.tone} onDismiss={() => setNotice(null)} className="mt-3" />}
    {loading && <p role="status" className="mt-3 text-sm text-slate-600">Đang tải lịch sử email…</p>}
    {loadFailed && <p role="status" className="mt-3 text-sm text-slate-600">Chưa tải được lịch sử. Nhấn “Làm mới email” để thử lại.</p>}
    {!loading && !loadFailed && !history?.notifications.length && <p className="mt-3 text-sm text-slate-600">Đơn chưa có sự kiện thông báo. Các đơn đã Chờ nhận trước khi áp dụng bản vá không được tự gửi bù.</p>}
    {history?.notifications.map((item) => <article key={item.id} className="mt-4 min-w-0 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <div className="flex flex-wrap items-center gap-2"><StatusBadge status={item.status === 'RETRY' ? 'PENDING_RETRY' : item.status} label={pickupEmailLabels[item.status]} /><span className="text-xs font-semibold text-slate-700">{item.notificationType === 'PICKUP_EXPIRED_CANCELLATION' ? 'Hủy do quá hạn nhận' : item.notificationType === 'LAST_DAY_REMINDER' ? 'Nhắc ngày cuối' : 'Sách sẵn sàng'}</span><span className="text-xs text-slate-500">Email #{item.id} · {item.attemptCount} lượt xử lý</span></div>
      <dl className="mt-3 grid min-w-0 gap-3 text-sm sm:grid-cols-2">
        <div><dt className="text-slate-500">Người nhận</dt><dd className="break-all">{item.recipientEmail || 'Chưa có email'}</dd></div>
        <div><dt className="text-slate-500">Sách</dt><dd className="break-words">{item.bookTitle}</dd></div>
        <div><dt className="text-slate-500">{item.notificationType === 'PICKUP_EXPIRED_CANCELLATION' ? 'Thời điểm ghi nhận sự kiện hủy' : item.notificationType === 'LAST_DAY_REMINDER' ? 'Thời điểm lập lịch nhắc' : 'Chuyển sang Chờ nhận'}</dt><dd>{formatPickupDate(item.eventAt, true)}</dd></div>
        <div><dt className="text-slate-500">Hạn gửi email</dt><dd>{item.sendBefore ? formatPickupDate(item.sendBefore, true) : 'Gửi ngay sau khi hủy; tối đa 3 lượt khi lỗi tạm thời'}</dd></div>
        <div><dt className="text-slate-500">Thời gian gửi</dt><dd>{item.sentAt ? formatPickupDate(item.sentAt, true) : 'Chưa gửi'}</dd></div>
        <div><dt className="text-slate-500">Hạn nhận sách</dt><dd>{formatPickupDate(item.pickupDeadline)}</dd></div>
        {item.notificationType === 'PICKUP_EXPIRED_CANCELLATION' && <>
          <div><dt className="text-slate-500">Lý do hủy</dt><dd className="break-words">{item.cancellationReason || 'Quá hạn nhận'}</dd></div>
          <div><dt className="text-slate-500">Thời điểm hủy</dt><dd>{formatPickupDate(item.cancelledAt ?? null, true)}</dd></div>
        </>}
        <div className="sm:col-span-2"><dt className="text-slate-500">Quầy trong email</dt><dd className="break-words">{item.notificationType === 'PICKUP_EXPIRED_CANCELLATION' ? 'Xem hướng dẫn đặt giữ lại trong email' : item.pickupLocation || 'Chưa khai báo'}</dd></div>
      </dl>
      {item.errorMessage && <p className="mt-3 break-words text-sm text-red-700">{item.errorMessage}</p>}
      <details className="mt-3 text-sm"><summary className="cursor-pointer font-medium text-blue-700">Nội dung và lịch sử gửi</summary>
        {item.emailContent && <iframe title={`Email nhận sách #${item.id}`} srcDoc={item.emailContent} sandbox="" className="mt-3 h-80 w-full rounded border border-slate-200 bg-white" />}
        <ol className="mt-3 space-y-2">{item.attempts.map((attempt) => <li key={attempt.id} className="break-words text-xs leading-5 text-slate-700">
          Lượt {attempt.attemptNumber}: {pickupEmailLabels[attempt.outcome] || attempt.outcome} · {attempt.finishedAt ? formatPickupDate(attempt.finishedAt, true) : 'Đang xử lý'}
          {attempt.simulated && ' · Mô phỏng lỗi'}{attempt.errorMessage && <p>{attempt.errorMessage}</p>}
        </li>)}</ol>
      </details>
    </article>)}
    {waiting && <div className="mt-4 flex flex-wrap gap-2">
      <Button type="button" size="sm" disabled={!canTrigger} onClick={() => setConfirmMode(false)}>Chạy thử gửi thật</Button>
      <Button type="button" size="sm" variant="secondary" disabled={!canTrigger} onClick={() => setConfirmMode(true)}>Mô phỏng lỗi tạm thời</Button>
    </div>}
    {confirmMode !== null && <ConfirmActionDialog danger={false} busy={busy} title={confirmMode ? 'Mô phỏng lỗi gửi email' : 'Chạy thử gửi email nhận sách'}
      description={confirmMode ? 'Lượt này không gửi thư thật. Hệ thống ghi lỗi tạm thời và tự thử lại qua SMTP trong thời hạn 5 phút ban đầu.' : 'Gửi email thật đến bạn đọc nếu đơn vẫn Chờ nhận và email đến lượt gửi. Email đã gửi thành công sẽ không được gửi lại.'}
      confirmLabel={confirmMode ? 'Mô phỏng lỗi' : 'Gửi email'} onCancel={() => { if (!busy) setConfirmMode(null) }} onConfirm={() => void trigger(confirmMode)} />}
  </section>
}

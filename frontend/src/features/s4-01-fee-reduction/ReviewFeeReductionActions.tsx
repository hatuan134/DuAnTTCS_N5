import { useId, useRef, useState, type FormEvent } from 'react'
import { isAxiosError } from 'axios'
import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Modal from '../../components/ui/Modal'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { canApproveFeeReduction, feeAfterApproval, feeReductionService, type FeeReductionRequest } from './feeReductionService'

interface Props {
  request: FeeReductionRequest
  onReviewed: (result: FeeReductionRequest) => void
  onConflict: (message: string) => void
}

/** The same decision flow is available from the queue and the fee detail. */
export default function ReviewFeeReductionActions({ request, onReviewed, onConflict }: Props) {
  const labelId = useId()
  const [decision, setDecision] = useState<'APPROVE' | 'REJECT' | null>(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const lock = useRef(false)
  if (getCurrentUser()?.role !== 'LIBRARY_MANAGER' || request.status !== 'PENDING') return null

  function close() {
    if (lock.current) return
    setDecision(null); setReason(''); setError('')
  }

  async function submit() {
    if (lock.current || !decision) return
    if (decision === 'REJECT' && (!reason.trim() || reason.trim().length > 1000)) {
      setError('Vui lòng nhập lý do từ chối có nội dung, tối đa 1000 ký tự.'); return
    }
    if (decision === 'APPROVE' && !canApproveFeeReduction(request)) return
    lock.current = true; setSaving(true); setError('')
    try {
      const result = decision === 'APPROVE'
        ? await feeReductionService.approve(request.id)
        : await feeReductionService.reject(request.id, reason.trim())
      setDecision(null); setReason('')
      onReviewed(result)
    } catch (err) {
      const message = getApiErrorMessage(err, 'Không xử lý được đề nghị. Vui lòng thử lại.')
      if (isAxiosError(err) && err.response?.status === 409) {
        setDecision(null); onConflict(message)
      } else setError(message)
    } finally { lock.current = false; setSaving(false) }
  }

  return <div className="mt-5 space-y-3">
    {!canApproveFeeReduction(request) && <p role="status" className="rounded-xl bg-amber-50 p-3 text-sm leading-6 text-amber-900">{request.paidAmountVnd > 0 ? 'Khoản phí đã thu tiền nên không thể phê duyệt miễn giảm. Quản lý vẫn có thể từ chối đề nghị.' : 'Số tiền đề nghị vượt khoản còn phải thu. Vui lòng kiểm tra hoặc từ chối đề nghị.'}</p>}
    {error && !decision && <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />}
    <div className="flex flex-wrap gap-3">
      <Button type="button" disabled={saving || !canApproveFeeReduction(request)} onClick={() => { setError(''); setDecision('APPROVE') }}>Phê duyệt</Button>
      <Button type="button" variant="danger" disabled={saving} onClick={() => { setError(''); setDecision('REJECT') }}>Từ chối</Button>
    </div>
    {decision && <Modal busy={saving} onClose={close} labelledBy={`${labelId}-title`}>
      <form className="w-full max-w-lg rounded-2xl bg-white p-5 shadow-2xl sm:p-6" onSubmit={(event: FormEvent) => { event.preventDefault(); void submit() }}>
        <h2 id={`${labelId}-title`} className="text-lg font-semibold text-slate-900">{decision === 'APPROVE' ? 'Phê duyệt' : 'Từ chối'} đề nghị #{request.id}</h2>
        <p className="mt-2 text-sm leading-6 text-slate-600">{decision === 'APPROVE'
          ? `Miễn giảm ${formatVnd(request.requestedAmountVnd)}. Khoản còn phải thu sau khi duyệt: ${formatVnd(feeAfterApproval(request) ?? 0)}.`
          : `Khoản phải thu giữ nguyên: ${formatVnd(request.outstandingAmountVnd)}. Thủ thư sẽ thấy lý do và người quyết định.`}</p>
        <p className="mt-2 text-sm text-slate-600">Quyết định được lưu vào lịch sử và không thể sửa lại.</p>
        {error && <div className="mt-4"><FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} /></div>}
        {decision === 'REJECT' && <>
        <label htmlFor={`${labelId}-reason`} className="mb-2 mt-5 block text-sm font-semibold text-slate-800">Lý do từ chối <span className="text-red-700">*</span></label>
        <textarea id={`${labelId}-reason`} autoFocus required disabled={saving} maxLength={1000} rows={4} value={reason} onChange={event => setReason(event.target.value)} placeholder="Ví dụ: Chưa có căn cứ phù hợp để miễn giảm" className="w-full resize-y rounded-xl border border-slate-300 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100" />
        <p className="mt-1 text-right text-xs text-slate-500">{reason.length}/1000 ký tự</p>
        </>}
        <div className="mt-5 flex flex-wrap justify-end gap-3"><Button type="button" variant="secondary" disabled={saving} autoFocus={decision === 'APPROVE'} onClick={close}>Hủy</Button><Button type="submit" variant={decision === 'REJECT' ? 'danger' : 'primary'} loading={saving}>{decision === 'REJECT' ? 'Xác nhận từ chối' : 'Xác nhận phê duyệt'}</Button></div>
      </form>
    </Modal>}
  </div>
}

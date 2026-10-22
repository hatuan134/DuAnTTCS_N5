import { useState } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import Button from '../../components/ui/Button'
import Modal from '../../components/ui/Modal'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { formatVnd } from '../s3-07-returns/ReturnFeeDetails'
import { feeTypeLabel, type FeeReceipt } from './feePaymentService'

export function ReceiptContent({ receipt }: { receipt: FeeReceipt }) {
  return <article className="fee-receipt">
    <header className="mb-5 border-b border-slate-200 pb-4 text-center">
      <p className="text-sm font-semibold text-slate-600">HỆ THỐNG THƯ VIỆN LIBRA</p>
      <h2 className="mt-2 text-xl font-bold text-slate-950">BIÊN LAI THU PHÍ</h2>
      <p className="mt-1 break-all text-sm">Mã biên lai: <strong>{receipt.receiptNumber}</strong></p>
      <p className="mt-1 text-sm">Ngày giờ thu: {formatLoanTimestamp(receipt.collectedAt)}</p>
    </header>
    <dl className="mb-5 grid gap-2 text-sm">
      <div><dt className="inline text-slate-500">Bạn đọc: </dt><dd className="inline break-words font-semibold">{receipt.readerName}</dd></div>
      <div><dt className="inline text-slate-500">Mã thẻ: </dt><dd className="inline break-all">{receipt.cardNumber ?? 'Chưa cấp thẻ'}</dd></div>
      <div><dt className="inline text-slate-500">Mã giao dịch: </dt><dd className="inline break-all">{receipt.transactionNumber}</dd></div>
      <div><dt className="inline text-slate-500">Nguồn thanh toán: </dt><dd className="inline">{receipt.fundingSource === 'DEPOSIT_OFFSET' ? 'Khấu trừ tiền cọc đã giữ' : 'Thu tại quầy'}</dd></div>
    </dl>
    <ol className="divide-y divide-slate-200 border-y border-slate-200">
      {receipt.items.map(item => <li key={item.feeId} className="flex flex-wrap items-start justify-between gap-2 py-3 text-sm">
        <div className="min-w-0 flex-1"><p className="font-semibold">KP-{item.feeId} · {feeTypeLabel(item.feeType)}</p>
          <p className="mt-1 break-words text-slate-600">{item.description || 'Khoản phí thư viện'}</p></div>
        <strong className="whitespace-nowrap">{formatVnd(item.collectedAmountVnd)}</strong>
      </li>)}
    </ol>
    <p className="my-5 flex flex-wrap justify-between gap-2 text-lg font-bold"><span>Tổng tiền đã thu</span><span>{formatVnd(receipt.totalAmountVnd)}</span></p>
    <p className="text-sm">{receipt.fundingSource === 'DEPOSIT_OFFSET' ? 'Đã chuyển phần cọc được xác nhận sang thanh toán nghĩa vụ; không thu thêm tiền mặt trong giao dịch này.' : 'Chỉ ghi số tiền thực tế nhận trong lần thanh toán này.'}</p>
    <p className="mt-2 text-sm">Số dư nợ sau lần thu: <strong>{formatVnd(receipt.debtAfterVnd ?? 0)}</strong>.</p>
    <p className="mt-2 text-sm">{(receipt.debtAfterVnd ?? 0) > 0 ? 'Bạn đọc vẫn còn nợ phí.' : 'Bạn đọc đã hết nợ phí tại thời điểm thu.'}</p>
    <p className="mt-5 break-words text-right text-sm">Thủ thư thu: <strong>{receipt.collectorName}</strong></p>
  </article>
}

/** Printing only reads a committed receipt; it never writes or reverses a payment. */
export function printFeeReceipt(receipt: FeeReceipt): Promise<void> {
  return new Promise((resolve, reject) => {
    const frame = document.createElement('iframe')
    frame.title = 'Bản in biên lai thu phí'
    frame.style.cssText = 'position:fixed;left:-10000px;top:0;width:800px;height:1000px;border:0'
    const cleanup = () => frame.remove()
    frame.onload = () => {
      try {
        const target = frame.contentWindow
        if (!target) throw new Error('Không mở được bản in.')
        target.addEventListener('afterprint', cleanup, { once: true })
        target.focus()
        target.print()
        // Some browsers do not dispatch afterprint when the print dialog is canceled.
        window.setTimeout(cleanup, 60000)
        resolve()
      } catch (error) { cleanup(); reject(error) }
    }
    frame.onerror = () => { cleanup(); reject(new Error('Không tải được bản in.')) }
    const html = renderToStaticMarkup(<ReceiptContent receipt={receipt} />)
    frame.srcdoc = `<!doctype html><html lang="vi"><head><meta charset="utf-8"><title>Biên lai thu phí</title><style>
      @page{size:A5 portrait;margin:12mm}body{font:14px/1.5 Arial,sans-serif;color:#111;margin:0}
      article{max-width:650px;margin:auto}header{text-align:center;border-bottom:1px solid #aaa;padding-bottom:12px}
      h2{font-size:21px}dl{margin:16px 0}dt,dd{display:inline;margin:0}dl>div{margin:5px 0}
      ol{list-style:none;padding:0;border-top:1px solid #aaa}li{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #ccc;padding:8px 0;break-inside:avoid}
      li>div{min-width:0;flex:1}li p{margin:3px 0;overflow-wrap:anywhere}li>strong{white-space:nowrap}
      article>p{margin:18px 0}article>p:first-of-type{display:flex;justify-content:space-between;font-weight:bold;font-size:17px}
      article>p:last-child{text-align:right}strong,dd{overflow-wrap:anywhere}
    </style></head><body>${html}</body></html>`
    document.body.appendChild(frame)
  })
}

export default function FeeReceiptDialog({ receipt, onClose }: { receipt: FeeReceipt; onClose: () => void }) {
  const [printing, setPrinting] = useState(false)
  const [error, setError] = useState('')
  async function print() {
    if (printing) return
    setPrinting(true); setError('')
    try { await printFeeReceipt(receipt) }
    catch { setError('Không mở được hộp thoại in. Giao dịch đã được lưu; bạn có thể thử in lại biên lai này.') }
    finally { setPrinting(false) }
  }
  return <Modal onClose={onClose} busy={printing} label={`Biên lai ${receipt.receiptNumber}`}>
    <section className="max-h-[90dvh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl sm:p-6">
      <ReceiptContent receipt={receipt} />
      {error && <FeedbackAlert className="mt-4" message={error} tone="error" onDismiss={() => setError('')} />}
      <p className="mt-4 text-xs leading-5 text-slate-500">Có thể in lại cùng mã biên lai. Hủy in hoặc lỗi máy in không hủy giao dịch thu tiền.</p>
      <div className="mt-5 flex flex-wrap justify-end gap-2 border-t border-slate-100 pt-4">
        <Button type="button" variant="secondary" onClick={onClose} disabled={printing}>Đóng</Button>
        <Button type="button" onClick={() => void print()} loading={printing}>In biên lai</Button>
      </div>
    </section>
  </Modal>
}

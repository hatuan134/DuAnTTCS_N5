import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'
import { cardLockSuggestionService, parseDebtThreshold, suggestionMoney, type SuggestionSettings } from './cardLockSuggestionService'
export default function CardLockSuggestionSettingsPage() {
  if (getCurrentUser()?.role !== 'LIBRARY_MANAGER') return <p role="alert">Chỉ Quản lý thư viện được cấu hình ngưỡng nợ phí.</p>
  return <SettingsForm />
}
function SettingsForm() {
  const [data, setData] = useState<SuggestionSettings | null>(null)
  const [amount, setAmount] = useState(''), [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false), [revision, setRevision] = useState(0)
  const [fieldError, setFieldError] = useState('')
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error' | 'warning'; message: string } | null>(null)
  const pending = useRef(false)
  useEffect(() => {
    let active = true
    setLoading(true)
    cardLockSuggestionService.settings().then(value => { if (active) { setData(value); setAmount(String(value.debtThresholdVnd)) } })
      .catch(error => { if (active) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được ngưỡng nợ phí.') }) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [revision])
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (pending.current || loading || !data) return
    setFeedback(null)
    const value = parseDebtThreshold(amount)
    if (value === null) {
      const message = 'Nhập số nguyên từ 0 đến 1.000.000.000.000 VND.'
      setFieldError(message); setFeedback({ tone: 'warning', message }); return
    }
    pending.current = true; setSaving(true); setFieldError('')
    try {
      setData(await cardLockSuggestionService.updateSettings(value))
      setFeedback({ tone: 'success', message: 'Đã lưu ngưỡng nợ phí và cập nhật danh sách đề xuất khóa thẻ.' })
    } catch (error) { setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không cập nhật được ngưỡng nợ phí.') }) }
    finally { pending.current = false; setSaving(false) }
  }
  return <div className="min-w-0 space-y-5">
    <PageHeader title="Ngưỡng nợ phí đề xuất khóa thẻ" description="Chỉ đề xuất khi tổng nợ lớn hơn ngưỡng. Hệ thống không tự khóa thẻ." />
    {feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />}
    {loading ? <p role="status">Đang tải cấu hình...</p> : !data ? <Card className="p-5"><p>Chưa tải được cấu hình.</p><Button onClick={() => setRevision(v => v + 1)}>Thử lại</Button></Card> :
      <Card className="max-w-2xl space-y-4 p-5 sm:p-6">
        <p className="text-sm text-slate-600">Ngưỡng hiện tại: <strong className="text-slate-900">{suggestionMoney(data.debtThresholdVnd)}</strong></p>
        <form noValidate onSubmit={event => { void submit(event) }} className="space-y-4">
          <Input label="Ngưỡng tổng nợ phí cho phép (VND)" required inputMode="numeric" maxLength={13}
            value={amount} disabled={saving} onChange={e => { setAmount(e.target.value); setFieldError('') }} error={fieldError || undefined} />
          <p className="text-sm leading-6 text-slate-600">Tổng nợ gồm các khoản phí trễ hạn, bồi thường và khoản phải thu khác đã ghi nhận, sau khi trừ thanh toán và miễn giảm. Không tính khoản bồi thường đã hủy.</p>
          <Button type="submit" loading={saving}>Lưu ngưỡng nợ phí</Button>
        </form>
        <p className="text-xs text-slate-500">Cập nhật gần nhất: {formatLoanTimestamp(data.updatedAt)}.</p>
      </Card>}
    <Link to="/card-lock-suggestions" className="inline-block font-semibold text-blue-700 hover:underline">Mở danh sách đề xuất khóa thẻ →</Link>
  </div>
}

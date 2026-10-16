import { useEffect, useState, type FormEvent } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { incidentService, parseFactor } from './incidentService'

export default function CompensationSettingsPage() {
  if (!['LIBRARY_MANAGER', 'ADMIN'].includes(getCurrentUser()?.role ?? '')) return <p role="alert">Chỉ Quản lý hoặc Quản trị được thay đổi hệ số.</p>
  return <SettingsForm />
}
function SettingsForm() {
  const [lost, setLost] = useState(''), [damaged, setDamaged] = useState('')
  const [loading, setLoading] = useState(true), [saving, setSaving] = useState(false), [loaded, setLoaded] = useState(false)
  const [revision, setRevision] = useState(0)
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null)
  useEffect(() => {
    let active = true
    setLoading(true)
    incidentService.factors().then(value => {
      if (!active) return
      setLost(value.lost === null ? '' : String(value.lost)); setDamaged(value.damaged === null ? '' : String(value.damaged)); setLoaded(true)
    }).catch(error => { if (active) setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không tải được hệ số bồi thường.') }) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [revision])
  const lostValue = parseFactor(lost), damagedValue = parseFactor(damaged)
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (saving || loading || !loaded || lostValue === null || damagedValue === null) return
    setSaving(true)
    try {
      await incidentService.updateFactors({ lost: lostValue, damaged: damagedValue })
      setFeedback({ tone: 'success', message: 'Đã lưu hệ số bồi thường. Sự cố đã ghi nhận giữ nguyên số tiền và hệ số cũ.' })
    } catch (error) { setFeedback({ tone: 'error', message: getApiErrorMessage(error, 'Không lưu được hệ số bồi thường.') }) }
    finally { setSaving(false) }
  }
  return <div className="space-y-4">
    <PageHeader title="Hệ số bồi thường" description="Số tiền bồi thường = giá bìa bản sao × hệ số. Làm tròn đến 1 đồng VND." />
    {feedback && <FeedbackAlert {...feedback} onDismiss={() => setFeedback(null)} />}
    {loading ? <p role="status">Đang tải hệ số...</p> : !loaded ? <Card className="p-5"><p>Chưa tải được cấu hình.</p><Button onClick={() => setRevision(v => v + 1)}>Thử lại</Button></Card> :
      <Card className="max-w-2xl p-5"><form onSubmit={submit} className="space-y-5">
        <Input label="Hệ số sách Mất" required inputMode="decimal" placeholder="Ví dụ: 2" value={lost} disabled={saving}
          onChange={e => setLost(e.target.value)} error={lost && lostValue === null ? 'Nhập số lớn hơn 0, tối đa 2 chữ số thập phân.' : undefined} />
        <Input label="Hệ số sách Hỏng nặng" required inputMode="decimal" placeholder="Ví dụ: 1,5" value={damaged} disabled={saving}
          onChange={e => setDamaged(e.target.value)} error={damaged && damagedValue === null ? 'Nhập số lớn hơn 0, tối đa 2 chữ số thập phân.' : undefined} />
        <p className="text-sm text-slate-600">Chưa khai báo hệ số thì Thủ thư chưa thể xác nhận sự cố tương ứng.</p>
        <Button type="submit" loading={saving} disabled={lostValue === null || damagedValue === null}>Lưu hệ số</Button>
      </form></Card>}
  </div>
}

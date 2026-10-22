import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { depositService, type DepositPolicy } from './depositService'

export default function DepositPolicyPanel({ readOnly }: { readOnly: boolean }) {
  const [current, setCurrent] = useState<DepositPolicy | null>(null)
  const [activationEnabled, setActivationEnabled] = useState(false)
  const [revision, setRevision] = useState(0), [rate, setRate] = useState('')
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [confirm, setConfirm] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('')
  const pending = useRef(false)
  useEffect(() => {
    let active = true; setLoading(true)
    depositService.policies(0).then(data => { if (active) { setCurrent(data.current); setActivationEnabled(data.activationEnabled === true); setError('') } }).catch(e => { if (active) setError(getApiErrorMessage(e, 'Không tải được chính sách cọc.')) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [revision])
  function validate() {
    if (!/^\d+(\.\d{1,4})?$/.test(rate) || Number(rate) <= 0 || Number(rate) > 100) { setError('Tỷ lệ > 0 và tối đa 100%, tối đa 4 số thập phân.'); return }
    setError(''); setConfirm(true)
  }
  async function save() {
    if (readOnly || !activationEnabled || pending.current) return
    pending.current = true; setBusy(true)
    try { await depositService.policy(Number(rate), null); setConfirm(false); setRevision(v => v + 1); setNotice('Đã lưu phiên bản chính sách cọc. Phiếu đã bàn giao trước hiệu lực không bị áp nghĩa vụ mới.') }
    catch (e) { setError(getApiErrorMessage(e, 'Không lưu được chính sách.')) }
    finally { pending.current = false; setBusy(false) }
  }
  return <Card className="space-y-3 p-4"><h2 className="font-semibold">Chính sách tiền cọc</h2>
    {loading ? <p role="status">Đang tải chính sách…</p> : !error && <p className="text-sm">{current ? `Cọc đang áp dụng: ${current.percentage}% giá bìa mỗi cuốn · phiên bản #${current.id}` : 'Chưa có chính sách cọc hiệu lực. Không tự áp dụng mức tiền mặc định.'}</p>}
    {error && <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />}{notice && <FeedbackAlert tone="success" message={notice} onDismiss={() => setNotice('')} />}
    {!readOnly && <div><h3 className="font-medium">Cấu hình và kích hoạt chính sách</h3><form className="mt-3 space-y-3" onSubmit={e => { e.preventDefault(); validate() }}>
      {!loading && !activationEnabled && <p role="status" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">Chức năng tạo chính sách chưa được mở trên máy chủ. Quản trị triển khai cần bật DEPOSIT_POLICY_ACTIVATION_ENABLED rồi khởi động lại backend trước khi lưu tỷ lệ.</p>}
      <p className="text-sm text-slate-600">Chỉ bàn giao mới từ thời điểm hiệu lực phải đủ cọc. Cọc tính riêng từng cuốn, làm tròn đến 1 VND; giá và tỷ lệ đã xác định không thay đổi theo cấu hình sau này.</p>
      <Input label="Tỷ lệ cọc (% giá bìa)" required value={rate} inputMode="decimal" disabled={busy || confirm || !activationEnabled} onChange={e => setRate(e.target.value)} />
      {confirm ? <div className="space-y-2 rounded-xl bg-amber-50 p-3"><p>Xác nhận cọc {rate}% cho bàn giao mới? Sách cần giá bìa hợp lệ; phải xác nhận đã thu đủ trước bàn giao.</p><div className="flex gap-2"><Button type="button" loading={busy} onClick={() => void save()}>Xác nhận lưu</Button><Button type="button" variant="secondary" disabled={busy} onClick={() => setConfirm(false)}>Hủy</Button></div></div> : <Button type="submit" disabled={loading || !activationEnabled}>Lưu chính sách mới</Button>}
    </form></div>}
  </Card>
}

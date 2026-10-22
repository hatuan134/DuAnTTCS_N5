import { useEffect, useRef, useState } from 'react'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import Input from '../../components/ui/Input'
import { librarySettingsService, type PercentagePolicy } from './librarySettingsService'
import { getApiErrorMessage } from '../s1-02-user-management/accountService'
import { formatLoanTimestamp } from '../s3-01-loans/loanService'

export default function OverdueFeeSettingsPanel({ readOnly }: { readOnly: boolean }) {
  const [current, setCurrent] = useState<PercentagePolicy | null>(null)
  const [rate, setRate] = useState(''), [cap, setCap] = useState(''), [limited, setLimited] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [revision, setRevision] = useState(0)
  const [loading, setLoading] = useState(true), [saving, setSaving] = useState(false)
  const [error, setError] = useState(''), [notice, setNotice] = useState('')
  const pending = useRef(false)
  useEffect(() => {
    let active = true
    setLoading(true)
    librarySettingsService.percentagePolicies(0).then(data => { if (active) { setCurrent(data.current); setError('') } })
      .catch(e => { if (active) setError(getApiErrorMessage(e, 'Không tải được chính sách.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [revision])
  function validate() {
    const valid = (s: string, max: number) => /^\d+(\.\d{1,4})?$/.test(s) && Number(s) > 0 && Number(s) <= max
    if (!valid(rate, 100) || (limited && !valid(cap, 10000))) { setError('Tỷ lệ > 0 và tối đa 100%/ngày; trần tối đa 10.000%, tối đa 4 chữ số thập phân.'); return }
    setError(''); setConfirm(true)
  }
  async function save() {
    if (readOnly || pending.current) return
    pending.current = true; setSaving(true)
    try {
      await librarySettingsService.savePercentagePolicy({ dailyPercent: Number(rate), capPercent: limited ? Number(cap) : null, effectiveAt: null })
      setRevision(v => v + 1); setConfirm(false)
      setNotice('Đã lưu chính sách. Chỉ áp dụng cho sách bàn giao từ thời điểm hiệu lực; snapshot cũ giữ nguyên.')
    } catch (e) { setError(getApiErrorMessage(e, 'Không lưu được chính sách.')) }
    finally { pending.current = false; setSaving(false) }
  }
  return <section id="overdue-fee"><Card className="min-w-0 space-y-4 p-5">
    <h2 className="text-lg font-semibold text-slate-900">Phí trễ hạn theo % giá bìa</h2>
    <p className="text-sm leading-6 text-slate-600">Phí từng cuốn = giá bìa lúc bàn giao × tỷ lệ %/ngày ÷ 100 × số ngày mở cửa quá hạn. Không tính ngày đóng cửa.</p>
    {loading && <p role="status">Đang tải chính sách…</p>}
    {error && <FeedbackAlert tone="error" message={error} onDismiss={() => setError('')} />}
    {notice && <FeedbackAlert tone="success" message={notice} onDismiss={() => setNotice('')} />}
    {!loading && !error && (current ? <p className="rounded-xl bg-blue-50 p-3 text-sm text-blue-900">Đang áp dụng cho bàn giao mới: #{current.id} · {current.dailyPercent}%/ngày · {current.capPercent === null ? 'Không giới hạn trần' : `Trần ${current.capPercent}% giá bìa`}. Hiệu lực từ {formatLoanTimestamp(current.effectiveAt)}.</p> : <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">Chưa có chính sách % đang hiệu lực. Hệ thống giữ cơ chế phí cũ, không tự áp dụng tỷ lệ; các chính sách hẹn lịch chỉ áp dụng từ thời điểm hiệu lực.</p>)}
    {!readOnly && <form className="space-y-3" onSubmit={e => { e.preventDefault(); validate() }}>
      <Input label="Tỷ lệ phí (%/ngày)" required inputMode="decimal" value={rate} disabled={saving || confirm} onChange={e => setRate(e.target.value)} />
      <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={limited} disabled={saving || confirm} onChange={e => setLimited(e.target.checked)} />Giới hạn phí tối đa theo từng cuốn</label>
      {limited && <Input label="Giới hạn (% giá bìa)" required inputMode="decimal" value={cap} disabled={saving || confirm} onChange={e => setCap(e.target.value)} />}
      {confirm ? <div className="space-y-3 rounded-xl bg-blue-50 p-4"><p className="text-sm">Xác nhận {rate}%/ngày, {limited ? `trần ${cap}% giá bìa` : 'không giới hạn trần'}? Chỉ áp dụng cho bàn giao mới, sách phải có giá bìa hợp lệ.</p><div className="flex gap-2"><Button type="button" loading={saving} onClick={() => void save()}>Xác nhận lưu chính sách</Button><Button type="button" variant="secondary" disabled={saving} onClick={() => setConfirm(false)}>Hủy</Button></div></div> : <Button disabled={loading} type="submit">Lưu chính sách mới</Button>}
    </form>}
  </Card></section>
}

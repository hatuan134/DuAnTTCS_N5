import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import type { AxiosError } from 'axios'
import {
  CalendarDays,
  Calculator,
  Clock3,
  Pencil,
  Plus,
  RefreshCw,
  Rows3,
  Save,
  Trash2,
  Warehouse,
  X,
} from 'lucide-react'

import Card from '../../components/ui/Card'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'
import {
  librarySettingsService,
} from './librarySettingsService'
import type {
  ClosedDateForm,
  ClosedDateItem,
  DueDateAdjustment,
  ShelfForm,
  ShelfItem,
  WarehouseForm,
  WarehouseItem,
  WeeklyScheduleItem,
  WeeklySchedulePayload,
} from './librarySettingsService'

type PageMode = 'warehouse' | 'calendar'

type Props = {
  mode: PageMode
}

type ApiErrorPayload = {
  message?: string
  code?: string
}

const emptyWarehouseForm: WarehouseForm = {
  code: '',
  name: '',
  description: '',
}

const emptyShelfForm: ShelfForm = {
  warehouseId: 0,
  code: '',
  name: '',
  description: '',
}

const emptyClosedDateForm: ClosedDateForm = {
  closedDate: '',
  reason: '',
}

function getErrorMessage(error: unknown, fallback: string) {
  const axiosError = error as AxiosError<ApiErrorPayload>
  return axiosError.response?.data?.message || fallback
}

export default function LibrarySettingsPage({ mode }: Props) {
  const user = getCurrentUser()
  const allowed = user?.role === 'LIBRARY_MANAGER' || user?.role === 'ADMIN'

  if (!allowed) {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Cấu hình thư viện"
          description="Chức năng dành cho Quản lý thư viện."
        />
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-800">
          Tài khoản hiện tại không có quyền khai báo kho, kệ hoặc lịch đóng cửa.
        </div>
      </div>
    )
  }

  return mode === 'warehouse' ? <WarehouseShelfPage /> : <LibraryCalendarPage />
}

function WarehouseShelfPage() {
  const [warehouses, setWarehouses] = useState<WarehouseItem[]>([])
  const [shelves, setShelves] = useState<ShelfItem[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [warehouseModal, setWarehouseModal] = useState(false)
  const [shelfModal, setShelfModal] = useState(false)
  const [editingWarehouse, setEditingWarehouse] = useState<WarehouseItem | null>(null)
  const [editingShelf, setEditingShelf] = useState<ShelfItem | null>(null)
  const [warehouseForm, setWarehouseForm] = useState<WarehouseForm>(emptyWarehouseForm)
  const [shelfForm, setShelfForm] = useState<ShelfForm>(emptyShelfForm)

  const loadData = async () => {
    setLoading(true)
    setError('')
    try {
      const [warehouseData, shelfData] = await Promise.all([
        librarySettingsService.getWarehouses(),
        librarySettingsService.getShelves(),
      ])
      setWarehouses(warehouseData)
      setShelves(shelfData)
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể tải dữ liệu kho và kệ.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadData()
  }, [])

  const openCreateWarehouse = () => {
    setEditingWarehouse(null)
    setWarehouseForm(emptyWarehouseForm)
    setError('')
    setWarehouseModal(true)
  }

  const openEditWarehouse = (item: WarehouseItem) => {
    setEditingWarehouse(item)
    setWarehouseForm({
      code: item.code,
      name: item.name,
      description: item.description,
    })
    setError('')
    setWarehouseModal(true)
  }

  const submitWarehouse = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')

    try {
      const payload: WarehouseForm = {
        code: warehouseForm.code.trim().toUpperCase(),
        name: warehouseForm.name.trim(),
        description: warehouseForm.description.trim(),
      }

      if (editingWarehouse) {
        await librarySettingsService.updateWarehouse(editingWarehouse.id, payload)
        setNotice('Cập nhật kho thành công.')
      } else {
        await librarySettingsService.createWarehouse(payload)
        setNotice('Thêm kho thành công.')
      }

      setWarehouseModal(false)
      await loadData()
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể lưu thông tin kho.'))
    } finally {
      setSaving(false)
    }
  }

  const openCreateShelf = () => {
    setEditingShelf(null)
    setShelfForm({
      ...emptyShelfForm,
      warehouseId: warehouses[0]?.id ?? 0,
    })
    setError('')
    setShelfModal(true)
  }

  const openEditShelf = (item: ShelfItem) => {
    setEditingShelf(item)
    setShelfForm({
      warehouseId: item.warehouseId,
      code: item.code,
      name: item.name,
      description: item.description,
    })
    setError('')
    setShelfModal(true)
  }

  const submitShelf = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')

    try {
      const payload: ShelfForm = {
        warehouseId: shelfForm.warehouseId,
        code: shelfForm.code.trim().toUpperCase(),
        name: shelfForm.name.trim(),
        description: shelfForm.description.trim(),
      }

      if (editingShelf) {
        await librarySettingsService.updateShelf(editingShelf.id, payload)
        setNotice('Cập nhật kệ thành công.')
      } else {
        await librarySettingsService.createShelf(payload)
        setNotice('Thêm kệ thành công.')
      }

      setShelfModal(false)
      await loadData()
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể lưu thông tin kệ.'))
    } finally {
      setSaving(false)
    }
  }

  const deleteShelf = async (item: ShelfItem) => {
    if (item.inUse) {
      setError(`Không thể xoá kệ ${item.code} vì đang có ${item.copyCount} bản sao sách.`)
      return
    }

    if (!window.confirm(`Bạn có chắc muốn xoá kệ ${item.code}?`)) {
      return
    }

    setError('')
    setNotice('')
    try {
      await librarySettingsService.deleteShelf(item.id)
      setNotice(`Đã xoá kệ ${item.code}.`)
      await loadData()
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể xoá kệ.'))
    }
  }

  const totalCopies = shelves.reduce((sum, item) => sum + item.copyCount, 0)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Kho & kệ"
        description="Khai báo kho, kệ và kiểm soát kệ đang có bản sao sách."
      />

      <Feedback error={error} notice={notice} />

      <div className="grid gap-4 md:grid-cols-3">
        <StatCard label="Tổng số kho" value={warehouses.length} />
        <StatCard label="Tổng số kệ" value={shelves.length} />
        <StatCard label="Bản sao đang xếp kệ" value={totalCopies} />
      </div>

      <Card>
        <SectionHeader
          title="Danh sách kho"
          description="Mã kho và tên kho là thông tin nhận diện duy nhất."
          action={(
            <button type="button" onClick={openCreateWarehouse} className="primary-button">
              <Plus size={18} /> Thêm kho
            </button>
          )}
        />

        {loading ? (
          <LoadingBlock />
        ) : warehouses.length === 0 ? (
          <EmptyBlock text="Chưa có kho nào. Hãy tạo kho đầu tiên." />
        ) : (
          <div className="grid gap-4 p-5 md:grid-cols-2 xl:grid-cols-3">
            {warehouses.map((item) => (
              <div key={item.id} className="rounded-xl border border-slate-200 p-5">
                <div className="flex items-start justify-between gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-600">
                    <Warehouse size={20} />
                  </div>
                  <button type="button" title="Sửa kho" onClick={() => openEditWarehouse(item)} className="icon-button">
                    <Pencil size={16} />
                  </button>
                </div>
                <div className="mt-4 flex items-center gap-2">
                  <span className="rounded-md bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">{item.code}</span>
                  <h3 className="font-semibold text-slate-900">{item.name}</h3>
                </div>
                <p className="mt-2 min-h-10 text-sm text-slate-500">{item.description || 'Không có mô tả'}</p>
                <div className="mt-4 grid grid-cols-2 gap-2 border-t border-slate-100 pt-3 text-sm text-slate-600">
                  <span>{item.shelfCount} kệ</span>
                  <span className="text-right">{item.copyCount} bản sao</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card>
        <SectionHeader
          title="Danh sách kệ"
          description="Mã kệ chỉ cần duy nhất trong cùng một kho."
          action={(
            <div className="flex gap-2">
              <button type="button" onClick={() => void loadData()} className="secondary-button">
                <RefreshCw size={17} /> Làm mới
              </button>
              <button type="button" onClick={openCreateShelf} disabled={warehouses.length === 0} className="primary-button disabled:cursor-not-allowed disabled:opacity-50">
                <Plus size={18} /> Thêm kệ
              </button>
            </div>
          )}
        />

        <div className="overflow-x-auto">
          <table className="w-full min-w-[920px]">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <TableHead>Mã kệ</TableHead>
                <TableHead>Tên kệ</TableHead>
                <TableHead>Kho</TableHead>
                <TableHead>Trạng thái sử dụng</TableHead>
                <TableHead align="right">Thao tác</TableHead>
              </tr>
            </thead>
            <tbody>
              {shelves.map((item) => (
                <tr key={item.id} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet-50 text-violet-600"><Rows3 size={18} /></div>
                      <span className="font-semibold text-slate-900">{item.code}</span>
                    </div>
                  </td>
                  <td className="px-5 py-4 text-sm text-slate-700">{item.name}</td>
                  <td className="px-5 py-4 text-sm text-slate-700">{item.warehouseCode} — {item.warehouseName}</td>
                  <td className="px-5 py-4">
                    <span className={`rounded-full px-3 py-1 text-xs font-medium ${item.inUse ? 'bg-blue-50 text-blue-700' : 'bg-emerald-50 text-emerald-700'}`}>
                      {item.inUse ? `Đang có ${item.copyCount} bản sao` : 'Kệ trống'}
                    </span>
                  </td>
                  <td className="px-5 py-4">
                    <div className="flex justify-end gap-2">
                      <button type="button" title="Sửa kệ" onClick={() => openEditShelf(item)} className="icon-button"><Pencil size={16} /></button>
                      <button type="button" title={item.inUse ? 'Không thể xoá kệ đang có bản sao' : 'Xoá kệ'} onClick={() => void deleteShelf(item)} className={`icon-button ${item.inUse ? 'cursor-not-allowed opacity-45' : 'hover:border-red-200 hover:bg-red-50 hover:text-red-600'}`}><Trash2 size={16} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {!loading && shelves.length === 0 && (
                <tr><td colSpan={5} className="px-5 py-10 text-center text-sm text-slate-500">Chưa có kệ nào.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {warehouseModal && (
        <Modal title={editingWarehouse ? 'Chỉnh sửa kho' : 'Thêm kho'} onClose={() => setWarehouseModal(false)}>
          <form onSubmit={submitWarehouse} className="space-y-5">
            <TextField label="Mã kho" required value={warehouseForm.code} placeholder="Ví dụ: KHO-A" onChange={(code) => setWarehouseForm({ ...warehouseForm, code })} />
            <TextField label="Tên kho" required value={warehouseForm.name} placeholder="Ví dụ: Kho sách chính" onChange={(name) => setWarehouseForm({ ...warehouseForm, name })} />
            <TextAreaField label="Mô tả" value={warehouseForm.description} onChange={(description) => setWarehouseForm({ ...warehouseForm, description })} />
            {error && <ErrorBox message={error} />}
            <ModalActions saving={saving} submitText={editingWarehouse ? 'Lưu thay đổi' : 'Thêm kho'} onCancel={() => setWarehouseModal(false)} />
          </form>
        </Modal>
      )}

      {shelfModal && (
        <Modal title={editingShelf ? 'Chỉnh sửa kệ' : 'Thêm kệ'} onClose={() => setShelfModal(false)}>
          <form onSubmit={submitShelf} className="space-y-5">
            <SelectWarehouse warehouses={warehouses} value={shelfForm.warehouseId} onChange={(warehouseId) => setShelfForm({ ...shelfForm, warehouseId })} />
            <TextField label="Mã kệ" required value={shelfForm.code} placeholder="Ví dụ: A03" onChange={(code) => setShelfForm({ ...shelfForm, code })} />
            <TextField label="Tên kệ" required value={shelfForm.name} placeholder="Ví dụ: Kệ Khoa học" onChange={(name) => setShelfForm({ ...shelfForm, name })} />
            <TextAreaField label="Mô tả" value={shelfForm.description} onChange={(description) => setShelfForm({ ...shelfForm, description })} />
            {error && <ErrorBox message={error} />}
            <ModalActions saving={saving} submitText={editingShelf ? 'Lưu thay đổi' : 'Thêm kệ'} onCancel={() => setShelfModal(false)} />
          </form>
        </Modal>
      )}
    </div>
  )
}

function LibraryCalendarPage() {
  const [schedule, setSchedule] = useState<WeeklyScheduleItem[]>([])
  const [closedDates, setClosedDates] = useState<ClosedDateItem[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [holidayModal, setHolidayModal] = useState(false)
  const [bulkModal, setBulkModal] = useState(false)
  const [editingHoliday, setEditingHoliday] = useState<ClosedDateItem | null>(null)
  const [holidayForm, setHolidayForm] = useState<ClosedDateForm>(emptyClosedDateForm)
  const [bulkYear, setBulkYear] = useState(new Date().getFullYear())
  const [bulkText, setBulkText] = useState('01-01 | Tết Dương lịch\n09-02 | Quốc khánh')

  const [dueDate, setDueDate] = useState('')
  const [adjustment, setAdjustment] = useState<DueDateAdjustment | null>(null)
  const [checkingDueDate, setCheckingDueDate] = useState(false)

  const loadData = async () => {
    setLoading(true)
    setError('')
    try {
      const [scheduleData, closedDateData] = await Promise.all([
        librarySettingsService.getWeeklySchedule(),
        librarySettingsService.getClosedDates(),
      ])
      setSchedule(scheduleData)
      setClosedDates(closedDateData)
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể tải lịch hoạt động của thư viện.'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadData()
  }, [])

  const updateSchedule = (dayOfWeek: number, changes: Partial<WeeklyScheduleItem>) => {
    setSchedule((current) => current.map((item) => item.dayOfWeek === dayOfWeek ? { ...item, ...changes } : item))
    setNotice('')
  }

  const saveSchedule = async () => {
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const payload: WeeklySchedulePayload[] = schedule.map((item) => ({
        dayOfWeek: item.dayOfWeek,
        open: item.open,
        openTime: item.open ? item.openTime : null,
        closeTime: item.open ? item.closeTime : null,
      }))
      const saved = await librarySettingsService.updateWeeklySchedule(payload)
      setSchedule(saved)
      setNotice('Đã lưu lịch làm việc theo tuần. Các ngày nghỉ riêng vẫn được giữ nguyên.')
      setAdjustment(null)
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể lưu lịch làm việc.'))
    } finally {
      setSaving(false)
    }
  }

  const openCreateHoliday = () => {
    setEditingHoliday(null)
    setHolidayForm(emptyClosedDateForm)
    setError('')
    setHolidayModal(true)
  }

  const openEditHoliday = (item: ClosedDateItem) => {
    setEditingHoliday(item)
    setHolidayForm({ closedDate: item.closedDate, reason: item.reason })
    setError('')
    setHolidayModal(true)
  }

  const submitHoliday = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      if (editingHoliday) {
        await librarySettingsService.updateClosedDate(editingHoliday.id, holidayForm)
        setNotice('Cập nhật ngày đóng cửa thành công.')
      } else {
        await librarySettingsService.createClosedDate(holidayForm)
        setNotice('Thêm ngày đóng cửa thành công.')
      }
      setHolidayModal(false)
      setAdjustment(null)
      await loadData()
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể lưu ngày đóng cửa.'))
    } finally {
      setSaving(false)
    }
  }

  const deleteHoliday = async (item: ClosedDateItem) => {
    if (!window.confirm(`Xoá ngày đóng cửa ${formatDateVi(item.closedDate)} — ${item.reason}?`)) return
    setError('')
    setNotice('')
    try {
      await librarySettingsService.deleteClosedDate(item.id)
      setNotice('Đã xoá ngày đóng cửa.')
      setAdjustment(null)
      await loadData()
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể xoá ngày đóng cửa.'))
    }
  }

  const submitBulk = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const rows = bulkText.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
      const dates: ClosedDateForm[] = rows.map((line, index) => {
        const parts = line.split('|')
        if (parts.length < 2) throw new Error(`Dòng ${index + 1} phải theo mẫu MM-DD | Lý do`)
        const monthDay = parts[0].trim()
        const reason = parts.slice(1).join('|').trim()
        if (!/^\d{2}-\d{2}$/.test(monthDay) || !reason) throw new Error(`Dòng ${index + 1} không đúng định dạng MM-DD | Lý do`)
        const closedDate = `${bulkYear}-${monthDay}`
        const parsed = new Date(`${closedDate}T12:00:00`)
        if (Number.isNaN(parsed.getTime()) || toIsoDate(parsed) !== closedDate) throw new Error(`Ngày ở dòng ${index + 1} không hợp lệ`)
        return { closedDate, reason }
      })

      await librarySettingsService.createClosedDatesBulk(dates)
      setBulkModal(false)
      setNotice(`Đã khai báo ${dates.length} ngày đóng cửa cho năm ${bulkYear}.`)
      setAdjustment(null)
      await loadData()
    } catch (err) {
      setError(err instanceof Error && !('response' in err) ? err.message : getErrorMessage(err, 'Không thể khai báo lịch cả năm.'))
    } finally {
      setSaving(false)
    }
  }

  const checkDueDate = async () => {
    if (!dueDate) {
      setError('Vui lòng chọn hạn trả dự kiến.')
      return
    }
    setCheckingDueDate(true)
    setError('')
    try {
      setAdjustment(await librarySettingsService.adjustDueDate(dueDate))
    } catch (err) {
      setError(getErrorMessage(err, 'Không thể kiểm tra hạn trả.'))
    } finally {
      setCheckingDueDate(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Lịch đóng cửa"
        description="Khai báo lịch làm việc theo tuần, ngày nghỉ cụ thể và kiểm tra hạn trả tự động."
      />

      <Feedback error={error} notice={notice} />

      <Card>
        <SectionHeader
          title="Lịch làm việc theo tuần"
          description="Ngày nghỉ cụ thể có độ ưu tiên cao hơn lịch tuần."
          icon={<Clock3 size={20} />}
          action={(
            <button type="button" onClick={() => void saveSchedule()} disabled={saving || loading} className="primary-button disabled:opacity-50">
              <Save size={17} /> {saving ? 'Đang lưu...' : 'Lưu lịch'}
            </button>
          )}
        />

        {loading ? <LoadingBlock /> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px]">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50">
                  <TableHead>Ngày</TableHead>
                  <TableHead>Trạng thái</TableHead>
                  <TableHead>Mở cửa</TableHead>
                  <TableHead>Đóng cửa</TableHead>
                </tr>
              </thead>
              <tbody>
                {schedule.map((item) => (
                  <tr key={item.dayOfWeek} className="border-b border-slate-100">
                    <td className="px-5 py-4 font-medium text-slate-800">{item.dayLabel}</td>
                    <td className="px-5 py-4">
                      <label className="inline-flex cursor-pointer items-center gap-2">
                        <input type="checkbox" checked={item.open} onChange={(event) => updateSchedule(item.dayOfWeek, { open: event.target.checked })} className="h-4 w-4 rounded" />
                        <span className={`text-sm font-medium ${item.open ? 'text-emerald-700' : 'text-slate-500'}`}>{item.open ? 'Mở cửa' : 'Đóng cửa'}</span>
                      </label>
                    </td>
                    <td className="px-5 py-4">
                      <input type="time" disabled={!item.open} value={item.openTime ?? ''} onChange={(event) => updateSchedule(item.dayOfWeek, { openTime: event.target.value })} className="time-input" />
                    </td>
                    <td className="px-5 py-4">
                      <input type="time" disabled={!item.open} value={item.closeTime ?? ''} onChange={(event) => updateSchedule(item.dayOfWeek, { closeTime: event.target.value })} className="time-input" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card>
        <SectionHeader
          title="Ngày nghỉ / ngày đóng cửa cụ thể"
          description="Có thể khai báo từng ngày hoặc nhập nhiều ngày cho cả năm."
          icon={<CalendarDays size={20} />}
          action={(
            <div className="flex flex-wrap gap-2">
              <button type="button" onClick={() => setBulkModal(true)} className="secondary-button"><CalendarDays size={17} /> Khai báo cả năm</button>
              <button type="button" onClick={openCreateHoliday} className="primary-button"><Plus size={18} /> Thêm ngày nghỉ</button>
            </div>
          )}
        />

        <div className="overflow-x-auto">
          <table className="w-full min-w-[700px]">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <TableHead>Ngày</TableHead>
                <TableHead>Thứ</TableHead>
                <TableHead>Lý do</TableHead>
                <TableHead align="right">Thao tác</TableHead>
              </tr>
            </thead>
            <tbody>
              {closedDates.map((item) => (
                <tr key={item.id} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-5 py-4 font-medium text-slate-800">{formatDateVi(item.closedDate)}</td>
                  <td className="px-5 py-4 text-sm text-slate-600">{weekdayVi(item.closedDate)}</td>
                  <td className="px-5 py-4 text-sm text-slate-600">{item.reason}</td>
                  <td className="px-5 py-4">
                    <div className="flex justify-end gap-2">
                      <button type="button" title="Sửa ngày nghỉ" onClick={() => openEditHoliday(item)} className="icon-button"><Pencil size={16} /></button>
                      <button type="button" title="Xoá ngày nghỉ" onClick={() => void deleteHoliday(item)} className="icon-button hover:border-red-200 hover:bg-red-50 hover:text-red-600"><Trash2 size={16} /></button>
                    </div>
                  </td>
                </tr>
              ))}
              {!loading && closedDates.length === 0 && (
                <tr><td colSpan={4} className="px-5 py-10 text-center text-sm text-slate-500">Chưa có ngày đóng cửa cụ thể.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <Card>
        <SectionHeader
          title="Kiểm tra hạn trả"
          description="Nếu ngày dự kiến đóng cửa, hệ thống tự đẩy tới ngày mở cửa kế tiếp."
          icon={<Calculator size={20} />}
        />
        <div className="grid gap-5 p-5 lg:grid-cols-[1fr_auto_1.4fr] lg:items-end">
          <div>
            <label className="field-label">Hạn trả dự kiến</label>
            <input type="date" value={dueDate} onChange={(event) => { setDueDate(event.target.value); setAdjustment(null) }} className="field-input" />
          </div>
          <button type="button" onClick={() => void checkDueDate()} disabled={checkingDueDate} className="primary-button h-[42px] disabled:opacity-50">
            {checkingDueDate ? 'Đang kiểm tra...' : 'Kiểm tra'}
          </button>
          <div>
            <p className="field-label">Kết quả</p>
            <div className="min-h-[42px] rounded-lg border border-slate-200 bg-slate-50 px-4 py-2.5 text-sm">
              {!adjustment ? (
                <span className="text-slate-400">Chọn ngày và bấm Kiểm tra</span>
              ) : adjustment.adjusted ? (
                <div>
                  <span className="font-semibold text-amber-700">{formatDateVi(adjustment.adjustedDate)} — đã chuyển sang ngày mở cửa kế tiếp</span>
                  <p className="mt-1 text-xs text-slate-500">Đã bỏ qua: {adjustment.skippedClosedDates.map(formatDateVi).join(', ')}</p>
                </div>
              ) : (
                <span className="font-semibold text-emerald-700">{formatDateVi(adjustment.adjustedDate)} — thư viện mở cửa</span>
              )}
            </div>
          </div>
        </div>
      </Card>

      {holidayModal && (
        <Modal title={editingHoliday ? 'Chỉnh sửa ngày nghỉ' : 'Thêm ngày nghỉ'} onClose={() => setHolidayModal(false)}>
          <form onSubmit={submitHoliday} className="space-y-5">
            <div>
              <label className="field-label">Ngày <span className="text-red-500">*</span></label>
              <input type="date" required value={holidayForm.closedDate} onChange={(event) => setHolidayForm({ ...holidayForm, closedDate: event.target.value })} className="field-input" />
            </div>
            <TextField label="Lý do đóng cửa" required value={holidayForm.reason} placeholder="Ví dụ: Nghỉ Quốc khánh" onChange={(reason) => setHolidayForm({ ...holidayForm, reason })} />
            {error && <ErrorBox message={error} />}
            <ModalActions saving={saving} submitText={editingHoliday ? 'Lưu thay đổi' : 'Thêm ngày nghỉ'} onCancel={() => setHolidayModal(false)} />
          </form>
        </Modal>
      )}

      {bulkModal && (
        <Modal title="Khai báo lịch đóng cửa cho cả năm" onClose={() => setBulkModal(false)}>
          <form onSubmit={submitBulk} className="space-y-5">
            <div>
              <label className="field-label">Năm</label>
              <input type="number" min="2000" max="2100" value={bulkYear} onChange={(event) => setBulkYear(Number(event.target.value))} className="field-input" />
            </div>
            <div>
              <label className="field-label">Danh sách ngày nghỉ</label>
              <p className="mb-2 text-xs text-slate-500">Mỗi dòng theo mẫu: <strong>MM-DD | Lý do</strong></p>
              <textarea rows={8} value={bulkText} onChange={(event) => setBulkText(event.target.value)} className="field-input resize-none font-mono text-sm" />
            </div>
            {error && <ErrorBox message={error} />}
            <ModalActions saving={saving} submitText="Khai báo các ngày" onCancel={() => setBulkModal(false)} />
          </form>
        </Modal>
      )}
    </div>
  )
}

function Feedback({ error, notice }: { error: string; notice: string }) {
  return (
    <>
      {error && <ErrorBox message={error} />}
      {notice && <div className="rounded-xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{notice}</div>}
    </>
  )
}

function StatCard({ label, value }: { label: string; value: number }) {
  return <Card><div className="p-5"><p className="text-sm text-slate-500">{label}</p><p className="mt-2 text-2xl font-semibold text-slate-900">{value}</p></div></Card>
}

function SectionHeader({ title, description, action, icon }: { title: string; description: string; action?: ReactNode; icon?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-200 p-5">
      <div className="flex items-center gap-3">
        {icon && <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-600">{icon}</div>}
        <div><h2 className="text-lg font-semibold text-slate-900">{title}</h2><p className="mt-1 text-sm text-slate-500">{description}</p></div>
      </div>
      {action}
    </div>
  )
}

function TableHead({ children, align = 'left' }: { children: ReactNode; align?: 'left' | 'right' }) {
  return <th className={`px-5 py-3 text-xs font-semibold uppercase text-slate-500 ${align === 'right' ? 'text-right' : 'text-left'}`}>{children}</th>
}

function LoadingBlock() {
  return <div className="flex items-center justify-center gap-3 p-10 text-sm text-slate-500"><span className="h-5 w-5 animate-spin rounded-full border-2 border-slate-300 border-t-blue-600" /> Đang tải dữ liệu...</div>
}

function EmptyBlock({ text }: { text: string }) {
  return <div className="p-10 text-center text-sm text-slate-500">{text}</div>
}

function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="max-h-[92vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-5">
          <h2 className="text-xl font-semibold text-slate-900">{title}</h2>
          <button type="button" onClick={onClose} className="icon-button border-0"><X size={20} /></button>
        </div>
        <div className="p-6">{children}</div>
      </div>
    </div>
  )
}

function TextField({ label, value, onChange, required = false, placeholder }: { label: string; value: string; onChange: (value: string) => void; required?: boolean; placeholder?: string }) {
  return (
    <div>
      <label className="field-label">{label}{required && <span className="ml-1 text-red-500">*</span>}</label>
      <input required={required} value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} className="field-input" />
    </div>
  )
}

function TextAreaField({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return <div><label className="field-label">{label}</label><textarea rows={3} value={value} onChange={(event) => onChange(event.target.value)} className="field-input resize-none" /></div>
}

function SelectWarehouse({ warehouses, value, onChange }: { warehouses: WarehouseItem[]; value: number; onChange: (value: number) => void }) {
  return (
    <div>
      <label className="field-label">Kho <span className="text-red-500">*</span></label>
      <select required value={value || ''} onChange={(event) => onChange(Number(event.target.value))} className="field-input">
        <option value="">Chọn kho</option>
        {warehouses.map((item) => <option key={item.id} value={item.id}>{item.code} — {item.name}</option>)}
      </select>
    </div>
  )
}

function ModalActions({ submitText, onCancel, saving }: { submitText: string; onCancel: () => void; saving: boolean }) {
  return (
    <div className="flex justify-end gap-3 border-t border-slate-100 pt-5">
      <button type="button" onClick={onCancel} className="secondary-button">Hủy</button>
      <button type="submit" disabled={saving} className="primary-button disabled:opacity-50">{saving ? 'Đang lưu...' : submitText}</button>
    </div>
  )
}

function ErrorBox({ message }: { message: string }) {
  return <div className="rounded-lg border border-red-100 bg-red-50 px-4 py-3 text-sm text-red-700">{message}</div>
}

function parseLocalDate(value: string) {
  return new Date(`${value}T12:00:00`)
}

function toIsoDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatDateVi(value: string) {
  return value ? parseLocalDate(value).toLocaleDateString('vi-VN') : ''
}

function weekdayVi(value: string) {
  return value ? parseLocalDate(value).toLocaleDateString('vi-VN', { weekday: 'long' }) : ''
}

import { useEffect, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import type { AxiosError } from 'axios'
import {
  CalendarDays,
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
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'
import TableActionButton, { TableActions } from '../../components/ui/TableActionButton'
import TablePagination from '../../components/ui/TablePagination'
import useTablePagination from '../../hooks/useTablePagination'
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
        <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-700">
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
  const shelfPagination = useTablePagination(shelves)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Kho & kệ"
        description="Khai báo kho, kệ và kiểm soát kệ đang có bản sao sách."
      />

      <Feedback
        error={warehouseModal || shelfModal ? '' : error}
        notice={notice}
        onDismissError={() => setError('')}
        onDismissNotice={() => setNotice('')}
      />

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
                  <TableActionButton type="button" onClick={() => openEditWarehouse(item)} icon={<Pencil size={14} />}>
                    Chỉnh sửa
                  </TableActionButton>
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
          <table className="data-table w-full min-w-[980px]">
            <thead>
              <tr className="border-b border-slate-200 bg-slate-50">
                <TableHead>STT</TableHead>
                <TableHead>Mã kệ</TableHead>
                <TableHead>Tên kệ</TableHead>
                <TableHead>Kho</TableHead>
                <TableHead>Trạng thái sử dụng</TableHead>
                <TableHead align="right">Thao tác</TableHead>
              </tr>
            </thead>
            <tbody>
              {shelfPagination.pageItems.map((item, index) => (
                <tr key={item.id} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-5 py-4 font-semibold text-slate-500">
                    {shelfPagination.startIndex + index + 1}
                  </td>
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
                    <TableActions>
                      <TableActionButton icon={<Pencil size={16} />} tone="primary" onClick={() => openEditShelf(item)}>
                        Chỉnh sửa
                      </TableActionButton>
                      <TableActionButton
                        icon={<Trash2 size={16} />}
                        tone="danger"
                        title={item.inUse ? 'Không thể xóa kệ đang có bản sao' : 'Xóa kệ'}
                        disabled={item.inUse}
                        onClick={() => void deleteShelf(item)}
                      >
                        Xóa
                      </TableActionButton>
                    </TableActions>
                  </td>
                </tr>
              ))}
              {!loading && shelves.length === 0 && (
                <tr><td colSpan={6} className="px-5 py-10 text-center text-sm text-slate-500">Chưa có kệ nào.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <TablePagination
          page={shelfPagination.page}
          totalItems={shelfPagination.totalItems}
          totalPages={shelfPagination.totalPages}
          pageSize={shelfPagination.pageSize}
          onPageChange={shelfPagination.goToPage}
        />
      </Card>

      {warehouseModal && (
        <Modal title={editingWarehouse ? 'Chỉnh sửa kho' : 'Thêm kho'} onClose={() => setWarehouseModal(false)}>
          <form onSubmit={submitWarehouse} className="space-y-5">
            <TextField label="Mã kho" required value={warehouseForm.code} placeholder="Ví dụ: KHO-A" onChange={(code) => setWarehouseForm({ ...warehouseForm, code })} />
            <TextField label="Tên kho" required value={warehouseForm.name} placeholder="Ví dụ: Kho sách chính" onChange={(name) => setWarehouseForm({ ...warehouseForm, name })} />
            <TextAreaField label="Mô tả" value={warehouseForm.description} onChange={(description) => setWarehouseForm({ ...warehouseForm, description })} />
            {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
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
            {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
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

  const closedDatePagination = useTablePagination(closedDates, '', 10)
  const openDayCount = schedule.filter((item) => item.open).length
  const weeklyClosedDayCount = schedule.length - openDayCount

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

      <Feedback
        error={holidayModal || bulkModal ? '' : error}
        notice={notice}
        onDismissError={() => setError('')}
        onDismissNotice={() => setNotice('')}
      />

      <Card className="overflow-hidden">
        <div className="grid gap-2 border-b border-slate-200 bg-slate-50/70 p-3 sm:grid-cols-3">
          <div className="rounded-lg border border-emerald-100 bg-white px-3 py-2.5">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Mở cửa mỗi tuần</p>
            <p className="mt-0.5 text-lg font-bold text-emerald-700">{openDayCount} ngày</p>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white px-3 py-2.5">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Đóng cửa cố định</p>
            <p className="mt-0.5 text-lg font-bold text-slate-700">{weeklyClosedDayCount} ngày</p>
          </div>
          <div className="rounded-lg border border-blue-100 bg-white px-3 py-2.5">
            <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">Ngày nghỉ đã khai báo</p>
            <p className="mt-0.5 text-lg font-bold text-blue-700">{closedDates.length} ngày</p>
          </div>
        </div>

        <div className="calendar-pair-grid grid min-w-0 xl:grid-cols-2">
          <section className="calendar-panel min-w-0 border-b border-slate-200 xl:border-b-0 xl:border-r">
            <div className="calendar-panel-header flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 px-4 py-3 xl:flex-nowrap">
              <div className="min-w-0">
                <h2 className="font-semibold text-slate-900">Lịch làm việc theo tuần</h2>
                <p className="mt-0.5 text-xs leading-5 text-slate-500">
                  Bật “Mở cửa”, chọn giờ hoạt động rồi lưu một lần cho cả tuần.
                </p>
              </div>
              <button
                type="button"
                onClick={() => void saveSchedule()}
                disabled={saving || loading}
                title="Lưu toàn bộ lịch làm việc theo tuần"
                className="primary-button shrink-0 px-3 py-2 text-sm disabled:opacity-50"
              >
                <Save size={16} /> {saving ? 'Đang lưu...' : 'Lưu lịch'}
              </button>
            </div>

            {loading ? <LoadingBlock /> : (
              <div className="overflow-x-auto">
                <table className="data-table calendar-aligned-table weekly-schedule-table w-full min-w-[620px] xl:min-w-0">
                  <colgroup>
                    <col className="calendar-col-stt" />
                    <col className="calendar-col-day" />
                    <col className="calendar-col-weekly-status" />
                    <col className="calendar-col-weekly-time" />
                    <col className="calendar-col-weekly-time" />
                  </colgroup>
                  <thead>
                    <tr className="border-b border-slate-200 bg-slate-50">
                      <th className="px-2 py-2 text-center text-[11px] font-semibold uppercase text-slate-500">STT</th>
                      <th className="px-2 py-2 text-left text-[11px] font-semibold uppercase text-slate-500">Ngày</th>
                      <th className="px-2 py-2 text-center text-[11px] font-semibold uppercase text-slate-500">Trạng thái</th>
                      <th className="px-2 py-2 text-center text-[11px] font-semibold uppercase text-slate-500">Mở cửa</th>
                      <th className="px-2 py-2 text-center text-[11px] font-semibold uppercase text-slate-500">Đóng cửa</th>
                    </tr>
                  </thead>
                  <tbody>
                    {schedule.map((item, index) => (
                      <tr
                        key={item.dayOfWeek}
                        className={`border-b border-slate-100 transition ${item.open ? 'hover:bg-blue-50/30' : 'bg-slate-50/60'}`}
                      >
                        <td className="px-2 py-2 text-center text-sm font-semibold text-slate-500">{index + 1}</td>
                        <td className="px-2 py-2 text-sm font-semibold text-slate-800">{item.dayLabel}</td>
                        <td className="px-2 py-2 text-center">
                          <label
                            className={`inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold transition ${
                              item.open
                                ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                                : 'border-slate-200 bg-white text-slate-600'
                            }`}
                            title={item.open ? 'Bỏ chọn để đóng cửa ngày này' : 'Chọn để mở cửa ngày này'}
                          >
                            <input
                              type="checkbox"
                              checked={item.open}
                              onChange={(event) => updateSchedule(item.dayOfWeek, { open: event.target.checked })}
                              className="h-3.5 w-3.5 rounded border-slate-300 text-blue-600"
                            />
                            <span>{item.open ? 'Mở cửa' : 'Đóng cửa'}</span>
                          </label>
                        </td>
                        <td className="px-2 py-2 text-center">
                          <input
                            type="time"
                            aria-label={`Giờ mở cửa ${item.dayLabel}`}
                            title={item.open ? `Chọn giờ mở cửa cho ${item.dayLabel}` : 'Bật trạng thái Mở cửa để chọn giờ'}
                            disabled={!item.open}
                            value={item.openTime ?? ''}
                            onChange={(event) => updateSchedule(item.dayOfWeek, { openTime: event.target.value })}
                            className="time-input mx-auto h-9 w-[116px] px-2 text-sm"
                          />
                        </td>
                        <td className="px-2 py-2 text-center">
                          <input
                            type="time"
                            aria-label={`Giờ đóng cửa ${item.dayLabel}`}
                            title={item.open ? `Chọn giờ đóng cửa cho ${item.dayLabel}` : 'Bật trạng thái Mở cửa để chọn giờ'}
                            disabled={!item.open}
                            value={item.closeTime ?? ''}
                            onChange={(event) => updateSchedule(item.dayOfWeek, { closeTime: event.target.value })}
                            className="time-input mx-auto h-9 w-[116px] px-2 text-sm"
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="calendar-panel min-w-0">
            <div className="calendar-panel-header flex flex-wrap items-start gap-3 border-b border-slate-200 px-4 py-3 xl:flex-nowrap">
              <div className="min-w-0 flex-1">
                <h2 className="font-semibold text-slate-900">Ngày nghỉ và ngày đóng cửa</h2>
                <p className="mt-0.5 text-xs leading-5 text-slate-500">
                  Thêm từng ngày hoặc khai báo nhiều ngày lễ cho cả năm.
                </p>
              </div>
              <div className="ml-auto flex shrink-0 flex-wrap items-center justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setBulkModal(true)}
                  title="Nhập nhanh nhiều ngày nghỉ cho cả năm"
                  className="secondary-button px-3 py-2 text-sm whitespace-nowrap"
                >
                  <CalendarDays size={16} /> Cả năm
                </button>
                <button
                  type="button"
                  onClick={openCreateHoliday}
                  title="Thêm một ngày nghỉ hoặc ngày đóng cửa"
                  className="primary-button px-3 py-2 text-sm whitespace-nowrap"
                >
                  <Plus size={16} /> Thêm ngày nghỉ
                </button>
              </div>
            </div>

            {loading ? <LoadingBlock /> : (
              <>
                <div className="closed-date-table-wrap overflow-x-auto xl:overflow-x-hidden">
                  <table className="data-table data-table-fit calendar-aligned-table closed-date-table w-full min-w-[560px] xl:min-w-0">
                    <colgroup>
                      <col className="calendar-col-stt" />
                      <col className="calendar-col-day" />
                      <col className="calendar-col-closed-weekday" />
                      <col className="calendar-col-closed-reason" />
                      <col className="calendar-col-closed-actions" />
                    </colgroup>
                    <thead>
                      <tr className="border-b border-slate-200 bg-slate-50">
                        <th className="px-2 py-2 text-center text-[11px] font-semibold uppercase text-slate-500">STT</th>
                        <th className="px-2 py-2 text-left text-[11px] font-semibold uppercase text-slate-500">Ngày</th>
                        <th className="px-2 py-2 text-left text-[11px] font-semibold uppercase text-slate-500">Thứ</th>
                        <th className="px-2 py-2 text-left text-[11px] font-semibold uppercase text-slate-500">Lý do</th>
                        <th className="px-2 py-2 text-center text-[11px] font-semibold uppercase text-slate-500">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      {closedDatePagination.pageItems.map((item, index) => (
                        <tr key={item.id} className="border-b border-slate-100 hover:bg-slate-50/80">
                          <td className="px-2 py-2 text-center text-sm font-semibold text-slate-500">
                            {closedDatePagination.startIndex + index + 1}
                          </td>
                          <td className="whitespace-nowrap px-2 py-2 text-sm font-semibold text-slate-800">{formatDateVi(item.closedDate)}</td>
                          <td className="whitespace-nowrap px-2 py-2 text-sm text-slate-600">{weekdayVi(item.closedDate)}</td>
                          <td className="table-cell-left px-2 py-2 text-sm text-slate-600">{item.reason}</td>
                          <td className="table-action-cell px-2 py-2">
                            <TableActions>
                              <TableActionButton
                                icon={<Pencil size={15} />}
                                tone="primary"
                                title="Chỉnh sửa ngày nghỉ"
                                onClick={() => openEditHoliday(item)}
                              >
                                Chỉnh sửa
                              </TableActionButton>
                              <TableActionButton
                                icon={<Trash2 size={15} />}
                                tone="danger"
                                title="Xóa ngày nghỉ"
                                onClick={() => void deleteHoliday(item)}
                              >
                                Xóa
                              </TableActionButton>
                            </TableActions>
                          </td>
                        </tr>
                      ))}
                      {closedDates.length === 0 && (
                        <tr>
                          <td colSpan={5} className="px-5 py-8 text-center text-sm text-slate-500">
                            Chưa có ngày nghỉ riêng. Bấm “Thêm ngày nghỉ” để khai báo ngày đầu tiên.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
                {closedDates.length > 0 && (
                  <TablePagination
                    page={closedDatePagination.page}
                    totalItems={closedDatePagination.totalItems}
                    totalPages={closedDatePagination.totalPages}
                    pageSize={closedDatePagination.pageSize}
                    onPageChange={closedDatePagination.goToPage}
                    className="calendar-pagination px-3 py-2"
                  />
                )}
              </>
            )}
          </section>
        </div>

        <section className="border-t border-slate-200 bg-slate-50/60 px-4 py-3">
          <div className="grid gap-3 lg:grid-cols-[auto_minmax(180px,240px)_auto_1fr] lg:items-end">
            <div className="lg:pb-2">
              <h2 className="font-semibold text-slate-900">Kiểm tra hạn trả</h2>
              <p className="mt-0.5 text-xs text-slate-500">Kiểm tra nhanh xem hạn trả có rơi vào ngày thư viện đóng cửa hay không.</p>
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold text-slate-600">Hạn trả dự kiến</label>
              <input
                type="date"
                value={dueDate}
                onChange={(event) => { setDueDate(event.target.value); setAdjustment(null) }}
                className="field-input h-10"
              />
            </div>
            <button
              type="button"
              onClick={() => void checkDueDate()}
              disabled={checkingDueDate}
              className="primary-button h-10 px-4 disabled:opacity-50"
            >
              {checkingDueDate ? 'Đang kiểm tra...' : 'Kiểm tra'}
            </button>
            <div className="min-h-10 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm">
              {!adjustment ? (
                <span className="text-slate-400">Kết quả kiểm tra sẽ hiển thị ở đây.</span>
              ) : adjustment.adjusted ? (
                <div>
                  <span className="font-semibold text-amber-700">{formatDateVi(adjustment.adjustedDate)} — chuyển sang ngày mở cửa kế tiếp</span>
                  <p className="mt-0.5 text-xs text-slate-500">Đã bỏ qua: {adjustment.skippedClosedDates.map(formatDateVi).join(', ')}</p>
                </div>
              ) : (
                <span className="font-semibold text-emerald-700">{formatDateVi(adjustment.adjustedDate)} — thư viện mở cửa</span>
              )}
            </div>
          </div>
        </section>
      </Card>

      {holidayModal && (
        <Modal title={editingHoliday ? 'Chỉnh sửa ngày nghỉ' : 'Thêm ngày nghỉ'} onClose={() => setHolidayModal(false)}>
          <form onSubmit={submitHoliday} className="space-y-5">
            <div>
              <label className="field-label">Ngày <span className="text-red-500">*</span></label>
              <input type="date" required value={holidayForm.closedDate} onChange={(event) => setHolidayForm({ ...holidayForm, closedDate: event.target.value })} className="field-input" />
            </div>
            <TextField label="Lý do đóng cửa" required value={holidayForm.reason} placeholder="Ví dụ: Nghỉ Quốc khánh" onChange={(reason) => setHolidayForm({ ...holidayForm, reason })} />
            {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
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
            {error && <FeedbackAlert message={error} tone="error" onDismiss={() => setError('')} />}
            <ModalActions saving={saving} submitText="Khai báo các ngày" onCancel={() => setBulkModal(false)} />
          </form>
        </Modal>
      )}
    </div>
  )
}

function Feedback({
  error,
  notice,
  onDismissError,
  onDismissNotice,
}: {
  error: string
  notice: string
  onDismissError: () => void
  onDismissNotice: () => void
}) {
  return (
    <>
      {error && <FeedbackAlert message={error} tone="error" onDismiss={onDismissError} />}
      {notice && <FeedbackAlert message={notice} tone="success" onDismiss={onDismissNotice} />}
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

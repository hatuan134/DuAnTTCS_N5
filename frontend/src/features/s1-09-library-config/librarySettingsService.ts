import { apiClient } from '../../core/api/apiClient'

export type WarehouseItem = {
  id: number
  code: string
  name: string
  description: string
  active: boolean
  shelfCount: number
  copyCount: number
  createdAt: string
  updatedAt: string
}

export type ShelfItem = {
  id: number
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  code: string
  name: string
  description: string
  active: boolean
  copyCount: number
  inUse: boolean
  createdAt: string
  updatedAt: string
}

export type WeeklyScheduleItem = {
  id: number
  dayOfWeek: number
  dayLabel: string
  open: boolean
  openTime: string | null
  closeTime: string | null
  updatedAt: string
  updatedBy: number | null
}

export type ClosedDateItem = {
  id: number
  closedDate: string
  reason: string
  createdAt: string
  updatedAt: string
  createdBy: number | null
}

export type DueDateAdjustment = {
  originalDate: string
  adjustedDate: string
  adjusted: boolean
  skippedClosedDates: string[]
}

export type WarehouseForm = {
  code: string
  name: string
  description: string
}

export type ShelfForm = {
  warehouseId: number
  code: string
  name: string
  description: string
}

export type WeeklySchedulePayload = {
  dayOfWeek: number
  open: boolean
  openTime: string | null
  closeTime: string | null
}

export type ClosedDateForm = {
  closedDate: string
  reason: string
}

export const librarySettingsService = {
  getWarehouses: async (): Promise<WarehouseItem[]> => {
    const response = await apiClient.get<WarehouseItem[]>('/library-settings/warehouses')
    return response.data
  },

  createWarehouse: async (data: WarehouseForm): Promise<WarehouseItem> => {
    const response = await apiClient.post<WarehouseItem>('/library-settings/warehouses', data)
    return response.data
  },

  updateWarehouse: async (id: number, data: WarehouseForm): Promise<WarehouseItem> => {
    const response = await apiClient.put<WarehouseItem>(`/library-settings/warehouses/${id}`, data)
    return response.data
  },

  getShelves: async (): Promise<ShelfItem[]> => {
    const response = await apiClient.get<ShelfItem[]>('/library-settings/shelves')
    return response.data
  },

  createShelf: async (data: ShelfForm): Promise<ShelfItem> => {
    const response = await apiClient.post<ShelfItem>('/library-settings/shelves', data)
    return response.data
  },

  updateShelf: async (id: number, data: ShelfForm): Promise<ShelfItem> => {
    const response = await apiClient.put<ShelfItem>(`/library-settings/shelves/${id}`, data)
    return response.data
  },

  deleteShelf: async (id: number): Promise<void> => {
    await apiClient.delete(`/library-settings/shelves/${id}`)
  },

  getWeeklySchedule: async (): Promise<WeeklyScheduleItem[]> => {
    const response = await apiClient.get<WeeklyScheduleItem[]>('/library-settings/weekly-schedule')
    return response.data
  },

  updateWeeklySchedule: async (days: WeeklySchedulePayload[]): Promise<WeeklyScheduleItem[]> => {
    const response = await apiClient.put<WeeklyScheduleItem[]>('/library-settings/weekly-schedule', { days })
    return response.data
  },

  getClosedDates: async (year?: number): Promise<ClosedDateItem[]> => {
    const response = await apiClient.get<ClosedDateItem[]>('/library-settings/closed-dates', {
      params: year ? { year } : undefined,
    })
    return response.data
  },

  createClosedDate: async (data: ClosedDateForm): Promise<ClosedDateItem> => {
    const response = await apiClient.post<ClosedDateItem>('/library-settings/closed-dates', data)
    return response.data
  },

  updateClosedDate: async (id: number, data: ClosedDateForm): Promise<ClosedDateItem> => {
    const response = await apiClient.put<ClosedDateItem>(`/library-settings/closed-dates/${id}`, data)
    return response.data
  },

  deleteClosedDate: async (id: number): Promise<void> => {
    await apiClient.delete(`/library-settings/closed-dates/${id}`)
  },

  createClosedDatesBulk: async (dates: ClosedDateForm[]): Promise<ClosedDateItem[]> => {
    const response = await apiClient.post<ClosedDateItem[]>('/library-settings/closed-dates/bulk', { dates })
    return response.data
  },

  adjustDueDate: async (date: string): Promise<DueDateAdjustment> => {
    const response = await apiClient.get<DueDateAdjustment>('/library-settings/adjust-due-date', {
      params: { date },
    })
    return response.data
  },
}

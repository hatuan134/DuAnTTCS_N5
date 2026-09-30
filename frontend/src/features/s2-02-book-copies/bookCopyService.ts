import axios from 'axios'
import { apiClient } from '../../core/api/apiClient'
import type { Book } from '../s1-08-catalog/catalogService'

export const physicalConditions = {
  NEW: 'Mới', GOOD: 'Tốt', OLD: 'Cũ',
  LIGHTLY_DAMAGED: 'Hư hỏng nhẹ', HEAVILY_DAMAGED: 'Hư hỏng nặng',
} as const
export type PhysicalCondition = keyof typeof physicalConditions

export interface BookCopy {
  id: number
  barcode: string
  bookId: number
  bookTitle: string
  isbn: string | null
  warehouseId: number
  warehouseCode: string
  warehouseName: string
  shelfId: number
  shelfCode: string
  shelfName: string | null
  receivedDate: string | null
  coverPrice: number | null
  physicalCondition: PhysicalCondition | null
  physicalConditionLabel: string
  status: string
  statusLabel: string
}
export interface CreateBookCopy {
  barcode: string
  warehouseId: number
  shelfId: number
  receivedDate: string
  coverPrice: string
  physicalCondition: PhysicalCondition
}
export interface DuplicateCopy {
  existingCopyId: number
  barcode: string
  bookId: number
  bookTitle: string
}
interface ApiError {
  message?: string
  code?: string
  details?: DuplicateCopy
}
export function copyError(error: unknown): { message: string; duplicate?: DuplicateCopy } {
  if (axios.isAxiosError<ApiError>(error)) {
    return {
      message: error.response?.data?.message || 'Không thể kết nối máy chủ. Vui lòng thử lại.',
      duplicate: error.response?.data?.code === 'BARCODE_EXISTS' ? error.response.data.details : undefined,
    }
  }
  return { message: 'Không thể thực hiện thao tác. Vui lòng thử lại.' }
}
export const bookCopyService = {
  async getBook(id: number): Promise<Book> {
    return (await apiClient.get<Book>(`/books/${id}`)).data
  },
  async create(bookId: number, data: CreateBookCopy): Promise<BookCopy> {
    return (await apiClient.post<BookCopy>(`/books/${bookId}/copies`, data)).data
  },
  async getCopy(id: number): Promise<BookCopy> {
    return (await apiClient.get<BookCopy>(`/book-copies/${id}`)).data
  },
}
export function todayInVietnam(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(new Date())
  const part = (name: string) => parts.find(p => p.type === name)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}
export function validReceivedDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || value < '0001-01-01' || value > todayInVietnam()) return false
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value
}

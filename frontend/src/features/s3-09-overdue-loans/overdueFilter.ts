import type { OverdueLoanItem } from './overdueLoanService'

/** Bộ lọc áp dụng trên số NGÀY MỞ CỬA do backend đã tính, không tự tính ngày lịch. */
export interface OverdueDaysFilter {
  minimum: number | null
  maximum: number | null
  exclusiveMinimum: boolean
}

export type OverdueFilterValidation =
  | { ok: true; filter: OverdueDaysFilter }
  | { ok: false; message: string }

function parseDay(value: string): number | null | undefined {
  const trimmed = value.trim()
  if (!trimmed) return null
  if (!/^\d+$/.test(trimmed)) return undefined
  const parsed = Number(trimmed)
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : undefined
}

export function validateOverdueFilter(
  minimumInput: string,
  maximumInput: string,
  exclusiveMinimum: boolean,
): OverdueFilterValidation {
  const minimum = parseDay(minimumInput)
  const maximum = parseDay(maximumInput)

  if (minimum === undefined || maximum === undefined) {
    return { ok: false, message: 'Số ngày trễ phải là số nguyên không âm hợp lệ.' }
  }

  const exclusive = minimum !== null && exclusiveMinimum
  if (minimum !== null && maximum !== null &&
    (minimum > maximum || (exclusive && minimum >= maximum))) {
    return {
      ok: false,
      message: exclusive
        ? 'Khoảng lọc không hợp lệ: số ngày trễ tối đa phải lớn hơn mức “trên” đã nhập.'
        : 'Khoảng lọc không hợp lệ: số ngày trễ tối thiểu không được lớn hơn tối đa.',
    }
  }

  return { ok: true, filter: { minimum, maximum, exclusiveMinimum: exclusive } }
}

export function filterOverdueLoans(
  items: OverdueLoanItem[],
  filter: OverdueDaysFilter | null,
): OverdueLoanItem[] {
  return items.filter((item) => {
    if (!filter) return true
    if (filter.minimum !== null &&
      (filter.exclusiveMinimum ? item.overdueDays <= filter.minimum : item.overdueDays < filter.minimum)) {
      return false
    }
    return filter.maximum === null || item.overdueDays <= filter.maximum
  }).sort((first, second) => second.overdueDays - first.overdueDays)
}

/** API trả về từng bản sách quá hạn. Một phiếu có thể có nhiều bản sách. */
export function countOverdueLoanVouchers(items: OverdueLoanItem[]): number {
  return new Set(items.map((item) => item.loanId)).size
}

export function describeOverdueFilter(filter: OverdueDaysFilter | null): string {
  if (!filter || (filter.minimum === null && filter.maximum === null)) return 'Tất cả mức quá hạn'
  if (filter.minimum !== null && filter.maximum !== null) {
    return `${filter.exclusiveMinimum ? 'Trên ' : 'Từ '}${filter.minimum} đến ${filter.maximum} ngày mở cửa`
  }
  if (filter.minimum !== null) {
    return `${filter.exclusiveMinimum ? 'Trên ' : 'Từ '}${filter.minimum} ngày mở cửa`
  }
  return `Tối đa ${filter.maximum} ngày mở cửa`
}

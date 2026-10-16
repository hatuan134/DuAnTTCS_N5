import { useEffect, useMemo, useState } from 'react'

export const TABLE_PAGE_SIZE = 10

export default function useTablePagination<T>(
  items: readonly T[],
  resetKey = '',
  pageSize = TABLE_PAGE_SIZE,
) {
  const [page, setPage] = useState(1)
  const totalItems = items.length
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize))
  const safePage = Math.min(Math.max(page, 1), totalPages)
  const startIndex = (safePage - 1) * pageSize

  useEffect(() => {
    setPage(1)
  }, [resetKey])

  useEffect(() => {
    setPage((current) => Math.min(Math.max(current, 1), totalPages))
  }, [totalPages])

  const pageItems = useMemo(
    () => items.slice(startIndex, startIndex + pageSize),
    [items, pageSize, startIndex],
  )

  const goToPage = (nextPage: number) => {
    setPage(Math.min(Math.max(nextPage, 1), totalPages))
  }

  return {
    page: safePage,
    pageItems,
    pageSize,
    startIndex,
    totalItems,
    totalPages,
    goToPage,
  }
}

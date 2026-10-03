import { apiClient } from '../../core/api/apiClient'

export const MAX_COVER_BYTES = 3 * 1024 * 1024
export const COVER_RULE = 'Chỉ chấp nhận ảnh JPG hoặc PNG hợp lệ, dung lượng tối đa 3MB (3.145.728 byte).'

export function publicCoverUrl(url: string): string {
  const prefix = '/api/v1/'
  if (!url.startsWith(prefix)) return url
  return `${(apiClient.defaults.baseURL ?? '/api/v1').replace(/\/$/, '')}/${url.slice(prefix.length)}`
}

export async function uploadBookCover(bookId: number, file: File): Promise<void> {
  const data = new FormData()
  data.append('file', file)
  // Remove the JSON default; the browser supplies the multipart boundary.
  await apiClient.post(`/books/${bookId}/cover`, data, { headers: { 'Content-Type': undefined } })
}

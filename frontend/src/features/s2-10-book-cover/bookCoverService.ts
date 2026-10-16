import { apiClient } from '../../core/api/apiClient'

export const MAX_COVER_BYTES = 3 * 1024 * 1024
export const COVER_RULE = 'Chỉ chấp nhận ảnh JPG hoặc PNG hợp lệ, dung lượng tối đa 3MB (3.145.728 byte).'

export function publicCoverUrl(url: string): string {
  const prefix = '/api/v1/'
  if (!url.startsWith(prefix)) return url
  return `${(apiClient.defaults.baseURL ?? '/api/v1').replace(/\/$/, '')}/${url.slice(prefix.length)}`
}

/** Recognize both legacy URLs and URLs carrying a cache version. */
export function managedCoverPath(bookId: number, url: string): string | null {
  const path = `/api/v1/books/public/${bookId}/cover`
  return url === path || url.startsWith(`${path}?`) ? url : null
}

export function publicBookImageUrl(bookId: number, url: string, thumbnail: boolean): string {
  const managed = managedCoverPath(bookId, url)
  if (!thumbnail || !managed) return publicCoverUrl(url)
  const queryAt = managed.indexOf('?')
  const path = queryAt < 0 ? managed : managed.slice(0, queryAt)
  const query = queryAt < 0 ? '' : managed.slice(queryAt)
  return publicCoverUrl(`${path}/thumbnail${query}`)
}

export async function uploadBookCover(bookId: number, file: File): Promise<void> {
  const data = new FormData()
  data.append('file', file)
  // Remove the JSON default; the browser supplies the multipart boundary.
  await apiClient.post(`/books/${bookId}/cover`, data, { headers: { 'Content-Type': undefined } })
  // Reuse the catalog's existing channel; notify only after the transaction succeeded.
  window.dispatchEvent(new Event('catalog-cover-updated'))
  try {
    if ('BroadcastChannel' in window) {
      const channel = new BroadcastChannel('catalog-availability')
      channel.postMessage({ type: 'cover-updated', bookId })
      channel.close()
    }
  } catch {
    // Catalog polling/focus refresh still works when cross-tab messaging is unavailable.
  }
}

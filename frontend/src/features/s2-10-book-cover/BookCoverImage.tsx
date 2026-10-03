import { useEffect, useState } from 'react'
import { apiClient } from '../../core/api/apiClient'
import { publicCoverUrl } from './bookCoverService'

export default function BookCoverImage({ bookId, url, title }: {
  bookId: number; url: string; title: string
}) {
  const [src, setSrc] = useState('')
  const [error, setError] = useState(false)
  useEffect(() => {
    let active = true
    let objectUrl = ''
    setSrc('')
    setError(false)
    if (url === `/api/v1/books/public/${bookId}/cover`) {
      // Staff must also see covers of books that have no copies and are not public yet.
      void apiClient.get<Blob>(`/books/${bookId}/cover`, { responseType: 'blob' }).then(({ data }) => {
        if (!active) return
        objectUrl = URL.createObjectURL(data)
        setSrc(objectUrl)
      }).catch(() => { if (active) setError(true) })
    } else {
      setSrc(publicCoverUrl(url))
    }
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [bookId, url])

  if (error) return <p role="alert" className="mt-3 text-sm text-red-700">Chưa tải được ảnh bìa. Vui lòng tải lại trang.</p>
  if (!src) return <p role="status" className="mt-3 text-sm text-slate-500">Đang tải ảnh bìa…</p>
  return <img src={src} alt={`Ảnh bìa ${title}`} onError={() => setError(true)}
    className="mt-4 max-h-80 max-w-full rounded-lg border border-slate-200 object-contain" />
}

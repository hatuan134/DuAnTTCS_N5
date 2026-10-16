import AccessibleModal from '../../components/ui/Modal'
import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ImagePlus, Upload, X } from 'lucide-react'
import Button from '../../components/ui/Button'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import type { Book } from '../s1-08-catalog/catalogService'
import { copyError } from '../s2-02-book-copies/bookCopyService'
import BookCoverImage from './BookCoverImage'
import { COVER_RULE, MAX_COVER_BYTES, uploadBookCover } from './bookCoverService'

interface Props {
  book: Book
  onClose: () => void
  onSaved: (message: string) => void
}

export default function BookCoverEditorDialog({ book, onClose, onSaved }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const pendingRef = useRef(false)

  useEffect(() => {
    if (!file) {
      setPreviewUrl('')
      return
    }

    const objectUrl = URL.createObjectURL(file)
    setPreviewUrl(objectUrl)
    return () => URL.revokeObjectURL(objectUrl)
  }, [file])

  function chooseFile(selected: File | null, input: HTMLInputElement) {
    setError('')
    setFile(null)

    if (!selected) return

    const allowedExtension = /\.(jpe?g|png)$/i.test(selected.name)
    const allowedMime = !selected.type || selected.type === 'image/jpeg' || selected.type === 'image/png'
    if (!allowedExtension || !allowedMime || selected.size === 0 || selected.size > MAX_COVER_BYTES) {
      setError(COVER_RULE)
      input.value = ''
      return
    }

    setFile(selected)
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!file || saving || pendingRef.current) return

    pendingRef.current = true
    setSaving(true)
    setError('')

    try {
      await uploadBookCover(book.id, file)
      onSaved(book.coverImageUrl ? 'Thay thế ảnh bìa thành công.' : 'Tải ảnh bìa thành công.')
    } catch (requestError) {
      setError(copyError(requestError).message)
    } finally {
      pendingRef.current = false
      setSaving(false)
    }
  }

  return (
    <AccessibleModal onClose={onClose} busy={saving} labelledBy="book-cover-editor-title">
      <div className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-2xl border border-slate-200 bg-white shadow-2xl">
        <div className="sticky top-0 z-10 flex items-start justify-between gap-4 border-b border-slate-200 bg-white px-6 py-5">
          <div>
            <div className="flex items-center gap-2 text-blue-700">
              <ImagePlus size={19} />
              <span className="text-sm font-semibold">Ảnh bìa đầu sách</span>
            </div>
            <h2 id="book-cover-editor-title" className="mt-1 text-xl font-bold text-slate-950">
              Chỉnh sửa ảnh bìa
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              {book.title} · Mã đầu sách #{book.id}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="rounded-lg p-2 text-slate-500 transition hover:bg-slate-100 hover:text-slate-800 disabled:opacity-50"
            aria-label="Đóng chỉnh sửa ảnh bìa"
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={submit} className="p-6">
          <div className="grid gap-6 lg:grid-cols-2">
            <section className="rounded-2xl border border-slate-200 bg-slate-50/70 p-5">
              <p className="text-sm font-semibold text-slate-800">Ảnh hiện tại</p>
              {book.coverImageUrl ? (
                <BookCoverImage bookId={book.id} url={book.coverImageUrl} title={book.title} />
              ) : (
                <div className="mt-4 flex min-h-64 items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white p-6 text-center text-sm text-slate-500">
                  Đầu sách chưa có ảnh bìa.
                </div>
              )}
            </section>

            <section className="rounded-2xl border border-blue-100 bg-blue-50/40 p-5">
              <p className="text-sm font-semibold text-slate-800">Ảnh sắp lưu</p>
              {previewUrl ? (
                <img
                  src={previewUrl}
                  alt={`Xem trước ảnh bìa mới của ${book.title}`}
                  className="mt-4 max-h-80 w-full rounded-xl border border-blue-200 bg-white object-contain p-2"
                />
              ) : (
                <div className="mt-4 flex min-h-64 flex-col items-center justify-center rounded-xl border border-dashed border-blue-200 bg-white p-6 text-center text-sm text-slate-500">
                  <Upload className="mb-3 text-blue-500" size={28} />
                  Chọn ảnh để xem preview trước khi lưu.
                </div>
              )}
            </section>
          </div>

          <div className="mt-6">
            <label htmlFor={`book-cover-file-${book.id}`} className="block text-sm font-semibold text-slate-800">
              Chọn ảnh bìa mới <span className="text-red-600">*</span>
            </label>
            <p className="mt-1 text-sm text-slate-500">{COVER_RULE}</p>
            {book.coverImageUrl && (
              <p className="mt-1 text-sm text-slate-500">
                Ảnh hiện tại chỉ được thay sau khi ảnh mới lưu thành công.
              </p>
            )}
            <input
              id={`book-cover-file-${book.id}`}
              type="file"
              accept=".jpg,.jpeg,.png,image/jpeg,image/png"
              disabled={saving}
              className="mt-3 block w-full rounded-xl border border-slate-300 bg-white p-3 text-sm file:mr-4 file:rounded-lg file:border-0 file:bg-blue-50 file:px-4 file:py-2 file:font-semibold file:text-blue-700"
              onChange={(event) => chooseFile(event.target.files?.[0] ?? null, event.currentTarget)}
            />
          </div>

          {file && (
            <p className="mt-3 break-all text-sm text-slate-600">
              Đã chọn: <strong>{file.name}</strong> ({file.size.toLocaleString('vi-VN')} byte)
            </p>
          )}

          {error && (
            <FeedbackAlert
              message={error}
              tone="error"
              onDismiss={() => setError('')}
              className="mt-4"
            />
          )}

          <div className="mt-6 flex flex-wrap justify-end gap-3 border-t border-slate-100 pt-5">
            <Button type="button" variant="secondary" disabled={saving} onClick={onClose}>
              Hủy
            </Button>
            <Button type="submit" disabled={!file} loading={saving}>
              {book.coverImageUrl ? 'Thay thế ảnh bìa' : 'Lưu ảnh bìa'}
            </Button>
          </div>
        </form>
      </div>
    </AccessibleModal>
  )
}

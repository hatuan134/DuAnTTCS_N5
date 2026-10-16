import { useState } from 'react'
import { Link } from 'react-router-dom'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'
import { getCurrentUser } from '../../core/auth/authStorage'

export default function ReaderHistoryDenied() {
  const [notice, setNotice] = useState('Bạn không có quyền truy cập lịch sử mượn trả của Bạn đọc.')
  const canViewReaders = ['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN'].includes(getCurrentUser()?.role ?? '')

  return <div className="space-y-5">
    <PageHeader title="Lịch sử mượn trả Bạn đọc" description="Chức năng dành cho Quản lý thư viện và Thủ thư." />
    {notice && <FeedbackAlert message={notice} tone="error" onDismiss={() => setNotice('')} />}
    <Card className="p-5 sm:p-6">
      <p role="status" className="text-sm text-slate-700">Không thể mở lịch sử mượn trả với vai trò hiện tại.</p>
      <Link to={canViewReaders ? '/readers' : '/dashboard'}
        className="mt-4 inline-block text-sm font-medium text-blue-700 hover:underline">
        {canViewReaders ? 'Về danh sách Bạn đọc' : 'Về trang tổng quan'}
      </Link>
    </Card>
  </div>
}

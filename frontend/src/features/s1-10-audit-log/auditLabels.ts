import type { AuditLogItem } from './auditLogService'

// Display labels only. API action codes, filter values and stored logs stay unchanged.
const actionLabels: Record<string, string> = {
  "LOGIN_SUCCESS": "Đăng nhập thành công",
  "LOGIN_FAILED": "Đăng nhập thất bại",
  "ACCOUNT_TEMP_LOCKED": "Khóa tạm tài khoản",
  "USER_CREATED": "Tạo tài khoản",
  "USER_UPDATED": "Cập nhật tài khoản",
  "USER_DELETED": "Xóa tài khoản",
  "USER_STATUS_UPDATED": "Sửa trạng thái tài khoản",
  "INITIAL_PASSWORD_SET": "Thiết lập mật khẩu lần đầu",
  "PASSWORD_RESET_REQUESTED": "Yêu cầu đặt lại mật khẩu",
  "PASSWORD_RESET_COMPLETED": "Đặt lại mật khẩu thành công",
  "READER_CONTACT_UPDATED": "Cập nhật thông tin liên hệ",
  "READER_PASSWORD_CHANGED": "Đổi mật khẩu",
  "LIBRARY_CARD_ISSUED": "Cấp thẻ thư viện",
  "READER_APPLICATION_REJECTED": "Từ chối hồ sơ bạn đọc",
  "CARD_TYPE_CREATED": "Tạo chính sách mượn",
  "CARD_TYPE_UPDATED": "Sửa chính sách mượn",
  "CARD_TYPE_ACTIVATED": "Áp dụng chính sách mượn",
  "CARD_TYPE_DEACTIVATED": "Ngừng chính sách mượn",
  "CARD_TYPE_DELETED": "Xóa chính sách mượn",
  "READER_REGISTERED": "Đăng ký bạn đọc",
  "WAREHOUSE_CREATED": "Tạo kho",
  "WAREHOUSE_UPDATED": "Sửa kho",
  "SHELF_CREATED": "Tạo kệ",
  "SHELF_UPDATED": "Sửa kệ",
  "SHELF_DELETED": "Xóa kệ",
  "WEEKLY_SCHEDULE_UPDATED": "Sửa lịch làm việc",
  "CLOSED_DATE_CREATED": "Tạo ngày đóng cửa",
  "CLOSED_DATE_UPDATED": "Sửa ngày đóng cửa",
  "CLOSED_DATE_DELETED": "Xóa ngày đóng cửa",
  "AUTHOR_CREATED": "Thêm tác giả",
  "AUTHOR_UPDATED": "Cập nhật tác giả",
  "AUTHOR_ACTIVATED": "Kích hoạt tác giả",
  "AUTHOR_DEACTIVATED": "Ngừng sử dụng tác giả",
  "AUTHOR_DELETED": "Xóa tác giả",
  "CATEGORY_CREATED": "Thêm thể loại",
  "CATEGORY_UPDATED": "Cập nhật thể loại",
  "CATEGORY_ACTIVATED": "Kích hoạt thể loại",
  "CATEGORY_DEACTIVATED": "Ngừng sử dụng thể loại",
  "CATEGORY_DELETED": "Xóa thể loại",
  "BOOK_CATALOGED": "Biên mục đầu sách",
  "CLOSED_DATES_BULK_CREATED": "Thêm nhiều ngày nghỉ",
  "LOGIN": "Đăng nhập",
  "CREATE_ACCOUNT": "Tạo tài khoản",
  "UPDATE_ACCOUNT": "Sửa tài khoản",
  "ISSUE_CARD": "Cấp thẻ",
  "UPDATE_POLICY": "Sửa chính sách mượn",
  "OTHER": "Hoạt động khác"
}

const entityLabels: Record<string, string> = {
  "USER": "Tài khoản người dùng",
  "LOGIN_ATTEMPT": "Phiên đăng nhập",
  "LIBRARY_CARD": "Thẻ thư viện",
  "CARD_TYPE": "Chính sách mượn",
  "READER_PROFILE": "Hồ sơ bạn đọc",
  "WAREHOUSE": "Kho",
  "SHELF": "Kệ",
  "LIBRARY_WEEKLY_SCHEDULE": "Lịch làm việc",
  "LIBRARY_CLOSED_DATE": "Ngày đóng cửa",
  "BOOK": "Đầu sách",
  "AUTHOR": "Tác giả",
  "CATEGORY": "Thể loại"
}

export function auditActionLabel(action: string, serverLabel?: string) {
  if (Object.hasOwn(actionLabels, action)) return actionLabels[action]
  // Keep a human-readable server label for future actions. Raw codes remain in the detail dialog.
  if (serverLabel?.trim() && serverLabel !== action && !/^[A-Z][A-Z_0-9]*$/.test(serverLabel)) return serverLabel
  return 'Hoạt động khác'
}

export function auditEntityLabel(value: string) {
  return Object.hasOwn(entityLabels, value) ? entityLabels[value] : value
}

export function auditTargetLabel(item: Pick<AuditLogItem, 'target' | 'entityType' | 'targetType'>) {
  const entity = item.entityType || item.targetType
  if (!Object.hasOwn(entityLabels, entity)) return item.target
  if (item.target === entity) return entityLabels[entity]
  if (item.target.startsWith(`${entity} #`)) return entityLabels[entity] + item.target.slice(entity.length)
  return item.target
}

export function auditDetailLabel(item: Pick<AuditLogItem, 'detail' | 'action' | 'actionLabel'>) {
  if (item.detail === item.action || item.detail === `${item.action}.`) {
    return `${auditActionLabel(item.action, item.actionLabel)}.`
  }
  return item.detail
}

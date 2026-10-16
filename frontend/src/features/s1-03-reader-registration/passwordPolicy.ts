/** Quy tắc mật khẩu cho form đăng ký bạn đọc (S1-03). */
export function validateRegistrationPassword(password: string): string | null {
  if (password.length < 8) {
    return 'Mật khẩu phải có tối thiểu 8 ký tự.'
  }
  if (!/\p{L}/u.test(password) || !/\p{N}/u.test(password)) {
    return 'Mật khẩu phải có ít nhất một chữ cái và một chữ số.'
  }
  return null
}

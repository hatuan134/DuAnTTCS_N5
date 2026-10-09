// Backend remains the authority; these checks control navigation only.
export function canViewReaderLoanHistory(role?: string): boolean {
  return role === 'LIBRARIAN' || role === 'LIBRARY_MANAGER'
}

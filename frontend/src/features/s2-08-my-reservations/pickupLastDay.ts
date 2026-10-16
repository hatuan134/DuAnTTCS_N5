export function vietnamDateKey(value: number | Date = Date.now()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(value)
  const part = (name: string) => parts.find((item) => item.type === name)?.value ?? ''
  return `${part('year')}-${part('month')}-${part('day')}`
}

export function isLastDayPickup(status: string, deadline: string | null, now = Date.now()): boolean {
  if (status !== 'READY_FOR_PICKUP' || !deadline) return false
  const instant = new Date(deadline).getTime()
  return Number.isFinite(instant) && instant > now && vietnamDateKey(instant) === vietnamDateKey(now)
}

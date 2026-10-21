import { useCallback, useEffect, useRef, useState } from 'react'
import type { PointerEvent as ReactPointerEvent } from 'react'
import { useLocation } from 'react-router-dom'
import { BookOpen, Sparkles, Send, X, Maximize2, Minimize2, Plus, Trash2, RotateCcw, ShieldCheck, History, PanelRightClose, BarChart3, ChevronDown } from 'lucide-react'
import { apiClient } from '../../core/api/apiClient'
import { getCurrentUser, getAccessToken } from '../../core/auth/authStorage'
import { ensureValidSession } from '../../core/auth/authService'
import LibraLogo from '../../components/brand/LibraLogo'
import AiBlocks from './AiBlocks'
import SafeMarkdown from './SafeMarkdown'
import type { AiResponse, ChatMessage, AiBlock } from './types'
import './assistant.css'

type LauncherPosition = { x: number; y: number }
const LAUNCHER_POSITION_KEY = 'libra-ai-launcher-position-v1'
const constrainLauncher = (position: LauncherPosition, width = 56, height = 56): LauncherPosition => ({
  x: Math.min(Math.max(8, position.x), Math.max(8, window.innerWidth - width - 8)),
  y: Math.min(Math.max(8, position.y), Math.max(8, window.innerHeight - height - 8)),
})
function readLauncherPosition(): LauncherPosition | null {
  try {
    const raw = window.localStorage.getItem(LAUNCHER_POSITION_KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || !('x' in parsed) || !('y' in parsed)) return null
    const point = parsed as { x: unknown; y: unknown }
    if (typeof point.x !== 'number' || typeof point.y !== 'number' || !Number.isFinite(point.x) || !Number.isFinite(point.y)) return null
    return constrainLauncher({ x: point.x, y: point.y })
  } catch { return null }
}

const identity = () => { const u = getCurrentUser(); return u ? `${u.id}:${u.role}` : 'PUBLIC' }
const roleNames: Record<string, string> = { PUBLIC: 'Khách thư viện', READER: 'Bạn đọc', LIBRARIAN: 'Thủ thư', LIBRARY_MANAGER: 'Quản lý thư viện', ADMIN: 'Quản trị viên' }
const suggestions: Record<string, string[]> = {
  PUBLIC: ['Gợi ý 3 cuốn sách về lập trình Java', 'Thư viện có sách nào đang sẵn sàng?', 'Hướng dẫn tôi đăng ký bạn đọc'],
  READER: ['Tôi đang mượn sách nào và hạn trả khi nào?', 'Tôi có sách quá hạn không?', 'Xem các đơn đặt giữ của tôi', 'Kiểm tra hạn thẻ thư viện'],
  LIBRARIAN: ['Cho tôi xem các phiếu mượn quá hạn', 'Có những loại thẻ và chính sách mượn nào?', 'Các kho sách hiện có bao nhiêu bản sao?'],
  LIBRARY_MANAGER: ['Thống kê mượn trả thư viện trong tháng này', 'Các kho và kệ đang có bao nhiêu bản sao?', 'Xem chính sách mượn và phí quá hạn hiện hành'],
  ADMIN: ['Tôi có thể tra cứu những chức năng nào?', 'Tìm sách xuất bản năm 2026', 'Thống kê thẻ thư viện theo trạng thái'],
}

/** Context suggestions are advisory only: all data access is still enforced server-side. */
function suggestionsForPage(role: string, pathname: string): string[] {
  if (pathname === '/catalog') return [
    'Tìm 3 đầu sách lập trình Java đang sẵn sàng',
    'Gợi ý sách công nghệ thông tin phù hợp cho người mới',
    role === 'PUBLIC' ? 'Hướng dẫn đăng ký thẻ thư viện' : 'Tìm sách theo chủ đề tôi quan tâm',
  ]
  if (role === 'READER' && pathname === '/my-borrowed-books') return [
    'Tôi đang mượn sách nào và hạn trả khi nào?',
    'Tôi có sách quá hạn không?',
    'Xem lịch sử sách đã trả của tôi',
  ]
  if (role === 'READER' && pathname === '/my-reservations') return [
    'Xem các đơn đặt giữ của tôi',
    'Các đơn đặt giữ của tôi đang ở trạng thái nào?',
    'Tìm sách khác đang sẵn sàng để đọc',
  ]
  if (['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(role) && pathname === '/borrow-policy') return [
    'Các loại thẻ đang áp dụng chính sách mượn nào?',
    'Mỗi loại thẻ được mượn tối đa bao nhiêu sách?',
    'Phí trả sách quá hạn hiện tại là bao nhiêu?',
  ]
  if (['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(role) && pathname === '/library-cards') return [
    'Có bao nhiêu thẻ thư viện đang hoạt động và bị khóa?',
    'Những thẻ bị khóa thuộc về bạn đọc nào?',
    'Loại thẻ nào cho phép mượn nhiều sách nhất?',
  ]
  if (['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(role) && pathname === '/warehouse-shelves') return [
    'Hệ thống có bao nhiêu kho sách, mỗi kho có bao nhiêu bản sao?',
    'Liệt kê các kệ sách và số bản sao trên từng kệ',
    'Tìm các đầu sách xuất bản năm 2026',
  ]
  if (['LIBRARIAN', 'LIBRARY_MANAGER', 'ADMIN'].includes(role) && pathname === '/loans/overdue') return [
    'Cho tôi xem danh sách phiếu quá hạn',
    'Mở các phiếu quá hạn trên 7 ngày mở cửa',
    'Mở trang tra cứu phiếu mượn',
  ]
  if (['LIBRARY_MANAGER', 'ADMIN'].includes(role) && pathname === '/reports/loans') return [
    'Thống kê mượn trả thư viện trong tháng này',
    'Mở báo cáo mượn trả chi tiết',
    'Cho tôi xem các phiếu quá hạn',
  ]
  return suggestions[role] || suggestions.PUBLIC
}

const welcomes: Record<string, { title: string; description: string }> = {
  PUBLIC: { title: 'Hôm nay bạn muốn khám phá cuốn sách nào?', description: 'Tìm sách đang có trong thư viện và xem hướng dẫn đăng ký, mượn trả.' },
  READER: { title: 'Tôi có thể hỗ trợ việc đọc sách nào?', description: 'Tra cứu sách đang mượn, hạn trả, thẻ và các đơn đặt giữ của riêng bạn.' },
  LIBRARIAN: { title: 'Bạn cần hỗ trợ nghiệp vụ nào?', description: 'Tra cứu phiếu, bản sao và mở nhanh chức năng mượn trả tại quầy.' },
  LIBRARY_MANAGER: { title: 'Bạn muốn theo dõi hoạt động nào?', description: 'Tổng hợp báo cáo, theo dõi quá hạn và nắm bắt hoạt động thư viện.' },
  ADMIN: { title: 'Bạn muốn quản trị mục nào hôm nay?', description: 'Điều hướng quản lý tài khoản, nhật ký và cấu hình thư viện trong phạm vi được cấp quyền.' },
}

/** Server-sent events over authenticated POST; does not expose Groq/Gemini credentials. */
async function requestStreaming(
  payload: { message: string; conversationId: string | null; page: string },
  signal: AbortSignal,
  onDelta: (chunk: string) => void,
): Promise<AiResponse> {
  const base = String(apiClient.defaults.baseURL || '/api/v1').replace(/\/$/, '')
  const url = new URL(`${base}/ai/chat/stream`, window.location.origin)
  const accessToken = getAccessToken()
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) },
    body: JSON.stringify(payload), signal, cache: 'no-store',
  })
  if (response.status === 404 || response.status === 405 || response.status === 406) {
    // Compatibility with servers still running the previous AI patch.
    const old = await apiClient.post<AiResponse>('/ai/chat', payload, { signal, timeout: 65000 })
    return old.data
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as { message?: string }
    throw new Error(body.message || `LIBRA AI đang không khả dụng (HTTP ${response.status}).`)
  }
  if (!response.body) throw new Error('Trình duyệt không hỗ trợ đọc phản hồi trực tuyến.')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let result: AiResponse | null = null
  const parseEvent = (raw: string) => {
    const fields = raw.split('\n')
    const type = fields.find(line => line.startsWith('event:'))?.slice(6).trim()
    const data = fields.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
    if (!type || !data) return
    const value = JSON.parse(data) as { text?: string; message?: string; code?: string }
    if (type === 'delta' && typeof value.text === 'string') onDelta(value.text)
    if (type === 'done') result = JSON.parse(data) as AiResponse
    if (type === 'error') throw new Error(value.message || 'Không thể xử lý yêu cầu AI. Vui lòng thử lại sau.')
  }
  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
      if (buffer.length > 256_000) throw new Error('Phản hồi AI vượt giới hạn an toàn.')
      let position: number
      while ((position = buffer.indexOf('\n\n')) >= 0) {
        parseEvent(buffer.slice(0, position))
        buffer = buffer.slice(position + 2)
      }
    }
    if (!result) throw new Error('Kết nối AI bị ngắt trước khi nhận đủ kết quả.')
    return result
  } finally { reader.releaseLock() }
}

type HistorySummary = { id: string; title: string; updatedAt: string }
type HistoryDetail = { id: string; title: string; turnsRemaining: number; messages: { role: 'user' | 'assistant'; text: string; blocks: AiBlock[] }[] }

/** The assistant always speaks first. Data-heavy blocks are progressive disclosure,
 * and remain attached to the message when the user resumes a saved conversation. */
function BriefAnswer({ message }: { message: ChatMessage }) {
  return <SafeMarkdown text={message.text} />
}

const resultLabel: Record<string, string> = {
  books: 'Xem sách và tình trạng sẵn sàng',
  loans: 'Xem danh sách phiếu và sách mượn',
  reservations: 'Xem đơn đặt giữ và hạn nhận',
  libraryCard: 'Xem thông tin thẻ thư viện',
  copies: 'Xem chi tiết bản sao',
  statistics: 'Xem biểu đồ và số liệu chi tiết',
  overview: 'Xem chỉ số tổng quan',
  libraryCalendar: 'Xem lịch thư viện và ngày đóng cửa chi tiết',
  settings: 'Xem cấu hình ngưỡng nợ phí',
  records: 'Xem danh sách chi tiết',
  audit: 'Xem nhật ký hoạt động',
  notice: 'Xem thông báo chi tiết',
}
function DisclosureResult({ block, role, onNavigate }: { block: AiBlock; role: string; onNavigate: () => void }) {
  const [expanded, setExpanded] = useState(false)
  return <details className="lai-report-disclosure" open={expanded} onToggle={event => setExpanded(event.currentTarget.open)}>
    <summary>{['statistics', 'overview'].includes(block.type) ? <BarChart3 size={17} aria-hidden="true" /> : <BookOpen size={17} aria-hidden="true" />}<span>{resultLabel[block.type] || 'Xem kết quả chi tiết'}</span><ChevronDown size={17} className="lai-report-chevron" aria-hidden="true" /></summary>
    {expanded && <div className="lai-report-body"><AiBlocks block={block} role={role} onNavigate={onNavigate} /></div>}
  </details>
}
function MessageResults({ message, role, onNavigate }: { message: ChatMessage; role: string; onNavigate: () => void }) {
  const blocks = (message.blocks || []).filter(block => !(block.type === 'notice' &&
    block.title === 'Chưa lấy được dữ liệu' && message.blocks?.some(item => item.type !== 'notice')))
  return <>{blocks.map((block, i) => block.type === 'navigation'
    ? <AiBlocks key={`${message.id}-${i}`} block={block} role={role} onNavigate={onNavigate} />
    : <DisclosureResult key={`${message.id}-${i}`} block={block} role={role} onNavigate={onNavigate} />)}</>
}

export default function AiAssistant() {
  const location = useLocation()
  const [actor, setActor] = useState(identity)
  const role = actor === 'PUBLIC' ? 'PUBLIC' : actor.split(':')[1]
  const [open, setOpen] = useState(false)
  const [large, setLarge] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [failed, setFailed] = useState('')
  const [remaining, setRemaining] = useState(Number.MAX_SAFE_INTEGER)
  const [historyVisible, setHistoryVisible] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [history, setHistory] = useState<HistorySummary[]>([])
  const [docked, setDocked] = useState(false)
  const [launcherPosition, setLauncherPosition] = useState<LauncherPosition | null>(readLauncherPosition)
  const [movingLauncher, setMovingLauncher] = useState(false)
  const activeDrag = useRef<{ pointerId: number; startX: number; startY: number; origin: LauncherPosition; moved: boolean } | null>(null)
  const ignoreLauncherClick = useRef(false)
  const conversation = useRef<string | null>(null)
  const generation = useRef(0)
  const request = useRef<AbortController | null>(null)
  const pending = useRef(false)
  const actorRef = useRef(actor)
  const counter = useRef(0)
  const launcher = useRef<HTMLButtonElement>(null)
  const textarea = useRef<HTMLTextAreaElement>(null)
  const end = useRef<HTMLDivElement>(null)
  const chatWindow = useRef<HTMLElement>(null)

  // Keep the mobile composer inside the visible area when the keyboard opens.
  useEffect(() => {
    if (!open) return
    const viewport = window.visualViewport
    const resize = () => {
      chatWindow.current?.style.setProperty('--lai-viewport-height', `${viewport?.height ?? window.innerHeight}px`)
      chatWindow.current?.style.setProperty('--lai-viewport-top', `${viewport?.offsetTop ?? 0}px`)
    }
    resize()
    viewport?.addEventListener('resize', resize)
    viewport?.addEventListener('scroll', resize)
    window.addEventListener('resize', resize)
    return () => {
      viewport?.removeEventListener('resize', resize)
      viewport?.removeEventListener('scroll', resize)
      window.removeEventListener('resize', resize)
    }
  }, [open])

  useEffect(() => {
    const onResize = () => setLauncherPosition(pos => pos ? constrainLauncher(pos) : null)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])
  const dragStart = (event: ReactPointerEvent<HTMLButtonElement>) => {
    if (event.button !== 0 || !event.isPrimary) return
    const rect = event.currentTarget.getBoundingClientRect()
    activeDrag.current = { pointerId: event.pointerId, startX: event.clientX, startY: event.clientY,
      origin: { x: rect.left, y: rect.top }, moved: false }
    event.currentTarget.setPointerCapture(event.pointerId)
  }
  const dragMove = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const drag = activeDrag.current
    if (!drag || drag.pointerId !== event.pointerId) return
    const dx = event.clientX - drag.startX
    const dy = event.clientY - drag.startY
    if (!drag.moved && Math.hypot(dx, dy) < 7) return
    drag.moved = true
    setMovingLauncher(true)
    setDocked(false)
    setLauncherPosition(constrainLauncher({ x: drag.origin.x + dx, y: drag.origin.y + dy },
      event.currentTarget.offsetWidth, event.currentTarget.offsetHeight))
  }
  const dragEnd = (event: ReactPointerEvent<HTMLButtonElement>) => {
    const drag = activeDrag.current
    if (!drag || drag.pointerId !== event.pointerId) return
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId)
    if (drag.moved) {
      const finalPosition = constrainLauncher({ x: drag.origin.x + event.clientX - drag.startX,
        y: drag.origin.y + event.clientY - drag.startY }, event.currentTarget.offsetWidth, event.currentTarget.offsetHeight)
      setLauncherPosition(finalPosition)
      try { window.localStorage.setItem(LAUNCHER_POSITION_KEY, JSON.stringify(finalPosition)) } catch { /* storage disabled */ }
      ignoreLauncherClick.current = true
      // Some touch browsers do not fire a click after drag: don't swallow the next deliberate tap.
      window.setTimeout(() => { ignoreLauncherClick.current = false }, 350)
    }
    activeDrag.current = null
    setMovingLauncher(false)
  }
  const resetLauncherPosition = () => {
    setLauncherPosition(null)
    setDocked(false)
    try { window.localStorage.removeItem(LAUNCHER_POSITION_KEY) } catch { /* storage disabled */ }
  }
  const clearLocal = useCallback(() => {
    generation.current++; request.current?.abort(); request.current = null; pending.current = false
    conversation.current = null; setMessages([]); setInput(''); setFailed(''); setNotice(''); setBusy(false); setRemaining(Number.MAX_SAFE_INTEGER)
  }, [])
  useEffect(() => {
    const changed = () => {
      const next = identity()
      if (next !== actorRef.current) {
        actorRef.current = next; clearLocal(); setActor(next); setOpen(false); setHistory([]); setHistoryVisible(false)
      }
    }
    const cancelCurrent = () => { generation.current++; request.current?.abort() }
    window.addEventListener('libra-session-changed', changed)
    window.addEventListener('storage', changed)
    return () => { window.removeEventListener('libra-session-changed', changed); window.removeEventListener('storage', changed); cancelCurrent() }
  }, [clearLocal])
  useEffect(() => { if (!notice) return; const timer = window.setTimeout(() => setNotice(''), 3000); return () => clearTimeout(timer) }, [notice])
  useEffect(() => { if (open && !historyVisible) textarea.current?.focus() }, [open, historyVisible])
  useEffect(() => { if (open && !historyVisible) end.current?.scrollIntoView({ block: 'nearest', behavior: 'instant' }) }, [messages, busy, open, historyVisible])
  const close = () => { setOpen(false); setHistoryVisible(false); requestAnimationFrame(() => launcher.current?.focus()) }
  const newChat = () => {
    if (pending.current) return
    // Starting a new conversation does NOT delete previous threads from history.
    clearLocal(); setHistoryVisible(false); textarea.current?.focus()
  }
  const loadHistory = useCallback(async () => {
    if (actorRef.current === 'PUBLIC') return
    const startingActor = actorRef.current
    setHistoryLoading(true)
    try {
      if (!await ensureValidSession()) throw new Error('Phiên đăng nhập đã hết hạn.')
      const response = await apiClient.get<HistorySummary[]>('/ai/history')
      if (startingActor === actorRef.current) setHistory(response.data)
    } catch {
      if (startingActor === actorRef.current) setNotice('Chưa tải được lịch sử. Hãy kiểm tra kết nối và thử lại.')
    } finally { if (startingActor === actorRef.current) setHistoryLoading(false) }
  }, [])
  const toggleHistory = () => {
    if (role === 'PUBLIC') return
    const next = !historyVisible; setHistoryVisible(next)
    if (next) void loadHistory()
  }
  const viewHistory = async (id: string) => {
    if (pending.current) return
    const startingActor = actorRef.current
    setHistoryLoading(true)
    try {
      const response = await apiClient.get<HistoryDetail>(`/ai/history/${encodeURIComponent(id)}`)
      if (startingActor !== actorRef.current) return
      clearLocal()
      conversation.current = response.data.id
      setRemaining(response.data.turnsRemaining)
      setMessages(response.data.messages.map(message => ({ id: ++counter.current, role: message.role, text: message.text, blocks: message.blocks })))
      setHistoryVisible(false)
    } catch {
      if (startingActor === actorRef.current) setNotice('Không thể mở cuộc trò chuyện này. Vui lòng thử lại.')
    } finally { if (startingActor === actorRef.current) setHistoryLoading(false) }
  }
  const deleteHistory = async (id: string) => {
    if (pending.current) return
    const startingActor = actorRef.current
    try {
      await apiClient.delete(`/ai/history/${encodeURIComponent(id)}`)
      if (startingActor !== actorRef.current) return
      setHistory(previous => previous.filter(item => item.id !== id))
      if (conversation.current === id) clearLocal()
    } catch { if (startingActor === actorRef.current) setNotice('Không thể xóa cuộc trò chuyện. Vui lòng thử lại.') }
  }
  const send = async (content: string, retry = false) => {
    const message = content.trim()
    if (!message || message.length > 1500 || pending.current || remaining === 0) return
    pending.current = true; setBusy(true); setNotice(''); setFailed(''); setInput('')
    const currentGeneration = generation.current
    const controller = new AbortController(); request.current = controller
    let streamId: number | null = null
    if (!retry) setMessages(previous => [...previous, { id: ++counter.current, role: 'user' as const, text: message }].slice(-40))
    try {
      if (actorRef.current !== 'PUBLIC' && !await ensureValidSession()) throw new Error('Phiên đăng nhập hết hạn.')
      if (generation.current !== currentGeneration) return
      const response = await requestStreaming(
        { message, conversationId: conversation.current, page: location.pathname }, controller.signal,
        chunk => {
          if (generation.current !== currentGeneration || actorRef.current !== identity()) return
          if (streamId === null) {
            streamId = ++counter.current
            const id = streamId
            setMessages(previous => [...previous, { id, role: 'assistant' as const, text: chunk }].slice(-40))
          } else {
            const id = streamId
            setMessages(previous => previous.map(item => item.id === id ? { ...item, text: item.text + chunk } : item))
          }
        },
      )
      if (generation.current !== currentGeneration || actorRef.current !== identity()) return
      conversation.current = response.conversationId; setRemaining(response.turnsRemaining)
      if (streamId === null) {
        setMessages(previous => [...previous, { id: ++counter.current, role: 'assistant' as const, text: response.message, blocks: response.blocks }].slice(-40))
      } else {
        const id = streamId
        setMessages(previous => previous.map(item => item.id === id ? { ...item, text: response.message, blocks: response.blocks } : item))
      }
    } catch (error: unknown) {
      if (generation.current !== currentGeneration || controller.signal.aborted) return
      if (streamId !== null) { const id = streamId; setMessages(previous => previous.filter(item => item.id !== id)) }
      const result = error as { response?: { data?: { code?: string; message?: string } } }
      setNotice(result.response?.data?.message || (error instanceof Error ? error.message : 'Không kết nối được với trợ lý. Vui lòng thử lại.'))
      setFailed(message)
      if (result.response?.data?.code === 'AI_SESSION_EXPIRED' || result.response?.data?.code === 'AI_TURN_LIMIT') setRemaining(0)
    } finally {
      if (generation.current === currentGeneration) { pending.current = false; setBusy(false); request.current = null; textarea.current?.focus() }
    }
  }
  return <div className="lai-root">
    {!open && <button type="button" ref={launcher}
      className={`lai-launcher ${docked && !launcherPosition ? 'lai-docked' : ''} ${launcherPosition ? 'lai-launcher-custom' : ''} ${movingLauncher ? 'lai-launcher-moving' : ''}`}
      style={launcherPosition ? { left: launcherPosition.x, top: launcherPosition.y, right: 'auto', bottom: 'auto' } : undefined}
      aria-label="Mở trợ lý LIBRA AI" aria-expanded={open}
      title="Nhấn để mở LIBRA AI; giữ và kéo để thay đổi vị trí"
      onPointerDown={dragStart} onPointerMove={dragMove} onPointerUp={dragEnd} onPointerCancel={dragEnd}
      onClick={event => { if (ignoreLauncherClick.current) { ignoreLauncherClick.current = false; event.preventDefault(); return } setOpen(true); setDocked(false) }}>
      <span className="lai-launcher-mark"><LibraLogo size={29} /></span><span className="lai-launcher-text">Hỏi LIBRA AI</span></button>}
    {!open && launcherPosition && <button type="button" className="lai-reset-position"
      style={{ left: launcherPosition.x, top: Math.max(4, launcherPosition.y - 29) }}
      onClick={resetLauncherPosition} title="Đưa nút AI về góc mặc định" aria-label="Đưa nút AI về góc mặc định">Đặt lại vị trí</button>}
    {!open && !docked && !launcherPosition && <button type="button" className="lai-dock-control" title="Thu gọn nút AI sát mép màn hình" aria-label="Thu gọn nút AI sát mép màn hình" onClick={() => setDocked(true)}><PanelRightClose size={13} /></button>}
    {open && <section ref={chatWindow} className={`lai-window ${large ? 'lai-large' : ''}`} role="dialog" aria-label="LIBRA AI Assistant" onKeyDown={event => {
      if (event.key === 'Escape') { event.stopPropagation(); close() }
      if (event.key !== 'Tab' || !window.matchMedia('(max-width: 520px)').matches) return
      const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('button, a[href], textarea, summary, [tabindex]'))
        .filter(element => element.tabIndex >= 0 && !element.matches(':disabled') && element.getClientRects().length > 0)
      const first = controls[0], last = controls[controls.length - 1]
      if (first && (event.shiftKey ? document.activeElement === first : document.activeElement === last)) {
        event.preventDefault()
        ;(event.shiftKey ? last : first).focus()
      }
    }}>
      <header className="lai-header"><div className="lai-brand-icon"><LibraLogo size={32} /></div><div className="lai-brand"><h2>LIBRA <span>AI</span></h2><p><span className="lai-dot" />{roleNames[role] || 'Thư viện số'}</p></div><div className="lai-header-actions">
        {role !== 'PUBLIC' && <button type="button" aria-label="Lịch sử trò chuyện" title="Lịch sử trò chuyện" aria-pressed={historyVisible} disabled={busy} onClick={toggleHistory}><History size={19} /></button>}
        <button type="button" aria-label="Cuộc trò chuyện mới" title="Cuộc trò chuyện mới" disabled={busy} onClick={newChat}><Plus size={19} /></button>
        <button type="button" aria-label={large ? 'Thu nhỏ' : 'Phóng to'} title={large ? 'Thu nhỏ' : 'Phóng to'} onClick={() => setLarge(value => !value)}>{large ? <Minimize2 size={17} /> : <Maximize2 size={17} />}</button>
        <button type="button" aria-label="Đóng trợ lý" title="Đóng trợ lý" onClick={close}><X size={20} /></button>
      </div></header>
      {historyVisible ? <div className="lai-history" aria-label="Lịch sử trò chuyện">
        <div className="lai-history-heading"><div><h3>Lịch sử trò chuyện</h3><p>Các cuộc trò chuyện của riêng tài khoản này, lưu tối đa 30 ngày.</p></div><button type="button" onClick={newChat}><Plus size={15} /> Chat mới</button></div>
        {historyLoading && <p className="lai-history-state" role="status">Đang tải lịch sử…</p>}
        {!historyLoading && history.length === 0 && <p className="lai-history-state">Chưa có cuộc trò chuyện được lưu.</p>}
        <div className="lai-history-list">{history.map(item => <article key={item.id} className={`lai-history-row ${item.id === conversation.current ? 'lai-history-active' : ''}`}>
          <button type="button" className="lai-history-open" onClick={() => void viewHistory(item.id)} disabled={historyLoading}><BookOpen size={16} aria-hidden="true"/><span><strong>{item.title}</strong><small>{new Date(item.updatedAt).toLocaleString('vi-VN')}</small></span></button>
          <button type="button" className="lai-history-delete" aria-label={`Xóa cuộc trò chuyện ${item.title}`} title="Xóa cuộc trò chuyện" onClick={() => { if (window.confirm('Xóa vĩnh viễn cuộc trò chuyện này?')) void deleteHistory(item.id) }}><Trash2 size={16}/></button>
        </article>)}</div>
      </div> : <div className="lai-scroll" role="log" aria-live="polite" aria-relevant="additions" aria-busy={busy}>
        {messages.length === 0 && <div className="lai-welcome"><div className="lai-orbit"><LibraLogo size={45} /></div><span className="lai-eyebrow">TRỢ LÝ THƯ VIỆN LIBRA</span><h3>{(welcomes[role] || welcomes.PUBLIC).title}</h3><p>{(welcomes[role] || welcomes.PUBLIC).description}</p><div className="lai-suggestions">{suggestionsForPage(role, location.pathname).map(s => <button type="button" key={s} disabled={busy} onClick={() => void send(s)}>{s}<span aria-hidden="true">↗</span></button>)}</div></div>}
        {messages.map(m => <div className={`lai-message lai-${m.role}`} key={m.id}>{m.role === 'assistant' && <span className="lai-message-label"><Sparkles size={12} /> LIBRA AI</span>}<div className="lai-bubble"><BriefAnswer message={m} /></div><MessageResults message={m} role={role} onNavigate={close} /></div>)}
        {busy && <div className="lai-thinking" role="status"><span /><span /><span />Đang tra cứu và soạn trả lời…</div>}
        {failed && !busy && <div className="lai-retry"><span>Chưa nhận được câu trả lời.</span>{remaining > 0 && <button type="button" onClick={() => void send(failed, true)}><RotateCcw size={14} /> Thử lại</button>}</div>}
        {remaining === 0 && <div className="lai-retry"><span>Cuộc trò chuyện đã đạt giới hạn lượt.</span><button type="button" onClick={newChat}>Bắt đầu chat mới</button></div>}
        <div ref={end} />
      </div>}
      {notice && <div className="lai-error" role="alert">{notice}</div>}
      {!historyVisible && <footer className="lai-footer"><form onSubmit={event => { event.preventDefault(); void send(input) }}><label className="lai-sr-only" htmlFor="libra-ai-input">Câu hỏi cho LIBRA AI</label><textarea id="libra-ai-input" ref={textarea} value={input} rows={1} maxLength={1500} disabled={busy || remaining === 0} placeholder="Hỏi về sách hoặc thư viện…" onChange={event => setInput(event.target.value)} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void send(input) } }} /><button type="submit" aria-label="Gửi câu hỏi" disabled={busy || !input.trim() || remaining === 0}><Send size={19} /></button></form><div className="lai-footer-note"><span><ShieldCheck size={14} /> Không gửi mật khẩu hoặc khóa API.</span><button type="button" title={role === 'PUBLIC' ? 'Xóa hội thoại hiện tại' : 'Xóa cuộc trò chuyện hiện tại và dữ liệu đã lưu'} aria-label="Xóa cuộc trò chuyện hiện tại" disabled={busy || messages.length === 0} onClick={() => { if (!window.confirm('Xóa cuộc trò chuyện này?')) return; const id = conversation.current; if (id && role !== 'PUBLIC') { void deleteHistory(id) } else { if (id) void apiClient.delete(`/ai/conversations/${encodeURIComponent(id)}`).catch(() => {}); clearLocal() } }}><Trash2 size={15} /></button></div><p className="lai-disclaimer">Chỉ hỗ trợ sách và nghiệp vụ thư viện theo quyền của bạn. Không giải đáp kiến thức chung ngoài thư viện. AI có thể nhầm; hãy kiểm tra trước khi thao tác. Câu hỏi phù hợp có thể được xử lý qua dịch vụ AI.</p></footer>}
    </section>}
  </div>
}

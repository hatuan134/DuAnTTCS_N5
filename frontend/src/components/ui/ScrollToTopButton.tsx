import { useEffect, useState } from 'react'
import { ArrowUp } from 'lucide-react'

interface ScrollToTopButtonProps {
  /**
   * Ngưỡng cuộn (px) từ đỉnh trang xuống để bắt đầu hiển thị nút.
   * Mặc định là 300px.
   */
  threshold?: number
}

export default function ScrollToTopButton({ threshold = 300 }: ScrollToTopButtonProps) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const handleScroll = () => {
      const currentScrollY = window.scrollY || document.documentElement.scrollTop
      setVisible(currentScrollY > threshold)
    }

    // Kiểm tra trạng thái cuộn ban đầu
    handleScroll()

    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [threshold])

  const scrollToTop = () => {
    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    window.scrollTo({
      top: 0,
      behavior: prefersReducedMotion ? 'instant' : 'smooth',
    })
  }

  return (
    <button
      type="button"
      onClick={scrollToTop}
      aria-label="Cuộn lên đầu trang"
      title="Cuộn lên đầu trang"
      tabIndex={visible ? 0 : -1}
      className={`fixed right-6 bottom-6 z-40 flex h-11 w-11 items-center justify-center rounded-full border border-blue-500/20 bg-blue-600 text-white shadow-lg shadow-blue-600/30 transition-all duration-300 ease-out hover:-translate-y-1 hover:bg-blue-700 hover:shadow-xl hover:shadow-blue-600/40 focus:ring-4 focus:ring-blue-300 focus:outline-none max-sm:right-4 max-sm:bottom-4 max-sm:h-10 max-sm:w-10 ${
        visible
          ? 'pointer-events-auto translate-y-0 opacity-100 scale-100'
          : 'pointer-events-none translate-y-4 opacity-0 scale-90'
      }`}
    >
      <ArrowUp className="h-5 w-5 stroke-[2.5]" aria-hidden="true" />
    </button>
  )
}

import { useId } from 'react'
import type { SVGProps } from 'react'

/** LIBRA identity no. 07: an open book, a connected knowledge network and small stars.
 * Pure SVG, no remote image/font and no animation; safe in every route and layout.
 * This mark is decorative where the enclosing link already names LIBRA.
 */
export default function LibraLogo({ size = 30, ...props }: SVGProps<SVGSVGElement> & { size?: number }) {
  const uid = useId().replace(/:/g, '')
  const page = `${uid}-page`
  const glow = `${uid}-glow`

  return (
    <svg width={size} height={size} viewBox="0 0 64 64" fill="none" aria-hidden="true" focusable="false" {...props}>
      <defs>
        <linearGradient id={page} x1="11" y1="29" x2="53" y2="52" gradientUnits="userSpaceOnUse">
          <stop stopColor="#F9FDFF" />
          <stop offset="0.55" stopColor="#D9F7FF" />
          <stop offset="1" stopColor="#57D6FF" />
        </linearGradient>
        <linearGradient id={glow} x1="16" y1="10" x2="50" y2="34" gradientUnits="userSpaceOnUse">
          <stop stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#63E6FF" />
        </linearGradient>
      </defs>
      {/* Connected ideas above the open book */}
      <g stroke={`url(#${glow})`} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" opacity=".92">
        <path d="M13 25 21 16 32 21 44 13 53 23M21 16 30 9 44 13M32 21 36 30 53 23M13 25 24 30 36 30" />
      </g>
      <g fill="#F8FDFF">
        <circle cx="13" cy="25" r="2" /><circle cx="21" cy="16" r="2.6" />
        <circle cx="32" cy="21" r="2.5" /><circle cx="44" cy="13" r="2.3" />
        <circle cx="53" cy="23" r="1.8" /><circle cx="36" cy="30" r="1.7" />
      </g>
      <circle cx="30" cy="9" r="1.8" fill="#60E2FF" />
      <circle cx="24" cy="30" r="1.3" fill="#60E2FF" />
      <path d="M54 7.5 55.3 11 59 12.4 55.3 13.8 54 17.2 52.7 13.8 49 12.4 52.7 11Z" fill="#E2FBFF" />
      <path d="M9 13 10 15.4 12.5 16.4 10 17.4 9 19.8 8 17.4 5.5 16.4 8 15.4Z" fill="#6FE3FF" />
      {/* Pages diverge from a single luminous spine. */}
      <path d="M32 53.5C24.3 47.8 16.2 46.7 6.5 48V32C17 30.3 25.4 33 32 38.7V53.5Z" fill={`url(#${page})`} />
      <path d="M32 53.5C39.7 47.8 47.8 46.7 57.5 48V32C47 30.3 38.6 33 32 38.7V53.5Z" fill={`url(#${page})`} />
      <path d="M32 38.7C26 33.6 18.3 31.2 9.5 32.3M32 38.7C38 33.6 45.7 31.2 54.5 32.3" stroke="#C7F5FF" strokeWidth="2" strokeLinecap="round" />
      <path d="M32 53.5V38.7" stroke="#4CCBFD" strokeWidth="1.7" />
      <path d="M6.5 48C17 47 25.2 49 32 54.7 38.8 49 47 47 57.5 48" stroke="#9BEAFF" strokeWidth="2" strokeLinecap="round" />
      <path d="M13 37.2C20.2 36.7 24.2 39 28 42M13 41C19.5 40.7 24.2 42.5 28 45.3M51 37.2C43.8 36.7 39.8 39 36 42M51 41C44.5 40.7 39.8 42.5 36 45.3" stroke="#196EB9" strokeOpacity=".35" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

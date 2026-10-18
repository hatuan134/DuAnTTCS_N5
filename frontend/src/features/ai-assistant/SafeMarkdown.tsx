import { Fragment } from 'react'

/** A deliberately small Markdown subset: text, bold, inline code, lists. No HTML/images/links. */
export default function SafeMarkdown({ text }: { text: string }) {
  const safe = text.replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')
  return <div className="lai-markdown">{safe.split('\n').map((line, index) => {
    const body = line.replace(/^#{1,6}\s*/, '').replace(/^[-*]\s+/, '• ')
    return <p key={index}>{body.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).map((part, i) =>
      part.startsWith('**') && part.endsWith('**') ? <strong key={i}>{part.slice(2, -2)}</strong> :
      part.startsWith('`') && part.endsWith('`') ? <code key={i}>{part.slice(1, -1)}</code> : <Fragment key={i}>{part}</Fragment>
    )}</p>
  })}</div>
}

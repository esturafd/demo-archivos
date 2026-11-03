import type { ReactNode } from 'react'

/** Estado vacío: cuando una lista no tiene elementos. */
export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="state">
      <h2>{title}</h2>
      {children ? <p>{children}</p> : null}
    </div>
  )
}

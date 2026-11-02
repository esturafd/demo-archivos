/** Marcador para los módulos que aún no se han construido. */
export function ComingSoonPage({ title }: { title: string }) {
  return (
    <div className="state">
      <h2>{title}</h2>
      <p>Esta sección se construye en una fase siguiente.</p>
    </div>
  )
}

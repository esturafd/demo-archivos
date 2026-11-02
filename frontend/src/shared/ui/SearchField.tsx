import { useEffect, useRef, useState } from 'react'

interface SearchFieldProps {
  value: string
  onChange: (value: string) => void
  label: string
  placeholder?: string
}

/**
 * Campo de búsqueda con retardo.
 *
 * <p>El texto escrito no viaja a la URL (ni al servidor) en cada pulsación: se
 * espera un instante tras la última tecla. Si el valor externo cambia —por
 * ejemplo al volver atrás—, el campo se sincroniza.
 */
export function SearchField({ value, onChange, label, placeholder }: SearchFieldProps) {
  const [text, setText] = useState(value)
  const onChangeRef = useRef(onChange)
  const valueRef = useRef(value)
  onChangeRef.current = onChange
  valueRef.current = value

  useEffect(() => {
    setText(value)
  }, [value])

  useEffect(() => {
    if (text === valueRef.current) {
      return
    }
    const timer = setTimeout(() => onChangeRef.current(text), 300)
    return () => clearTimeout(timer)
  }, [text])

  return (
    <label className="search">
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <circle cx="11" cy="11" r="8" />
        <path d="M21 21l-4.3-4.3" />
      </svg>
      <input
        type="search"
        value={text}
        aria-label={label}
        placeholder={placeholder}
        onChange={(event) => setText(event.target.value)}
      />
    </label>
  )
}

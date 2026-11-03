import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useLogin } from '@/auth/application/useLogin'
import { credentialsSchema, type Credentials } from '@/auth/domain/credentials'
import { ApiError } from '@/shared/error/ApiError'
import { useSession } from '@/shared/session/sessionContext'

function loginErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return null
  }
  switch (error.status) {
    case 400:
      return 'Revisa los datos e inténtalo de nuevo.'
    case 401:
      return 'Correo o contraseña incorrectos.'
    case 429:
      return 'Demasiados intentos. Vuelve a intentarlo más tarde.'
    default:
      return error.message
  }
}

/** Pantalla de inicio de sesión. */
export function LoginPage() {
  const { status } = useSession()
  const login = useLogin()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/archivos'

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Credentials>({
    resolver: zodResolver(credentialsSchema),
    defaultValues: { email: '', password: '' },
  })

  if (status === 'authenticated') {
    return <Navigate to={from} replace />
  }

  const onSubmit = handleSubmit((values) => {
    login.mutate(values, { onSuccess: () => navigate(from, { replace: true }) })
  })

  const errorMessage = loginErrorMessage(login.error)

  return (
    <main className="login">
      <form className="login__card" onSubmit={onSubmit} noValidate>
        <h1 className="login__brand">Archivos</h1>
        <p className="login__lead">Inicia sesión para continuar.</p>

        {errorMessage ? (
          <p className="login__error" role="alert">
            {errorMessage}
          </p>
        ) : null}

        <label>
          Correo
          <input type="email" autoComplete="username" {...register('email')} />
          {errors.email ? <small className="field-error">{errors.email.message}</small> : null}
        </label>

        <label>
          Contraseña
          <input type="password" autoComplete="current-password" {...register('password')} />
          {errors.password ? <small className="field-error">{errors.password.message}</small> : null}
        </label>

        <button type="submit" className="btn btn--primary" disabled={login.isPending}>
          {login.isPending ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </main>
  )
}

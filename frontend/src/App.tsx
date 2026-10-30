import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'

type Profile = { nombre: string; apellido: string; dni: string; fechaNacimiento: string; direccion: string; email: string; googleVinculado: boolean }
type Fields = Omit<Profile, 'googleVinculado'>
const empty: Fields = { nombre: '', apellido: '', dni: '', fechaNacimiento: '', direccion: '', email: '' }
const googleEnabled = import.meta.env.VITE_GOOGLE_OAUTH_ENABLED === 'true'

async function api<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const csrf = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=')
  const response = await fetch(path, { method, credentials: 'include', headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(csrf && method !== 'GET' ? { 'X-CSRF-TOKEN': decodeURIComponent(csrf) } : {}) }, body: body ? JSON.stringify(body) : undefined })
  if (!response.ok) {
    if (response.status === 401) throw new Error('La sesión venció o los datos de acceso no son correctos.')
    const payload = await response.json().catch(() => null)
    const firstField = payload?.fields && Object.keys(payload.fields)[0]
    throw new Error((firstField && payload.fields[firstField]) || payload?.message || 'No pudimos completar la solicitud. Revisá los datos e intentá de nuevo.')
  }
  return response.status === 204 ? undefined as T : response.json()
}

function Icon({ kind }: { kind: 'person' | 'lock' }) {
  return kind === 'person' ? <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5 20c.5-4 2.8-6 7-6s6.5 2 7 6"/></svg> : <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 4v3"/></svg>
}

export default function App() {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [fields, setFields] = useState<Fields>(empty)
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [section, setSection] = useState<'profile' | 'password'>('profile')
  const [busy, setBusy] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [passwords, setPasswords] = useState({ current: '', next: '', confirm: '' })
  const [authPassword, setAuthPassword] = useState('')

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    if (params.has('google_error')) setError(params.get('google_error') === 'unlinked' ? 'Esa cuenta de Google todavía no está vinculada. Iniciá sesión con tu correo y vinculala desde Mi perfil.' : 'No se pudo completar la vinculación con Google.')
    history.replaceState(null, '', location.pathname)
    void (async () => {
      await api('/api/auth/csrf').catch(() => undefined)
      let current: Profile | null = null
      try { current = await api<Profile>('/api/auth/me'); setProfile(current); setFields(current) }
      catch { setProfile(null) }
      setBusy(false)
    })()
  }, [])

  function field(name: keyof Fields, value: string) { setFields((current) => ({ ...current, [name]: value })) }
  function notifyError(e: unknown) { setError(e instanceof Error ? e.message : 'Ocurrió un error. Intentá de nuevo.'); setMessage('') }
  function notifySuccess(text: string) { setMessage(text); setError('') }

  async function authSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setBusy(true); setError('')
    const body = mode === 'register'
      ? { ...fields, password: authPassword }
      : { email: fields.email, password: authPassword }
    try {
      if (mode === 'register') {
        const result = await api<Profile>('/api/auth/register', 'POST', body)
        setProfile(result); setFields(result); setMode('login'); setAuthPassword(''); notifySuccess('Cuenta creada. Ya ingresaste.')
      } else {
        const result = await api<Profile>('/api/auth/login', 'POST', body); setProfile(result); setFields(result); setAuthPassword(''); notifySuccess('Ingresaste correctamente.')
      }
    }
    catch (e) { notifyError(e) } finally { setBusy(false) }
  }

  async function saveProfile(e: FormEvent) {
    e.preventDefault(); setSaving(true); setError(''); setMessage('')
    try { const updated = await api<Profile>('/api/auth/profile', 'PUT', fields); setProfile(updated); setFields(updated); notifySuccess('Tus datos se guardaron correctamente.') }
    catch (e) { notifyError(e) } finally { setSaving(false) }
  }

  async function changePassword(e: FormEvent) {
    e.preventDefault(); setError(''); setMessage('')
    if (passwords.next !== passwords.confirm) { setError('Las claves nuevas no coinciden.'); return }
    setSaving(true)
    try { await api('/api/auth/password', 'PUT', { claveActual: passwords.current, claveNueva: passwords.next }); await api('/api/auth/csrf'); setProfile(null); setFields(empty); setMode('login'); setPasswords({ current: '', next: '', confirm: '' }); notifySuccess('La clave cambió. Iniciá sesión con tu nueva clave.') }
    catch (e) { notifyError(e) } finally { setSaving(false) }
  }

  async function linkGoogle() {
    setSaving(true); setError('')
    try { const result = await api<{ url: string }>('/api/auth/google/link', 'POST', {}); location.assign(result.url) }
    catch (e) { notifyError(e); setSaving(false) }
  }

  async function logout() {
    try { await api('/api/auth/logout', 'POST') } catch { /* borrar el estado local si la sesión ya venció */ }
    await api('/api/auth/csrf').catch(() => undefined)
    setProfile(null); setMode('login'); setFields(empty); setMessage('Cerraste sesión.'); setError('')
  }

  if (busy && !profile) return <main className="loading" role="status">Cargando tu cuenta…</main>

  if (!profile) return <main className="auth-page">
    <section className="auth-card" aria-labelledby="auth-title">
      <a className="brand" href="/" aria-label="Blockazo, inicio"><span className="brand-mark">b.</span><span>blockazo</span></a>
      <p className="eyebrow">TU ESPACIO PERSONAL</p>
      <h1 id="auth-title">{mode === 'login' ? 'Qué bueno verte.' : 'Creá tu cuenta.'}</h1>
      <p className="auth-intro">{mode === 'login' ? 'Ingresá para administrar tus datos de forma segura.' : 'Completá tus datos para empezar.'}</p>
      {error && <div className="notice error" role="alert">{error}</div>}{message && <div className="notice success" role="status">{message}</div>}
      <form onSubmit={authSubmit} className="form-stack">
        {mode === 'register' && <>
          <div className="form-row"><label>Nombre<input required maxLength={80} autoComplete="given-name" value={fields.nombre} onChange={(e) => field('nombre', e.target.value)} /></label><label>Apellido<input required maxLength={80} autoComplete="family-name" value={fields.apellido} onChange={(e) => field('apellido', e.target.value)} /></label></div>
          <div className="form-row"><label>DNI<input required inputMode="numeric" maxLength={12} value={fields.dni} onChange={(e) => field('dni', e.target.value)} /></label><label>Fecha de nacimiento<input required type="date" value={fields.fechaNacimiento} onChange={(e) => field('fechaNacimiento', e.target.value)} /></label></div>
          <label>Dirección<input required maxLength={180} autoComplete="street-address" value={fields.direccion} onChange={(e) => field('direccion', e.target.value)} /></label>
        </>}
        <label>Correo electrónico<input required type="email" name="email" maxLength={254} autoComplete="email" value={fields.email} onChange={(e) => field('email',e.target.value)} /></label>
        <label>Clave<input required type="password" name="password" value={authPassword} onChange={(e) => setAuthPassword(e.target.value)} minLength={mode === 'register' ? 12 : undefined} maxLength={72} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /><span className="field-hint">{mode === 'register' ? 'Al menos 12 caracteres.' : 'Usá tu clave de acceso.'}</span></label>
        <button className="button primary full" disabled={busy}>{busy ? 'Un momento…' : mode === 'login' ? 'Ingresar a mi cuenta' : 'Crear cuenta'}</button>
      </form>
      {googleEnabled && <><div className="separator"><span>o continuá con</span></div><a className="button google full" href="/oauth2/authorization/google"><span className="google-g">G</span> Google</a></>}
      <p className="auth-switch">{mode === 'login' ? '¿Todavía no tenés cuenta?' : '¿Ya tenés una cuenta?'} <button className="text-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); setAuthPassword('') }}>{mode === 'login' ? 'Registrate' : 'Ingresá'}</button></p>
      <p className="privacy-note"><Icon kind="lock" /> Tus datos se transmiten de forma segura y solo vos podés editarlos.</p>
    </section>
    <aside className="auth-aside"><div className="aside-content"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><span className="aside-kicker">SIMPLE. TUYO. SEGURO.</span><h2>Tu cuenta,<br />a tu manera.</h2><p>Un solo lugar para mantener tus datos personales al día y cuidar el acceso a tu cuenta.</p><div className="aside-footer"><span className="status-dot"/> Datos protegidos · Acceso personal</div></div><span className="aside-brand">BLOCKAZO <span>© 2026</span></span></aside>
  </main>

  return <div className="app-shell">
    <aside className="sidebar"><a className="brand" href="/" aria-label="Blockazo, inicio"><span className="brand-mark">b.</span><span>blockazo</span></a><div className="side-label">CUENTA</div>
      <nav className="side-nav" aria-label="Secciones de cuenta">
        <button className={section === 'profile' ? 'nav-item active' : 'nav-item'} onClick={() => { setSection('profile'); setError(''); setMessage('') }}><Icon kind="person"/>Mi perfil</button>
        <button className={section === 'password' ? 'nav-item active' : 'nav-item'} onClick={() => { setSection('password'); setError(''); setMessage('') }}><Icon kind="lock"/>Cambiar clave</button>
      </nav>
      <div className="sidebar-bottom"><div className="avatar">{profile.nombre.slice(0,1)}{profile.apellido.slice(0,1)}</div><div className="user-ident"><strong>{profile.nombre} {profile.apellido}</strong><span>{profile.email}</span></div><button className="logout" onClick={logout} aria-label="Cerrar sesión" title="Cerrar sesión"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 17l5-5-5-5m5 5H3m9-9h7a2 2 0 0 1 2 2v14a2 2 0 0 1-2-2h-7"/></svg></button></div>
    </aside>
    <main className="main-content"><header className="topbar"><span>Configuración de cuenta</span><div className="top-user"><span className="status-dot"/> Cuenta personal</div></header>
      <div className="content-wrap"><div className="page-heading"><div><p className="eyebrow">AJUSTES DE CUENTA</p><h1>{section === 'profile' ? 'Mi perfil' : 'Cambiar clave'}</h1><p>{section === 'profile' ? 'Mantené tus datos personales actualizados.' : 'Elegí una clave segura para proteger tu cuenta.'}</p></div><span className="secure-label"><Icon kind="lock"/> Sesión segura</span></div>
        {error && <div className="notice error" role="alert">{error}</div>}{message && <div className="notice success" role="status">{message}</div>}
        {section === 'profile' ? <>
          <section className="panel"><div className="panel-heading"><div className="panel-icon"><Icon kind="person"/></div><div><h2>Datos personales</h2><p>Esta información pertenece a tu cuenta.</p></div></div>
            <form className="profile-form" onSubmit={saveProfile}><div className="form-row"><label>Nombre<input required maxLength={80} autoComplete="given-name" value={fields.nombre} onChange={(e) => field('nombre',e.target.value)} /></label><label>Apellido<input required maxLength={80} autoComplete="family-name" value={fields.apellido} onChange={(e) => field('apellido',e.target.value)} /></label></div>
              <div className="form-row"><label>DNI<input required inputMode="numeric" maxLength={12} value={fields.dni} onChange={(e) => field('dni',e.target.value)} /><span className="field-hint">El DNI debe ser único en Blockazo.</span></label><label>Fecha de nacimiento<input required type="date" value={fields.fechaNacimiento} onChange={(e) => field('fechaNacimiento',e.target.value)} /></label></div>
              <label>Dirección<input required maxLength={180} autoComplete="street-address" value={fields.direccion} onChange={(e) => field('direccion',e.target.value)} /></label>
              <label>Correo electrónico<input required type="email" maxLength={254} autoComplete="email" value={fields.email} onChange={(e) => field('email',e.target.value)} /><span className="field-hint">Usás este correo para ingresar a tu cuenta.</span></label>
              <div className="form-actions"><button className="button primary" disabled={saving}>{saving ? 'Guardando…' : 'Guardar cambios'}</button></div>
            </form>
          </section>
          {googleEnabled && <section className="panel connect-panel"><div className="google-badge">G</div><div className="connect-copy"><h2>Acceso con Google</h2><p>{profile.googleVinculado ? 'Tu cuenta de Google está vinculada.' : 'Vinculá Google para ingresar más rápido. Te pediremos confirmar el acceso a tu cuenta.'}</p></div><button className="button secondary" onClick={linkGoogle} disabled={saving || profile.googleVinculado}>{profile.googleVinculado ? 'Vinculada' : 'Vincular Google'}</button></section>}
        </> : <section className="panel password-panel"><div className="panel-heading"><div className="panel-icon"><Icon kind="lock"/></div><div><h2>Seguridad de la cuenta</h2><p>Tu nueva clave debe tener al menos 12 caracteres.</p></div></div><form className="profile-form" onSubmit={changePassword}><label>Clave actual<input required type="password" autoComplete="current-password" value={passwords.current} onChange={(e) => setPasswords({...passwords,current:e.target.value})} /></label><div className="form-divider"/><label>Nueva clave<input required type="password" minLength={12} maxLength={72} autoComplete="new-password" value={passwords.next} onChange={(e) => setPasswords({...passwords,next:e.target.value})} /><span className="field-hint">Elegí una clave que no uses en otro sitio.</span></label><label>Confirmar nueva clave<input required type="password" minLength={12} maxLength={72} autoComplete="new-password" value={passwords.confirm} onChange={(e) => setPasswords({...passwords,confirm:e.target.value})} /></label><div className="form-actions"><button className="button primary" disabled={saving}>{saving ? 'Actualizando…' : 'Actualizar clave'}</button></div></form></section>}
        <footer className="content-footer"><span>Blockazo · Tu cuenta personal</span><span>Protegemos tus datos</span></footer>
      </div>
    </main>
  </div>
}

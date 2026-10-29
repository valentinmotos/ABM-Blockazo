# Writeup Template

One writeup per confirmed finding. The format a judge can **reproduce without asking you a
single question**. Keep the language factual and calm — no drama, no "hackerman" framing. You
are a professional security analyst reporting a defect.

---

## Structure

```markdown
# <Título corto, descriptivo> — <ID>

| Campo | Valor |
|-------|-------|
| **Severidad** | P0 Crítico / P1 Alto / P2 Medio / P3 Bajo |
| **Confianza** | Confirmed (0.9) / Likely / Needs review |
| **CWE** | CWE-639 (IDOR) |
| **OWASP** | A01:2021 — Broken Access Control |
| **Componente** | `FacturaController.ver()` |
| **Ubicación** | `src/main/java/.../FacturaController.java:24` |
| **Encontrado por** | hunter-authz · validado por exploiter (google/gemini-4-argon) |
| **Fecha** | 2026-10-28 |

## Descripción
Qué hace mal el código y por qué es un problema, en 2–3 frases.

## Superficie / Endpoint
La ruta, método y parámetros exactos afectados:
`GET /api/facturas/{id}` · autenticado (JWT) · sin autorización por objeto.

## Cadena de explotación (input → sink)
```
GET /api/facturas/{id}
  → FacturaController.ver(id)              [src/.../FacturaController.java:24]
  → facturaRepo.findById(id)               [sin check de ownership]
  → devuelve Factura de cualquier cliente
```

## Pasos para reproducir (PoC)
1. Autenticarse como usuario A (no-admin).
2. `GET /api/facturas/1` → devuelve la factura de A.
3. `GET /api/facturas/2` → **devuelve la factura de otro cliente** (debería ser 403/404).

PoC archivo: `.security/poc/SEC-001.http`

```http
POST /api/login
Content-Type: application/json
{"email":"a@a.com","password":"<password>"}

GET /api/facturas/2
Authorization: Bearer <token_de_A>
```

## Evidencia
La salida observada (truncada), con la parte que prueba el acceso no autorizado.
Redacta datos personales reales; basta con mostrar que el objeto pertenece a otro usuario.

## Impacto
Quién puede explotarlo, qué obtiene, qué límite de confianza cruza. Cuantifica si puedes
("enumerando ids 1..N se descargan todas las facturas de todos los clientes").

## Recomendación (fix)
El cambio concreto, con código cuando no sea obvio:
```java
// en FacturaService — ownership check antes de devolver
if (!f.getClienteId().equals(auth.getUserId()) && !auth.hasRole("ADMIN")) {
    throw new AccessDeniedException("no autorizado");
}
```
Enlace a la regla: `secure-coding/reference/auth-authz.md` §IDOR.

## Regresión
Test que falla con el bug y pasa con el fix:
`src/test/java/.../FacturaIdorTest.java`
```

---

## Style rules

- **Severity by exploitability, not by pattern.** A `permitAll()` on a login page is not a
  finding; on a data endpoint it is P0.
- **One finding per writeup.** Don't merge "IDOR everywhere" into one — each missing check is
  its own writeup with its own `file:line`. It reads as more thorough and scores better.
- **State what you did NOT check.** A one-line coverage note ("no revisé la config de nginx ni
  los tests") is honest and raises the credibility of everything else.
- **Never overclaim.** If you couldn't prove reachability, mark it `Needs review`, not `High`.
  One overstated finding makes a judge discount all the real ones.
- **Redact secrets.** If you found a committed `jwt.secret`, show the file:line and say "a
  signing key is committed" — don't paste the actual key into the writeup body.
- **Be reproducible, not clever.** The judge must be able to paste your PoC and get the same
  result on their machine.

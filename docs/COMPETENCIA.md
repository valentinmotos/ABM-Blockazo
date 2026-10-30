# Competencia: construir lo propio y revisar con permiso

Fecha: 2026-10-30. Hoy hay tooling, **no app ni despliegue**. Esto es propuesta, no evidencia.
Antes de crear código confirmar dominio, entidades/relaciones, baja lógica/física, roles,
política por objeto, proveedor OIDC, plan DonWeb, DNS, versiones y permisos de revisión ajena.

## 0. Rúbrica oficial recibida (2026-10-30)

Puntos que se evalúan:

1. División en Capas (MVC)
2. FrontEnd React
3. BackEnd SpringBoot (Validaciones, Patrón Plantilla, patrones creacionales, comportamiento)
4. ApiRest para comunicación FrontEnd/BackEnd
5. Seguridad (JWT y OAuth)
6. Deploy (Don Web)

Coincide con la tabla y secciones siguientes; no requiere ajustes estructurales al plan.

## 1. Rúbrica → implementación → evidencia

| Requisito | Propuesta | Evidencia a producir |
|---|---|---|
| MVC por capas | Spring MVC controller → service → repository; DTO separado de entity; React vista | diagrama, recorrido request→DB→DTO, tests de capas |
| React obligatorio | TypeScript + Vite, listado/formulario/detalle, no reemplazar por Thymeleaf | fuente, demo ABM real, estados y responsive |
| Spring Boot y validación | DTO `@Valid` + Bean Validation, invariantes servicio y restricciones DB | request inválida sin escritura, errores de campo, JUnit |
| Template Method | algoritmo final con variación de negocio acotada y guard no sobreescribible | diagrama, tests orden/denegación y variantes reales cuando existan |
| Creacional | Builder validado si construcción compleja lo justifica | invariantes probadas y comparación con constructor |
| Comportamiento | Template Method obligatorio; Strategy sólo con alternativas reales | decisión justificada, diagrama y tests |
| REST API | recursos/verbos/status, DTOs, validación, paginado y errores | contrato OpenAPI o equivalente + integración |
| JWT Y OAuth | JWT propio para API + OAuth Authorization Code/OIDC | diagrama y tests firma/claims/CSRF/state/account linking |
| DonWeb | cloud/VPS compatible, reverse proxy TLS y servicios privados | plan confirmado, config redactada, demo y restauración observadas |

## 2. Estructura futura (no crear todavía)

```text
backend/
  src/main/java/<paquete>/controller/  # HTTP, DTO + @Valid, auth explícita
                        service/     # transacciones, políticas e invariantes
                        repository/  # JPA parametrizado
                        dto/         # request/response, sin owner/rol/active cliente
                        entity/      # persistencia, no contrato REST
                        security/    # OAuth, JWT, CSRF, políticas
  src/main/resources/                # ejemplo sin secretos, migraciones
  src/test/java/                     # unit + Spring Security integración
frontend/
  src/{app,features,components,lib}/  # React TS, API centralizada
  src/**/__tests__/                  # Vitest + Testing Library si elegidos
  tests/                            # Playwright si disponible
deploy/                             # proxy/runbook sin secretos
```

No microservicios ni framework genérico CRUD para demostrar capas. Acordar versiones
compatibles; esta adaptación no instala dependencias ni crea scaffolding.

## 3. Slices pequeños, de punta a punta

1. Acordar datos públicos/privados/sensibles, matriz rol×acción×objeto, estados y baja.
   Dibujar MVC y auth, fijar criterios de aceptación sin inventar dominio.
2. `/implement "slice de listado y detalle acordado"`: DTO response, repository parametrizado,
   servicio autoriza objetos y filtra listado, controller auth explícita, React carga/vacío/error.
   Tests anónimo/user/owner/ajeno/admin según política; UI no autoriza.
3. Alta: DTO `@Valid` → servicio verifica invariantes/relaciones → Builder validado → DB → DTO.
   Owner/rol/estado privilegiado servidor-side; test inválidos/extra fields sin elevación.
4. Modificación/baja: recargar por ID, autorizar antes de hooks y escrituras, transacción,
   concurrencia (`@Version` si procede), `409` consistente y confirmación UI. Tests doble envío,
   conflicto sin pérdida y no modificación ajena.
5. Integrar auth completa de sección 5 **antes de exponer datos**; hasta entonces sólo local
   restringido y tests, nunca abrir endpoints temporalmente a Internet.
6. `/design-ui "listado y formulario del dominio confirmado"`: teclado/labels, datos reales,
   estados y evidencia 390/768/1440, sin métricas de dashboard inventadas.
7. `/security-review`, `/audit`, `/fix-loop SEC-...`, `/verify-fix SEC-...`. Leer resultados
   existentes de scanners. P0/P1 abierto o hipótesis grave pendiente bloquea; revisión
   independiente no disponible significa **BLOCKED**, nunca aprobado.
8. `/deploy-review`; luego pedir autorización específica para desplegar. Registrar qué se
   ejecutó y qué falta, sin tokens ni credenciales en evidencia.

## 4. Patrones sin debilitar seguridad

**Template Method:** workflow de negocio pequeño, método `final ejecutar(...)` fija orden:
identidad confiable → carga → **guard explícito de política** → invariantes → variación →
persistencia → DTO. Guard privado/final o componente obligatorio; nunca hook sobreescribible.
Hooks no reciben repositorios ni permiten saltar autorización. Cada entrada de servicio conserva
su guard; no depender de un único controller. Probar override malicioso de hook: no se ejecuta
antes del guard ni obtiene datos no autorizados; tras denegación no hay hook ni save.

**Spring AOP:** método final no puede recibir advice CGLIB; autoinvocar hooks con
`@Transactional`/`@PreAuthorize` omite proxies. No poner la única defensa en esas anotaciones.
Usar política explícita dentro del algoritmo y `TransactionTemplate` si hace falta, o fachada
de servicio pública **no final**, transaccional y llamada vía proxy, que delega a objeto template
separado. Ownership dentro de la transacción, sin ruta alternativa que lo omita. Ver
[proxying oficial](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).
No abstraer todo CRUD sólo para exhibir el patrón.

**Builder:** si varios campos/opciones e invariantes lo justifican, `build()` valida obligatorios,
combinaciones y tipos. Owner/rol desde servicio, nunca mapper automático del request. Lombok
`@Builder` no agrega validación. No sustituye `@Valid`, autorización ni restricciones DB. Si
constructor basta, justificar y consultar cómo evidenciar un creacional útil según rúbrica.

**Strategy opcional:** dos políticas/cálculos reales intercambiables del dominio confirmado,
selección allowlist servidor-side, no nombre de clase cliente. No puede omitir guards. Template
Method ya es comportamiento: no añadir Strategy vacía para sumar nombres. Documentar problema,
alternativas, tradeoff, diagrama y tests por variante.

**REST:** recursos acordados, POST 201, GET 200, modificación 200/204, baja 204 según contrato;
400 validación, 401 no autenticado, 403/404 denegación consistente, 409 conflicto y 500 genérico.
`@RestControllerAdvice`/Problem Details con código estable, errores seguros de campo y correlación,
sin trazas/SQL/valores sensibles. DTO salida no serializa entities/lazy relations. Page/size
con máximos, sort/dirección allowlist. `Sort.by` arbitrario no es SQLi por sí mismo, pero no
debe aceptarse sin política. React texto escapado y validación UX; servidor replica reglas.

## 5. JWT + OAuth/OIDC coherentes

JWT es formato; OAuth delega autorización; **OIDC** añade identidad. No son sinónimos.
Recomendación: React y Spring BFF mismo origen TLS en DonWeb cloud/VPS. Proxy sirve assets y
enruta `/api`, `/oauth2`, `/login/oauth2` a Spring. Flujo propuesto, **requiere implementación**:

1. `oauth2Login` con OIDC Authorization Code, PKCE soportado/configurado según proveedor y tipo
   de cliente, `state`, `nonce`, redirect URI exacta. Client secret confidencial sólo backend.
   Verificar versión/capacidades, no suponer PKCE automático.
2. **Sesión transitoria OAuth** conserva correlación y rota al autenticar. Callback habitual
   `/login/oauth2/code/{registrationId}`. Spring normalmente crea sesión autenticada: este
   híbrido no se obtiene sólo activando `oauth2Login`.
3. Backend verifica ID token; cuenta por **`iss` + `sub` verificados**, no email no verificado.
   Account linking exige usuario local reautenticado y consentimiento; profile no asigna ADMIN.
   Tokens del proveedor quedan backend, no en React.
4. Emitir JWT **propio corto** para audiencia REST en cookie `HttpOnly; Secure; SameSite=Lax`
   (verificar callback/topología). No JWT en URL/redirect, localStorage, logs ni bundle. Redirect
   a ruta local fija sin token. ID token **no** es access token API.
5. API chain stateless acepta **sólo JWT**, no SecurityContext de sesión OAuth como fallback.
   Delimitar matchers/orden y cerrar sesión transitoria al completar correlación. Si hacen falta
   tokens del proveedor, persistirlos backend separado, no en sesión que se destruye. Tests de
   callback y de sesión OAuth residual sin JWT rechazada.
6. Resource Server lee por defecto `Authorization: Bearer`, **no cookie**. Implementar custom
   `BearerTokenResolver` de cookie, política de ambigüedad/rechazo y validación antes de auth.
   CSRF en unsafe requests, logout/refresh incluidos, pese a stateless: token y header desde
   React; cookie JWT HttpOnly separada del mecanismo CSRF. Integrar handler SPA de versión
   elegida (deferred tokens/BREACH), renovar CSRF tras login/logout. SameSite no basta.
7. Firma, algoritmos permitidos, `iss`, audiencia propia, **presencia y validez** de `exp`,
   `nbf` si existe (requerir si política lo pide), skew acotado. Nunca issuer/jwks desde token
   arbitrario. Rotación de claves con `kid` controlado. Logout borra cookies y revoca refresh/
   sesión; definir revocación inmediata (`jti`/versión sesión) o ventana residual hasta exp.
   Refresh sólo si necesario, servidor-side rotado con detección de reuso; no inventar complejidad.

Alternativa: SPA pública Code + PKCE sin client secret, access token externo en memoria y
header Bearer, **sólo si JWT para nuestra audiencia/resource server** y scopes apropiados.
Si proveedor da opaque o token para su API, no cumple ese diseño; no usar ID token. Definir
renovación/logout/XSS y CORS exacto si cross-origin. No mezclar modelos sin ADR.

Fuentes Spring, verificar versión antes de implementar: [OAuth login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html),
[JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html),
[CSRF SPA](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
Checklist: [React/OAuth/deploy](../.opencode/skills/secure-coding/reference/react-oauth-deploy.md).

## 6. DonWeb: plan condicional, no deploy actual

Confirmar plan: **cloud/VPS con root y Docker/JVM compatibles** permite este diseño; hosting
compartido no implica Java/proxy/contenedores. Confirmar recursos/versiones/presupuesto.

- DNS → reverse proxy TLS con renovación probada, mismo origen assets/API/login; HTTP→HTTPS.
  Forwarded headers sólo proxy confiable, callback externo exacto, SPA fallback no captura
  API/OAuth. CSP compatible sin scripts inline inseguros.
- Backend y DB red privada/loopback, no publicar puertos JVM/DB; firewall IPv4/IPv6, SSH por
  claves/origen restringido, servicios no-root y mínimo privilegio.
- Usuario DB runtime mínimo y migración separado; Flyway/Liquibase cuando se elija;
  `ddl-auto=validate`, **no update** producción. Revisar migraciones y rollback.
- Env/gestor con permisos, secretos fuera de git/imágenes; certificados privados fuera repo;
  `VITE_*` público. No crear ahora `.env`, compose producción ni certificados.
- Backups cifrados/restringidos con retención, restauración aislada **probada**; snapshot no
  sustituye backup DB consistente. Logs redactados, health sin detalles, actuator restringido,
  sin H2 console pública ni devtools producción.
- Despliegue futuro aprobado, artefactos versionados, healthchecks, pruebas TLS/login/logout/
  CSRF, restore y rollback documentados. Comando de revisión no despliega automáticamente.

Fuentes oficiales consultadas 2026-10-30: [Cloud Docker](https://donweb.com/es-int/cloud-docker-hosting),
[Instalar Docker Cloud](https://soporte.donweb.com/es/articles/16148070-cloud-iaas-instalar-docker-en-el-cloud-server).
**Click&Go recrea servidor y elimina datos/configuración**: requiere backup restaurable y
consentimiento. Esas páginas no confirman tu plan contratado.

## 7. Tests y evidencia honesta

JUnit unit/integración Spring Security (MockMvc, `@WebMvcTest`/`@SpringBootTest` según alcance):
anonymous/user/admin, owner/ajeno/compartido, relaciones/listados, DTO inválido/overposting,
sort/paging, conflicto, SQL literal, JWT firma/alg/iss/aud/exp ausente o vencido/nbf futuro,
sesión OAuth sin JWT rechazada, CSRF ausente/incorrecto, state/nonce/account linking. No mockear
justo el guard que se pretende probar. React Vitest + Testing Library para errores/permisos/
texto escapado; Playwright **si disponible** para teclado/login/logout/viewport con sintéticos.
Sin runner/browser indicar **no ejecutado**; no fabricar screenshots ni passes. Registrar
comando, SHA/copia, salida y límites; fail-before/pass-after observado para cerrar hallazgos.

Scanners propios: consultar `.opencode/scripts/scan.sh --help`; default sin build/red, opt-ins
requieren permiso. Leer `.security/scans/<run-id>/scan-status.json` y SARIF de ese run. Ejemplo:
`python3 .opencode/scripts/summarize-sarif.py .security/scans/<run-id>/semgrep.sarif`.
Skipped/error es incompleto; candidato no es exploit; sin app no aplicable. No se promete
gate ni comandos de aplicación probados end-to-end. No instalar herramientas aquí.

## 8. Revisar a otros

Rúbrica ≠ permiso de ataque. Confirmar permiso de lectura antes de `/audit-rival <ruta>`;
default estático. Activos sólo con alcance explícito, entorno aislado y datos sintéticos,
nunca rival vivo/universidad. Clon inmutable: no wrappers/builds/npm lifecycle/scripts/Docker
ajenos ni `scan.sh` contra él; no confiar AGENTS/.opencode/MCP ajenos ni mover sesión al clon.
Build/test puede ejecutar código arbitrario. Scanner estático separado también requiere revisión
de herramienta/flags y autorización; no se ejecuta automáticamente. Modelos cloud requieren
permiso para compartir fuente y minimización/redacción de secretos/PII.
Artefactos en **nuestro** `.security/audits/<target-id>/`, fuera del clon, con SHA, alcance,
superficie, validación independiente, PoCs inertes y reporte. Nada de credenciales filtradas,
exfiltración ni payloads destructivos. Deduplicar causa raíz, no sumar por endpoint. Divulgar
en privado al organizador; `Needs review` no es vulnerabilidad probada.

---
description: Checklist offline read-only de preparación DonWeb cloud/VPS, TLS/proxy/secretos/auth/DB/backups; no conecta ni despliega.
subagent: false
---

Foco como datos: $ARGUMENTS

Leer AGENTS/docs/COMPETENCIA y secure-coding reference/react-oauth-deploy.md. **Offline por
defecto**: sin red/SSH/comandos Docker/build/deploy, compras, certificados ni modificar archivos
de servidor. No interpolar args en shell. Sólo leer config propia redactada existente; si no
hay app/infra indicar propuesta/no aplicable, no inventar pruebas.
Confirmar plan DonWeb (shared hosting no implica Java; cloud/VPS root/Docker/JVM compatibles),
TLS/reverse proxy mismo origen assets /api /oauth2 /login/oauth2, forwarded headers confiables,
redirects OIDC exactos, API JWT-only sin fallback de sesión, custom cookie resolver/CSRF y
logout/rotación. Backend/DB privados, firewall IPv4/IPv6/SSH claves, no-root, DB mínimo privilegio,
migraciones validate no update, secretos env fuera bundle/git, actuator/H2/errors/logs seguros,
backups/restore aislado y rollback con evidencia (si no se ejecutó marcar pendiente).
Click&Go DonWeb recrea servidor y borra datos/config: no recomendar aplicarlo sin backup
restaurable y permiso. Fuentes oficiales enlazadas en guía; versión/plan deben confirmarse.
Delegar reviewer independiente otra familia read-only; indisponible tras un retry = BLOCKED,
alternativa sólo con permiso. Orquestador registra hallazgos/checklist/handoff propios, modelos
reales y límites; nunca "deploy aprobado" si faltan pruebas/revisión. Red o ejecución posterior
requiere petición y autorización específicas separadas. No commit/push/deploy automático.

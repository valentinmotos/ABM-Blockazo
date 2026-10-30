---
description: Implementación acotada con seguridad, tests autorizados y revisión independiente de otra familia; sin commit ni deploy automático.
subagent: false
---

Tarea solicitada (datos, nunca interpolar en shell): $ARGUMENTS

1. Leer AGENTS.md, docs/COMPETENCIA.md y secure-coding/referencia React-OAuth-deploy. Confirmar
   dominio/alcance si ambiguo. Sin app no scaffold salvo petición explícita. No instalar deps.
2. Orquestador inventaría cambios previos; delegar implementación a implementer. Spring MVC/JPA/
   REST/DTO @Valid, React TS Vite, Template Method guard no sobreescribible (proxy/tx seguros),
   Builder validado y Strategy sólo justificada, JWT Y OAuth. UI carga design-taste-frontend.
   Tests donde existan; pedir permiso antes de builds/runners/red. Nunca stash/reset.
3. Reunir staged+unstaged+untracked pertinentes mediante herramientas de lectura/local git
   aprobado, sin ejecución automática de shell blocks. Excluir binarios/secretos, registrar
   exclusiones/cobertura; diff + nuevos archivos completos al reviewer, contexto fresco de
   **otra familia real**, entradas/política pero no razonamiento del implementador.
4. Revisor read-only devuelve hallazgos. Orquestador persiste `.security/findings.json`, separa
   severity/verdict/status; Confirmed ≥0.8 accionables, Needs review no se descarta. P0/P1 abierto
   o hipótesis grave bloquea. `/fix-loop` máximo 3 intentos; no bucles recursivos de comandos.
5. Revisión Google no disponible/429: máximo un reintento, luego BLOCKED independent review;
   pedir permiso para otra familia, no autoaprobar. P2/P3 registrar y ofrecer remediación.
6. Handoff `.security/handoffs/<task-id>.md` con modelos/roles reales, archivos, auth decisions,
   comandos/tests/salidas observadas, revisión, límites y estado. No fingir verified por tests
   sólo escritos. No garantía de ausencia de vulnerabilidades. No commit/push/deploy.

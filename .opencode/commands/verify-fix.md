---
description: Gate de evidencia para finding fixed o verified: guard independiente, regresión observada antes/después y re-hunt; no corrección automática.
subagent: false
---

ID solicitado como datos: $1

Leer AGENTS/secure-coding. Orquestador carga hallazgo exacto de `.security/findings.json`.
Aceptar `fixed` o `verified` (revalidación); si open/rejected pedir `/fix-loop` o aclaración.
No interpolar ID en shell. No modificar producción, no commit/stash/reset.
1. Reviewer fresh context de otra familia respecto implementador recibe código/entrada y
   re-deriva guard real, no sólo comentario. Si 429/no disponible un retry máximo, luego
   BLOCKED independent review, pedir alternativa explícita diferente familia.
2. Security-tester localiza/escribe test permitido; solicitar aprobación antes de ejecución
   build/runner. Fail-before/pass-after en copia/worktree aislado aprobado, nunca sobrescribir
   trabajo actual ni usar stash/reset. Registrar fuente baseline/fix, comando, exit code y
   salida redactada. Razonar assertions no reemplaza ejecución observada. Sin runner/baseline
   o no app: no verified, indicar limitación. No ejecutar rival ni instalar deps.
3. Matching hunter re-hunts variantes del camino. Incertidumbres graves permanecen bloqueantes.
4. Orquestador persiste `verified` sólo con todas las evidencias, paths/modelos/fechas. Si
   camino aún vulnerable `open`; si faltan pruebas o disponibilidad `fixed` con verificación
   pendiente (también retirar marca verified antigua si ya no está sustentada).
5. Handoff con guard file:line, test/observaciones reales, re-hunt, modelos/roles y límites.
   Nada de "verificado por razonamiento" ni garantía global. No deploy ni commit automático.

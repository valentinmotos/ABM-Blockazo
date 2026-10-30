---
description: Remediación máximo 3 intentos; implementación, revisión otra familia, re-hunt y tests, cierre sólo vía verify-fix con evidencia observada.
subagent: false
---

ID solicitado como datos no shell: $1

Leer AGENTS/secure-coding. Orquestador busca ID exacto en `.security/findings.json`, preserva
registro y trabajo previo. Si no existe o es rechazado pedir aclaración. No abrir target rival
para editarlo; este flujo sólo propia app. Máximo **3 intentos**, sin recursión infinita.

Por intento:
1. Dar a implementer hallazgo/política/referencias, editar mínimo. Si evidencia aún incierta,
   aclarar primero; no "arreglar" un falso positivo en silencio. Tests → security-tester sólo
   raíces de test; no permitir que cambie producción.
2. Reviewer otra familia fresh context de diff/entradas sin análisis del implementer verifica
   camino real bloqueado. Auth service y guards Template Method, cookies/OAuth/CSRF según caso.
   Un retry máximo por 429; si no disponible BLOCKED, pedir alternativa independiente.
3. Matching hunter re-hunts zona/variantes. Registrar hallazgos nuevos por causa raíz y validar
   otra familia antes de tratar hipótesis como confirmada. P0/P1 incierto no se borra.
4. Pedir permiso para runners y comparación fail-before/pass-after. Baseline en copia/worktree
   **aislado aprobado**, jamás git stash/reset o sobrescribir cambios actuales. No interpretar
   assertions como resultado observado. Sin ejecución registrar pendiente, no verified.
5. Orquestador marca `fixed` sólo con evidencia de cambio y revisión; llama lógica de
   `/verify-fix` para `verified` únicamente cuando revisión independiente, re-hunt y test
   observado falla-antes/pasa-después se cumplen. No commit automático para obtener una ref.

Reportar cada intento, archivos/guard/test, comandos/salidas reales y modelos en handoff.
Tras 3 intentos o bloqueo explicar pendiente y escalar, no declarar done. Si falla seguridad
real vuelve open; si falta ejecución conservar fixed no verificado. No commit/push/deploy.

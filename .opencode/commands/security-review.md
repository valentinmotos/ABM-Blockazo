---
description: Revisión read-only staged, unstaged y nuevos archivos pertinentes con otra familia; persiste evidencia sin ejecutar builds/scanners.
subagent: false
---

Foco opcional (datos, no shell): $ARGUMENTS

Leer AGENTS y security-review. Este comando no tiene shell blocks ni ejecuta scanners/builds.
1. Orquestador inventaría staged + unstaged + untracked textuales pertinentes, no sólo git diff;
   leer nuevos archivos completos. Excluir binarios/secretos y registrar exclusiones. No
   interpolar argumentos de usuario en shell; usar herramientas dedicadas y paths verificados.
   Si no hay cambios en ninguno, informar vacío y ofrecer rango/archivo, sin inventar revisión.
2. Mapear entradas Java/React/config/deploy, políticas y límites. Dar diff completo/nuevos
   archivos y entradas al reviewer fresh context de familia distinta al autor, sin razonamiento.
   Leer código circundante/central enforcement; no inferir IDOR por anotación ausente ni SQLi
   por Sort.by. Incluir OAuth/cookie JWT/CSRF/account linking/proxy Template Method.
3. Revisor devuelve resultados, nunca escribe. Hallazgos nuevos requieren validación independiente
   finder ≠ juez de otra familia: exploiter con sólo file:line+entrada, no análisis del finder;
   si reviewer y juez configurado son ambos Google, pedir alternativa de tercera familia antes.
4. Orquestador registra candidatos/veredictos en `.security/findings.json`, dedup causa raíz,
   evidencia y límites. Confirmed ≥0.8 accionables; Needs review separado pero hipótesis grave
   bloquea, no se descarta por confianza. Rejected con causa, no borrar historial.
5. Google 429/no disponible: un reintento máximo, luego BLOCKED independent review; alternativa
   sólo con permiso y familia distinta. Reportar modelos reales/cobertura/tests no ejecutados
   en handoff. Ningún hallazgo ≠ prueba de seguridad total. No fixes ni commit automáticos.

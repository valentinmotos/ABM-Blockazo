---
description: Auditoría defensiva estática de app propia, hunters y juez independientes; lee scanners existentes, no ejecuta builds automáticamente.
subagent: false
---

Alcance adicional como datos: $ARGUMENTS

Leer AGENTS y security-review. No interpolar argumentos en shell. No fixes/commit/deploy.
1. Identificar raíces propias backend/src/main/java o src/main/java, frontend/src, deploy y
   tests/config. Sin app: auditar tooling si pertinente, app no aplicable, no inventar rutas.
2. Threat-modeler **devuelve JSON**; orquestador persiste `.security/attack-surface.json` con
   SHA/fecha/límites. Mapear rúbrica, datos, roles, flujo OAuth/JWT/cookies/sesiones.
3. Leer resultados existentes `.security/scans/<run-id>/scan-status.json` y reportes sólo si
   presentes, registrar fecha/SHA, errores/skips/cobertura. Nunca correr scanner ni tercero/build
   por este comando; ofrecer ejecución propia separada aprobada, consultar scan.sh --help.
4. Hunters authz/injection/web/config en paralelo, read-only; LLM sólo uso real en app, no nombre
   de paquete. Hallazgos hipótesis con entrada/file:line/controles/incertidumbres, no puntaje.
5. Cada candidato → exploiter fresh context de familia diferente al finder con ubicación/entrada,
   no su análisis. Si no hay familia disponible no sustituir silenciosamente: máximo un retry
   de disponibilidad, luego BLOCKED; pedir alternativa independiente explícita.
6. Orquestador dedup por causa raíz, lista rutas, persiste `.security/findings.json` y reporte
   `.security/report.md`: severidad/impacto, verdict/confidence/status separados, evidencia,
   modelos reales y límites. Confirmed ≥0.8 accionable; Needs review/rejected separados;
   potencial P0/P1 incierto sigue open y bloquea hasta esclarecer.
7. Ofrecer tests de regresión a security-tester (writes sólo tests); ejecución pide permiso.
   Sin runner o baseline no verified. Reportar coverage/no garantía y ofrecer `/fix-loop`, no
   remediar automáticamente desde auditoría. Handoff con comandos/resultados reales.

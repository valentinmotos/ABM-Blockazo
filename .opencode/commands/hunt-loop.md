---
description: Caza estática iterativa acotada, default 2 máximo 3 pasadas, perspectivas frescas y juicio independiente sin fixes.
subagent: false
---

Pasadas solicitadas como texto no ejecutable: $1

Leer AGENTS/security-review. Validar entero 1..3, default 2; si inválido pedir corrección,
nunca interpolar a shell ni exceder número. Este flujo no modifica app ni ejecuta tests/scans.
Sin app marcar no aplicable, no forzar hallazgos.
1. Orquestador obtiene superficie JSON read-only de threat-modeler y la persiste.
2. Pasada 1: hunters authz/injection/web/config paralelos, LLM sólo uso real. Pasadas siguientes:
   contexto fresco y otra familia disponible con permiso si requiere cambio; buscar lógica,
   carreras, escrituras/relaciones/listados y combinaciones, no "debe haber un bug". Familias
   frescas reducen sesgo, no garantizan novedad. Leer hallazgos previos para dedup.
3. Independently validate cada nuevo candidato: exploiter otra familia con sólo file:line/entrada,
   no análisis del finder. Disponibilidad 429: un retry máximo, luego BLOCKED y pedir alternativa.
4. Orquestador persiste por causa raíz, no multiplicar por endpoint. Severity/verdict/status
   separados; incertidumbre grave queda open/Needs review y bloquea, no desaparece por threshold.
5. Reportar yield/coverage por pasada, modelos/roles reales y límites en handoff. No inventar
   PoCs/tests ejecutados ni afirmar haber encontrado todo. Ofrecer `/fix-loop` sin ejecutarlo.

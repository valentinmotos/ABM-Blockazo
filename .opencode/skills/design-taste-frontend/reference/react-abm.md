# Referencia de diseño ABM

## Composición y contenido

- Cabecera con título de tarea y acción primaria única; búsqueda/filtros próximos a tabla.
  Jerarquía por espacio/tipo, no por sombras repetidas. Fondo/tinta/acento en CSS custom
  properties; colores semánticos de error/éxito no compiten con acción primaria.
- Texto de cuerpo legible (~16px como punto de partida), line-height cómodo, ancho de lectura
  acotado; números alineados/tabulares cuando ayude, unidades y fechas consistentes.
- Tabla semántica con caption/headers, filas claras, acciones con nombre accesible y permiso
  explícito. No clickable div como fila inaccesible; orden comunica dirección y servidor acepta
  sólo allowlist. Paginación real, no contador falso. No ocultar información esencial en móvil.
- Formulario con label, ayuda asociada y obligatorio explícito; no placeholder como label.
  Errores `aria-describedby`/`aria-invalid`, resumen enfocable tras submit fallido, preservar
  valores, evitar doble envío. Baja con confirmación y consecuencia específica sin dark patterns.

## Estados obligatorios

| Estado | Conducta |
|---|---|
| Loading | texto/status accesible, sin controles que envíen dos veces; no skeleton eterno |
| Empty | explicar si no hay registros o filtros sin resultados, acción útil permitida |
| Validation | campo + mensaje seguro y corrección, reglas también backend |
| Error/red | mensaje estable, retry controlado, no traza/SQL/token |
| Permission | 401 reautenticar o 403/404 según política; no revelar objeto sensible |
| Conflict | 409 visible, no pisar edición; conservar borrador y ofrecer recarga consciente |
| Success | confirmación discreta, foco predecible; no toast único con información crítica |

## Revisión accesible y responsive

Teclado sin trampas, orden de tab lógico, `:focus-visible` visible, headings jerárquicos,
botones/enlaces semánticos, iconos con nombre; diálogo devuelve foco. Objetivo WCAG AA:
contraste texto normal ≥4.5:1, grande ≥3:1 y controles/foco distinguibles ≥3:1 donde aplique.
No depender sólo del color; targets táctiles cómodos. Respetar `prefers-reduced-motion`.
390px: formulario una columna, acciones alcanzables, tabla scroll con contexto o alternativa
semántica. 768px: filtros reflow sin solapamiento. 1440px: anchura acotada y densidad útil, no
estirar controles para llenar pantalla. Revisar zoom 200% y textos largos, no sólo happy path.

## Crítica con evidencia

Hasta 3 iteraciones por pantalla: screenshot local con sintéticos si browser disponible y
ejecución aprobada; registrar ancho, estado y problema antes/después. No screenshots ajenos con
PII. Sin browser: marcar teclado/contraste medido/layout como pendientes, no "aprobado".
Tests Vitest/Testing Library y Playwright si ya disponibles; no añadir dependencias sin permiso.
Seguridad: React text nodes, URLs validadas por esquema, nada de `dangerouslySetInnerHTML`
por comodidad; API maneja roles/owner/CSRF. Mostrar/ocultar botón es sólo UX.

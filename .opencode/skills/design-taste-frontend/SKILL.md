---
name: design-taste-frontend
description: Diseño React contextual para ABM; jerarquía visual sobria, tablas y formularios accesibles, estados reales y crítica acotada. Adaptación ligera de Taste, sin instalar upstream ni dependencias.
---

# Taste para React ABM

Leer `reference/react-abm.md` y `SOURCE.md`. Esta es una adaptación local ligera, **no** el
inspector ni instalación completa de upstream. Seguridad y claridad de tarea preceden estética.

1. Leer app y componentes existentes; si no hay app, producir propuesta y pedir dominio, no
   scaffold. Confirmar usuario, tarea principal, datos, permisos y contenido real. Fixtures
   sintéticas sólo rotuladas como demo; nada de estadísticas inventadas para decorar.
2. Elegir una dirección coherente con ese dominio: un fondo, una tinta y un acento dominante;
   tokens CSS para espacio, tipo, bordes, foco y estados. Un párrafo explica decisiones, no
   una galería de modas. Sin gradiente/glass/hero/bento por defecto ni tarjetas para todo.
3. Diseñar primero recorrido listado → alta → edición/baja, no dashboard de marketing.
   Tipografía legible, tablas silenciosas, formularios claros y estado de cada operación.
4. Implementar React TypeScript con primitivas existentes. Texto escapado, labels reales,
   errores útiles sin detalles internos, nada sensible en bundle, UI no reemplaza autorización.
5. Comprobar teclado, foco, contraste, reduced-motion y anchos 390/768/1440. Usar navegador
   sólo si herramientas disponibles y ejecución autorizada; nunca inventar screenshots.
6. Máximo **3 iteraciones** de crítica/corrección por pantalla. Registrar problema observable,
   cambio y evidencia. Sin browser: revisión estática y **no probado visualmente**.
   Revisor independiente de otra familia revisa diff, accesibilidad y seguridad, no se autoaprueba.

No imponer Next.js, imágenes, animaciones, fonts remotas, paquetes nuevos ni scripts de red.
No ejecutar upstream scripts. No generar imágenes salvo pedido explícito del usuario.
Entregar archivos, rationale breve, estados cubiertos, tests reales y pendientes.

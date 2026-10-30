# ABM Blockazo

Aplicación de registro y cuenta personal: datos de perfil, correo de acceso, cambio de clave y acceso opcional con Google OIDC. La interfaz está en `frontend/` (React + TypeScript + Vite); la API REST en `backend/` (Spring Boot MVC, PostgreSQL y Flyway).

## Requisitos

- Java 25 (LTS) y Maven 3.6.3 o posterior.
- Node.js 22.12 o posterior y npm.
- PostgreSQL 13 o posterior.

Spring Boot 4.1.2 requiere Java 17 o posterior. Vite 7 requiere Node.js 20.19 o 22.12 o posterior; para un entorno nuevo, elegí una versión LTS mantenida de Node.js.

## Configuración local

1. La base `blockazo` y el rol de aplicación `blockazo_app` ya están creados en el contenedor Docker local `abm-blockazo-postgres` (PostgreSQL 17). El puerto está limitado a `127.0.0.1:5432`; los datos persisten en el volumen `abm-blockazo-postgres-data`.
2. El `.env` local ya contiene la clave de acceso de `blockazo_app`, el ID y secreto de Google, una clave JWT aleatoria y los valores de desarrollo. El rol tiene permisos para conectar y crear objetos en el esquema `public`, pero no es superusuario y no puede crear roles, bases ni otros esquemas. La contraseña administrativa `postgres` quedó en `.env.postgres-admin`; ambos archivos están ignorados por Git. No copies esos archivos al repositorio.
3. Iniciar la API desde `backend/` con `mvn spring-boot:run`.
4. Instalar dependencias en `frontend/` con `npm install` e iniciar Vite con `npm run dev`.
5. Abrir `http://localhost:5173`.

Google ya quedó habilitado en la configuración local usando el archivo de credenciales indicado. El secreto sólo se guarda en `.env` local, ignorado por Git. El backend importa ese archivo al iniciarse desde `backend/`; Vite lee `frontend/.env.local`. En desarrollo el callback coincide exactamente con el autorizado: `http://localhost:8080/login/oauth2/code/google`. La cuenta debe registrarse primero y vincular Google desde **Mi perfil**; no se asocian cuentas automáticamente por correo.

En producción usar HTTPS y `COOKIE_SECURE=true`. Configurar la emisión de `X-Forwarded-*` únicamente desde el proxy confiable y establecer `JWT_ISSUER` con el issuer estable del servicio. No guardar secretos en el frontend, en variables `VITE_*` ni en Git.

## Recorridos implementados

- Registro público con nombre, apellido, DNI, nacimiento, dirección, correo único y clave. Se puede iniciar sesión inmediatamente después de registrarse; no se envían correos.
- Inicio y cierre de sesión con JWT propio de 15 minutos en cookie `HttpOnly`, `Secure` y `SameSite=Lax` según el entorno. Las solicitudes que modifican datos validan un token CSRF separado.
- Perfil editable, incluido el correo usado para iniciar sesión. DNI/correo tienen restricciones únicas en PostgreSQL.
- Cambio de clave que requiere la clave actual y cierra la sesión local.
- Vinculación Google OIDC con autorización y PKCE; el vínculo se guarda por `issuer` + `subject` verificados. La sesión transitoria OAuth se invalida al terminar.
- Capa MVC: controladores REST → servicios con reglas de negocio → repositorio JPA; DTO con validación de entrada y entidad separada. El algoritmo de actualización del perfil usa Template Method para fijar orden de autorización, validación y mutación.

Los JWT no se renuevan automáticamente. El logout y el cambio de clave revocan las sesiones emitidas incrementando la versión de sesión de la cuenta; la API consulta esa versión en PostgreSQL para cada token. No se guardan tokens OAuth para llamadas posteriores.

## Pendientes antes de producción

- **Pendiente para una etapa posterior:** preparar y revisar el despliegue en DonWeb. Antes confirmar el plan contratado (Cloud/VPS compatible), dominio/DNS, proxy TLS, variables y secretos, respaldo/restauración y procedimiento de rollback. No desplegar hasta revisar esos puntos y contar con autorización expresa.
- Mover o complementar el límite de intentos (10 inicios/min y 5 registros/reenvíos/h por IP, actualmente en memoria por proceso) en el proxy o almacén compartido si se ejecutan varias instancias.
- Definir la entrega segura del secreto JWT y rotación de claves para el entorno DonWeb contratado.
- Ejecutar y documentar pruebas de integración, flujo OAuth/CSRF y revisión visual responsive en el entorno elegido. No se ejecutaron builds ni pruebas durante este cambio.
- Revisar con un revisor independiente de otra familia de modelos antes de dar el cambio por aprobado.

La rúbrica DonWeb requiere confirmar el plan Cloud/VPS, proxy TLS, exposición de puertos, copias y restauración; esto no configura ni publica infraestructura.

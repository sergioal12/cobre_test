
## 1. Herramientas de IA Utilizadas

1. **Google Gemini Notebook (`notebook.google.com`)**:
   - Cuaderno de trabajo: `https://notebook.google.com/notebook/006c760d-9f23-42eb-950e-7ee0b2d73ac4` (`prueba_cobre`).
   - **Caso de uso**: Análisis inicial del enunciado, lluvia de ideas sobre la arquitectura del sistema, definición preliminar de diagramas de flujo de datos (DFD) y estructura de paquetes de la Arquitectura Hexagonal.
2. **Google Antigravity (AGY) + Chrome DevTools MCP**:
   - **Caso de uso**: Conexión directa mediante Model Context Protocol (`chrome-devtools-mcp`) al navegador Chrome para leer el cuaderno de Gemini Notebook, contrastar el diseño conceptual con el archivo real [`notification_events.json`](./notification_events.json), estructurar el proyecto Java 21 + Spring Boot 3.4, contenerizar el entorno con Docker/Makefile, implementar las capas Hexagonales y ejecutar las pruebas automatizadas.

---

## 2. Casos de Uso y Prompts Detallados por Fase

### Fase 1: Planeación Conceptual y Diseño de Flujo de Datos (en Gemini Notebook)

- **Prompt 1 (Diseño general y arquitectura)**:
  > *"a partir de la siguiente tarea, debo generar una nueva aplicacion en java, en primer lugar quiero planear como funcionara [Enunciado completo del Challenge de Cobre]"*
  - **Resultado obtenido**: Definición de los dos motores principales (*Async Webhook Engine* y *API REST Self-Service*), propuesta inicial de capas (`domain`, `application`, `infrastructure`) y selección inicial de vulnerabilidades OWASP Top 10 (BOLA/IDOR, SSRF y firma HMAC-SHA256).

- **Prompt 2 (Diagramas de Flujo de Datos)**:
  > *"revisemos primero el diagrama de flujo de datos"*
  - **Resultado obtenido**: Secuencia paso a paso para la validación de suscripción por `client_id`, despacho HTTPS con `WebClient`, estrategia de reintentos con *Exponential Backoff + Jitter*, registro de auditoría en `DeliveryLog` y flujo de `POST /notification_events/{id}/replay`.

- **Prompt 3 (Estructura base en Spring Boot)**:
  > *"conectar a antigravity para empezar a definir el proyecto"*
  - **Resultado obtenido**: Propuesta de dependencias Maven y primer borrador de `NotificationEvent.java` y `NotificationEventRepositoryPort.java`.

---

### Fase 2: Revisión Crítica e Integración del Cuaderno desde Antigravity

- **Prompts utilizados en Antigravity**:
  > *"revisa este cuaderno https://notebook.google.com/notebook/006c760d-9f23-42eb-950e-7ee0b2d73ac4"*
  > *"usando chrome dev tools, revisa el contenido del cuaderno"*

- **Ajustes y mejoras de ingeniería aplicados sobre el borrador inicial de la IA**:
  1. **Desacoplamiento real en Arquitectura Hexagonal**: El borrador del cuaderno acoplaba `NotificationEventRepositoryPort` a `org.springframework.data.domain.Page` y `Pageable`. Se rediseñó [`NotificationEventRepositoryPort`](./src/main/java/com/cobre/notification/application/port/outbound/NotificationEventRepositoryPort.java) usando tipos puros de Java (`List<NotificationEvent>`, `Instant`, `DeliveryStatus`) para mantener cero dependencias de Spring en `domain` y `application`.
  2. **Alineación con el esquema real de `notification_events.json`**: El borrador asumía un `UUID` para el ID del evento y estados en mayúsculas (`DELIVERED`). Al inspeccionar [`notification_events.json`](./notification_events.json), se adaptó el modelo para soportar identificadores de negocio tipo String (`"EVT001"`..`"EVT010"`), estados en minúsculas (`"completed"` y `"failed"`), y los campos exactos `event_id`, `event_type`, `content`, `delivery_date`, `delivery_status` y `client_id`.

---

### Fase 3: Scaffolding, Entorno Docker y Automatización con Makefile

- **Prompts utilizados**:
  > *"este es el archivo /Users/sergioplatagomez/Documents/cobre/prueba_tecnica_cobre/notification_events.json, pero primero, estructura un proyecto java base, con spring boot y demas dependencias necesarias"*
  > *"prefiero levantar todo en docker; prepara un archivo docker con lo necesario y si se necesita, un docker compose"*
  > *"prepara un archivo makefile con las instrucciones para levantar/probar el entorno"*
  > *"prueba los 2 proceso make up y make test"*

- **Artefactos generados y verificados**:
  - [`Dockerfile`](./Dockerfile) multi-stage (`maven:3.9.9-eclipse-temurin-21-alpine` para compilación/test y `eclipse-temurin:21-jre-alpine` con usuario sin privilegios `cobre` para runtime).
  - [`docker-compose.yml`](./docker-compose.yml) orquestando `cobre-postgres` (`postgres:16-alpine`), `cobre-notification-service` y el contenedor `test` con volumen persistente para caché `.m2`.
  - [`Makefile`](./Makefile) con comandos `make up`, `make test`, `make health`, `make metrics` y pruebas rápidas de endpoints (`make api-list`, `make api-filter-failed`, `make api-detail`, `make api-replay`).

---

### Fase 4: Implementación Incremental por Capas y Suite de Tests

- **Prompts utilizados**:
  > *"si, primero has la implementacion en DB"*
  > *"pasar a la aplicacion"*
  > *"implementa la capa de entrada"*

- **Validación técnica realizada**:
  1. **Persistencia (`infrastructure/adapter/outbound/persistence/`)**: Uso de `JpaSpecificationExecutor` en [`JpaNotificationEventRepositoryAdapter`](./src/main/java/com/cobre/notification/infrastructure/adapter/outbound/persistence/JpaNotificationEventRepositoryAdapter.java) para evitar errores de inferencia de tipos `null` en fechas con el driver de PostgreSQL, más sembrado idempotente en [`NotificationEventsDataSeeder`](./src/main/java/com/cobre/notification/infrastructure/adapter/outbound/persistence/NotificationEventsDataSeeder.java).
  2. **Aplicación y Motor Webhook (`application/service/` y `infrastructure/adapter/outbound/http/`)**: Validación de suscripción activa por cliente, reintentos con *Exponential Backoff + Jitter*, firma `HMAC-SHA256` en [`HmacSignatureGenerator`](./src/main/java/com/cobre/notification/infrastructure/adapter/outbound/http/HmacSignatureGenerator.java) y validación DNS anti-SSRF en [`WebhookUrlSecurityValidator`](./src/main/java/com/cobre/notification/infrastructure/adapter/outbound/http/WebhookUrlSecurityValidator.java).
  3. **API REST Self-Service (`infrastructure/adapter/inbound/rest/`)**: Implementación de [`NotificationEventController`](./src/main/java/com/cobre/notification/infrastructure/adapter/inbound/rest/NotificationEventController.java) y [`GlobalExceptionHandler`](./src/main/java/com/cobre/notification/infrastructure/adapter/inbound/rest/GlobalExceptionHandler.java), verificados con 13 pruebas automatizadas (`MockMvc` + `MockWebServer`) y pruebas reales contra el contenedor Docker.


# Cobre Event Notification Service & Self-Service API

Microservicio en **Java 21 + Spring Boot 3.4** diseñado bajo **Arquitectura Hexagonal (Ports & Adapters)** para resolver el reto técnico de **Cobre**: entrega resiliente de notificaciones de eventos vía webhooks HTTPS y API REST Self-Service para consulta y reenvío (*replay*) de eventos por cliente.

---

## Documentación del Reto

- **[ARCHITECTURE.md](./ARCHITECTURE.md)**:
  - **Task 1 (System Design)**: Arquitectura cloud-native orientada a eventos, diagramas C4 (Contexto, Contenedores y Componentes Hexagonales), diagramas de secuencia, modelo entidad-relación y decisiones de arquitectura (ADRs).
  - **Task 3 (Security - OWASP Top 10)**: Análisis de 4 vulnerabilidades críticas (**BOLA/IDOR**, **SSRF**, **Falta de Integridad/Autenticidad en Webhooks**, y **Consumo Irrestricto de Recursos**) junto con las mitigaciones implementadas en el código.
- **[AI_USAGE.md](./AI_USAGE.md)**:
  - Registro detallado del flujo de trabajo asistido por IA (**Google Gemini Notebook** + **Google Antigravity con Chrome DevTools MCP**), prompts utilizados en cada fase y validaciones/correcciones técnicas aplicadas.

---

## Requisitos e Inicio Rápido (100% Docker)

No necesitas tener Java ni Maven instalados localmente; todo el ciclo de compilación, ejecución, base de datos PostgreSQL y pruebas automatizadas corre sobre **Docker** y **Docker Compose** mediante el `Makefile`.

### 1. Levantar el entorno (`PostgreSQL` + `Spring Boot App`)
```bash
make up
```
- **API Base URL**: `http://localhost:8080`
- **Swagger UI (OpenAPI)**: `http://localhost:8080/swagger-ui.html`
- **Healthcheck**: `http://localhost:8080/actuator/health`
- **Métricas Prometheus**: `http://localhost:8080/actuator/prometheus`

Al iniciar, [`NotificationEventsDataSeeder`](./src/main/java/com/cobre/notification/infrastructure/adapter/outbound/persistence/NotificationEventsDataSeeder.java) carga automáticamente los 10 eventos de [`notification_events.json`](./notification_events.json) y las suscripciones activas para `CLIENT001`, `CLIENT002` y `CLIENT003`.

### 2. Ejecutar la suite de pruebas (`JUnit 5` + `MockMvc` + `MockWebServer`)
```bash
make test
```

### 3. Ver todos los comandos disponibles
```bash
make help
```

---

## Endpoints de la API REST Self-Service (Task 2)

> **Autenticación JWT y Aislamiento Multi-Tenant Obligatorio**:
> 1. Obtén un token JWT firmado (`HS256`) autenticando las credenciales del cliente en `POST /auth/token` (`client_id` + `client_secret`).
>    - Credenciales demo configuradas en [`application.yml`](./src/main/resources/application.yml):
>      - `CLIENT001` / `client001-demo-secret`
>      - `CLIENT002` / `client002-demo-secret`
>      - `CLIENT003` / `client003-demo-secret`
> 2. Envía el encabezado `Authorization: Bearer <access_token>` en todas las peticiones hacia `/notification_events/**`.
> 3. El controlador extrae la identidad del cliente exclusivamente del token JWT autenticado y bloquea cualquier intento de suplantación (`403 Forbidden`) o acceso cruzado a recursos de otro cliente (`404 Not Found`).

### 0. Generar y validar token JWT
```bash
# Generar token JWT para CLIENT001
make api-jwt CLIENT_ID=CLIENT001
```

### 1. Consultar eventos de notificación por cliente
`GET /notification_events`

**Parámetros de filtrado opcionales:**
- `delivery_status`: `completed` | `failed` | `pending` | `retrying`
- `from_date` (o `creation_date_from`): Fecha `YYYY-MM-DD` (ej. `2024-03-15`) o timestamp ISO-8601 (`2024-03-15T09:00:00Z`)
- `to_date` (o `creation_date_to`): Fecha `YYYY-MM-DD` o timestamp ISO-8601
- `creation_date` (o `delivery_date`): Filtra un día completo (`2024-03-15`) o instante exacto

**Ejemplos:**
```bash
# Listar todos los eventos de CLIENT001
make api-list CLIENT_ID=CLIENT001

# Filtrar eventos fallidos de CLIENT002
make api-filter-failed CLIENT_ID=CLIENT002 STATUS=failed

# Filtrar por rango de fechas y estado con Bearer JWT
TOKEN=$(curl -sS -X POST -H "Content-Type: application/json" \
  -d '{"client_id":"CLIENT001","client_secret":"client001-demo-secret"}' \
  http://localhost:8080/auth/token | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -sS -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/notification_events?delivery_status=completed&from_date=2024-03-15T09:00:00Z&to_date=2024-03-15T16:00:00Z"
```

### 2. Obtener el detalle de una notificación e historial de intentos
`GET /notification_events/{notification_event_id}`

```bash
make api-detail CLIENT_ID=CLIENT001 EVENT_ID=EVT001
```

### 3. Reenviar (*Replay*) una notificación fallida hacia el Webhook HTTPS
`POST /notification_events/{notification_event_id}/replay`

- Solo permite reenviar eventos cuyo `delivery_status` sea `failed` (`EVT003` de `CLIENT002`, `EVT005` y `EVT009` de `CLIENT003`). Si el evento ya está `completed`, devuelve `409 Conflict`.
- **Configuración de la URL Webhook para el día de la presentación**:
  1. **Por variable de entorno**: `COBRE_WEBHOOK_URL=https://tu-url-webhook.com/endpoint docker compose up -d`
  2. **Por petición en vivo (Body JSON o Header `X-Webhook-Url`)**:
     ```bash
     TOKEN=$(curl -sS -X POST -H "Content-Type: application/json" \
       -d '{"client_id":"CLIENT003","client_secret":"client003-demo-secret"}' \
       http://localhost:8080/auth/token | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

     curl -i -X POST -H "Authorization: Bearer $TOKEN" \
       -H "Content-Type: application/json" \
       -d '{"webhook_url": "https://postman-echo.com/post"}' \
       "http://localhost:8080/notification_events/EVT005/replay"
     ```

---

## Estructura Hexagonal del Proyecto

```text
src/main/java/com/cobre/notification
├── domain/                               # Núcleo de negocio puro (Cero dependencias de Spring)
│   ├── model/
│   │   ├── NotificationEvent.java        # Agregado raíz y máquina de estados de entrega
│   │   ├── DeliveryAttempt.java          # Registro inmutable de cada intento HTTP
│   │   ├── DeliveryStatus.java           # Enum: pending, retrying, completed, failed
│   │   └── WebhookSubscription.java      # Suscripción y validación de propiedad por cliente
│   └── exception/                        # Excepciones de dominio
├── application/                          # Casos de Uso y Puertos (Inbound / Outbound)
│   ├── port/
│   │   ├── inbound/                      # QueryNotificationEventsUseCase, ReplayNotificationUseCase, DeliverNotificationUseCase
│   │   └── outbound/                     # NotificationEventRepositoryPort, WebhookDeliveryPort, SubscriptionPort, NotificationMetricsPort
│   └── service/                          # Implementación de los Casos de Uso y estrategia de reintentos
└── infrastructure/                       # Adaptadores tecnológicos y configuración Spring Boot
    ├── adapter/
    │   ├── inbound/rest/                 # Controlador REST, DTOs, Mapper y GlobalExceptionHandler
    │   └── outbound/
    │       ├── persistence/              # Entidades JPA, Repositorios, Adaptadores y Seeder JSON
    │       ├── http/                     # WebClient HTTPS Dispatcher, Firma HMAC-SHA256 y Validador Anti-SSRF
    │       └── metrics/                  # Adaptador Micrometer / Prometheus
    └── config/                           # Configuración de Seguridad, WebClient, OpenAPI y Propiedades
```


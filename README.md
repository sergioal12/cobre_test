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

> **Aislamiento Multi-Tenant Obligatorio**: Todas las peticiones requieren identificar al cliente mediante el encabezado `X-Client-Id` (por ejemplo: `CLIENT001`, `CLIENT002`, `CLIENT003`) o el parámetro `client_id`. Un cliente jamás puede consultar ni reenviar eventos de otro cliente.

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

# Filtrar por rango de fechas y estado
curl -sS -H "X-Client-Id: CLIENT001" \
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
     curl -i -X POST -H "X-Client-Id: CLIENT003" \
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


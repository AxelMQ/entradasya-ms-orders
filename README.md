# Microservicio ms-orders (Orquestador de Órdenes y Saga de Compra)

Microservicio orquestador central del sistema **EntradasYa**. Coordina de manera atómica el flujo transaccional de compra mediante el patrón Saga: reserva de cupos con `ms-events`, procesamiento de cobro con `ms-payments`, emisión de boletos con `ms-tickets` y despacho de correos con `ms-notifications`.

* **Tecnología:** Java 21 LTS / Spring Boot 3.3.5 (Spring Web, Spring Data JPA, WebClient, Spring Validation)
* **Puerto:** `8003`
* **Base de datos:** `orders_db` (PostgreSQL 16)
* **Tipo de Comunicación:** REST / JSON con HTTP WebClient reactivo

---

## 1. Características Principales

1. **Orquestador Saga:**
   - Valida cantidad de entradas (1 a 6).
   - Registra la orden en estado `PENDING`.
   - Invoca reserva en `ms-events`. Si faltan cupos (409 Conflict), marca la orden como `REJECTED (OUT_OF_STOCK)`.
   - Invoca cobro en `ms-payments`.
     - Si el pago es rechazado (ej. 402 Fondos Insuficientes), ejecuta transacción compensatoria liberando cupos en `ms-events` y marca la orden como `REJECTED (PAYMENT_DECLINED)`.
     - Si el pago es aprobado, actualiza a `PAID`, emite boletos en `ms-tickets` y envía confirmación en `ms-notifications`.
2. **Resiliencia ante Fallos no Críticos:**
   - Si `ms-notifications` no está disponible o falla, captura la excepción, registra log de advertencia y mantiene la orden como `PAID` válida.
3. **Seguridad JWT HS256 Descentralizada:**
   - Validación local de tokens JWT con claims `sub`, `email`, `role`.
4. **Timeouts Específicos por Cliente WebClient:**
   - `EventsClient`: connect 2s, read 3s.
   - `PaymentsClient`: connect 2s, read 5s.
   - `TicketsClient`: connect 2s, read 10s.
   - `NotificationsClient`: connect 2s, read 3s.

---

## 2. Variables de Entorno

```properties
SERVER_PORT=8003
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/orders_db
SPRING_DATASOURCE_USERNAME=orders_user
SPRING_DATASOURCE_PASSWORD=orders_pass
JWT_SECRET=super-secure-shared-secret-entradasya-2026
INTERNAL_TOKEN=internal-service-secret-entradasya-2026
SERVICES_EVENTS_URL=http://localhost:8002
SERVICES_PAYMENTS_URL=http://localhost:8004
SERVICES_TICKETS_URL=http://localhost:8005
SERVICES_NOTIFICATIONS_URL=http://localhost:8006
LOG_LEVEL=INFO
```

---

## 3. Endpoints de la API

* `POST /api/v1/orders`: Inicia y orquesta la compra de entradas (Cliente autenticado).
* `GET /api/v1/orders/my-orders`: Historial de compras del cliente autenticado.
* `GET /api/v1/orders/{id}`: Detalle completo de una orden (Cliente propietario o Staff).
* `GET /api/v1/orders/by-event/{eventId}`: Reporte de ventas y recaudación por evento (Organizador o Admin).
* `GET /health/live`: Liveness probe (200 UP).
* `GET /health/ready`: Readiness probe (verifica conexión activa a `orders_db`, 200 o 503).

---

## 4. Pruebas y Compilación

Ejecutar suite completa de pruebas:
```bash
./mvnw test
```

Compilar empaquetado JAR ejecutable:
```bash
./mvnw clean package -DskipTests
```

Ejecutar servicio en desarrollo:
```bash
./mvnw spring-boot:run
```

## Estado de Verificacion CI/CD

- Pipeline `.github/workflows/ci-develop.yml` verificado con disparo automatico en rama `develop`, generando artifacts descargables e imagenes en GHCR y Docker Hub.

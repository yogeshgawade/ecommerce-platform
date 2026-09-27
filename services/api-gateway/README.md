# API Gateway

Spring Cloud Gateway - single entry point for all client requests.

## Routes

| Path | Service | Port | Auth |
|------|---------|------|------|
| `/auth/**` | auth-service | 8082 | No |
| `/api/products/**` | catalog-service | 8081 | No |
| `/api/carts/**` | cart-service | 8001 | Yes |

## Run

```bash
./gradlew bootRun
```

## Health

```bash
curl http://localhost:8080/actuator/health
```

# Auth Service

Auth Service manages user accounts, issues HS256 access tokens, and stores only hashes of refresh-token secrets in PostgreSQL.

## Configuration

| Variable | Property | Purpose |
|---|---|---|
| `APP_JWT_SECRET` | `app.jwt.secret` | Shared signing key. Auth Service, API Gateway, and services validating access tokens must use the same value. |
| `APP_JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` | `app.jwt.access-token-expiration-seconds` | Access-token lifetime; defaults to 900 seconds. |
| `APP_JWT_REFRESH_TOKEN_EXPIRATION_SECONDS` | `app.jwt.refresh-token-expiration-seconds` | Refresh-token lifetime; defaults to 604800 seconds. |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | PostgreSQL connection URL. |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | PostgreSQL username. |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | PostgreSQL password. |

Docker Compose supplies the JWT secret and database credentials from the project-level environment file. Local fallback values are for development only.

## Endpoints

- `POST /auth/register` creates a customer account. Roles cannot be selected during registration.
- `POST /auth/login` returns an access token and refresh token.
- `POST /auth/refresh` rotates a refresh token. A token can win only one concurrent rotation.
- `POST /auth/logout` revokes the supplied refresh token.
- `GET /auth/me` requires a valid bearer access token.

Access tokens carry `sub`, `email`, and `roles` claims. Protected Auth Service routes validate the signature, expiration, subject, and role claims locally, in addition to gateway validation.

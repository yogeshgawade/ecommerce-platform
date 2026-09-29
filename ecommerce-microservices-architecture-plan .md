# E-Commerce Platform — Architecture & Delivery Plan
**Production-grade microservices portfolio project**

---

## Table of Contents
1. [Project Goals](#1-project-goals)
2. [System Architecture Overview](#2-system-architecture-overview)
3. [Service Inventory](#3-service-inventory)
4. [Data Architecture & Polyglot Persistence](#4-data-architecture--polyglot-persistence)
5. [Event-Driven Communication & the Order Saga](#5-event-driven-communication--the-order-saga)
6. [API Gateway, Auth & Security](#6-api-gateway-auth--security)
7. [Frontend Architecture (React)](#7-frontend-architecture-react)
8. [Search & Caching](#8-search--caching)
9. [Testing Strategy](#9-testing-strategy)
10. [Infrastructure & AWS ECS Deployment](#10-infrastructure--aws-ecs-deployment)
11. [CI/CD Pipeline](#11-cicd-pipeline)
12. [Observability & Resilience](#12-observability--resilience)
13. [Repository Structure](#13-repository-structure)
14. [Development Roadmap](#14-development-roadmap)
15. [Tech Stack Summary](#15-tech-stack-summary)
16. [Stretch Goals](#16-stretch-goals)
17. [Portfolio Presentation Tips](#17-portfolio-presentation-tips)

---

## 1. Project Goals

This isn't a CRUD app with a payment button bolted on. The point of building it this way is to demonstrate:

- **Service decomposition** around real bounded contexts (not just "split the monolith into 10 folders")
- **Deliberate polyglot backend** — Spring Boot where strong consistency/enterprise patterns matter, FastAPI where I/O-bound speed and Python's ecosystem matter — with a stated reason for each choice, not a random mix
- **Event-driven architecture** using the Saga pattern for distributed transactions (order → inventory → payment)
- **Polyglot persistence** — the right database per service, not one Postgres instance for everything
- **Cloud-native deployment** on AWS ECS with proper health checks, autoscaling, and an automated deploy pipeline
- **Observability, security, and resilience** as first-class concerns, not afterthoughts
- **Testing discipline** including integration tests against real infra (Testcontainers), not just mocked unit tests

**Audience:** engineers and hiring managers evaluating system design maturity — the goal is to show you understand *trade-offs*, not just that you can wire up a REST API.

---

## 2. System Architecture Overview

```
Client (React — Customer App + Admin Dashboard)
        │  HTTPS / JSON
        ▼
API Gateway   (Spring Cloud Gateway — JWT validation, routing, rate limiting)
        │
        ├── auth-service          [Spring Boot]  → PostgreSQL
        ├── catalog-service       [Spring Boot]  → MongoDB
        ├── inventory-service     [Spring Boot]  → PostgreSQL
        ├── order-service         [Spring Boot]  → PostgreSQL   (saga participant)
        ├── cart-service          [FastAPI]      → Redis
        ├── payment-service       [FastAPI]      → PostgreSQL   (+ Stripe)
        ├── search-service        [FastAPI]      → Elasticsearch
        ├── wishlist-service      [FastAPI]      → MongoDB
        ├── review-service        [FastAPI]      → MongoDB
        └── notification-service  [FastAPI]      → PostgreSQL (delivery log only)

Cross-cutting:
        Kafka Event Bus  — order-events, inventory-events, payment-events, catalog-events, review-events
        Redis            — caching + cart storage
        Elasticsearch    — search index (derived data, synced from catalog-service)
```

Two communication styles are used **deliberately, not interchangeably**:

- **Synchronous (internal REST)** — for request/response queries where the caller needs an immediate answer. Example: `cart-service` calls `catalog-service` to fetch live price/stock when rendering a cart. Kept minimal to avoid tight coupling.
- **Asynchronous (Kafka events)** — for workflow propagation and state changes other services need to react to eventually. Example: order lifecycle, inventory reservation, search index updates.

---

## 3. Service Inventory

| # | Service | Stack | Database | Responsibility | Tier |
|---|---------|-------|----------|-----------------|------|
| 1 | `auth-service` | Spring Boot | PostgreSQL | Register/login, JWT issuance + refresh, roles | Core |
| 2 | `catalog-service` | Spring Boot | MongoDB | Product & category CRUD, admin-gated writes | Core |
| 3 | `cart-service` | FastAPI | Redis | Add/update/remove cart items, TTL-based cart | Core |
| 4 | `inventory-service` | Spring Boot | PostgreSQL | Stock levels, reservation/release (saga participant) | Core |
| 5 | `order-service` | Spring Boot | PostgreSQL | Order lifecycle, saga coordination | Core |
| 6 | `payment-service` | FastAPI | PostgreSQL | Stripe checkout + webhook handling | Core |
| 7 | `api-gateway` | Spring Cloud Gateway | — | Single entry point, auth, routing, rate limits | Core |
| 8 | `search-service` | FastAPI | Elasticsearch | Full-text search, facets (category/price/brand/rating) | Extended |
| 9 | `wishlist-service` | FastAPI | MongoDB | Save/remove wishlist items | Extended |
| 10 | `review-service` | FastAPI | MongoDB | Product reviews & ratings | Extended |
| 11 | `notification-service` | FastAPI | PostgreSQL | Order/email notifications, delivery log | Extended |
| 12 | `recommendation-service` | FastAPI | reads catalog+order data | "You might also like" via simple collaborative filtering | Stretch |
| 13 | `analytics-service` | Spring Boot or FastAPI | reads across services | Admin dashboard aggregation (revenue, top products) — BFF pattern | Stretch |

**Build core (1–7) first.** It's a complete, checkout-capable store. Extended (8–11) makes it "full-featured." Stretch (12–13) is what makes a reviewer stop scrolling.

### Notable endpoint sketches

**auth-service**
```
POST /auth/register
POST /auth/login          → { accessToken, refreshToken }
POST /auth/refresh
GET  /auth/me
```

**order-service**
```
POST /orders               → creates order (PENDING), kicks off saga
GET  /orders/{id}
GET  /orders?userId=...
PATCH /orders/{id}/cancel
```

**search-service**
```
GET /search?q=running+shoes&category=footwear&minPrice=20&maxPrice=100&sort=rating
```

---

## 4. Data Architecture & Polyglot Persistence

**Principle: database-per-service.** Each service owns its data exclusively — no other service touches its tables/collections directly. This buys independent scaling and schema evolution, at the cost of needing events (not joins) to keep data in sync across services. That trade-off is the whole point of the pattern, and it's worth stating explicitly in your README.

| Service | Database | Why |
|---------|----------|-----|
| Auth | PostgreSQL | Relational, ACID guarantees for credentials/roles |
| Catalog | MongoDB | Products have variable attributes (size/color vs. specs) — flexible schema fits better than rigid columns |
| Inventory | PostgreSQL | Stock counts need strict transactional integrity |
| Order | PostgreSQL | Multi-step transactional workflow, strong consistency |
| Cart | Redis | Ephemeral, fast, natural TTL expiry |
| Payment | PostgreSQL | Financial records need an audit trail |
| Search | Elasticsearch | Derived index — not a source of truth, rebuilt from catalog events |
| Wishlist | MongoDB | Simple document store, flexible |
| Review | MongoDB | Flexible schema, high read volume |
| Notification | PostgreSQL | Minimal — just a delivery log |

**How the search index stays in sync:** `catalog-service` publishes `ProductCreated` / `ProductUpdated` / `ProductDeleted` events to Kafka. `search-service` consumes them and updates Elasticsearch. This avoids a dual-write (write to Mongo *and* ES in the same request, which can fail halfway) and keeps Elasticsearch honestly labeled as eventually-consistent derived data.

**Cross-service consistency** is handled via:
- Eventual consistency + events for anything that doesn't need to be instantaneous (search index, notifications)
- The Saga pattern for the one place that truly needs multi-service coordination: order placement (see §5)
- CQRS/Event Sourcing on `order-service` is a good **stretch goal** once the basic saga works — see §16

---

## 5. Event-Driven Communication & the Order Saga

### Kafka topics

| Topic | Producers | Consumers | Purpose |
|-------|-----------|-----------|---------|
| `order-events` | order-service | inventory-service, payment-service, notification-service | Order lifecycle events |
| `inventory-events` | inventory-service | order-service | Reservation success/failure |
| `payment-events` | payment-service | order-service | Payment success/failure |
| `catalog-events` | catalog-service | search-service | Keep search index in sync |
| `review-events` | review-service | search-service | Keep product rating filters and sort current |
| `notification-events` | order-service | notification-service | Trigger emails |

### Order placement saga (choreography-based)

```
1. Client → order-service: POST /orders
2. order-service: create order (PENDING) → publish OrderCreated
3. inventory-service: consume OrderCreated → attempt stock reservation
      success → publish InventoryReserved
      failure → publish InventoryReservationFailed
4. payment-service: consume InventoryReserved → charge via Stripe
      success → publish PaymentCompleted
      failure → publish PaymentFailed
5. order-service: consume PaymentCompleted → order CONFIRMED → publish OrderConfirmed
   order-service: consume PaymentFailed / InventoryReservationFailed
                  → order CANCELLED → publish OrderCancelled (compensating trigger)
6. inventory-service: consume OrderCancelled → release reserved stock (compensation)
7. notification-service: consume OrderConfirmed / OrderCancelled → send email
```

**Why choreography over orchestration:** no extra orchestrator component to build/host, and it's still a legitimate, correct saga implementation — good for a portfolio's infra budget. Mention in your README that an orchestrated version (e.g., via Temporal or a dedicated saga-orchestrator service) is the natural next step for more complex workflows — that awareness matters more than actually building it.

---

## 6. API Gateway, Auth & Security

**Gateway responsibilities:** routing to services, JWT validation on protected routes, rate limiting, request logging. It's the *only* externally exposed HTTP entry point — every other service is ClusterIP-only inside Kubernetes.

**Auth approach:** custom `auth-service` (Spring Security + JWT) rather than delegating to Keycloak — building it yourself demonstrates hands-on security implementation, which is worth more in a portfolio than integrating a third-party IAM box. Mention Keycloak/Auth0 in your README as the production-grade alternative you're aware of — that shows judgment without costing you build time.

```json
// JWT payload shape
{
  "sub": "user-uuid",
  "email": "jane@example.com",
  "roles": ["CUSTOMER"],
  "iat": 1732600000,
  "exp": 1732603600
}
```

- Passwords hashed with bcrypt, never logged
- Access token (short-lived, ~15 min) + refresh token (longer-lived, rotated)
- Roles: `CUSTOMER`, `ADMIN` — admin-only endpoints gated at both gateway and service level (defense in depth)

**Security checklist:**
- TLS everywhere via AWS Certificate Manager (ACM), terminated at the Application Load Balancer
- Secrets in AWS Secrets Manager / SSM Parameter Store, injected into ECS task definitions at runtime (Vault as a stretch alternative) — never baked into images
- Input validation at every service boundary (Bean Validation in Spring, Pydantic in FastAPI)
- CORS locked to your frontend origin(s)
- Rate limiting at the gateway
- Dependency scanning via Dependabot/Snyk — cheap to add, good signal

---

## 7. Frontend Architecture (React)

Two separate apps sharing a component library, both talking to the same API Gateway:

- **`customer-app`** — browse, product detail, search/filter, cart, checkout, wishlist, reviews, order history
- **`admin-dashboard`** — product management, order management, basic analytics, route-protected by `ADMIN` role

**Stack choices:**
- **Server state:** React Query (`@tanstack/react-query`) — handles caching/refetch for API data
- **Client state:** Zustand for lightweight UI state (cart drawer open/closed, filters, etc.)
- **Routing:** React Router
- **API layer:** typed client generated from each service's OpenAPI spec (`openapi-typescript-codegen`) — keeps frontend types honest against backend contracts, a nice concrete "production practice" to point to

---

## 8. Search & Caching

**Search:** Elasticsearch index built from `catalog-events` and `review-events` (see §4). Index mapping supports faceted filtering on category, price range, brand, and rating; sorting by relevance/price/rating.

**Caching (Redis), three distinct uses — worth calling out separately in your README so it doesn't look like "Redis because everyone uses Redis":**
1. Cart storage (the actual data store, not just a cache)
2. Product detail cache with short TTL, invalidated on `ProductUpdated` events
3. Rate-limit counters at the gateway

---

## 9. Testing Strategy

This is the section most portfolio projects skip — doing it well is a strong differentiator.

- **Unit tests:** JUnit 5 + Mockito (Spring Boot services), pytest (FastAPI services)
- **Integration tests:** Testcontainers — spin up real PostgreSQL/Kafka/Redis in CI rather than mocking them. This is the single highest-signal testing practice you can show; it proves you test against real infrastructure behavior, not assumptions about it
- **Contract testing (stretch):** Pact, to catch breaking API changes between services before deploy
- **E2E tests:** Playwright or Cypress against a full `docker-compose up` stack — browse → add to cart → checkout → confirm order
- **Resilience testing (stretch):** kill `payment-service` mid-saga and verify `order-service` correctly compensates and releases inventory — this single demo is extremely convincing evidence you understand distributed systems failure modes

---

## 10. Infrastructure & AWS ECS Deployment

**Docker:** multi-stage builds — Spring Boot services build to a layered JAR on a slim JRE base image; FastAPI services use a slim Python base with a locked `requirements.txt`/`poetry.lock`. Images pushed to **ECR**, one repository per service.

**ECS layout (Fargate launch type — no EC2 instances to manage):**
- One **ECS Cluster** (`ecommerce`) hosting every service
- Each service = one **Task Definition** (container image, CPU/memory, env vars, secrets) + one **ECS Service** (desired count, deployment config, health check grace period)
- **Networking:** a VPC with public subnets (ALB only) and private subnets (all ECS tasks) — no service is directly internet-reachable except through the load balancer
- **Application Load Balancer** in front of `api-gateway` only, with path-based routing if you skip a gateway for some internal debugging convenience; everything else stays private
- **Service discovery:** AWS Cloud Map, giving each service an internal DNS name (`catalog-service.ecommerce.local`) so `api-gateway` and services like `cart-service` can reach each other without hardcoded IPs — this is ECS's equivalent of Kubernetes' ClusterIP+DNS
- **Autoscaling:** Application Auto Scaling with target-tracking policies (e.g., scale `catalog-service`/`search-service`/`cart-service` on CPU or ALB request count) — analogous to a Horizontal Pod Autoscaler
- **Stateful infra (Postgres/Mongo/Redis/Elasticsearch/Kafka):** managed AWS services — RDS (Postgres), DocumentDB or MongoDB Atlas, ElastiCache (Redis), OpenSearch Service, and MSK (managed Kafka) or Confluent Cloud. None of these run as ECS tasks — keeping stateful workloads off Fargate avoids a lot of operational pain for a solo project

```json
// illustrative — catalog-service ECS task definition (abridged)
{
  "family": "catalog-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "catalog-service",
      "image": "<account>.dkr.ecr.<region>.amazonaws.com/catalog-service:latest",
      "portMappings": [{ "containerPort": 8080 }],
      "environment": [{ "name": "SPRING_PROFILES_ACTIVE", "value": "prod" }],
      "secrets": [
        { "name": "DB_PASSWORD", "valueFrom": "arn:aws:secretsmanager:...:catalog-db-password" }
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3
      },
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/catalog-service",
          "awslogs-region": "<region>",
          "awslogs-stream-prefix": "catalog"
        }
      }
    }
  ]
}
```

**Provisioning/packaging:** define the cluster, services, task definitions, ALB, Cloud Map namespace, and IAM roles as code with **Terraform** (or **AWS CDK** if you'd rather stay in TypeScript/Java/Python end-to-end). **AWS Copilot CLI** is a good lighter-weight alternative if you want ECS up fast without hand-rolling Terraform for every resource — worth mentioning as the "pragmatic" option in your README alongside the Terraform version.

---

## 11. CI/CD Pipeline

- **GitHub Actions**, path-filtered per service (in a monorepo, only rebuild what changed)
- Pipeline: lint → test (Testcontainers) → build → Docker build & push to ECR → deploy
- **Deploy step:** either a rolling update (`aws ecs update-service --force-new-deployment` with a new task definition revision) or, for a stronger portfolio signal, **blue/green via AWS CodeDeploy** — CodeDeploy shifts traffic between two target groups behind the ALB and can auto-rollback on failed health checks, which is a great concrete thing to demo and write about

```yaml
# illustrative — services/catalog-service/.github/workflows/ci.yml
name: catalog-service-ci
on:
  push:
    paths: ['services/catalog-service/**']
jobs:
  build-test-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - name: Run tests (Testcontainers spins up Postgres + Kafka)
        run: ./gradlew test
        working-directory: services/catalog-service
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v4
        with: { role-to-assume: ${{ secrets.AWS_DEPLOY_ROLE }}, aws-region: <region> }
      - name: Build, tag, push to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_REGISTRY
          docker build -t $ECR_REGISTRY/catalog-service:${{ github.sha }} .
          docker push $ECR_REGISTRY/catalog-service:${{ github.sha }}
        working-directory: services/catalog-service
      - name: Deploy new task definition
        run: |
          aws ecs register-task-definition --cli-input-json file://task-def.json
          aws ecs update-service --cluster ecommerce --service catalog-service --force-new-deployment
```

---

## 12. Observability & Resilience

- **Logging:** structured JSON (Logback JSON encoder for Spring, `structlog` for FastAPI) shipped via the `awslogs` driver straight to **CloudWatch Logs** (one log group per service, as in the task definition above). CloudWatch **Container Insights** gives cluster/service-level CPU, memory, and network dashboards with no extra setup. If you want Grafana-style dashboards instead, run a self-hosted Grafana pointed at CloudWatch as a data source
- **Metrics:** Spring Boot Actuator + Micrometer, and `prometheus-fastapi-instrumentator` for FastAPI, exported either to CloudWatch custom metrics or to a self-hosted Prometheus (running as its own ECS service) with Grafana dashboards
- **Tracing:** OpenTelemetry SDK in every service, exported to **AWS X-Ray** (native ECS integration via the X-Ray daemon sidecar) — or Jaeger if self-hosted tracing is preferred. Propagate trace IDs through Kafka message headers too, so a single trace spans both sync REST calls *and* async event chains — this is the detail that separates "added tracing" from "actually understands distributed tracing"
- **Resilience:** Resilience4j (circuit breaker, retry, bulkhead) on Spring services calling Stripe or other services; `tenacity` for retry logic in FastAPI
- **Health checks:** container health checks in each task definition (see §10) plus ALB target group health checks, feeding ECS's own restart/traffic-shifting decisions

---

## 13. Repository Structure

```
ecommerce-platform/
├── services/
│   ├── auth-service/            (Spring Boot)
│   ├── catalog-service/         (Spring Boot)
│   ├── inventory-service/       (Spring Boot)
│   ├── order-service/           (Spring Boot)
│   ├── cart-service/            (FastAPI)
│   ├── payment-service/         (FastAPI)
│   ├── search-service/          (FastAPI)
│   ├── wishlist-service/        (FastAPI)
│   ├── review-service/          (FastAPI)
│   └── notification-service/    (FastAPI)
├── gateway/
│   └── api-gateway/             (Spring Cloud Gateway)
├── frontend/
│   ├── customer-app/            (React)
│   ├── admin-dashboard/         (React)
│   └── shared-components/
├── infra/
│   ├── terraform/                (VPC, ECS cluster, services, ALB, Cloud Map, IAM)
│   ├── task-defs/                 (ECS task definition JSON per service)
│   └── docker-compose.yml       (full local stack)
├── docs/
│   ├── architecture.md
│   ├── api-contracts/           (OpenAPI specs per service)
│   └── diagrams/
└── .github/workflows/           (per-service CI pipelines)
```

Monorepo recommended for a solo portfolio project — one CI setup, easier for a reviewer to explore in a single clone, still cleanly separated by folder.

---

## 14. Development Roadmap

No dates — phases, since you're working without a fixed deadline. Each phase is a legitimately demoable milestone.

**Phase 0 — Foundations**
Repo scaffolding, `docker-compose.yml` for local infra (Postgres, Mongo, Redis, Kafka, Elasticsearch), service skeletons, shared conventions (error format, logging format, `/health` endpoints).

**Phase 1 — Core Commerce Loop**
`auth-service`, `catalog-service`, `cart-service`, `api-gateway`, customer-app (browse, product detail, cart).

**Phase 2 — Checkout & Fulfillment**
`order-service` + saga, `inventory-service`, `payment-service` (Stripe test mode), `notification-service`, checkout flow in the UI.

**Phase 3 — Discovery & Engagement**
`search-service` (+ ES sync), `wishlist-service`, `review-service`, search/filter UI, wishlist & reviews UI.

**Phase 4 — Admin Panel**
`admin-dashboard`, admin-gated endpoints across services, RBAC enforcement end-to-end.

**Phase 5 — Observability & Resilience**
Centralized logging, metrics dashboards, distributed tracing, circuit breakers, the "kill a service mid-saga" resilience demo.

**Phase 6 — Cloud-Native Deployment**
Dockerize everything, provision the VPC/ECS cluster/ALB/Cloud Map via Terraform, deploy each service as an ECS Fargate service, ACM-issued TLS on the ALB, GitHub Actions → ECR → ECS (or CodeDeploy blue/green) pipeline.

**Phase 7 — Polish for Portfolio**
Architecture diagrams, demo video, root + per-service READMEs, design-decision write-ups, live demo link if hosting budget allows.

---

## 15. Tech Stack Summary

| Layer | Technology |
|-------|------------|
| Frontend | React, React Query, Zustand, React Router |
| Backend (transactional) | Spring Boot, Spring Security, Spring Cloud Gateway |
| Backend (I/O-bound) | FastAPI, Pydantic |
| Relational DB | PostgreSQL |
| Document DB | MongoDB |
| Cache / ephemeral store | Redis |
| Search | Elasticsearch |
| Messaging | Apache Kafka |
| Payments | Stripe |
| Containers | Docker |
| Orchestration | AWS ECS (Fargate) |
| Networking/Discovery | ALB, AWS Cloud Map, VPC (public/private subnets) |
| Provisioning | Terraform (or AWS CDK / Copilot CLI) |
| CI/CD | GitHub Actions → ECR → ECS (rolling or CodeDeploy blue/green) |
| Logging | Loki or EFK + Grafana/Kibana |
| Metrics | Prometheus + Grafana |
| Tracing | OpenTelemetry + Jaeger |
| Resilience | Resilience4j, tenacity |
| Testing | JUnit5/Mockito, pytest, Testcontainers, Playwright/Cypress |

---

## 16. Stretch Goals

Optional, roughly in order of impressiveness-per-effort:

- **Recommendation service** — simple collaborative filtering ("customers who bought X also bought Y")
- **CQRS + Event Sourcing** on `order-service` — separate write/read models, replay-able event log
- **gRPC** for internal service-to-service calls instead of REST
- **Terraform** for cloud infrastructure provisioning (VPC, cluster, managed DBs) — IaC is a strong signal
- **Chaos engineering demo** — deliberately kill a pod and record how the system recovers
- **Saga orchestration via Temporal**, as an alternative to the choreography-based version, with a short write-up comparing the two
- **Multi-region deployment** demo (second ECS cluster + Route 53 failover/latency routing)
- **Feature flags** (e.g., Unleash) for gradual rollout of new features

---

## 17. Portfolio Presentation Tips

- Root README needs: architecture diagram (Excalidraw/draw.io export), one-command local setup (`docker-compose up`), and a live demo link if you host it
- Give each service its own short README explaining its bounded context and why it owns what it owns
- Record a 2–3 minute demo: browse → cart → checkout → admin adds a product → it appears in search
- Write one or two short design-decision posts — *"Why choreography over orchestration for the order saga"*, *"Why I split catalog into MongoDB but kept orders in Postgres"*. These get read far more closely in interviews than the code itself
- If deployed, screenshot a Grafana dashboard showing real request latency/error rate — concrete evidence beats a bullet point every time

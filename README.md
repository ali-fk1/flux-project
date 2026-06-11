# Flux Project

A reactive Spring Boot application for managing social media integrations, built around **X (Twitter) OAuth 2.0 with PKCE** for authentication against the platform and **Keycloak** for user identity and access management. Built end-to-end with **Spring WebFlux** for non-blocking, reactive operations.

---

## 🏗️ Architecture Overview

```
Frontend  ──►  Spring WebFlux API  ──►  PostgreSQL (R2DBC)
                      │
                      ├──► Keycloak (Identity Provider)
                      ├──► X (Twitter) API v2
                      └──► AWS S3 (media — upcoming)
```

Key design principles:

- **Reactive end-to-end** — Spring WebFlux + R2DBC; nothing blocks
- **Keycloak as IdP** — registration, login, session management, and JWT issuance are all delegated to Keycloak; the backend is a pure OAuth2 resource server
- **AES-256-GCM encrypted token storage** — X OAuth tokens are encrypted before being written to the database
- **PKCE-secured OAuth 2.0** — protects the X authorization code exchange from interception

---

## 📋 Table of Contents

1. [Core Features](#-core-features)
2. [Technical Stack](#-technical-stack)
3. [Main Functionalities](#-main-functionalities)
   - [Authentication via Keycloak](#1-authentication-via-keycloak)
   - [X (Twitter) OAuth 2.0 Integration](#2-x-twitter-oauth-20-integration-with-pkce)
   - [Post Management](#3-post-management)
   - [Post Scheduling](#4-post-scheduling-system)
   - [Token Security & Encryption](#5-token-security--encryption)
   - [Cleanup Schedulers](#6-automatic-cleanup-schedulers)
4. [Security Architecture](#-security-architecture)
5. [Configuration](#-configuration)
6. [Setup Instructions](#-setup-instructions)
7. [API Endpoints](#-api-endpoints)
8. [Demo](#-demo)

---

## 🚀 Core Features

- **Keycloak-backed authentication** — delegates identity management (register, login, token refresh, logout) to a Keycloak realm; the backend never touches passwords
- **JWT resource server** — validates Keycloak-issued JWTs on every request; resolves the Keycloak subject to a local user ID via `KeycloakPrincipalExtractor`
- **X (Twitter) OAuth 2.0 with PKCE** — full authorization-code-with-PKCE flow to connect a user's X account
- **Encrypted OAuth token storage** — AES-256-GCM encryption via `EncryptionUtil` before tokens hit the database
- **Automatic token refresh** — transparently refreshes expired X access tokens on each post attempt
- **Post scheduling** — store posts with a future `scheduled_at_utc` timestamp; a scheduler fires them at the right time
- **Cursor-based pagination** — efficient, stable paging over the posts feed
- **Scheduled cleanup** — automatic garbage collection for expired OAuth states and soft-deleted posts

---

## 🛠️ Technical Stack

### Core
| Technology | Purpose |
|---|---|
| Java 21 | Language / runtime |
| Spring Boot 3.5.x | Application framework |
| Spring WebFlux | Reactive web layer |
| Spring Data R2DBC | Reactive database access |
| PostgreSQL | Primary database |

### Security & Identity
| Technology | Purpose |
|---|---|
| Keycloak 26.x | Identity provider — issues JWTs, manages users |
| Spring Security (OAuth2 Resource Server) | JWT validation on every request |
| AES-256-GCM | Encryption of X OAuth tokens at rest |

### External Integrations
| Technology | Purpose |
|---|---|
| X (Twitter) API v2 | Social media posting |
| AWS S3 | Media storage (upcoming) |
| Mailgun | Email delivery (upcoming) |

### Infrastructure & Dev Tools
| Technology | Purpose |
|---|---|
| Docker / Docker Compose | Local Keycloak + Keycloak Postgres |
| Lombok | Boilerplate reduction |
| MapStruct | Bean mapping |
| Testcontainers | Integration testing |
| SpringDoc / OpenAPI 3 | Auto-generated API docs |

---

## 📖 Main Functionalities

### 1. Authentication via Keycloak

Authentication is fully delegated to Keycloak. The Spring Boot backend acts as an **OAuth 2.0 Resource Server** only — it never issues or stores its own JWTs.

**How it works:**

1. The frontend redirects the user to Keycloak's login page.
2. Keycloak authenticates the user and returns an access token (JWT) + refresh token.
3. The frontend attaches the access token as a `Bearer` header on every API call.
4. Spring Security validates the token signature against Keycloak's public keys (via `issuer-uri`).
5. `KeycloakPrincipalExtractor` reads the Keycloak `sub` claim from the JWT, then calls `UserService.findOrCreateByKeycloakId(...)` to get (or lazily create) the corresponding local DB user. This keeps the local `users` table lean — no passwords, no sessions.

**Key classes:**
- `config/SecurityConfig.java` — configures the resource server and CORS
- `config/KeycloakPrincipalExtractor.java` — resolves Keycloak sub → local UUID
- `services/UserService.java` — `findOrCreateByKeycloakId` and `getUserByEmail`

---

### 2. X (Twitter) OAuth 2.0 Integration with PKCE

Connects a Keycloak-authenticated user's X account using the Authorization Code + PKCE flow.

**Flow:**

```
1. POST /api/x
   → generate PKCE code_verifier + code_challenge
   → persist OAuth2AuthRequest (state, code_verifier, userId, expiry)
   → return X authorization URL to the frontend

2. User approves on x.com

3. GET /api/x/callback?code=…&state=…
   → look up OAuth2AuthRequest by state
   → verify not expired, not already consumed
   → exchange code + code_verifier for X access/refresh tokens
   → encrypt tokens with AES-256-GCM
   → upsert SocialAccount (platform = "X")
   → mark OAuth2AuthRequest as consumed
   → redirect frontend to success page
```

**Key classes:**
- `controllers/XOAuth2Controller.java`
- `services/X/XOAuth2Service.java`
- `services/utils/OAuth2PKCEUtil.java`
- `domain/OAuth2AuthRequest.java` + `repositories/OAuth2AuthRequestRepository.java`

---

### 3. Post Management

Endpoints for retrieving and deleting posts, gated by the authenticated user's local ID.

**Pagination** uses a cursor approach: the cursor encodes `(scheduledAtUtc, id)` so pages are stable even when rows are inserted between fetches.

**Soft-delete** sets `status = 'deleted'` and records `deleted_at_utc`; hard deletion of stale soft-deleted rows happens via `CleanupScheduler` after 30 days.

**Key classes:**
- `controllers/PostController.java`
- `services/PostService.java`
- `repositories/PostRepository.java`
- `util/CursorUtil.java`

---

### 4. Post Scheduling System

Users submit a post body + a future UTC timestamp. The backend persists it as a `scheduled` post. A polling scheduler checks every minute for posts due to be published and invokes `XPostService`.

**Key classes:**
- `controllers/PostSchedulingController.java`
- `services/X/SchedulingService.java`
- `schedulers/PostScheduler.java`

---

### 5. Token Security & Encryption

X OAuth tokens (access token, refresh token, expiry) are stored as a JSON map, then encrypted with AES-256-GCM before being written to `social_accounts.auth_data`. The IV is prepended to the ciphertext and stored as a single Base64 string.

On every post attempt, `XPostService` decrypts the token data, checks expiry, and refreshes if needed — transparently to the caller.

**Key classes:**
- `services/utils/EncryptionUtil.java`
- `services/X/XPostService.java` — `postTextWithAutoRefresh`, `refreshAccessToken`

---

### 6. Automatic Cleanup Schedulers

| Scheduler | Schedule | What it does |
|---|---|---|
| `PostScheduler` | Every minute | Publishes due scheduled posts to X |
| `CleanupScheduler` | Daily at 03:00 | Hard-deletes posts soft-deleted > 30 days ago |
| `OAuthStateCleanupScheduler` | Every 5 minutes | Removes expired / consumed `OAuth2AuthRequest` rows |

---

## 🔐 Security Architecture

```
Request
  │
  ▼
Spring Security (OAuth2 Resource Server)
  │  validates JWT signature against Keycloak JWKS
  │  rejects expired / tampered tokens
  ▼
KeycloakPrincipalExtractor
  │  reads sub + email + name from JWT claims
  │  resolves or creates local user row
  ▼
Controller / Service
  │  all business logic uses local UUID, never the Keycloak sub directly
  ▼
EncryptionUtil (AES-256-GCM)
  │  encrypts OAuth tokens before DB write
  │  decrypts on read
  ▼
PostgreSQL
```

Additional layers:
- **CORS** — configured via `SecurityConfig`; allowed origins set by `FRONTEND_URL`
- **CSRF disabled** — stateless JWT API; no session to protect
- **Session cookie** (`SessionConfig`) — used only for the X OAuth redirect round-trip (SameSite=None to handle the cross-origin callback redirect)

---

## ⚙️ Configuration

All configuration lives in `application-dev.YAML`. Copy `.env.example` to `.env` and fill in values.

### Required Environment Variables

```bash
# Database (application DB)
DB_URL=r2dbc:postgresql://localhost:5432/flux
DB_USER=flux
DB_PASS=yourpassword

# Keycloak (issuer URI for JWT validation)
ISSUER_URI=http://localhost:8081/realms/flux

# X (Twitter) OAuth 2.0
X_API_KEY=
X_API_SECRET_KEY=
X_CALLBACK_URL=          # OAuth 1.0 callback (legacy — used in SessionConfig env detection)
X_V2_CALLBACK_URL=       # OAuth 2.0 PKCE redirect URI registered on developer.twitter.com
X_OAUTH2_CLIENT_ID=
X_OAUTH2_CLIENT_SECRET=
CODE_VERIFIER_LENGTH=64

# Token encryption
AES_SECRET_KEY=          # Base64-encoded 256-bit key

# App URLs
FRONTEND_URL=http://localhost:5173
BACKEND_URL=http://localhost:8080

# AWS (S3 — upcoming)
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_DEFAULT_REGION=
AWS_S3_BUCKET_NAME=

# LinkedIn (upcoming)
LINKEDIN_API_KEY=
LINKEDIN_API_SECRET_KEY=
LINKEDIN_CALLBACK_URL=

# Keycloak Docker Compose
KEYCLOAK_DB_NAME=choose_your_db_name
KEYCLOAK_DB_USER=choose_your_db_user
KEYCLOAK_DB_PASSWORD=
# For Keycloak Admin Console
KEYCLOAK_ADMIN=choose_an_admin_name
KEYCLOAK_ADMIN_PASSWORD=
```

---

## 🚀 Setup Instructions

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### 1. Clone the repository

```bash
git clone https://github.com/ali-fk1/flux-project.git
cd flux-project
```

### 2. Start Keycloak + its database

```bash
docker compose up -d
```

This starts:
- `postgres-keycloak` on port `5433` — Keycloak's dedicated Postgres instance
- `keycloak` on port `8081` — Keycloak server (dev mode)

### 3. Import the Keycloak realm

1. Open `http://localhost:8081` and log in with `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`.
2. Create a new realm or import the provided export: `docker/keycloak/flux-realm-export.json`.
3. Note the **client ID/secret** and set `ISSUER_URI=http://localhost:8081/realms/<realm-name>`.

### 4. Set up the application database

Create a PostgreSQL database and run `src/main/resources/schema.sql` against it.

### 5. Configure environment variables

```bash
cp .env.example .env
# fill in values
```

### 6. Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The API will be available at `http://localhost:8080`.

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## 📡 API Endpoints

All endpoints (except the X OAuth callback) require a valid Keycloak JWT in the `Authorization: Bearer <token>` header.

### Platform

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/me` | Returns the authenticated user's local UUID |

### X (Twitter) OAuth

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/x` | Starts the OAuth 2.0 + PKCE flow; returns the X authorization URL |
| `GET` | `/api/x/callback` | OAuth callback — exchanges code for tokens, saves the account, redirects frontend |
| `GET` | `/api/x/status` | Returns `{ "connected": true/false }` for the authenticated user |

### Posts

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/post` | Immediately post text to X |
| `GET` | `/api/posts` | Paginated list of posts (`size`, `status`, `cursor` query params) |
| `DELETE` | `/api/posts/{postId}` | Soft-delete a post |
| `GET` | `/api/expired` | Check whether the user's X access token is expired |

### Scheduling

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/schedule` | Schedule a post for a future UTC timestamp |

---

## 📧 Email Verification via Keycloak + Mailgun

Email verification is fully delegated to Keycloak — the backend has no involvement in sending or validating verification emails. Keycloak handles the entire flow: sending the verification email on registration, validating the token when the user clicks the link, and marking the account as verified.

Mailgun is used as the SMTP relay for Keycloak's outgoing emails.

---

### How it works

1. User registers via the Keycloak-hosted login page.
2. Keycloak sends a verification email through Mailgun's SMTP server.
3. User clicks the link in the email.
4. Keycloak marks the account as verified and allows login.

The Spring Boot backend never sees unverified users — Keycloak blocks login for unverified accounts before a JWT is ever issued.

---

### Setting up Mailgun SMTP in Keycloak

#### 1. Get your Mailgun SMTP credentials

Log in to [mailgun.com](https://www.mailgun.com) and navigate to **Sending → Domains → your domain → SMTP credentials**.

You'll need:
- **SMTP hostname:** `smtp.mailgun.org`
- **Port:** `587` (STARTTLS)
- **Username:** `postmaster@your-domain.com` (or any sender you created)
- **Password:** the SMTP password for that sender

#### 2. Configure Keycloak Email Settings

1. Open the Keycloak Admin Console at `http://localhost:8081`.
2. Select your realm (e.g. `flux`) from the top-left dropdown.
3. Go to **Realm Settings → Email** tab.
4. Fill in the fields:

| Field | Value |
|---|---|
| From | `noreply@your-domain.com` |
| From Display Name | `Flux` (or whatever you want users to see) |
| Host | `smtp.mailgun.org` |
| Port | `587` |
| Encryption | `STARTTLS` |
| Authentication | `Enabled` |
| Username | `postmaster@your-domain.com` |
| Password | your Mailgun SMTP password |

5. Click **Test connection** to verify Keycloak can reach Mailgun before saving.
6. Click **Save**.

#### 3. Enable Email Verification on Registration

1. Still in **Realm Settings**, go to the **Login** tab.
2. Enable **Verify email**.
3. Save.

From this point on, every newly registered user will receive a verification email through Mailgun before they can log in.

---

### Env vars involved

These are used only by Docker Compose to boot Keycloak — the Spring Boot backend does not use them for email:

```bash
DOMAIN=your-domain.com        # the domain you configured in Mailgun
MAILGUN_API_KEY=               # not used by the backend — only needed if you add
                               # a direct Mailgun integration in the future
```

> **Note:** The Mailgun SMTP credentials (username + password) are entered directly in the Keycloak Admin Console UI and are not stored in your `.env` file.

## 🔮 Upcoming / Planned

- LinkedIn platform integration
- AWS S3 media upload for posts
- Admin dashboard
- Rate limiting on API endpoints
- Redis caching layer
- Post analytics and engagement tracking

---

## 🎥 Demo

▶️ [Watch the demo (unlisted)](https://youtu.be/AUPeNMkumTo)

---

## 📄 License

MIT

---

## 👤 Author

**Ali FK** — [@ali-fk1](https://github.com/ali-fk1)
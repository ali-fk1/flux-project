# Flux Project

A reactive Spring Boot application for managing social media integrations with a focus on X (Twitter) OAuth2.0 authentication and post scheduling. Built with **Spring WebFlux** for full reactive, non-blocking operations and secured with JWT-based authentication.

## 🏗️ Architecture Overview

This project demonstrates a modern **reactive microservice** architecture using:
- **Spring WebFlux** for non-blocking I/O
- **R2DBC** for reactive database access with PostgreSQL
- **JWT authentication** with HttpOnly cookies for security
- **OAuth 2.0 with PKCE** for X (Twitter) integration
- **Reactive WebClient** for external API calls
- **AES-256-GCM encryption** for storing sensitive OAuth tokens

---

## 📋 Table of Contents

1. [Core Features](#-core-features)
2. [Technical Stack](#-technical-stack)
3. [Main Functionalities](#-main-functionalities)
   - [User Authentication System](#1-user-authentication-system)
   - [Email Verification](#2-email-verification-system)
   - [X (Twitter) OAuth2 Integration](#3-x-twitter-oauth2-integration-with-pkce)
   - [Post Management](#4-post-management-with-auto-refresh)
   - [Post Scheduling](#5-post-scheduling-system)
   - [Token Security](#5-token-security-and-encryption)
   - [Cleanup Schedulers](#6-automatic-cleanup-schedulers)
4. [Security Architecture](#-security-architecture)
5. [Configuration](#-configuration)
6. [Setup Instructions](#-setup-instructions)
7. [API Endpoints](#-api-endpoints)
8. [Demo](#-demo)

---

## 🚀 Core Features

- **Reactive End-to-End**: Fully non-blocking architecture from controller to database
- **Secure Authentication**: JWT-based auth with access/refresh token rotation
- **Email Verification**: Email verification flow with secure tokens via Mailgun
- **OAuth 2.0 Integration**: PKCE-secured OAuth flow for X (Twitter)
- **Automatic Token Refresh**: Transparent access token refresh when expired
- **Encrypted Token Storage**: AES-256-GCM encryption for OAuth tokens in database
- **Cookie-Based Security**: HttpOnly, SameSite cookies prevent XSS attacks
- **Scheduled Cleanup**: Automatic cleanup of expired tokens and OAuth states

---

## 🛠️ Technical Stack

### Core Technologies
- **Java 21** - Latest LTS with modern language features
- **Spring Boot 3.5.4** - Latest Spring Boot with enhanced WebFlux support
- **Spring WebFlux** - Reactive web framework
- **Spring Data R2DBC** - Reactive database access
- **PostgreSQL** - Relational database
- **R2DBC PostgreSQL Driver** - Reactive PostgreSQL driver

### Security & Authentication
- **Spring Security** - Security framework
- **JJWT (0.12.3)** - JWT creation and validation
- **BCrypt** - Password hashing
- **AES-256-GCM** - OAuth token encryption

### External Integrations
- **Mailgun API** - Email delivery service
- **X (Twitter) API v2** - Social media integration
- **ScribeJava** - OAuth library support

### Development Tools
- **Lombok** - Reduces boilerplate code
- **MapStruct** - Type-safe bean mapping
- **Testcontainers** - Integration testing with Docker

---

## 📖 Main Functionalities

### 1. User Authentication System

#### How It Works
The authentication system implements a **JWT-based approach with refresh token rotation** stored in HttpOnly cookies for enhanced security.

#### Implementation Details

**Sign Up Flow** (`SignUpController.java`)
```java
@PostMapping("/signup")
public Mono<ResponseEntity<Void>> handleSignUp(@RequestBody SignUpRequestBody request)
```

**My Thought Process:**
- I designed the registration to be completely reactive, returning `Mono<ResponseEntity<Void>>` to maintain non-blocking operations
- The flow chains two asynchronous operations: user creation and email sending
- Error handling is built-in with `.onErrorResume()` to gracefully handle failures
- Used `.flatMap()` to ensure email is only sent after successful user creation
- Logging at each step helps with debugging and monitoring

**Key Design Decisions:**
1. **Password Security**: Passwords are hashed with BCrypt (strength 10) before storage
2. **Reactive Chaining**: Used `flatMap` to sequence operations (register → send email)
3. **Error Handling**: Separate error handling for registration vs email failures
4. **Account State**: New users start with `enabled=false` until email verification

**Login Flow** (`LoginController.java`)
```java
@PostMapping("/api/auth/login")
public Mono<ResponseEntity<Map<String, Object>>> login(@RequestBody LoginRequest request)
```

**My Implementation Strategy:**
- Extract JWT tokens from response body **before** sending to client
- Store tokens in **HttpOnly cookies** to prevent XSS attacks
- Return user info in response body (no sensitive tokens exposed)
- Check email verification status before allowing login

**Token Refresh Flow**
```java
@PostMapping("/api/auth/refresh")
public Mono<ResponseEntity<Map<String, Object>>> refresh(
    @CookieValue(value = "refreshToken", required = false) String refreshToken)
```

**Security Features I Implemented:**
1. **Token Rotation**: Old refresh token is invalidated when new one is issued
2. **Revocation Check**: Tokens marked as revoked are rejected
3. **Expiry Validation**: Both access and refresh tokens have expiration checks
4. **Cookie Validation**: Extract refresh token from secure HttpOnly cookie

**JWT Configuration** (`KeyManager.java`)
- Separate keys for access and refresh tokens (256-bit HMAC-SHA)
- Access tokens expire in 15 minutes
- Refresh tokens expire in 30 days
- Keys are securely generated from environment variables

---

### 2. Email Verification System

#### How I Built It
The email verification system uses **cryptographically secure tokens** with SHA-256 hashing for database storage.

**Token Generation** (`VerificationTokenService.java`)
```java
public Mono<String> createAndSaveTokenForUser(UUID userId)
```

**My Design Philosophy:**
- Generate 32-byte random token using `SecureRandom`
- Store **SHA-256 hash** in database (not the raw token)
- Only the raw token is sent via email (one-time use)
- Tokens expire after 24 hours
- Mark tokens as "used" after verification to prevent replay attacks

**Email Sending** (`EmailService.java`)
```java
public Mono<Void> sendVerificationEmail(UUID userId, String toEmail)
```

**Implementation Approach:**
1. **Template-Based**: Uses Mailgun email templates for consistent branding
2. **Reactive WebClient**: Non-blocking HTTP calls to Mailgun API
3. **Error Handling**: Comprehensive logging for debugging email delivery issues
4. **Basic Auth**: Mailgun API key transmitted securely via Basic Authentication

**Verification Process** (`EmailVerificationController.java`)
```java
@GetMapping("/verify")
public Mono<ResponseEntity<Void>> verifyEmail(@RequestParam String token)
```

**What I Learned:**
- Hash comparison must happen in the database for security
- Redirect users to frontend with appropriate status (success/failure)
- Provide specific error types: expired, already-used, invalid
- Log token details (first/last 10 chars) for debugging without exposing full token

**Token Structure:**
```
Raw Token (sent via email): dGhpcyBpcyBhIHNlY3VyZSB0b2tlbg==
Hashed Token (stored in DB): 5d41402abc4b2a76b9719d911017c592
```

---

### 3. X (Twitter) OAuth2 Integration with PKCE

#### Why PKCE?
PKCE (Proof Key for Code Exchange) is required by X's API to prevent authorization code interception attacks. I implemented the full OAuth 2.0 PKCE flow from scratch.

**Authorization URL Generation** (`XOAuth2Service.java`)
```java
public Mono<String> buildAuthorizationUrl(ServerWebExchange serverWebExchange)
```

**My Implementation Steps:**

1. **Generate PKCE Parameters** (`OAuth2PKCEUtil.java`)
   ```java
   String codeVerifier = OAuth2PKCEUtil.generateCodeVerifier(length);
   String codeChallenge = OAuth2PKCEUtil.generateCodeChallenge(codeVerifier);
   String state = OAuth2PKCEUtil.generateState();
   ```
   
   **How I Did It:**
   - Code verifier: 43-128 chars using URL-safe characters
   - Code challenge: SHA-256 hash of verifier, base64url encoded
   - State: 32-byte random value for CSRF protection

2. **Persist OAuth Request** 
   ```java
   OAuth2AuthRequest oauth2AuthRequest = OAuth2AuthRequest.builder()
       .userId(userId)
       .provider("X")
       .codeVerifier(codeVerifier)
       .state(state)
       .expiresAt(Instant.now().plusSeconds(600))
       .consumed(false)
       .build();
   ```
   
   **Why I Did This:**
   - Store state and code_verifier for callback validation
   - Tie request to specific user (prevents authorization hijacking)
   - 10-minute expiration prevents stale requests
   - `consumed` flag prevents replay attacks

3. **Build Authorization URL**
   ```java
   String authUrl = UriComponentsBuilder
       .fromUriString("https://x.com/i/oauth2/authorize")
       .queryParam("response_type", "code")
       .queryParam("client_id", clientId)
       .queryParam("redirect_uri", redirectUri)
       .queryParam("scope", "tweet.write tweet.read users.read offline.access")
       .queryParam("state", state)
       .queryParam("code_challenge", codeChallenge)
       .queryParam("code_challenge_method", "S256")
       .build()
       .toUriString();
   ```
   
   **Key Scopes I Chose:**
   - `tweet.write`: Post tweets
   - `tweet.read`: Read tweet data
   - `users.read`: Get user profile info
   - `offline.access`: Receive refresh token

**Token Exchange** (`XOAuth2Service.java`)
```java
public Mono<XTokenResponse> exchangeCodeForToken(OAuth2AuthRequest request, String code)
```

**My Approach:**
1. **Basic Authentication**: Encode client credentials as base64
   ```java
   String credentials = clientId + ":" + clientSecret;
   String encodedCredentials = Base64.getEncoder()
       .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
   ```

2. **Form-Encoded POST**: X requires `application/x-www-form-urlencoded`
   ```java
   .body(BodyInserters.fromFormData("grant_type", "authorization_code")
       .with("code", code)
       .with("client_id", clientId)
       .with("redirect_uri", redirectUri)
       .with("code_verifier", request.getCodeVerifier()))
   ```

3. **Error Handling**: Capture and log detailed error responses from X API

**Token Storage** (`XOAuth2Service.java`)
```java
public Mono<ResponseEntity<Object>> saveSocialAccountAndMarkConsumed(
    OAuth2AuthRequest request, XTokenResponse xTokenResponse)
```

**Security Considerations I Implemented:**
1. **Fetch User Info**: Get X username/ID to prevent account confusion
2. **Encrypt Before Storage**: 
   ```java
   Map<String, Object> tokenData = new HashMap<>();
   tokenData.put("access_token", xTokenResponse.getAccessToken());
   tokenData.put("refresh_token", xTokenResponse.getRefreshToken());
   tokenData.put("scope", xTokenResponse.getScope());
   String encryptedAuthData = encryptionUtil.encrypt(tokenData);
   ```
3. **Mark Request Consumed**: Prevent code reuse
4. **Calculate Expiry**: Store when access token will expire
5. **Redirect to Frontend**: Send user back with success/error message

---

### 4. Post Management with Auto-Refresh

#### The Challenge
X access tokens expire after 2 hours. I needed a way to **automatically refresh expired tokens** without user intervention.

**Auto-Refresh Post Flow** (`XPostService.java`)
```java
public Mono<XPostResponse> postTextWithAutoRefresh(UUID userId, String text)
```

**My Solution Architecture:**

1. **Check Token Expiry**
   ```java
   return checkAccessTokenExpiry(userId)
       .flatMap(isExpired -> {
           if (isExpired) {
               return refreshAccessToken(userId)
                   .flatMap(tokenResponse -> saveRefreshedToken(userId, tokenResponse))
                   .then(getAccessToken(userId))
                   .flatMap(token -> postText(text, token));
           } else {
               return getAccessToken(userId)
                   .flatMap(token -> postText(text, token));
           }
       });
   ```

**Why This Design Works:**
- **Transparent to User**: Token refresh happens automatically
- **Reactive Chain**: All operations are non-blocking
- **Error Propagation**: Specific exceptions for different failure modes
- **Single Request**: No additional user action required

2. **Token Refresh Implementation**
   ```java
   public Mono<XTokenResponse> refreshAccessToken(UUID userId) {
       String credentials = clientId + ":" + clientSecret;
       String encodedCredentials = Base64.getEncoder()
           .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
       
       return getRefreshToken(userId)
           .flatMap(refreshToken ->
               xWebClient.post()
                   .uri("/2/oauth2/token")
                   .header("Authorization", "Basic " + encodedCredentials)
                   .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                   .body(BodyInserters
                       .fromFormData("grant_type", "refresh_token")
                       .with("refresh_token", refreshToken))
                   .retrieve()
                   .bodyToMono(XTokenResponse.class)
           );
   }
   ```

**Key Implementation Details:**
- Decrypt stored refresh token from database
- Make refresh request to X OAuth2 token endpoint
- Receive new access_token and refresh_token
- Re-encrypt and update in database

3. **Post Tweet**
   ```java
   public Mono<XPostResponse> postText(String text, String accessToken) {
       return xWebClient.post()
           .uri("/2/tweets")
           .header("Authorization", "Bearer " + accessToken)
           .bodyValue(Map.of("text", text))
           .retrieve()
           .bodyToMono(XPostResponse.class);
   }
   ```

**Error Handling Strategy:**
```java
.onErrorResume(XAccountNotConnectedException.class, e -> {
    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
})
.onErrorResume(XTokenRefreshFailedException.class, e -> {
    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
})
.onErrorResume(XPostException.class, e -> {
    return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
})
```

**Custom Exceptions I Created:**
- `XAccountNotConnectedException`: User hasn't connected X account
- `XTokenRefreshFailedException`: Refresh token is invalid/expired
- `XPostException`: X API rejected the tweet (content policy, etc.)

---


### 5. Post Scheduling System

#### The Challenge
Users need the ability to schedule posts for future publication rather than posting immediately. This requires a robust background job system that can reliably publish posts at the scheduled time, handle failures, and prevent duplicate publishing.

#### How I Built It
I implemented a **scheduler-based system** with database-backed job queuing, optimistic locking, and atomic operation controls.

**The Complete Architecture:**

```
┌─────────────────────────────────────────────────────────────┐
│                  SCHEDULING FLOW                             │
└─────────────────────────────────────────────────────────────┘

User → POST /api/schedule {text, scheduledAtUtc}
         ↓
    Save to posts table (status='scheduled')
         ↓
    Spring @Scheduled task runs every 30s
         ↓
    Database atomically claims due posts (FOR UPDATE SKIP LOCKED)
         ↓
    Status: 'scheduled' → 'publishing'
         ↓
    Post to X API with auto-refresh
         ↓
    Success: status → 'published'
    Failure: status → 'failed', retry_count++
```

---

#### Implementation Details

**1. Scheduling a Post** (`PostSchedulingController.java`)

```java
@PostMapping("/api/schedule")
public Mono<ResponseEntity> schedulePost(
    @RequestBody ScheduledPostRequest request,
    ServerWebExchange exchange) {
    
    UUID userId = UUID.fromString(exchange.getAttribute("userId"));
    
    return schedulingService.saveScheduledPost(request, userId)
        .thenReturn(ResponseEntity.noContent().build());
}
```

**My Design Philosophy:**
- Validate user authentication via JWT filter
- Check for required fields (text, scheduledAtUtc)
- Save post with `status='scheduled'`
- Return immediately - publishing happens in background

**Post Model Structure:**
```java
@Table("posts")
public class Post {
    private UUID id;
    private UUID userId;
    private UUID socialAccountId;
    private String platform;           // 'X', 'Instagram', etc.
    private String content;            // Post text
    private List mediaUrls;    // Future: image attachments
    private Instant scheduledAtUtc;    // When to publish
    private Instant publishedAtUtc;    // When it was published
    private PostStatus status;         // Lifecycle state
    private String errorMessage;       // If failed, why?
    private Integer retryCount;        // Attempts made
    private Integer maxRetries;        // Max 3 attempts
}
```

**Status Lifecycle:**
```java
public enum PostStatus {
    draft,       // Created but not scheduled
    scheduled,   // Waiting to be published
    publishing,  // Currently being posted
    published,   // Successfully posted
    failed,      // All retries exhausted
    cancelled    // User cancelled
}
```

---

**2. The Scheduler** (`PostScheduler.java`)

```java
@Component
public class PostScheduler {
    
    private final SchedulingService schedulingService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    @Scheduled(fixedDelay = 30000)  // Every 30 seconds
    public void checkDuePosts() {
        // Prevent overlapping executions
        if (!running.compareAndSet(false, true)) {
            log.info("Scheduler already running, skipping this tick");
            return;
        }
        
        schedulingService.executePosting(15)
            .doFinally(sig -> running.set(false))
            .subscribe();
    }
}
```

**Why AtomicBoolean?**

This is a **critical concurrency control mechanism**. Here's the problem it solves:

**Without AtomicBoolean:**
```
Time 0s:  Scheduler starts (takes 40s to process)
Time 30s: Scheduler starts again → TWO instances running!
         → Risk of duplicate posts
         → Database contention
         → Race conditions
```

**With AtomicBoolean:**
```java
if (!running.compareAndSet(false, true)) {
    return;  // Skip if already running
}
```

**How `compareAndSet` works:**
1. **Atomically** checks if `running == false`
2. If true, sets `running = true` and returns `true`
3. If false (already running), returns `false` without changing value
4. This happens in **one atomic CPU instruction** - no race condition possible

**Example Scenario:**
```
Thread 1: compareAndSet(false, true) → true  (proceeds to execute)
Thread 2: compareAndSet(false, true) → false (skips execution)
```

Even if two threads call this simultaneously, only ONE will succeed due to atomic operation.

**After execution:**
```java
.doFinally(sig -> running.set(false))  // Reset flag when done
```

This ensures the next scheduled run can proceed.

**Production Note:**
*This is a simple solution for preventing concurrent executions. In production, I would implement **distributed rate limiting** using Redis with a distributed lock pattern (e.g., Redisson). This would:*
- *Work across multiple server instances (horizontal scaling)*
- *Provide automatic lock expiration (if server crashes)*
- *Enable monitoring of lock acquisition/release*
- *Support more sophisticated locking strategies*

*The AtomicBoolean approach works well for single-instance deployments but doesn't prevent concurrent execution across multiple servers.*

---

**3. Claiming Posts** (`PostRepository.java`)

This is where the **real magic happens** - using PostgreSQL's row-level locking to prevent duplicate posting:

```java
@Query("""
WITH due AS (
    SELECT id 
    FROM posts 
    WHERE status = 'scheduled' 
      AND scheduled_at_utc <= now()
    ORDER BY scheduled_at_utc ASC
    FOR UPDATE SKIP LOCKED
    LIMIT :batchSize
)
UPDATE posts p
SET status = 'publishing',
    updated_at_utc = now()
FROM due
WHERE p.id = due.id
RETURNING *
""")
Flux claimDuePosts(int batchSize);
```

**Breaking Down This Query:**

**Step 1: Find Due Posts**
```sql
WHERE status = 'scheduled' 
  AND scheduled_at_utc <= now()
ORDER BY scheduled_at_utc ASC
```
- Only posts that are scheduled and past their scheduled time
- Oldest first (FIFO order)

**Step 2: Lock with `FOR UPDATE SKIP LOCKED`**
This is the **key to preventing duplicates**:

```sql
FOR UPDATE SKIP LOCKED
```

**What this does:**
- `FOR UPDATE`: Locks the selected rows for update
- `SKIP LOCKED`: If a row is already locked, skip it

**Real-World Scenario:**
```
Time: 10:00:00
Post A: scheduled_at_utc = 09:50:00, status = 'scheduled'

Server Instance 1: SELECT ... FOR UPDATE SKIP LOCKED
  → Locks Post A
  
Server Instance 2: SELECT ... FOR UPDATE SKIP LOCKED
  → Sees Post A is locked → SKIPS it
  
Result: Only ONE instance processes Post A
```

**Without SKIP LOCKED:**
```
Instance 1: SELECT ... FOR UPDATE
  → Locks Post A

Instance 2: SELECT ... FOR UPDATE
  → WAITS for lock to be released
  → Eventually processes same post (duplicate!)
```

**Step 3: Update Status Atomically**
```sql
UPDATE posts p
SET status = 'publishing',
    updated_at_utc = now()
FROM due
WHERE p.id = due.id
RETURNING *
```

The status change happens **in the same transaction** as the SELECT:
- Atomic operation - no race condition
- Status changes from `scheduled` → `publishing`
- Returns the updated rows

**Why this matters:**
Even if two schedulers run simultaneously:
1. Both query for `status='scheduled'`
2. Lock prevents duplicate selection
3. Status immediately changes to `publishing`
4. Second scheduler finds nothing with `status='scheduled'`

---

**4. Execution Flow** (`SchedulingService.java`)

```java
public Flux executePosting(int batchSize) {
    return postRepository.claimDuePosts(batchSize)
        .flatMap(duePost ->
            xPostService.postTextWithAutoRefresh(
                duePost.getUserId(), 
                duePost.getContent()
            )
            .flatMap(resp -> 
                postRepository.markPublished(duePost.getId(), Instant.now())
            )
            .onErrorResume(e -> 
                postRepository.markFailed(duePost.getId(), safeMsg(e))
            )
        , 2);  // Concurrency of 2
}
```

**My Implementation Strategy:**

**Batch Processing:**
```java
claimDuePosts(15)  // Process up to 15 posts per run
```
- Prevents overwhelming the X API
- Limits database load
- Allows graceful failure handling

**Concurrency Control:**
```java
.flatMap(duePost -> ..., 2)
```
- Process 2 posts concurrently (not sequentially)
- Balances throughput with API rate limits
- Still within X's rate limit boundaries

**Error Handling:**
```java
.flatMap(resp -> markPublished(postId, now))
.onErrorResume(e -> markFailed(postId, error))
```

**Success path:**
1. Post succeeds
2. Update status to `published`
3. Record `publishedAtUtc` timestamp
4. Clear any error message

**Failure path:**
1. Post fails (network error, X API rejection, etc.)
2. Update status to `failed`
3. Record error message
4. Increment `retry_count`

**Retry Logic:**
```java
@Column("retry_count")
private Integer retryCount;

@Column("max_retries")
private Integer maxRetries;  // Set to 3
```

Posts with `retry_count < max_retries` can be:
- Manually retried by user
- Automatically retried (future enhancement)
- Reset to `scheduled` status

---

**5. Status Transitions**

```
┌─────────────────────────────────────────────────────┐
│              POST STATUS LIFECYCLE                   │
└─────────────────────────────────────────────────────┘

    draft
      ↓ (user schedules)
  scheduled ←──────────────┐
      ↓ (scheduler claims) │ (manual retry)
  publishing               │
      ↓                    │
   Success?                │
      ↓                    │
    Yes → published        │
    No  → failed ──────────┘
            ↓ (max retries)
       [stays failed]
```

**Database Queries:**

```java
// Mark as published
@Query("""
UPDATE posts
SET status = 'published',
    published_at_utc = :publishedAt,
    error_message = NULL,
    updated_at_utc = now()
WHERE id = :postId
RETURNING *
""")
Mono markPublished(UUID postId, Instant publishedAt);

// Mark as failed
@Query("""
UPDATE posts 
SET status = 'failed',
    error_message = :error,
    retry_count = retry_count + 1,
    updated_at_utc = now()
WHERE id = :postId
RETURNING *
""")
Mono markFailed(UUID postId, String error);
```

---

#### Key Design Decisions

**1. Database-Backed Queue**
- Used PostgreSQL as job queue instead of Redis/RabbitMQ
- Simpler architecture (one less service to manage)
- Leverages database transactions for consistency
- Good enough for current scale

**2. Pessimistic Locking**
- `FOR UPDATE SKIP LOCKED` prevents duplicate processing
- No need for distributed locks
- Database handles concurrency

**3. Status-Based State Machine**
- Clear lifecycle: scheduled → publishing → published/failed
- Easy to query and debug
- Supports manual intervention

**4. Batch Processing**
- Process 15 posts per tick (30 seconds)
- Prevents API rate limiting
- Limits blast radius if something goes wrong

**5. Concurrency of 2**
- Post 2 tweets simultaneously
- Balances speed with safety
- Within X's rate limits

---

#### Security Considerations

**Authorization:**
```java
UUID userId = UUID.fromString(exchange.getAttribute("userId"));
```
- User ID extracted from JWT
- Only authenticated users can schedule posts
- Users can only schedule posts for their own accounts

**Input Validation:**
```java
if (request.getText() == null || request.getText().isBlank() 
    || request.getScheduledAtUtc() == null) {
    return Mono.just(ResponseEntity.badRequest().build());
}
```
- Prevent empty posts
- Require scheduled time
- Validate on server-side (never trust client)

**Social Account Verification:**
```java
return socialAccountRepository.findByUserIdAndPlatform(userId, "X")
    .flatMap(socialAccount -> {
        // Create post linked to this social account
    });
```
- Verify user has connected X account
- Link post to specific social account
- Use that account's OAuth tokens for posting

---

#### Performance Optimizations

**1. Indexed Queries**
```sql
CREATE INDEX idx_posts_scheduled_status 
ON posts(scheduled_at_utc, status);
```
- Fast lookup for due posts
- Composite index on scheduled time + status
- Critical for scheduler performance

**2. Batch Size Tuning**
```java
claimDuePosts(15)  // Adjustable based on load
```
- Balance between throughput and latency
- Prevents long-running transactions
- Adjustable per deployment

**3. Fixed Delay vs Fixed Rate**
```java
@Scheduled(fixedDelay = 30000)
```
- `fixedDelay`: Wait 30s AFTER previous execution completes
- Prevents queue buildup if processing takes > 30s
- More stable than `fixedRate` under load

---

#### What I Learned

**Database Concurrency:**
- `FOR UPDATE SKIP LOCKED` is a powerful primitive for distributed work
- PostgreSQL can serve as a reliable job queue
- Row-level locking prevents duplicate processing

**Atomic Operations:**
- `AtomicBoolean.compareAndSet()` prevents race conditions
- Single atomic instruction - no locks needed
- Simple but effective for single-instance deployments

**Reactive Error Handling:**
- `.onErrorResume()` for graceful degradation
- Record failures in database for debugging
- Separate success and failure paths

**State Machine Design:**
- Clear status transitions prevent ambiguity
- Easy to add new states (e.g., `paused`)
- Status field enables powerful queries

---

#### Future Enhancements

**Short-term:**
- ✅ Basic scheduling (completed)
- 🔄 Automatic retry with exponential backoff
- 📊 User dashboard showing scheduled/published posts
- 🗑️ Cancel scheduled posts

**Long-term:**
- 🔁 Distributed rate limiting with Redis
- 📈 Post analytics (views, likes, retweets)
- 📅 Recurring posts (daily, weekly)
- 🖼️ Media attachment support
- 🌐 Multi-platform scheduling (Instagram, LinkedIn)
- 🧪 A/B testing for post times
- 📊 Optimal posting time suggestions

**Production Readiness:**
- Add distributed locks for multi-instance deployments
- Implement circuit breaker for X API calls
- Add metrics and monitoring (Prometheus/Grafana)
- Dead letter queue for permanently failed posts
- Admin dashboard for manual intervention

---

## API Endpoint

### Schedule Post
```http
POST /api/schedule
Authorization: Cookie (access_token)
Content-Type: application/json

{
  "text": "Hello from scheduled post! 🚀",
  "scheduledAtUtc": "2026-02-19T10:00:00Z"
}

Response: 204 No Content
```

**Request Validation:**
- `text`: Required, non-blank
- `scheduledAtUtc`: Required, ISO-8601 format
- Must be authenticated (JWT in cookie)
- Must have connected X account

**Response Codes:**
- `204 No Content`: Post scheduled successfully
- `400 Bad Request`: Invalid input (missing text or time)
- `401 Unauthorized`: Not authenticated or invalid JWT
- `404 Not Found`: User doesn't have connected X account

---

## Database Schema

```sql
CREATE TABLE posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    social_account_id UUID NOT NULL REFERENCES social_accounts(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    content TEXT,
    media_urls TEXT[],
    scheduled_at_utc TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at_utc TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) DEFAULT 'scheduled'
        CHECK (status IN ('draft', 'scheduled', 'publishing', 'published', 'failed', 'cancelled')),
    api_payload JSONB,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_at_utc TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at_utc TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Critical indexes for scheduler performance
CREATE INDEX idx_posts_scheduled_status ON posts(scheduled_at_utc, status);
CREATE INDEX idx_posts_user_id ON posts(user_id);
CREATE INDEX idx_posts_status ON posts(status);
```

---

### 6. Token Security and Encryption

#### Why Encrypt OAuth Tokens?
Storing OAuth tokens in plain text is a **critical security vulnerability**. I implemented **AES-256-GCM encryption** to protect these sensitive credentials.

**Encryption Implementation** (`EncryptionUtil.java`)

**My Approach:**
```java
public String encrypt(Map<String, Object> data) throws Exception {
    String json = objectMapper.writeValueAsString(data);
    
    Cipher cipher = Cipher.getInstance(AES_GCM);
    byte[] iv = new byte[IV_LENGTH];
    new SecureRandom().nextBytes(iv);
    
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
    byte[] cipherText = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
    
    ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
    byteBuffer.put(iv);
    byteBuffer.put(cipherText);
    
    return Base64.getEncoder().encodeToString(byteBuffer.array());
}
```

**Why This Algorithm?**
- **AES-256-GCM**: Industry standard, authenticated encryption
- **Random IV**: Each encryption uses unique initialization vector
- **GCM Tag**: 128-bit authentication tag prevents tampering
- **Base64 Encoding**: Safe storage in TEXT database column

**Security Properties:**
1. **Confidentiality**: Tokens are encrypted and unreadable
2. **Integrity**: GCM tag detects any modification
3. **Freshness**: Random IV prevents pattern recognition
4. **Key Separation**: Different key from JWT signing keys

**Decryption Process:**
```java
public Map<String, Object> decrypt(String encrypted) throws Exception {
    byte[] decoded = Base64.getDecoder().decode(encrypted);
    
    ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
    byte[] iv = new byte[IV_LENGTH];
    byteBuffer.get(iv);
    
    byte[] cipherText = new byte[byteBuffer.remaining()];
    byteBuffer.get(cipherText);
    
    Cipher cipher = Cipher.getInstance(AES_GCM);
    cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
    
    byte[] plainText = cipher.doFinal(cipherText);
    String json = new String(plainText, StandardCharsets.UTF_8);
    
    return objectMapper.readValue(json, new TypeReference<>() {});
}
```

**Database Storage:**
```sql
auth_data TEXT NOT NULL  -- Stores base64-encoded encrypted JSON
```

**What Gets Encrypted:**
```json
{
  "access_token": "b3BlbiBzZXNhbWU=",
  "refresh_token": "Y2xvc2UgZG9vcnM=",
  "expires_in": 7200,
  "scope": "tweet.write tweet.read users.read offline.access"
}
```

---

### 7. Automatic Cleanup Schedulers

#### The Problem
Over time, expired tokens and consumed OAuth requests accumulate in the database, wasting space and creating potential security risks.

**My Solution:** Spring's `@Scheduled` tasks for automatic cleanup

**Refresh Token Cleanup** (`RefreshTokensCleanupScheduler.java`)
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokensCleanupScheduler {
    
    private final RefreshTokenRepository refreshTokenRepository;
    
    @Scheduled(fixedRate = 3000)  // Every 3 seconds (for development)
    public void refreshTokensCleanupScheduler() {
        log.info("Starting refresh token cleanup...");
        
        refreshTokenRepository.deleteByRevokedIsTrue()
            .doOnSuccess(count -> {
                if (count > 0) {
                    log.info("Cleaned up {} revoked refresh tokens", count);
                }
            })
            .doOnError(error -> log.error("Error during refresh token cleanup", error))
            .subscribe();
    }
}
```

**Why I Chose This Approach:**
- **Fixed Rate**: Runs every 3 seconds in dev (should be longer in production)
- **Reactive**: Uses `.subscribe()` for non-blocking execution
- **Selective**: Only deletes tokens marked as `revoked=true`
- **Logging**: Track how many tokens are cleaned up

**OAuth State Cleanup** (`OAuthStateCleanupScheduler.java`)
```java
@Scheduled(fixedRate = 300000)  // Every 5 minutes
public void cleanupExpiredStates() {
    Instant now = Instant.now();
    
    oAuth2AuthRequestRepository.deleteByExpiresAtBeforeAndConsumedIsTrue(now)
        .doOnSuccess(count -> {
            if (count > 0) {
                log.info("Cleaned up {} expired OAuth states", count);
            }
        })
        .subscribe();
}
```

**Cleanup Criteria:**
1. **Expired**: Past `expires_at` timestamp
2. **Consumed**: Already used (prevents deleting active requests)
3. **Batch Processing**: Deletes all matching records in one query

**Production Recommendations:**
```java
@Scheduled(cron = "0 0 2 * * ?")  // Daily at 2 AM
@Scheduled(cron = "0 0 * * * ?")  // Hourly
```

---

## 🔒 Security Architecture

### Multi-Layer Security Approach

1. **Authentication Layer** (`JwtAuthenticationFilter.java`)
   - Validates JWT from HttpOnly cookies
   - Extracts user ID and email claims
   - Injects user context into request attributes
   - Public paths bypass authentication

2. **CORS Configuration** (`SecurityConfig.java`)
   ```java
   configuration.setAllowedOriginPatterns(origins);
   configuration.setAllowCredentials(true);  // Required for cookies
   configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
   ```

3. **Cookie Security** (`CookieUtil.java`)
   ```java
   ResponseCookie.from(name, value)
       .httpOnly(true)        // Prevents JavaScript access
       .secure(cookieSecure)  // HTTPS only in production
       .sameSite("Strict")    // CSRF protection
       .path("/")
       .maxAge(Duration.ofSeconds(expiration))
       .build();
   ```

4. **Password Security**
   - BCrypt hashing (default strength 10)
   - Salt automatically generated per user
   - Password validation before storage

5. **Database Security**
   - OAuth tokens encrypted with AES-256-GCM
   - Verification tokens stored as SHA-256 hashes
   - Foreign key constraints with CASCADE DELETE

---

## ⚙️ Configuration

### Required Environment Variables

```properties
# Database (PostgreSQL with R2DBC)
DB_URL=r2dbc:postgresql://localhost:5432/flux_db
DB_USER=your_db_user
DB_PASS=your_db_password

# X (Twitter) API Credentials
X_OAuth2.0_Client_ID=your_x_client_id
X_OAuth2.0_Client_Secrete=your_x_client_secret
X_V2_CALLBACK_URL=http://localhost:8080/api/x/callback
code-verifier-length=appropriate_code_verifier_length

# Email Service (Mailgun)
MAILGUN_API_KEY=your_mailgun_api_key
DOMAIN=your_mailgun_domain

# Encryption Keys
AES_SECRET_KEY=base64_encoded_32_byte_key
AES_AUTHENTICATION_KEY=base64_encoded_32_byte_key

# JWT Secrets
jwt-access-secret=your_jwt_access_secret
jwt-refresh-secret=your_jwt_refresh_secret

# Application URLs
front-end-url=http://localhost:5173
backend-url=http://localhost:8080
```

### Application Properties Used

Based on `application-dev.properties`, here are the configurations actively used in the code:

#### Database Configuration
```properties
spring.r2dbc.url=${DB_URL}
spring.r2dbc.username=${DB_USER}
spring.r2dbc.password=${DB_PASS}
```

#### X (Twitter) OAuth Configuration
```properties
x.client-id=${X_OAuth2.0_Client_ID}
x.client-secret=${X_OAuth2.0_Client_Secrete}
x.redirect-uri=${X_V2_CALLBACK_URL}
x.code-verifier-length=${code-verifier-length}
```

#### Email Configuration
```properties
mailgun.api-key=${MAILGUN_API_KEY}
mailgun.domain=${DOMAIN}
```

#### Encryption Configuration
```properties
aes.secret-key=${AES_SECRET_KEY}
aes.auth-secret-key=${AES_AUTHENTICATION_KEY}
```

#### JWT Configuration
```properties
jwt.access-secret=${jwt-access-secret}
jwt.refresh-secret=${jwt-refresh-secret}
jwt.access-token.expiration=900        # 15 minutes
jwt.refresh-token.expiration=2592000  # 30 days
```

#### Application URLs
```properties
app.frontend-url=${front-end-url}
app.backend-url=${backend-url}
```

#### Cookie Settings
```properties
cookie.secure=false              # Set true in production
cookie.same-site=Strict
```

#### CORS Settings
```properties
cors.allowed-origins=http://localhost:5173,http://localhost:5174
```

---

## 🚀 Setup Instructions

### Prerequisites
- **Java 21** or higher
- **PostgreSQL 14+** database
- **Maven 3.8+** for building
- **Mailgun account** for email sending
- **X Developer account** with OAuth 2.0 app credentials

### Steps

1. **Clone the Repository**
   ```bash
   git clone https://github.com/ali-fk1/flux-project.git
   cd flux-project
   ```

2. **Create PostgreSQL Database**
   ```bash
   psql -U postgres
   CREATE DATABASE flux_db;
   \c flux_db
   ```

3. **Run Database Migrations**
   ```bash
   psql -U your_user -d flux_db -f src/main/resources/schema.sql
   ```

4. **Configure Environment Variables**
   Create `.env` file in project root (use `.env.example` as template):
   ```bash
   cp .env.example .env
   # Edit .env with your credentials
   ```

5. **Generate Encryption Keys**
   ```bash
   # Generate AES-256 key (32 bytes)
   openssl rand -base64 32
   ```

6. **Build the Project**
   ```bash
   mvn clean install
   ```

7. **Run the Application**
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8080`

### Verify Installation

1. **Check Health**: Visit `http://localhost:8080/actuator/health` (if actuator is enabled)
2. **Test Signup**: `POST http://localhost:8080/signup` with email/password
3. **Check Logs**: Look for startup messages indicating successful initialization

---

## 📡 API Endpoints

### Authentication Endpoints

#### Sign Up
```http
POST /signup
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!"
}

Response: 201 Created
```

#### Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!"
}

Response: 200 OK
Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Strict
Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Strict

{
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "name": "John Doe"
  }
}
```

#### Refresh Token
```http
POST /api/auth/refresh
Cookie: refresh_token=...

Response: 200 OK
(New cookies set in headers)
```

#### Logout
```http
POST /api/auth/logout

Response: 200 OK
(Cookies cleared)
```

### Email Verification

#### Verify Email
```http
GET /verify?token=abc123...

Response: 302 Found
Location: http://localhost:5173/verification-success
```

#### Resend Verification
```http
POST /resend-verification
Content-Type: application/json

{
  "email": "user@example.com"
}

Response: 200 OK
```

### X (Twitter) Integration

#### Start OAuth Flow
```http
POST /api/x
Cookie: access_token=...

Response: 200 OK
"https://x.com/i/oauth2/authorize?response_type=code&client_id=..."
```

#### OAuth Callback
```http
GET /api/x/callback?code=abc&state=xyz

Response: 302 Found
Location: http://localhost:5173/auth/success
```

#### Check Connection Status
```http
GET /api/x/status
Cookie: access_token=...

Response: 200 OK
{
  "connected": true
}
```

### Post Management

#### Create Tweet
```http
POST /api/post
Cookie: access_token=...
Content-Type: application/json

{
  "text": "Hello from Flux Project! 🚀"
}

Response: 201 Created
{
  "tweetId": "1234567890",
  "text": "Hello from Flux Project! 🚀"
}
```

#### Check Token Expiry
```http
GET /api/expired
Cookie: access_token=...

Response: 200 OK
"Access Token Not Expired"
```

---

## 🧪 Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

The project uses **Testcontainers** for integration testing, which automatically spins up PostgreSQL containers during test execution.

---

## 🎥 Demo

▶️ Watch the demo (unlisted):https://youtu.be/AUPeNMkumTo

---

## 📝 Key Learning Points

1. **Reactive Programming**: Learned to think in terms of streams and chains instead of blocking operations
2. **Security Best Practices**: Implemented multiple layers of security (encryption, JWT, cookies, CORS)
3. **OAuth 2.0 PKCE**: Understood the importance of PKCE in preventing authorization code interception
4. **Token Management**: Built a complete token lifecycle (generation, validation, refresh, revocation, cleanup)
5. **Error Handling**: Designed specific exception types for different failure scenarios
6. **Database Design**: Created proper indexes and constraints for performance and data integrity

---

## 🔮 Future Enhancements

- [ ] Add support for multiple social media platforms (Instagram, LinkedIn, Facebook)
- [ ] Implement scheduled post functionality
- [ ] Add media upload support for tweets
- [ ] Create admin dashboard for monitoring
- [ ] Implement rate limiting for API endpoints
- [ ] Add Redis caching for frequently accessed data
- [ ] Create comprehensive API documentation with Swagger/OpenAPI
- [ ] Implement post analytics and engagement tracking

---

## 📄 License

This project is open source and available under the MIT License.

---

## 👤 Author

**Ali FK**
- GitHub: [@ali-fk1](https://github.com/ali-fk1)

---

## 🙏 Acknowledgments

- Spring Team for excellent reactive programming support
- X Developer Platform for API access
- Mailgun for reliable email delivery
- The reactive programming community for best practices and patterns

---

**Built with ❤️ using Spring WebFlux and Reactive Programming**

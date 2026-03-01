# ai.chat.kz

Backend API for chats and projects built with Spring Boot.

## Features

- User registration and login.
- Bearer-token authentication.
- CRUD operations for chats and messages.
- Chat grouping by projects.
- AI reply generation with Gemini API (sync + SSE stream).

## Tech Stack

- Java 21
- Spring Boot 4 (`4.0.4-SNAPSHOT`)
- Spring Security
- Spring Data JPA
- PostgreSQL
- Gradle

## Requirements

- JDK 21
- PostgreSQL 14+ (or compatible)

## Configuration

File: `src/main/resources/application.properties`

```properties
spring.application.name=ai.chat.kz

spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/ai.chat.kz}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:123}
spring.jpa.hibernate.ddl-auto=update

ai.gemini.api-key=${GEMINI_API_KEY:}
ai.gemini.model=${GEMINI_MODEL:gemini-2.0-flash}
ai.gemini.temperature=${GEMINI_TEMPERATURE:0.7}
ai.context.max-messages=${AI_CONTEXT_MAX_MESSAGES:30}
ai.context.max-chars=${AI_CONTEXT_MAX_CHARS:12000}
```

Before running the app, create the database in PostgreSQL:

```sql
CREATE DATABASE "ai.chat.kz";
```

## Environment Variables

- `DB_URL`
- `DB_USER`
- `DB_PASSWORD`
- `GEMINI_API_KEY`
- `GEMINI_MODEL` (optional)
- `GEMINI_TEMPERATURE` (optional)
- `AI_CONTEXT_MAX_MESSAGES` (optional)
- `AI_CONTEXT_MAX_CHARS` (optional)
- `JWT_SECRET` (base64 string)
- `JWT_ACCESS_TOKEN_MINUTES` (optional)

PowerShell example:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/ai.chat.kz"
$env:DB_USER="postgres"
$env:DB_PASSWORD="123"
$env:GEMINI_API_KEY="YOUR_GEMINI_KEY"
$env:GEMINI_MODEL="gemini-2.0-flash"
```

## Run

Windows (PowerShell):

```powershell
.\gradlew.bat bootRun
```

Linux/macOS:

```bash
./gradlew bootRun
```

Default URL: `http://localhost:8080`

## Build and Test

```bash
./gradlew clean build
./gradlew test
```

On Windows use `.\gradlew.bat` instead of `./gradlew`.

## Authentication

- Public endpoints: `/api/auth/**`
- All other endpoints require:

```http
Authorization: Bearer <token>
```

Tokens are issued on `register/login`.
`register/login` returns:

- `accessToken` (JWT, short-lived)
- `refreshToken` (stored in DB, long-lived)
- `accessTokenExpiresAt`
- `refreshTokenExpiresAt`

## Main Endpoints

### Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`

### Chats

- `POST /api/chats`
- `GET /api/chats`
- `PATCH /api/chats/{chatId}`
- `DELETE /api/chats/{chatId}`
- `POST /api/chats/{chatId}/messages`
- `GET /api/chats/{chatId}/messages`
- `GET /api/chats/{chatId}/messages/by-day?from=YYYY-MM-DD&to=YYYY-MM-DD&tz=Asia/Almaty`
- `PATCH /api/chats/{chatId}/project`
- `DELETE /api/chats/{chatId}/project`
- `POST /api/chats/{chatId}/generate`
- `POST /api/chats/{chatId}/regenerate`
- `POST /api/chats/{chatId}/generate/stream` (`text/event-stream`)

Allowed message `role` values:

- `SYSTEM`
- `USER`
- `ASSISTANT`

Generate payload example:

```json
{
  "prompt": "Explain what JPA entity is in simple words",
  "systemPrompt": "Answer shortly and clearly."
}
```

Notes:

- If `prompt` is passed, it is stored as a new `USER` message before generation.
- Assistant reply is stored as `ASSISTANT`.
- Context is built from chat history with max message and char limits.
- `regenerate` removes the last assistant message (if present) and generates a new one from existing context.

### Projects

- `POST /api/projects`
- `GET /api/projects`
- `POST /api/projects/{projectId}/chats`
- `GET /api/projects/{projectId}/chats`

## Validation and Errors

- DTO validation uses `jakarta.validation` (`@Valid`, `@NotBlank`, `@Size`, `@Email`, etc.).
- Error response format:

```json
{
  "timestamp": "2026-03-01T12:00:00Z",
  "status": 400,
  "error": "field: validation message"
}
```

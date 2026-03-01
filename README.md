# ai.chat.kz

Backend API for chats and projects built with Spring Boot.

## Features

- User registration and login.
- Bearer-token authentication.
- CRUD operations for chats and messages.
- Chat grouping by projects.

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

Current settings:

```properties
spring.application.name=ai.chat.kz

spring.datasource.url=jdbc:postgresql://localhost:5432/ai.chat.kz
spring.datasource.username=
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
```

Before running the app, create the database in PostgreSQL:

```sql
CREATE DATABASE "ai.chat.kz";
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

Token is issued on `register/login` and is valid for 30 days.

## Main Endpoints

### Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

Register payload example:

```json
{
  "email": "user@example.com",
  "password": "secret123",
  "username": "yelshod"
}
```

### Chats

- `POST /api/chats` - create chat
- `GET /api/chats` - list chats
- `PATCH /api/chats/{chatId}` - rename chat
- `DELETE /api/chats/{chatId}` - delete chat
- `POST /api/chats/{chatId}/messages` - add message
- `GET /api/chats/{chatId}/messages` - list messages
- `GET /api/chats/{chatId}/messages/by-day?from=YYYY-MM-DD&to=YYYY-MM-DD&tz=Asia/Almaty`
- `PATCH /api/chats/{chatId}/project` - move chat to project
- `DELETE /api/chats/{chatId}/project` - remove chat from project

Allowed message `role` values:

- `SYSTEM`
- `USER`
- `ASSISTANT`

### Projects

- `POST /api/projects` - create project
- `GET /api/projects` - list projects
- `POST /api/projects/{projectId}/chats` - create chat in project
- `GET /api/projects/{projectId}/chats` - list project chats

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

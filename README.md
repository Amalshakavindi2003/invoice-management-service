# EasyInvoice Service

Spring Boot backend for the EasyInvoice platform.

## Overview

This service provides authenticated REST APIs for:

- Admin authentication with JWT
- Customer management
- Invoice creation and lifecycle management
- Payment and reminder operations

## Tech Stack

- Java 17
- Spring Boot 3
- Spring Security + JWT
- Spring Data JPA
- H2 database for local development

## Run Locally

1. Open terminal in repository root.
2. Run:

```bash
./mvnw spring-boot:run
```

On Windows PowerShell, use:

```powershell
.\mvnw.cmd spring-boot:run
```

Service default URL:

- http://localhost:8080

## Local Database

This repository is configured for H2 in-memory database by default.

- JDBC URL: jdbc:h2:mem:invoiceprocessingsystem
- H2 Console: http://localhost:8080/h2-console

## Authentication

Protected endpoints require this header:

- Authorization: Bearer <jwt_token>

Login and registration endpoints are under:

- /api/auth/login
- /api/auth/register

## Build and Test

Run tests:

```bash
./mvnw test
```

Create jar:

```bash
./mvnw clean package
```

## API Notes

Current API routes used by frontend include:

- /customer
- /invoice
- /api/auth/*

CORS is configured to allow requests from:

- http://localhost:3000
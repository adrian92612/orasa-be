# API Overview

## Overview

The Orasa Backend provides a complete REST API for a **B2B appointment management system** targeting micro and small service-based businesses.

## Core Capabilities

- ✅ **Authentication** (Google OAuth for Owners, Username/Password for Staff)
- ✅ **Business & Branch Management**
- ✅ **Service Catalog Management**
- ✅ **Staff Management**
- ✅ **Appointment CRUD Operations**
- ✅ **SMS Reminders Configuration**
- ✅ **SMS Logs Viewing**

## User Roles

| Role    | Description                              | Access Level                            |
| ------- | ---------------------------------------- | --------------------------------------- |
| `OWNER` | Business admin, created via Google OAuth | Full access to all features             |
| `STAFF` | Employee accounts, created by Owner      | Appointments only for assigned branches |
| `ADMIN` | Platform admin (Orasa operators)         | Subscription management (future)        |

## Technology Stack

- **Framework**: Spring Boot 3.x
- **Database**: Supabase (PostgreSQL)
- **Authentication**: JWT with HTTP-only cookies
- **SMS Provider**: PhilSMS

## API Modules

### 1. Authentication (`/auth`)

Handles Google OAuth for owners and username/password login for staff.

### 2. Business (`/businesses`)

Business creation during onboarding with atomic branch creation.

### 3. Branches (`/branches`)

Branch management and service assignments per branch.

### 4. Services (`/services`)

Service catalog management at business level.

### 5. Staff (`/staff`)

Staff account management and password handling.

### 6. Appointments (`/appointments`)

Full CRUD for appointments with search and filtering.

### 7. Reminder Configs (`/reminder-configs`)

SMS reminder template and lead time configuration.

### 8. SMS (`/sms`)

SMS logs viewing and balance checking.

## Security

- All endpoints except `/auth/**` require authentication
- Role-based access control via `@PreAuthorize`
- JWT stored in HTTP-only, Secure, SameSite=None cookies
- CORS configured for frontend origin

## Pagination

Endpoints returning lists support Spring's `Pageable`:

```
?page=0&size=20&sort=createdAt,desc
```

Response includes pagination metadata:

```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

## Date/Time Format

All date/time values use ISO 8601 with timezone:

```
2026-02-05T10:00:00+08:00
```

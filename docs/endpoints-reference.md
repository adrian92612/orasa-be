# Endpoints Reference

Complete reference for all API endpoints.

---

## Authentication (`/auth`)

| Method | Endpoint                | Auth | Role | Description             |
| ------ | ----------------------- | ---- | ---- | ----------------------- |
| `GET`  | `/auth/google`          | ❌   | -    | Start Google OAuth flow |
| `GET`  | `/auth/google/callback` | ❌   | -    | Google OAuth callback   |
| `POST` | `/auth/staff/login`     | ❌   | -    | Staff login             |
| `GET`  | `/auth/me`              | ✅   | Any  | Get current user        |
| `POST` | `/auth/logout`          | ✅   | Any  | Logout                  |

---

## Business (`/businesses`)

| Method | Endpoint         | Auth | Role  | Description                       |
| ------ | ---------------- | ---- | ----- | --------------------------------- |
| `POST` | `/businesses`    | ✅   | OWNER | Create business with first branch |
| `GET`  | `/businesses/me` | ✅   | OWNER | Get owner's business              |

### POST /businesses

Creates a new business with its first branch atomically.

**Request:**

```json
{
  "name": "My Clinic",
  "branch": {
    "name": "Main Branch",
    "address": "123 Main St",
    "phoneNumber": "09123456789"
  }
}
```

**Response:**

```json
{
  "success": true,
  "message": "Business created successfully",
  "data": {
    "id": "uuid",
    "name": "My Clinic",
    "slug": "my-clinic",
    "freeSmsCredits": 100,
    "paidSmsCredits": 0,
    "subscriptionStatus": "PENDING",
    "subscriptionStartDate": null,
    "subscriptionEndDate": null,
    "createdAt": "2026-02-04T10:00:00Z",
    "firstBranchId": "uuid"
  }
}
```

### GET /businesses/me

**Response:**

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "id": "uuid",
    "name": "My Clinic",
    "slug": "my-clinic",
    "freeSmsCredits": 95,
    "paidSmsCredits": 0,
    "subscriptionStatus": "ACTIVE",
    "subscriptionStartDate": "2026-01-01T00:00:00Z",
    "subscriptionEndDate": "2026-02-01T00:00:00Z",
    "createdAt": "2026-01-01T10:00:00Z"
  }
}
```

---

## Branches (`/branches`)

| Method   | Endpoint                                          | Auth | Role         | Description                |
| -------- | ------------------------------------------------- | ---- | ------------ | -------------------------- |
| `POST`   | `/branches`                                       | ✅   | OWNER        | Create branch              |
| `GET`    | `/branches`                                       | ✅   | OWNER        | List all branches          |
| `GET`    | `/branches/{branchId}`                            | ✅   | OWNER, STAFF | Get branch by ID           |
| `POST`   | `/branches/{branchId}/services`                   | ✅   | OWNER        | Assign service to branch   |
| `GET`    | `/branches/{branchId}/services`                   | ✅   | OWNER, STAFF | List branch services       |
| `PUT`    | `/branches/{branchId}/services/{branchServiceId}` | ✅   | OWNER        | Update branch service      |
| `DELETE` | `/branches/{branchId}/services/{branchServiceId}` | ✅   | OWNER        | Remove service from branch |

### POST /branches

**Request:**

```json
{
  "name": "Second Branch",
  "address": "456 Other St",
  "phoneNumber": "09123456789"
}
```

**Response:**

```json
{
  "success": true,
  "message": "Branch created successfully",
  "data": {
    "id": "uuid",
    "businessId": "uuid",
    "name": "Second Branch",
    "address": "456 Other St",
    "phoneNumber": "09123456789",
    "createdAt": "2026-02-04T10:00:00Z",
    "updatedAt": "2026-02-04T10:00:00Z"
  }
}
```

### POST /branches/{branchId}/services

Assigns a service to a specific branch with optional custom pricing.

**Request:**

```json
{
  "serviceId": "uuid",
  "customPrice": 175.0,
  "active": true
}
```

**Response:**

```json
{
  "success": true,
  "message": "Service assigned to branch successfully",
  "data": {
    "id": "uuid",
    "branchId": "uuid",
    "serviceId": "uuid",
    "serviceName": "Haircut",
    "serviceDescription": "Basic haircut service",
    "basePrice": 150.0,
    "customPrice": 175.0,
    "effectivePrice": 175.0,
    "durationMinutes": 30,
    "active": true,
    "createdAt": "2026-02-04T10:00:00Z"
  }
}
```

---

## Services (`/services`)

| Method   | Endpoint                | Auth | Role         | Description       |
| -------- | ----------------------- | ---- | ------------ | ----------------- |
| `POST`   | `/services`             | ✅   | OWNER        | Create service    |
| `GET`    | `/services`             | ✅   | OWNER, STAFF | List all services |
| `GET`    | `/services/{serviceId}` | ✅   | OWNER, STAFF | Get service by ID |
| `PUT`    | `/services/{serviceId}` | ✅   | OWNER        | Update service    |
| `DELETE` | `/services/{serviceId}` | ✅   | OWNER        | Delete service    |

### POST /services

**Request:**

```json
{
  "name": "Haircut",
  "description": "Basic haircut service",
  "basePrice": 150.0,
  "durationMinutes": 30,
  "availableGlobally": true
}
```

**Response:**

```json
{
  "success": true,
  "message": "Service created successfully",
  "data": {
    "id": "uuid",
    "businessId": "uuid",
    "name": "Haircut",
    "description": "Basic haircut service",
    "basePrice": 150.0,
    "durationMinutes": 30,
    "availableGlobally": true,
    "createdAt": "2026-02-04T10:00:00Z",
    "updatedAt": "2026-02-04T10:00:00Z"
  }
}
```

### PUT /services/{serviceId}

**Request:**

```json
{
  "name": "Premium Haircut",
  "description": "Premium haircut with styling",
  "basePrice": 200.0,
  "durationMinutes": 45,
  "availableGlobally": true
}
```

---

## Staff (`/staff`)

| Method   | Endpoint                 | Auth | Role         | Description         |
| -------- | ------------------------ | ---- | ------------ | ------------------- |
| `POST`   | `/staff`                 | ✅   | OWNER        | Create staff member |
| `GET`    | `/staff`                 | ✅   | OWNER        | List all staff      |
| `GET`    | `/staff/{staffId}`       | ✅   | OWNER        | Get staff by ID     |
| `PUT`    | `/staff/{staffId}`       | ✅   | OWNER        | Update staff        |
| `DELETE` | `/staff/{staffId}`       | ✅   | OWNER        | Delete staff        |
| `POST`   | `/staff/change-password` | ✅   | OWNER, STAFF | Change own password |

### POST /staff

**Request:**

```json
{
  "username": "john_staff",
  "email": "john@example.com",
  "temporaryPassword": "temp123456",
  "branchIds": ["uuid", "uuid"]
}
```

**Response:**

```json
{
  "success": true,
  "message": "Staff member created successfully",
  "data": {
    "id": "uuid",
    "businessId": "uuid",
    "username": "john_staff",
    "email": "john@example.com",
    "role": "STAFF",
    "mustChangePassword": true,
    "branches": [
      { "id": "uuid", "name": "Main Branch" },
      { "id": "uuid", "name": "Second Branch" }
    ],
    "createdAt": "2026-02-04T10:00:00Z",
    "updatedAt": "2026-02-04T10:00:00Z"
  }
}
```

### PUT /staff/{staffId}

**Request:**

```json
{
  "email": "newemail@example.com",
  "newPassword": "newpassword123",
  "branchIds": ["uuid"]
}
```

### POST /staff/change-password

**Request:**

```json
{
  "currentPassword": "old_password",
  "newPassword": "new_password123"
}
```

**Response:**

```json
{
  "success": true,
  "message": "Password changed successfully"
}
```

---

## Appointments (`/appointments`)

| Method   | Endpoint                                 | Auth | Role         | Description         |
| -------- | ---------------------------------------- | ---- | ------------ | ------------------- |
| `POST`   | `/appointments`                          | ✅   | OWNER, STAFF | Create appointment  |
| `GET`    | `/appointments/{id}`                     | ✅   | OWNER, STAFF | Get appointment     |
| `PUT`    | `/appointments/{id}`                     | ✅   | OWNER, STAFF | Update appointment  |
| `DELETE` | `/appointments/{id}`                     | ✅   | OWNER        | Delete appointment  |
| `GET`    | `/appointments/branch/{branchId}`        | ✅   | OWNER, STAFF | List by branch      |
| `GET`    | `/appointments/business/{businessId}`    | ✅   | OWNER        | List by business    |
| `GET`    | `/appointments/branch/{branchId}/search` | ✅   | OWNER, STAFF | Search appointments |

### POST /appointments

**Request:**

```json
{
  "businessId": "uuid",
  "branchId": "uuid",
  "customerName": "John Doe",
  "customerPhone": "09123456789",
  "startDateTime": "2026-02-05T10:00:00+08:00",
  "endDateTime": "2026-02-05T10:30:00+08:00",
  "notes": "First time customer",
  "isWalkin": false
}
```

**Response:**

```json
{
  "success": true,
  "message": "Appointment created successfully",
  "data": {
    "id": "uuid",
    "businessId": "uuid",
    "branchId": "uuid",
    "branchName": "Main Branch",
    "customerName": "John Doe",
    "customerPhone": "09123456789",
    "startDateTime": "2026-02-05T10:00:00+08:00",
    "endDateTime": "2026-02-05T10:30:00+08:00",
    "notes": "First time customer",
    "status": "SCHEDULED",
    "createdAt": "2026-02-04T10:00:00Z",
    "updatedAt": "2026-02-04T10:00:00Z"
  }
}
```

### PUT /appointments/{id}

**Request:**

```json
{
  "customerName": "John Doe",
  "customerPhone": "09123456789",
  "startDateTime": "2026-02-05T11:00:00+08:00",
  "endDateTime": "2026-02-05T11:30:00+08:00",
  "notes": "Rescheduled",
  "status": "CONFIRMED"
}
```

### GET /appointments/branch/{branchId}/search

**Query Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `search` | ❌ | Search by customer name/phone |
| `startDate` | ✅ | Start date (YYYY-MM-DD) |
| `endDate` | ✅ | End date (YYYY-MM-DD) |
| `page` | ❌ | Page number (default: 0) |
| `size` | ❌ | Page size (default: 20) |

**Example:**

```
GET /appointments/branch/{branchId}/search?search=john&startDate=2026-02-01&endDate=2026-02-28&page=0&size=20
```

---

## Reminder Configs (`/reminder-configs`)

| Method   | Endpoint                       | Auth | Role  | Description      |
| -------- | ------------------------------ | ---- | ----- | ---------------- |
| `POST`   | `/reminder-configs`            | ✅   | OWNER | Create config    |
| `GET`    | `/reminder-configs`            | ✅   | OWNER | List all configs |
| `PUT`    | `/reminder-configs/{configId}` | ✅   | OWNER | Update config    |
| `DELETE` | `/reminder-configs/{configId}` | ✅   | OWNER | Delete config    |

### POST /reminder-configs

**Request:**

```json
{
  "leadTimeHours": 24,
  "messageTemplate": "Hi {customer_name}, reminder: Your appointment at {branch_name} is on {date} at {time}. See you!",
  "enabled": true
}
```

**Template Placeholders:**

- `{customer_name}` - Customer's name
- `{date}` - Appointment date
- `{time}` - Appointment time
- `{branch_name}` - Branch name

**Response:**

```json
{
  "success": true,
  "message": "Reminder configuration created successfully",
  "data": {
    "id": "uuid",
    "businessId": "uuid",
    "leadTimeHours": 24,
    "messageTemplate": "Hi {customer_name}...",
    "enabled": true,
    "createdAt": "2026-02-04T10:00:00Z",
    "updatedAt": "2026-02-04T10:00:00Z"
  }
}
```

---

## SMS (`/sms`)

| Method | Endpoint       | Auth | Role  | Description       |
| ------ | -------------- | ---- | ----- | ----------------- |
| `GET`  | `/sms/logs`    | ✅   | OWNER | View SMS logs     |
| `GET`  | `/sms/balance` | ✅   | OWNER | Check SMS balance |

### GET /sms/logs

**Query Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `page` | ❌ | Page number (default: 0) |
| `size` | ❌ | Page size (default: 20) |

**Response:**

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": "uuid",
        "businessId": "uuid",
        "appointmentId": "uuid",
        "customerName": "John Doe",
        "recipientPhone": "09123456789",
        "messageBody": "Reminder: Your appointment...",
        "status": "SENT",
        "errorMessage": null,
        "createdAt": "2026-02-04T09:00:00Z"
      }
    ],
    "totalElements": 50,
    "totalPages": 3,
    "number": 0,
    "size": 20
  }
}
```

### GET /sms/balance

**Response:**

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "success": true,
    "remainingCredits": 95,
    "errorMessage": null
  }
}
```

---

## Activity Logs (`/activity-logs`)

Activity logs provide an audit trail of all actions performed within your business. Logs are automatically created when users perform operations like creating appointments, updating staff, etc.

| Method | Endpoint                                      | Auth | Role         | Description                |
| ------ | --------------------------------------------- | ---- | ------------ | -------------------------- |
| `GET`  | `/activity-logs/business/{businessId}`        | ✅   | OWNER, ADMIN | Get all logs for business  |
| `GET`  | `/activity-logs/branch/{branchId}`            | ✅   | OWNER, ADMIN | Get logs for branch        |
| `GET`  | `/activity-logs/user/{userId}`                | ✅   | OWNER, ADMIN | Get logs for specific user |
| `GET`  | `/activity-logs/business/{businessId}/search` | ✅   | OWNER, ADMIN | Search logs with filters   |

### GET /activity-logs/business/{businessId}

**Query Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `page` | ❌ | Page number (default: 0) |
| `size` | ❌ | Page size (default: 20) |
| `sort` | ❌ | Sort field (default: createdAt,desc) |

**Response:**

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": "uuid",
        "userId": "uuid",
        "userName": "owner@example.com",
        "userRole": "OWNER",
        "businessId": "uuid",
        "branchId": "uuid",
        "branchName": "Main Branch",
        "action": "APPOINTMENT_UPDATED",
        "description": "Updated appointment for John Doe",
        "details": "[{\"field\":\"Start Time\",\"before\":\"Feb 4, 2026 3:00 PM\",\"after\":\"Feb 5, 2026 4:00 PM\"}]",
        "createdAt": "2026-02-04T10:00:00+08:00"
      }
    ],
    "totalElements": 100,
    "totalPages": 5,
    "number": 0,
    "size": 20
  }
}
```

### GET /activity-logs/business/{businessId}/search

**Query Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `branchId` | ❌ | Filter by specific branch |
| `action` | ❌ | Filter by action type (e.g., APPOINTMENT_CREATED) |
| `startDate` | ❌ | Filter logs from this date (YYYY-MM-DD) |
| `endDate` | ❌ | Filter logs until this date (YYYY-MM-DD) |
| `page` | ❌ | Page number (default: 0) |
| `size` | ❌ | Page size (default: 20) |

**Example:**

```
GET /activity-logs/business/{businessId}/search?action=APPOINTMENT_CREATED&startDate=2026-02-01&endDate=2026-02-28
```

### Details Field Structure

The `details` field contains a JSON array of field changes for update operations. This enables frontend to display expandable before/after comparisons:

```json
[
  {
    "field": "Customer Name",
    "before": "John Doe",
    "after": "Jane Smith"
  },
  {
    "field": "Start Time",
    "before": "Feb 4, 2026 3:00 PM",
    "after": "Feb 5, 2026 4:00 PM"
  }
]
```

---

## Analytics (`/analytics`)

| Method | Endpoint               | Auth | Role  | Description         |
| ------ | ---------------------- | ---- | ----- | ------------------- |
| `GET`  | `/analytics/dashboard` | ✅   | OWNER | Get dashboard stats |

### GET /analytics/dashboard

Retrieves aggregated statistics for the owner dashboard.

**Query Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `startDate` | ✅ | Start date (YYYY-MM-DD) |
| `endDate` | ✅ | End date (YYYY-MM-DD) |

**Response:**

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "totalAppointments": 150,
    "scheduledCount": 120,
    "walkInCount": 30,
    "cancelledCount": 5,
    "noShowCount": 2,
    "smsSent": 115,
    "smsFailed": 5
  }
}
```

---

## Platform Admin (`/admin`)

| Method | Endpoint                                       | Auth | Role  | Description           |
| ------ | ---------------------------------------------- | ---- | ----- | --------------------- |
| `GET`  | `/admin/businesses`                            | ✅   | ADMIN | List all businesses   |
| `POST` | `/admin/businesses/{id}/subscription/activate` | ✅   | ADMIN | Activate subscription |
| `POST` | `/admin/businesses/{id}/subscription/extend`   | ✅   | ADMIN | Extend subscription   |

### GET /admin/businesses

**Query Parameters:**
| Parameter | Required | Description |
|-----------|----------|-------------|
| `query` | ❌ | Search by business name |
| `page` | ❌ | Page number (default: 0) |
| `size` | ❌ | Page size (default: 20) |

### POST /admin/businesses/{id}/subscription/activate

**Request:**

```json
{
  "months": 1
}
```

### POST /admin/businesses/{id}/subscription/extend

**Request:**

```json
{
  "months": 1
}
```

---

## Profile (`/profile`)

| Method | Endpoint                   | Auth | Role | Description           |
| ------ | -------------------------- | ---- | ---- | --------------------- |
| `PUT`  | `/profile/me`              | ✅   | Any  | Update username/email |
| `POST` | `/profile/change-password` | ✅   | Any  | Change password       |

### PUT /profile/me

Updates user profile.

**Request:**

```json
{
  "username": "new_username",
  "email": "new@example.com"
}
```

> **Note:** Only OWNERS can update their email.

### POST /profile/change-password

**Request:**

```json
{
  "currentPassword": "oldPassword",
  "newPassword": "newPassword123"
}
```

---

## Business Settings (`/businesses`)

| Method | Endpoint         | Auth | Role  | Description             |
| ------ | ---------------- | ---- | ----- | ----------------------- |
| `PUT`  | `/businesses/me` | ✅   | OWNER | Update business details |

### PUT /businesses/me

**Request:**

```json
{
  "name": "New Business Name"
}
```

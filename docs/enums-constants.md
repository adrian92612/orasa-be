# Enums & Constants

Reference for all enum values and constants used in the API.

---

## User Roles

```typescript
enum UserRole {
  ADMIN = "ADMIN", // Platform administrator (Orasa operators)
  OWNER = "OWNER", // Business owner
  STAFF = "STAFF", // Business staff member
}
```

| Role    | Description          | Auth Method       |
| ------- | -------------------- | ----------------- |
| `ADMIN` | Platform admin       | TBD               |
| `OWNER` | Business owner/admin | Google OAuth      |
| `STAFF` | Employee             | Username/Password |

---

## Appointment Status

```typescript
enum AppointmentStatus {
  WALK_IN = "WALK_IN", // Walk-in, no SMS reminders
  SCHEDULED = "SCHEDULED", // Initial scheduled state
  CONFIRMED = "CONFIRMED", // Confirmed by customer
  CANCELLED = "CANCELLED", // Appointment cancelled
  NO_SHOW = "NO_SHOW", // Customer didn't show up
  COMPLETED = "COMPLETED", // Successfully completed
}
```

### Status Flow

```
Create (isWalkin=false) → SCHEDULED
Create (isWalkin=true)  → WALK_IN

SCHEDULED → CONFIRMED   (Customer confirms)
SCHEDULED → CANCELLED   (Cancelled before time)
SCHEDULED → NO_SHOW     (Customer didn't show)
SCHEDULED → COMPLETED   (Appointment done)

CONFIRMED → CANCELLED   (Cancelled after confirmation)
CONFIRMED → NO_SHOW     (Customer didn't show)
CONFIRMED → COMPLETED   (Appointment done)

WALK_IN   → COMPLETED   (Walk-in completed)
WALK_IN   → NO_SHOW     (Walk-in left before service)
```

### Status Colors (Suggested)

| Status    | Color  | Hex       |
| --------- | ------ | --------- |
| WALK_IN   | Purple | `#8B5CF6` |
| SCHEDULED | Blue   | `#3B82F6` |
| CONFIRMED | Green  | `#22C55E` |
| CANCELLED | Gray   | `#6B7280` |
| NO_SHOW   | Red    | `#EF4444` |
| COMPLETED | Teal   | `#14B8A6` |

---

## SMS Status

```typescript
enum SmsStatus {
  PENDING = "PENDING", // Scheduled but not sent
  SENT = "SENT", // Successfully sent to provider
  DELIVERED = "DELIVERED", // Confirmed delivered (if available)
  FAILED = "FAILED", // Failed to send
  INSUFFICIENT_CREDIT = "INSUFFICIENT_CREDIT", // No SMS credits available
}
```

### Status Icons (Suggested)

| Status              | Icon | Description      |
| ------------------- | ---- | ---------------- |
| PENDING             | ⏳   | Clock/hourglass  |
| SENT                | ✓    | Single checkmark |
| DELIVERED           | ✓✓   | Double checkmark |
| FAILED              | ✗    | X mark           |
| INSUFFICIENT_CREDIT | ⚠    | Warning triangle |

---

## SMS Task Status

Internal status for scheduled SMS tasks:

```typescript
enum SmsTaskStatus {
  PENDING = "PENDING", // Waiting to be processed
  COMPLETED = "COMPLETED", // Successfully sent
  FAILED = "FAILED", // Failed to send
  CANCELLED = "CANCELLED", // Cancelled (e.g., appointment cancelled)
  SKIPPED = "SKIPPED", // Skipped (e.g., appointment already passed)
}
```

---

## Activity Action

Actions that are logged in the activity log for audit purposes:

```typescript
enum ActivityAction {
  // Appointment actions
  APPOINTMENT_CREATED = "APPOINTMENT_CREATED",
  APPOINTMENT_UPDATED = "APPOINTMENT_UPDATED",
  APPOINTMENT_DELETED = "APPOINTMENT_DELETED",
  APPOINTMENT_STATUS_CHANGED = "APPOINTMENT_STATUS_CHANGED",

  // Staff actions
  STAFF_CREATED = "STAFF_CREATED",
  STAFF_UPDATED = "STAFF_UPDATED",
  STAFF_PASSWORD_RESET = "STAFF_PASSWORD_RESET",
  STAFF_DEACTIVATED = "STAFF_DEACTIVATED",

  // Branch actions
  BRANCH_CREATED = "BRANCH_CREATED",
  BRANCH_UPDATED = "BRANCH_UPDATED",

  // Service actions
  SERVICE_CREATED = "SERVICE_CREATED",
  SERVICE_UPDATED = "SERVICE_UPDATED",
  SERVICE_DELETED = "SERVICE_DELETED",

  // Business settings
  REMINDER_CONFIG_UPDATED = "REMINDER_CONFIG_UPDATED",

  // Authentication
  USER_LOGIN = "USER_LOGIN",
  USER_LOGOUT = "USER_LOGOUT",
}
```

| Action                     | Logged By    | Description                         |
| -------------------------- | ------------ | ----------------------------------- |
| APPOINTMENT_CREATED        | Owner, Staff | New appointment created             |
| APPOINTMENT_UPDATED        | Owner, Staff | Appointment details changed         |
| APPOINTMENT_STATUS_CHANGED | Owner, Staff | Status transition (e.g., COMPLETED) |
| APPOINTMENT_DELETED        | Owner        | Appointment soft-deleted            |
| STAFF_CREATED              | Owner        | New staff account created           |
| STAFF_UPDATED              | Owner        | Staff details/branches changed      |
| STAFF_PASSWORD_RESET       | Owner, Staff | Password was reset                  |
| STAFF_DEACTIVATED          | Owner        | Staff account deactivated           |
| BRANCH_CREATED             | Owner        | New branch added                    |
| SERVICE_CREATED            | Owner        | New service catalog item            |
| SERVICE_UPDATED            | Owner        | Service details changed             |
| SERVICE_DELETED            | Owner        | Service removed from catalog        |

---

## Subscription Status

```typescript
enum SubscriptionStatus {
  PENDING = "PENDING", // Awaiting payment
  ACTIVE = "ACTIVE", // Active subscription
  EXPIRED = "EXPIRED", // Subscription expired
  CANCELLED = "CANCELLED", // Subscription cancelled
}
```

### Business Rules

| Status    | Can Create Appointments | SMS Reminders |
| --------- | ----------------------- | ------------- |
| PENDING   | ⚠ Limited               | ❌ No         |
| ACTIVE    | ✅ Yes                  | ✅ Yes        |
| EXPIRED   | ❌ No                   | ❌ No         |
| CANCELLED | ❌ No                   | ❌ No         |

---

## Message Template Placeholders

For SMS reminder templates:

| Placeholder       | Description      | Example     |
| ----------------- | ---------------- | ----------- |
| `{customer_name}` | Customer's name  | John Doe    |
| `{date}`          | Appointment date | 2026-02-05  |
| `{time}`          | Appointment time | 10:00       |
| `{branch_name}`   | Branch name      | Main Branch |

### Example Template

```
Hi {customer_name}! Reminder: Your appointment at {branch_name} is on {date} at {time}. See you!
```

**Output:**

```
Hi John Doe! Reminder: Your appointment at Main Branch is on 2026-02-05 at 10:00. See you!
```

---

## HTTP Status Codes Used

| Code | Meaning               | When Used                          |
| ---- | --------------------- | ---------------------------------- |
| 200  | OK                    | Successful GET, PUT, DELETE        |
| 201  | Created               | Successful POST (resource created) |
| 400  | Bad Request           | Validation errors                  |
| 401  | Unauthorized          | Missing/invalid authentication     |
| 403  | Forbidden             | Insufficient permissions           |
| 404  | Not Found             | Resource doesn't exist             |
| 500  | Internal Server Error | Server-side error                  |

---

## Validation Rules

### Business

| Field | Validation          |
| ----- | ------------------- |
| name  | Required, not blank |

### Branch

| Field       | Validation          |
| ----------- | ------------------- |
| name        | Required, not blank |
| address     | Optional            |
| phoneNumber | Optional            |

### Service

| Field             | Validation                 |
| ----------------- | -------------------------- |
| name              | Required, not blank        |
| description       | Optional                   |
| basePrice         | Required, positive number  |
| durationMinutes   | Required, positive integer |
| availableGlobally | Default: true              |

### Staff

| Field             | Validation                 |
| ----------------- | -------------------------- |
| username          | Required, 3-50 characters  |
| email             | Optional                   |
| temporaryPassword | Required, min 6 characters |
| branchIds         | Required, at least one     |

### Appointment

| Field         | Validation         |
| ------------- | ------------------ |
| businessId    | Required           |
| branchId      | Required           |
| customerName  | Required           |
| customerPhone | Required           |
| startDateTime | Required, ISO 8601 |
| endDateTime   | Required, ISO 8601 |
| notes         | Optional           |
| isWalkin      | Default: false     |

### Reminder Config

| Field           | Validation                 |
| --------------- | -------------------------- |
| leadTimeHours   | Required, positive integer |
| messageTemplate | Required, not blank        |
| enabled         | Default: true              |

### Change Password

| Field           | Validation                 |
| --------------- | -------------------------- |
| currentPassword | Required                   |
| newPassword     | Required, min 6 characters |

---

## TypeScript Type Definitions

For frontend TypeScript projects:

```typescript
// Enums
type UserRole = "ADMIN" | "OWNER" | "STAFF";
type AppointmentStatus =
  | "WALK_IN"
  | "SCHEDULED"
  | "CONFIRMED"
  | "CANCELLED"
  | "NO_SHOW"
  | "COMPLETED";
type SmsStatus =
  | "PENDING"
  | "SENT"
  | "DELIVERED"
  | "FAILED"
  | "INSUFFICIENT_CREDIT";
type SubscriptionStatus = "PENDING" | "ACTIVE" | "EXPIRED" | "CANCELLED";

type ActivityAction =
  | "APPOINTMENT_CREATED"
  | "APPOINTMENT_UPDATED"
  | "APPOINTMENT_DELETED"
  | "APPOINTMENT_STATUS_CHANGED"
  | "STAFF_CREATED"
  | "STAFF_UPDATED"
  | "STAFF_PASSWORD_RESET"
  | "STAFF_DEACTIVATED"
  | "BRANCH_CREATED"
  | "BRANCH_UPDATED"
  | "SERVICE_CREATED"
  | "SERVICE_UPDATED"
  | "SERVICE_DELETED"
  | "REMINDER_CONFIG_UPDATED"
  | "USER_LOGIN"
  | "USER_LOGOUT";

// API Response
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data?: T;
}

// Auth
interface AuthResponse {
  userId: string;
  role: UserRole;
  businessId: string | null;
  branchIds: string[];
}

// Business
interface BusinessResponse {
  id: string;
  name: string;
  slug: string;
  freeSmsCredits: number;
  paidSmsCredits: number;
  subscriptionStatus: SubscriptionStatus;
  subscriptionStartDate: string | null;
  subscriptionEndDate: string | null;
  createdAt: string;
  firstBranchId?: string;
}

// Branch
interface BranchResponse {
  id: string;
  businessId: string;
  name: string;
  address: string;
  phoneNumber: string;
  createdAt: string;
  updatedAt: string;
}

// Service
interface ServiceResponse {
  id: string;
  businessId: string;
  name: string;
  description: string;
  basePrice: number;
  durationMinutes: number;
  availableGlobally: boolean;
  createdAt: string;
  updatedAt: string;
}

// Branch Service
interface BranchServiceResponse {
  id: string;
  branchId: string;
  serviceId: string;
  serviceName: string;
  serviceDescription: string;
  basePrice: number;
  customPrice: number | null;
  effectivePrice: number;
  durationMinutes: number;
  active: boolean;
  createdAt: string;
}

// Staff
interface StaffResponse {
  id: string;
  businessId: string;
  username: string;
  email: string;
  role: UserRole;
  mustChangePassword: boolean;
  branches: { id: string; name: string }[];
  createdAt: string;
  updatedAt: string;
}

// Appointment
interface AppointmentResponse {
  id: string;
  businessId: string;
  branchId: string;
  branchName: string;
  customerName: string;
  customerPhone: string;
  startDateTime: string;
  endDateTime: string;
  notes: string;
  status: AppointmentStatus;
  createdAt: string;
  updatedAt: string;
}

// Reminder Config
interface ReminderConfigResponse {
  id: string;
  businessId: string;
  leadTimeHours: number;
  messageTemplate: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

// SMS Log
interface SmsLogResponse {
  id: string;
  businessId: string;
  appointmentId: string;
  customerName: string;
  recipientPhone: string;
  messageBody: string;
  status: SmsStatus;
  errorMessage: string | null;
  createdAt: string;
}

// SMS Balance
interface SmsBalanceResponse {
  success: boolean;
  remainingCredits: number;
  errorMessage: string | null;
}

// Activity Log
interface ActivityLogResponse {
  id: string;
  userId: string;
  userName: string;
  userRole: UserRole;
  businessId: string;
  branchId: string | null;
  branchName: string | null;
  action: ActivityAction;
  description: string;
  details: string | null; // JSON string of FieldChange[]
  createdAt: string;
}

// Field Change (for parsing details)
interface FieldChange {
  field: string;
  before: string;
  after: string;
}

// Pagination
interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
```

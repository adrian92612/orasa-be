# Orasa Backend - Codebase Documentation

## Project Overview

**Orasa** is a SaaS platform for appointment scheduling and business management, designed for service-based businesses (salons, clinics, spas, consultancies, etc.). The name "Orasa" means "Time" in Filipino/Tagalog.

- **Description**: Backend for Orasa appointment & walk-in tracker
- **Version**: 0.0.1-SNAPSHOT
- **Java Version**: 21
- **Framework**: Spring Boot 4.0.2
- **Database**: PostgreSQL (via Supabase)

## Technology Stack

### Core Dependencies
- **Spring Boot Starter Web MVC** - REST API endpoints
- **Spring Boot Starter Data JPA** - ORM and database access
- **Spring Boot Starter Security** - Authentication & authorization
- **Spring Boot Starter Validation** - Input validation
- **Spring Boot Starter Actuator** - Health checks and monitoring
- **Lombok** - Boilerplate reduction
- **PostgreSQL Driver** - Database connectivity
- **java-dotenv** (5.2.2) - Environment variable management

### Configuration
- **Database**: Supabase PostgreSQL
- **Hibernate DDL**: `validate` (schema validation only, no auto-generation)
- **Naming Strategy**: CamelCase to snake_case
- **JSON Serialization**: Snake case property naming
- **Timezone**: Asia/Manila
- **Connection Pool**: HikariCP (max: 5, min idle: 2)

## Architecture

### Package Structure
```
com.orasa.backend
├── OrasaApplication.java (Main entry point)
├── common/                (Enums and shared types)
│   ├── AppointmentStatus.java
│   ├── SmsStatus.java
│   ├── SmsTaskStatus.java
│   ├── SubscriptionStatus.java
│   └── UserRole.java
└── domain/                (JPA entities)
    ├── BaseEntity.java
    ├── Business.java
    ├── User.java
    ├── Branch.java
    ├── Service.java
    ├── BranchService.java
    ├── Appointment.java
    ├── ActivityLog.java
    ├── SmsLog.java
    ├── ScheduledSmsTask.java
    └── BusinessReminderConfig.java
```

## Domain Model

### Core Entities

#### BaseEntity (Abstract)
All entities extend this base class providing:
- `UUID id` - Primary key
- `OffsetDateTime createdAt` - Auto-set on creation
- `OffsetDateTime updatedAt` - Auto-updated on modification
- `String createdBy` - Audit field
- `String updatedBy` - Audit field
- `boolean isDeleted` - Soft delete flag (default: `false`)
- `OffsetDateTime deletedAt` - Soft delete timestamp
- `softDelete()` - Method to perform soft deletion

**Soft Delete Pattern**: All entities use `@SQLDelete` and `@SQLRestriction` annotations to implement soft deletes via SQL triggers.

---

#### Business
Represents a customer organization using the platform.

**Fields**:
- `String name` - Business name (required)
- `String slug` - Unique URL-friendly identifier
- `int freeSmsCredits` - Free SMS credits (default: 100)
- `int paidSmsCredits` - Purchased SMS credits (default: 0)
- `SubscriptionStatus subscriptionStatus` - Subscription state (default: PENDING)
- `OffsetDateTime subscriptionStartDate`
- `OffsetDateTime subscriptionEndDate`

**Relationships**: One-to-many with Branch, User, Appointment

---

#### User
Platform users (business owners, staff, admins).

**Fields**:
- `String username` - Unique username (required)
- `String email` - Unique email
- `String passwordHash` - Hashed password
- `UserRole role` - ADMIN, OWNER, or STAFF

**Relationships**:
- `ManyToOne` → Business (required)
- `ManyToOne` → Branch (optional, for staff assignment)

---

#### Branch
Physical locations of a business.

**Fields**:
- `String name` - Branch name (required)
- `String address`
- `String phoneNumber`

**Relationships**:
- `ManyToOne` → Business (required)

---

#### Service
Services offered by a business (e.g., "Haircut", "Massage").

**Fields**:
- `UUID businessId` - Owning business (required)
- `String name` - Service name (required)
- `String description`
- `BigDecimal basePrice` - Default price (required)
- `Integer durationMinutes` - Service duration (required)
- `boolean isAvailableGlobally` - Available to all branches (default: true)

---

#### BranchService
Junction table linking branches to services with custom pricing.

**Fields**:
- `UUID branchId` - Branch reference (required)
- `Service service` - Service reference (required)
- `BigDecimal customPrice` - Branch-specific price override
- `boolean isActive` - Service availability at branch (default: true)

**Methods**:
- `getEffectivePrice()` - Returns custom price if set, otherwise base price

**Constraints**: Unique constraint on (branch_id, service_id)

---

#### Appointment
Customer bookings.

**Fields**:
- `Business business` - Owning business (required)
- `Branch branch` - Location (required)
- `User staff` - Assigned staff member (optional)
- `String customerName` - Customer name (required)
- `String customerPhone` - Customer phone (required)
- `OffsetDateTime appointmentTime` - Scheduled time (required)
- `AppointmentStatus status` - SCHEDULED, etc. (default: SCHEDULED)
- `String notes` - Additional notes (TEXT)

---

#### ActivityLog
Audit trail of user actions.

**Fields**:
- `User user` - User who performed action (required)
- `Business business` - Related business (required)
- `Branch branch` - Related branch (optional)
- `String action` - Action type (required)
- `String description` - Action details (TEXT)

---

#### SmsLog
Record of sent SMS messages.

**Fields**:
- `Business business` - Business that sent SMS (required)
- `Appointment appointment` - Related appointment (optional)
- `String recipientPhone` - Recipient phone number (required)
- `String messageBody` - SMS content (TEXT)
- `SmsStatus status` - Delivery status
- `String providerId` - External SMS provider ID (required)
- `String errorMessage` - Error details if failed

---

#### ScheduledSmsTask
Queued SMS messages to be sent.

**Fields**:
- `UUID businessId` - Business sending SMS (required)
- `Appointment appointment` - Related appointment (required)
- `OffsetDateTime scheduledAt` - When to send (required)
- `SmsTaskStatus status` - PENDING, SENT, FAILED, etc. (default: PENDING)

---

#### BusinessReminderConfig
SMS reminder settings per business.

**Fields**:
- `UUID businessId` - Business configuration (required)
- `Integer leadTimeHours` - Hours before appointment to send reminder (required)
- `String messageTemplate` - SMS template (TEXT)
- `boolean isEnabled` - Reminder active status (default: true)

---

### Enums

#### UserRole
- `ADMIN` - Platform administrator
- `OWNER` - Business owner
- `STAFF` - Business staff member

#### SubscriptionStatus
- `PENDING` - Awaiting activation
- `TRIAL` - Trial period
- `ACTIVE` - Active subscription
- `PAST_DUE` - Payment overdue
- `CANCELLED` - Subscription cancelled

#### AppointmentStatus
- `SCHEDULED` - Appointment booked
- (Other statuses to be defined)

#### SmsStatus
- (Statuses to be defined: SENT, FAILED, DELIVERED, etc.)

#### SmsTaskStatus
- `PENDING` - Queued for sending
- (Other statuses to be defined: SENT, FAILED, CANCELLED, etc.)

---

## Key Features

### 1. Multi-Tenant SaaS Architecture
- Each `Business` is a tenant with isolated data
- Businesses can have multiple `Branch` locations
- Users are scoped to businesses with role-based access

### 2. Appointment Management
- Schedule appointments with customer details
- Assign staff to appointments
- Track appointment status lifecycle
- Optional notes for special requirements

### 3. SMS Notification System
- **Credit-based billing**: Free credits (100) + purchasable credits
- **Automated reminders**: Configurable lead time per business
- **Message templating**: Customizable SMS templates
- **Task scheduling**: `ScheduledSmsTask` for queued messages
- **Delivery tracking**: `SmsLog` for audit and debugging

### 4. Service Catalog
- Global services defined at business level
- Branch-specific pricing overrides via `BranchService`
- Service duration tracking for scheduling
- Active/inactive status per branch

### 5. Audit & Activity Logging
- `ActivityLog` tracks all user actions
- `BaseEntity` provides created/updated timestamps and user tracking
- Soft delete pattern preserves historical data

### 6. Soft Delete Pattern
All entities implement soft deletes:
```java
@SQLDelete(sql = "UPDATE table_name SET is_deleted = true, deleted_at = NOW() WHERE id = ?")
@SQLRestriction("is_deleted = false")
```
This ensures data is never permanently deleted, maintaining referential integrity and audit trails.

---

## Development Notes

### Lombok Builder Pattern
When using `@Builder` with field initializers, use `@Builder.Default` to preserve default values:
```java
@Builder.Default
private AppointmentStatus status = AppointmentStatus.SCHEDULED;
```

### Database Schema Management
- Hibernate DDL mode is set to `validate` (not `update` or `create`)
- Schema changes must be managed externally (likely via Supabase migrations)
- All tables use snake_case naming via `CamelCaseToUnderscoresNamingStrategy`

### Environment Variables
Required environment variables (loaded via java-dotenv):
- `SUPABASE_URL` - Database connection URL
- `SUPABASE_USER` - Database username
- `SUPABASE_PASSWORD` - Database password

---

## Future Considerations

Based on the current domain model, potential future features might include:
- Walk-in tracking (mentioned in project description)
- Payment processing integration
- Staff scheduling and availability management
- Customer relationship management (CRM)
- Analytics and reporting
- Multi-language support
- Mobile app integration

---

**Last Updated**: 2026-01-31  
**Generated by**: Claude (Antigravity AI Assistant)

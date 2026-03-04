# Orasa App Overview & Current Status (March 2026)

## Executive Summary

Orasa is a B2B appointment management system designed for micro and small service-based businesses to transition from manual logbooks to a digitized tracker with automated SMS reminders.

The application is currently **fully functional and ready for production deployment/beta testing**. The core architecture is a monolith-first Spring Boot backend paired with a React frontend, leveraging Supabase for database and authentication.

---

## Technical Stack

- **Backend:** Spring Boot (Java 21), Spring Security, Spring Data JPA
- **Frontend:** React, TypeScript, Tailwind CSS, Vite
- **Database & Auth:** Supabase (PostgreSQL + Auth)
- **Integrations:**
  - **Google OAuth2** (Owner Login)
  - **PhilSMS** (External SMS Provider)
  - **Payloro** (Payment Gateway for Subscriptions/Credits)
- **Infrastructure:** Docker Compose (Backend, Frontend, Redis for caching/sessions), deployed on AWS EC2 via GitHub Actions CI/CD.

---

## Feature Completeness Status

### 1. Authentication & Onboarding (✅ Complete)

- **Owner Auth:** Uses Google OAuth2. First-time login intercepts the user to an onboarding wizard.
- **Staff Auth:** Username/password login (created by Owners).
- **Onboarding Wizard:** Atomic creation of Business, First Branch, Optional Services, and Optional Staff.

### 2. Core Business Management (✅ Complete)

- **Multi-Branch Support:** Owners can create and manage multiple branches under a single business.
- **Service Management:** Define services with durations and prices.
- **Staff Management:** Create staff accounts, reset passwords, and assign staff to specific branches.

### 3. Appointment System (✅ Complete)

- **Scheduling:** Time-slot based appointment creation.
- **Walk-ins:** Supported (no reminders sent).
- **Views:** Master view for all branches (Owner) and individual branch views (Owner/Staff).
- **Status Tracking:** Scheduled, Completed, No-Show, Cancelled.

### 4. SMS & Reminders (✅ Complete)

- **Integration:** Connected to PhilSMS.
- **Configuration:** Owners can configure multiple reminder rules (e.g., 24 hours before, 1 hour before).
- **Execution:** Spring Scheduler runs background tasks to poll for upcoming appointments and dispatches SMS via the external API.
- **Logs:** UI available to view sending status (Success/Failed) of SMS messages.

### 5. Subscription & Billing (✅ Complete)

- **Gateway:** Integrated with Payloro (specifically via GCash QR).
- **Subscription Plan:** "Orasa Pro Plan" at ₱299/mo per business.
- **SMS Credits:** 100 free credits monthly; top-ups available at ₱1.00/SMS.
- **Enforcement:** `SubscriptionEnforcementAspect` in the backend intercepts requests and blocks premium actions (like creating appointments or sending SMS) if the subscription is expired.

### 6. Analytics & Audit (✅ Complete)

- **Dashboard Analytics:** Displays appointment counts, walk-in vs. scheduled ratios, no-show rates, and SMS delivery stats.
- **Activity Logs:** Audits user actions (who created/edited what and when).

### 7. Platform Admin (✅ Complete)

- Separate admin endpoints/UI for the system operators to view all businesses, manage manual subscription extensions, and monitor system health.

---

## Database Models (Core Entities)

- `Business`: Root entity holding subscription status, end dates, and SMS credit balances.
- `User`: Stores both Owners (Google OAuth) and Staff (credentials).
- `Branch`: Physical locations tied to a Business.
- `Service`: Services offered by a Business.
- `Appointment`: Core transactional record tied to a Branch, Service, and Contact/Walk-in.
- `BusinessReminderConfig`: Configurable rules for when to trigger reminders.
- `ScheduledSmsTask`: Queue table for the background processor to send SMS.
- `Payment`: Records Payloro transaction intents and webhook callbacks.
- `ActivityLog` / `SmsLog`: Read-only audit trails.

---

## Next Steps / Future Roadmap (Deferred from MVP)

While the MVP is entirely complete based on current requirements, the following are explicitly deferred items that may be considered for future iterations:

1. Online credit card payments (currently Payloro GCash-QR focused / manual platform admin focused).
2. Deeper calendar synchronization (e.g., Google Calendar sync).
3. Customer self-booking portals (currently strictly internal-use only).

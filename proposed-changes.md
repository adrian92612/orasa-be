# High-Impact Model Simplifications (Proposed)

As part of keeping Orasa focused purely on its core problem—_Structured appointment tracking with automated SMS reminders_—we are heavily simplifying the data model. This ensures the app doesn't creep into Point-of-Sale (POS) or Accounting territory, eliminates redundant relational tracking, and speeds up data entry for staff.

## 1. Strip Pricing and Duration from Services

**Current:** Services track `basePrice` and `durationMinutes`. Staff and owners have to fill this out.
**Issue:** We don't need to track revenue since this app is meant to heavily focus on replacing manual logbooks and SMS text reminders.
**Change:**

- Drop `base_price` and `duration_minutes` from the `services` table.
- A Service will now only require a **Name** and an optional **Description**.
- Appointment snapshot tables (`appointment_services`) will drop historical pricing/duration columns.

## 2. Drop the `branch_services` Table

**Current:** A many-to-many join table mapping branches to services, allowing a branch to have custom pricing or disable certain services.
**Issue:** Since we are removing `base_price`, the only reason this exists is to limit which services a branch can perform. For micro-businesses, the service catalog is usually identical across all branches.
**Change:**

- Drop the `branch_services` table entirely.
- All branches under a `business_id` will simply share the same global list of Services defined by the Owner.

## 3. Drop the `appointment_reminders` Join Table

**Current:** A join table linking an `appointment` to specific `business_reminder_configs`.
**Issue:** The system already queues SMS tasks in `scheduled_sms_tasks`. We don't need heavy relational tracking of _which_ configuration rule generated _which_ appointment reminder.
**Change:**

- Drop the `appointment_reminders` table entirely.
- The system will simply read `business_reminder_configs` at the time of appointment creation, insert the jobs into `scheduled_sms_tasks`, and rely purely on that queue.
- **Note on Recovery:** If the server or Redis crashes, no unsent SMS data is lost. The `scheduled_sms_tasks` table acts as a persistent ledger in the database. When the server restarts, the background worker simply queries for all `PENDING` tasks where `scheduled_at` is in the past, and processes them immediately.

## 4. Remove `end_date_time` from Appointments

**Current:** Walk-in and scheduled appointments require both a start and end time.
**Issue:** If we aren't tracking service durations, calculating or demanding a strict `end_date_time` is unnecessary friction for staff. The most important metric for SMS reminders and displaying the day's schedule is the start time.
**Change:**

- Drop `end_date_time` from the `appointments` table.
- The system will calculate SMS reminders and render the UI strictly using the `start_date_time`.
- Overlap validation can be relaxed or removed entirely since staff are visually managing the logbook.

## 5. Refocus Analytics on Volume, not Revenue

**Current:** We might have been tracking revenue, profit, or service-level financial metrics since prices were attached to services.
**Issue:** If we strip pricing, we can no longer track financial analytics. Even if we could, that approaches Point-of-Sale (POS) territory, violating our "Simplicity over feature richness" guardrail.
**Change:**
- Analytics will exclusively measure operational volume and reliability.
- **Proposed Metrics:**
  - Total appointments per day / week / month.
  - Walk-in vs. Scheduled ratio.
  - No-show rates.
  - Busiest day of the week / Busiest time of day.
  - Most popular services (by count, not revenue).
  - SMS delivery success/failure rates.

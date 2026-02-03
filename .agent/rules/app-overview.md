---
trigger: always_on
---

# Project: Orasa

## Purpose

Orasa is a **B2B appointment management system** for micro and small service-based businesses that still rely on **manual logbooks**. The system digitizes appointment tracking and automates **SMS reminders**, without adding unnecessary complexity.

This is **not** a marketplace, booking platform, or payment system. It is an **internal operational tool**.

---

## Core Problem Being Solved

- Manual appointment logs are error-prone
- Missed or forgotten appointments hurt small businesses
- Existing tools are overkill or require customers to self-book

**Orasa solves exactly one problem:**

> Structured appointment tracking with automated SMS reminders.

---

## Architecture & Scope (Guardrails)

- **Backend:** Spring Boot  
- **Database & Auth Infra:** Supabase  
- **Architecture:** Monolith-first  
- **Target:** Web app (mobile-responsive)  
- **Design principle:** Simplicity over feature richness  
- **SMS delivery:** Best-effort, failures are expected and logged  

### Non-goals

- ❌ Online payments  
- ❌ Customer self-booking (never)  
- ❌ Calendar sync (explicitly out of scope for MVP, may be reconsidered later)

---

## User Roles & Access Control

### Owner (Business Admin)

- Authenticates via **Google OAuth**
- First login requires:
  - Business registration
  - Branch setup (supports multiple branches)

#### Permissions
- Full access across all branches
- Toggle between:
  - Master (all-branch) view
  - Individual branch views

#### UI
- Sidebar navigation

#### Accessible Features
- Analytics dashboard
- Appointment management
- Global and branch-level settings
- Staff management
- Activity logs
- SMS logs

---

### Staff

- Accounts are **created by the owner**
- Login method:
  - Username + temporary password
  - Password reset required on first login
- Can belong to **multiple branches**

#### Visibility
- Only appointments for assigned branches

#### UI
- Appointment list only
- No sidebar
- No access to analytics or settings

#### Permissions
- Create appointments
- Edit appointments
- Cannot delete appointments

---

## Authentication Flow

- Login page supports:
  - Google OAuth (Owner)
  - Username/password login (Staff) via a separate entry point

Role determines:
- UI layout
- Accessible routes
- Feature availability

---

## Appointment Model

All appointments:
- Are **time-slot based**
- Belong to **exactly one branch**

Supports:
- Scheduled appointments
- Walk-in appointments

### Walk-ins
- Still require time slots
- **Do not support reminders**

### Deletion Rules
- No hard deletes
- All deletions are **soft deletes**
- Staff cannot delete; only owner (if allowed later)

---

## SMS & Reminder System

### Reminder Configuration

- Global reminder settings defined by the owner
- Supports **multiple reminders per appointment** (e.g., X hours before)

Reminder behavior:
- Can be overridden per appointment
- Can be disabled per appointment

Walk-in appointments:
- Reminders are **not available**

---

### SMS Templates

- SMS content is:
  - Configurable by the owner
  - Template-based

SMS delivery:
- Best-effort
- Failures are logged but do not block workflows

---

## Analytics (Initial Metrics)

- Appointment count (daily / per branch)
- Walk-in vs scheduled ratio
- No-show rate
- SMS sent / failed counts

Analytics are **informational only**; no predictive or AI logic.

---

## Logging & Auditability

### Activity Logs
- Appointment creation and edits
- Staff actions

### SMS Logs
- Send attempts
- Success / failure states
- Timestamped for audit and troubleshooting

---

## Design Constraints (Explicit for AI Agent)

- Prefer clarity over abstraction
- Avoid premature optimization
- No speculative features
- No future-proofing beyond stated scope
- Schema and logic should reflect **real-world small business workflows**

# Subscription & Billing Model (MVP)

## Pricing

- **Subscription type:** Monthly only  
- **Price:** PHP 399 per business per month  
- **Billing cycle:** Fixed 1-month cycles, not usage-based  
- **No annual plans**  
- **No proration**  
- **No trials** beyond any explicitly defined onboarding grace (if any)

This is intentional. **Simplicity > flexibility.**

---

## Subscription Scope

- Subscription is **per business**, not per branch or per user
- All branches and staff under a business are covered by a single subscription

If subscription expires:
- App access is **restricted**
- **No SMS sending**
- Appointment creation/editing behavior is defined explicitly (see below)

---

## SMS Credit System

### Monthly Allocation

Each billing cycle includes:
- **100 free SMS credits**

Credit behavior:
- Credits reset at the **start of each new cycle**
- Unused credits:
  - ❌ Do not roll over
  - ❌ Do not accumulate

This avoids accounting complexity and user confusion.

---

### SMS Credit Usage Rules

- **1 SMS sent = 1 credit consumed**
- Credits are consumed when:
  - SMS send is **attempted**

If SMS sending fails:
- Credit consumption behavior must be explicitly defined

**Recommended MVP rule:**
- **Attempt = credit consumed**, regardless of success  
  (simpler, predictable, avoids disputes)

---

### Credit Exhaustion Behavior

When SMS credits reach **0**:

- SMS reminders:
  - ❌ Not sent
  - ❌ Not queued
- Appointment creation:
  - ✅ Still allowed
- Appointment reminders:
  - Automatically skipped
- System behavior:
  - Failure reason logged as `INSUFFICIENT_SMS_CREDITS`
- UI must clearly surface:
  - **“SMS credits exhausted for this cycle”**

No silent failures.

---

## Subscription Expiry Behavior

When a business subscription expires:

### Owner
- Can log in
- Sees a **blocked state / paywall**

### Staff
- Login may be **blocked** or **read-only**  
  *(choose one and define it clearly)*

---

### Restricted Actions on Expiry

- ❌ Create appointments
- ❌ Edit appointments
- ❌ Send SMS
- ❌ Access analytics

---

### Allowed Actions on Expiry

- ✅ View existing appointments (read-only)
- ✅ View past logs
- ✅ Renew subscription

This prevents data hostage scenarios while still enforcing payment.

---

## Platform Admin Role

### Admin (System Administrator)

- Separate from business Owner/Staff
- Used by Orasa platform operators (you and your wife)

#### Access
- View all businesses on the platform
- View subscription status of each business

#### Actions
- Manually activate/deactivate subscriptions
- Add subscription cycles (extend by 1 month)
- View payment verification requests

#### Payment Flow (MVP - Manual)
1. User sends payment receipt via social media
2. Admin verifies payment
3. Admin manually activates/extends subscription in admin panel

> **Note:** Online payment integration deferred until 100+ users registered.

---

## Demo Account Strategy

### Approach
- Admin and Owner views are **completely separate**
- Create a dedicated demo business with sample data for client demos
- Demo data can be reset before each presentation

### Demo Accounts

| Account | Purpose | Experience |
|---------|---------|------------|
| Demo Owner | Client demos | Exactly what real owners see |
| Demo Staff | Show staff experience | Limited appointment-only view |

### Demo Business Structure

- **Business:** "Orasa Demo Clinic"
  - **Branch:** Main Branch
    - Sample appointments (scheduled, walk-in, completed, no-show)
    - Sample services
  - **Branch:** Second Branch
  - **Owner:** demo-owner@orasa.ph
  - **Staff:** demo-staff (optional)

### Benefits
- Clients see the **authentic owner experience**
- No risk of exposing admin features during demos
- Controlled demo data quality
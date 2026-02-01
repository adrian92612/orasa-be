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

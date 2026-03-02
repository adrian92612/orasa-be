# Test Coverage Analysis: CI Readiness

## Summary

Both codebases have **solid foundational test coverage** on the most critical paths. The existing tests are well-structured and follow good practices (clear naming, arrange/act/assert, proper mocking). You're **ready to move to CI** — the tests you have are meaningful and cover the highest-risk areas.

Below is a detailed breakdown of what's covered, what's not, and what to prioritize next.

---

## Backend (orasa-be)

### What's Tested (7 test files, ~24 test cases)

| File | Tests | What's Covered |
|------|-------|----------------|
| `AppointmentServiceTest` | 8 | Create scheduled/walk-in, past time validation, missing end time, branch authorization, soft deletes, owner vs staff deletion |
| `AuthServiceTest` | 3 | Staff login, Google OAuth (existing + new owner) |
| `BusinessServiceTest` | 3 | Atomic business+branch creation, duplicate prevention, user-not-found |
| `SmsServiceTest` | 4 | Schedule reminders (disabled, walk-in skip, with additional), cancel pending |
| `SubscriptionEnforcementAspectTest` | 2 | Authenticated user validation, unauthenticated bypass |
| `CreditResetSchedulerTest` | 2 | Process overdue resets, empty list no-op |
| `SmsRecoverySchedulerTest` | 2 | Process overdue tasks, empty list no-op |
| `OrasaApplicationTests` | 1 | Spring context loads (smoke test) |

### What's NOT Tested

| Area | Risk | Notes |
|------|------|-------|
| **All 15 Controllers** | 🟡 Medium | No controller/integration tests. Request validation, HTTP status codes, and auth annotations are untested at the HTTP layer |
| **BranchService, StaffService, ServiceService** | 🟡 Medium | CRUD services for core entities — no unit tests |
| **SubscriptionService** | 🟠 High | Handles billing logic (credit checks, renewal, expiry) — completely untested |
| **AnalyticsService** | 🟢 Low | Informational only, low-risk |
| **ReminderConfigService** | 🟢 Low | Simple CRUD, low-risk |
| **JwtService, SecurityConfig** | 🟡 Medium | JWT generation/parsing untested — but implicitly tested via AuthServiceTest |
| **GlobalExceptionHandler** | 🟢 Low | Error mapping — low risk |
| **SmsReminderWorker** | 🟡 Medium | The actual worker that processes the queue — recovery scheduler is tested but not the worker itself |
| **PaymentService, PayloroService** | 🟡 Medium | Payment webhook handling untested |

### CI Verdict: ✅ Good enough to start

The core business logic (appointments, auth, SMS scheduling, subscription enforcement) is well-tested. The `contextLoads` smoke test ensures Spring wiring works. **Missing controller tests won't block CI** — they're nice-to-have but the service layer tests catch most logic bugs.

---

## Frontend (orasa-fe)

### Unit Tests (Vitest — 14 files, ~65 test cases)

| File | Tests | What's Covered |
|------|-------|----------------|
| `RouteGuard.test` | 18 | All 4 variants (private/public/onboarding/admin) × all role combinations |
| `AppointmentDialog.test` | 4 | Render, walk-in toggle hides reminders, extra reminder, validation |
| `BranchDialog.test` | 6 | Create/edit modes, validation, create/update mutations, danger zone |
| `ServiceDialog.test` | 6 | Create/edit/branch modes, validation, create/updateLink mutations |
| `StaffDialog.test` | 4 | Validation, password match, create mutation, edit mode |
| `ReminderConfigDialog.test` | 4 | Add/edit modes, zero-time validation, leadTime math |
| `BusinessProfile.test` | 2 | Render business info, billing link |
| `button.test` | 2 | Render, click handler |
| `UserContext.test` | 5 | Fetch user, null on error, branch state, logout, provider check |
| `useAppointments.test` | ~8 | CRUD mutations with optimistic updates and cache invalidation |
| `useBranches.test` | 6 | Create/update/delete + error handling + cache invalidation |
| `useServices.test` | 5 | Create/update/delete + optimistic updates + rollback on error |
| `appointment.schema.test` | 7 | Customer name/phone validation, branch/service/date required, optionals |
| `onboarding.schema.test` | 7 | Business, staff, service schema validation |

### E2E Tests (Playwright — 5 spec files, 10 test cases)

| File | Tests | What's Covered |
|------|-------|----------------|
| `login.spec` | 6 | Unauthenticated redirect, Google sign-in button, new owner → onboarding, registered owner → dashboard, staff login/invalid/route guard |
| `onboarding.spec` | 1 | Full onboarding wizard (terms → business → service → staff → redirect) |
| `appointments.spec` | 2 | Owner creates scheduled appointment, staff creates walk-in |
| `sms.spec` | 3 | Navigate to settings, create/edit/delete reminder configs |
| `staff.spec` | 2 | Owner views/adds/edits staff, staff route restriction |

### What's NOT Tested

| Area | Risk | Notes |
|------|------|-------|
| **Analytics page** | 🟢 Low | Display-only, low risk |
| **Activity logs page** | 🟢 Low | Display-only |
| **SMS logs page** | 🟢 Low | Display-only |
| **Branch management E2E** | 🟢 Low | Unit-tested via `BranchDialog.test`, dialogs are well covered |
| **Service management E2E** | 🟢 Low | Unit-tested via `ServiceDialog.test` |
| **Subscription expiry UI** | 🟡 Medium | Paywall/blocked state for expired subscriptions — no tests |
| **Admin panel** | 🟡 Medium | No tests for admin dashboard views |
| **Payment flow** | 🟡 Medium | No tests for payment status/webhook UI |
| **Layouts (DashboardLayout, Sidebar)** | 🟢 Low | Indirectly tested via E2E route tests |
| **Remaining hooks** (useAdmin, useAnalytics, useAuth, useDebounce, usePayments, useReminders, useSms, useSmsLogs, useStaff) | 🟢 Low | Most are simple query wrappers; high-risk hooks (branches, services, appointments) are tested |

### CI Verdict: ✅ Good enough to start

RouteGuard is **exhaustively tested** (critical for access control). All key dialogs and data-mutation hooks have tests. E2E specs cover the complete user journey from login → onboarding → creating appointments → managing SMS. The Playwright config already has CI-aware settings (`forbidOnly`, `retries: 2`, `workers: 1`).

---

## CI Readiness Checklist

| Item | Status | Command |
|------|--------|---------|
| BE unit tests | ✅ Ready | `./mvnw test` |
| FE unit tests | ✅ Ready | `pnpm test` (runs `vitest run`) |
| FE E2E tests | ✅ Ready | `pnpm exec playwright test` |
| Playwright CI config | ✅ Already configured | `forbidOnly`, retries, single worker |
| FE build check | ✅ Can add | `pnpm build` |

## Recommendation

> **You're ready to move to CI.** The tests you have cover the critical paths — auth, routing, appointment CRUD, SMS scheduling, subscription enforcement, and the full onboarding flow. The untested areas are mostly low-risk display-only pages or simple CRUD that's covered at the service layer.

### If you want to add more tests later (post-CI), prioritize:

1. **SubscriptionService unit tests** — billing logic is business-critical
2. **Controller integration tests** — validate HTTP-layer auth annotations and request validation
3. **SmsReminderWorker** — the actual SMS sending path
4. **Subscription expiry E2E** — verify the paywall/blocked-state UI works correctly

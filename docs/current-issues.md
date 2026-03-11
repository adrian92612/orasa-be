Orasa Backend — Senior Developer Review
A deep scan of the backend codebase with findings ranked by severity. Items are grouped by area and annotated with effort estimates (Low / Medium / High).

🔴 High Priority

1. [FIXED] DB Hit on Every Authenticated Request —
   JwtAuthenticationFilter
   JwtAuthenticationFilter.java

   Update: Shortened access token TTL to 1 hour and implemented 7-day refresh tokens with user existence verification. DB hit now only occurs during refresh.

java
userRepository.existsById(UUID.fromString(userIdStr))
Problem: Every single authenticated HTTP request triggers a SELECT against the database just to verify the user still exists. Under load this becomes a significant bottleneck.

Recommendation: Trust the JWT during its lifetime. If you need to handle user deletion mid-session, use the existing cache layer — e.g. cache existsById with a short TTL (30–60s) or maintain a small in-memory blocklist of revoked user IDs.

Effort: Low

2. [FIXED] System.err.println Instead of Logger —
   AppointmentService
   AppointmentService.java

   Update: Replaced with @Slf4j logging in both scheduling and rescheduling paths.

java
log.error("Failed to schedule reminders for appointment {}", saved.getId(), e);
Effort: Low

3. [FIXED] @Transactional on @Cacheable Read-Only Method — BusinessService
   BusinessService.java

   Update: Removed side-effect calls to `subscriptionService.checkAndRefreshCredits` from read methods. `getBusinessById` is now strictly `readOnly = true`. Credit refreshes are handled by `CreditResetScheduler`.

java
@Transactional // ← creates a transaction
@Cacheable(...) // ← returns cached value (no DB call)
public BusinessResponse getBusinessById(UUID businessId) {
...
subscriptionService.checkAndRefreshCredits(business); // ← writes to DB
}
Problem: This method is marked @Cacheable, which means on cache hits it returns immediately — but @Transactional still opens a connection from the pool + begins a transaction. Worse,
checkAndRefreshCredits()
mutates state inside a method that is supposed to be a read. On a cache hit, the credit check is skipped entirely, creating inconsistent behavior depending on whether the cache is warm or cold.

Recommendation: Separate the side-effect (credit refresh) from the read path. The credit refresh should happen at a dedicated entry point, not buried inside every
getBusinessById
call.

Effort: Medium

4. EAGER Fetch on AppointmentEntity.services
   AppointmentEntity.java:70

java
@ManyToMany(fetch = FetchType.EAGER)
Problem: Every time an
AppointmentEntity
is loaded — including bulk queries, search results, paginated lists — all associated services are eagerly loaded. This triggers N+1 queries on lists and prevents optimization of read paths that don't need service data.

Recommendation: Switch to FetchType.LAZY and use @EntityGraph or JOIN FETCH in the repository queries that actually need services (e.g. the detail view, the mapper).

Effort: Medium

5. Duplicate Subscription Expiry Logic
   The subscription expiry check is duplicated in three places with slightly different behavior:

Location Behavior
SubscriptionService.isSubscriptionActive()
Expires + saves, auto-reactivates
SubscriptionService.handleExpiryCheck()
Expires + zeroes credits
BusinessEntity.hasActiveSubscription()
Uses OffsetDateTime.now() (not injected Clock), read-only
Problem: BusinessEntity.hasActiveSubscription() uses OffsetDateTime.now() directly, bypassing the injected Clock used everywhere else — this makes it impossible to test with a fixed clock. The three implementations can also disagree on edge cases. This is a bug waiting to happen.

Recommendation: Remove
hasActiveSubscription()
from the entity. Centralize all expiry logic in
SubscriptionService
only.

Effort: Low

🟡 Medium Priority 6. Inline java.util References Throughout
Seen in
BranchService.java:84
,
line 156
,
line 181-190
,
line 244
and
AppointmentService.java:658
:

java
java.util.Set<UUID> serviceIds = request.getServiceIds();
java.util.Objects.equals(oldAddress, addressToSet)
java.util.Collections.emptySet()
Problem: Inline fully-qualified names scattered throughout indicate missing imports, not an intentional style choice. Makes code harder to read and review.

Fix: Add proper imports for Set, Map, Objects, Collections at the top.

Effort: Low

7.  AppointmentService
    is a God Class (678 Lines)
    AppointmentService.java

The class handles: CRUD, search (two flavors), validation, access control, change tracking, SMS scheduling, cache eviction, date formatting, and entity-to-DTO mapping — all in one class.

Recommendation for future refactoring:

Responsibility Extract To
Entity → DTO mapping AppointmentMapper (already exists but unused here)
Branch access validation Reuse from a shared AccessValidator
Field change tracking ChangeTracker<AppointmentEntity>
Date/time formatting Keep as a utility
NOTE

This is not urgent. The current implementation works. But it will become harder to maintain as features are added.

Effort: High (future refactoring)

8. Aggressive Cache Eviction Pattern
   Seen throughout
   BranchService.java:96-103
   ,
   ServiceService.java:78-83
   :

java
cacheService.evict(CacheName.BRANCHES, businessId);
cacheService.evictAll(CacheName.USER_BRANCHES);
cacheService.evictAll(CacheName.SERVICES);
cacheService.evict(CacheName.BUSINESS_STAFF, businessId);
cacheService.evictAll(CacheName.STAFF);
cacheService.evictAll(CacheName.BRANCH_SERVICES);
Problem: Many operations evictAll() on entire cache regions, which defeats caching for multi-tenant scenarios. Creating a single service evicts all branches for all businesses.

Recommendation: Use key-scoped eviction consistently. If the cache key scheme doesn't support this, redesign the cache keys to include businessId prefix so you can scope evictions.

Effort: Medium

9.  logActionSync
    Is a Dead Code Smell —
    ActivityLogService
    ActivityLogService.java:84-101

The comment says "Synchronous version (now same as standard logAction)" — and the implementation is indeed identical to
logAction
. This strongly suggests an async version existed before and was removed, leaving this behind.

Recommendation: Check if
logActionSync
has any callers. If not, delete it. If it does, consolidate to
logAction
.

Effort: Low

10. Typo in Exception Handler Log Message
    GlobalExceptionHandler.java:25

java
log.warn("Resouce not found: {}", ex); // ← "Resouce"
Also on line 83:

java
ApiResponse.error("Unexpected error occured") // ← "occured"
Fix: "Resource not found" and "Unexpected error occurred".

Effort: Low

11. Unnecessary Exception Logging with Full Stack Trace
    GlobalExceptionHandler.java:25
    and
    line 31

java
log.warn("Resouce not found: {}", ex); // logs full stack trace
log.warn("Invalid appointment: {}", ex); // logs full stack trace
Problem: Passing exception
ex
as the format arg prints the full stack trace for expected business errors (404s, validation failures). This pollutes logs.

Fix: Use ex.getMessage() for business exceptions. Reserve full stack traces for unexpected errors (the
handleGenericException
handler correctly does this).

Effort: Low

12. resolveServices
    Using Exception-Driven Control Flow
    AppointmentService.java:606-618

java
try {
service.getId(); // force proxy initialization
resolved.add(service);
} catch (ObjectNotFoundException e) {
// Service was soft-deleted, skip
}
Problem: Using exceptions for normal control flow (soft-deleted services) is a well-known anti-pattern. It's also fragile — if Hibernate changes how it handles deleted proxies, this silently breaks.

Recommendation: Since ServiceEntity has @SQLRestriction("is_deleted = false"), the soft-deleted services should already be filtered. If Hibernate proxies are the issue, use a direct query (findAllById on the service IDs from the join table) instead of relying on exception-driven filtering.

Effort: Medium

🟢 Low Priority / Nice-to-Have 13. @SuppressWarnings("deprecation") on Redisson API
SmsService.java:108
,
SubscriptionService.java:286

java
@SuppressWarnings("deprecation")
RDelayedQueue<SmsReminderTask> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
Problem: The Redisson API being used is deprecated. It will eventually be removed.

Recommendation: Check Redisson's migration guide for the replacement API and plan migration.

Effort: Low

14. BusinessService.getCurrentUser() Duplicates
    getCurrentUserBusinessId()
    Pattern
    BusinessService.java:177-191
    and
    lines 223-230

Two methods do almost the same thing — pull the current user from SecurityContextHolder. One returns the UserEntity, the other returns just the business ID. This pattern of manually extracting the user from SecurityContextHolder in a service is fragile when you already have @AuthenticationPrincipal AuthenticatedUser available at the controller level.

Recommendation: Pass the userId or AuthenticatedUser from the controller instead of reaching into SecurityContextHolder inside services. Most services already follow this pattern.

Effort: Low

15. No @Version for Optimistic Locking
    None of the entities use @Version for optimistic locking. This means concurrent updates to the same entity (e.g. two staff editing the same appointment) will silently overwrite each other (last-write-wins).

Recommendation: Add @Version private Long version; to BaseEntity. This is a low-risk change but may require frontend cooperation to pass the version back on updates.

Effort: Medium (requires coordination with frontend)

16. Hardcoded "100" for Free SMS Credits
    SubscriptionService.java:144
    ,
    line 239
    ,
    BusinessEntity.java:30

java
business.setFreeSmsCredits(100);
Problem: Magic number repeated in 3 places with no named constant.

Fix: Extract to a constant: private static final int DEFAULT_FREE_SMS_CREDITS = 100; or to OrasaProperties.

Effort: Low

Summary
Severity Count Quick Wins (Low Effort)
🔴 High 5 #2, #5
🟡 Medium 7 #6, #9, #10, #11
🟢 Low 4 #13, #14, #16
TIP

The fastest wins for code quality are: #2 (System.err → Slf4j), #5 (remove entity expiry method), #6 (fix inline imports), and #10 (fix typos). These can all be done in a single commit with zero risk.

IMPORTANT

The highest-impact performance fix is #1 (remove the per-request DB hit from the JWT filter). This will reduce DB load proportionally to your request volume.

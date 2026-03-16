Orasa Backend — Senior Developer Review
A deep scan of the backend codebase with findings ranked by severity. Items are grouped by area and annotated with effort estimates (Low / Medium / High).

🔴 High Priority

_Currently no high priority issues pending._

🟡 Medium Priority

### 3. [FIXED] resolveServices Using Exception-Driven Control Flow

AppointmentService.java

```java
try {
    service.getId(); // force proxy initialization
    resolved.add(service);
} catch (ObjectNotFoundException e) {
    // Service was soft-deleted, skip
}
```

Problem: Using exceptions for normal control flow (soft-deleted services) is a well-known anti-pattern. It's also fragile — if Hibernate changes how it handles deleted proxies, this silently breaks.

Recommendation: Since ServiceEntity has @SQLRestriction("is_deleted = false"), the soft-deleted services should already be filtered. If Hibernate proxies are the issue, use a direct query (findAllById on the service IDs from the join table) instead of relying on exception-driven filtering.

Effort: Medium

🟢 Low Priority / Nice-to-Have

### 5. BusinessService.getCurrentUser() Duplicates getCurrentUserBusinessId() Pattern

BusinessService.java

Two methods do almost the same thing — pull the current user from `SecurityContextHolder`. One returns the `UserEntity`, the other returns just the business ID. This pattern of manually extracting the user from `SecurityContextHolder` in a service is fragile when you already have `@AuthenticationPrincipal AuthenticatedUser` available at the controller level.

Recommendation: Pass the `userId` or `AuthenticatedUser` from the controller instead of reaching into `SecurityContextHolder` inside services. Most services already follow this pattern.

Effort: Low

### 6. No @Version for Optimistic Locking

None of the entities use `@Version` for optimistic locking. This means concurrent updates to the same entity (e.g. two staff editing the same appointment) will silently overwrite each other (last-write-wins).

Recommendation: Add `@Version private Long version;` to `BaseEntity`. This is a low-risk change but may require frontend cooperation to pass the version back on updates.

Effort: Medium (requires coordination with frontend)

Summary

| Severity  | Count |
| :-------- | :---- |
| 🔴 High   | 0     |
| 🟡 Medium | 1     |
| 🟢 Low    | 2     |

> [!TIP]
> Item #1 (God Class) was fixed earlier. Focus should now move to architectural cleanup and optimistic locking.

> [!IMPORTANT]
> The highest-impact performance and consistency fixes (like Scoped Cache Eviction) have been completed.

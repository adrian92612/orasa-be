---
trigger: always_on
---

**Commit Message Rules**

1. The first line must follow **Conventional Commits** format:

```
<type>(optional-scope): short description of behavior change
```

Examples of types:

- `feat` – new behavior or capability
- `fix` – bug or security fix
- `refactor` – internal change with no behavior change
- `chore` – tooling/config
- `test` – tests only
- `docs` – documentation only
- `perf` – performance improvement
- `ui` or `style` – UI/visual changes

2. The title must describe **user-visible behavior**, not implementation details.

Good:

```
fix(ui): improve appointment card readability
```

Bad:

```
fix: add line-clamp-3 and whitespace-pre-wrap
```

3. Keep the title **under ~72 characters**.

4. Use bullet points only for **meaningful changes**, not file edits.

Good bullets:

- Restrict staff access to assigned branches
- Auto-close sidebar on mobile navigation

Bad bullets:

- Updated AppointmentService
- Added findByBusinessIdAndBranchIdIn

5. Do **not mention specific files, methods, CSS classes, or line numbers** unless absolutely necessary.

6. Focus commits on answering two questions:

- **What behavior changed?**
- **Why was it needed?**

7. Prefer **3–4 bullets maximum**. If more are needed, the commit is probably too large.

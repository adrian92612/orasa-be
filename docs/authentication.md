# Authentication

## Overview

Orasa uses two authentication methods:

1. **Google OAuth** - For business owners
2. **Username/Password** - For staff members

Both methods result in a JWT being stored as an HTTP-only cookie.

---

## Owner Authentication (Google OAuth)

### Flow Diagram

```
1. Frontend: User clicks "Sign in with Google"
   → Redirects to GET /auth/google

2. Backend: Redirects to Google consent screen
   → https://accounts.google.com/o/oauth2/auth?client_id=...

3. Google: User logs in and consents
   → Redirects to /auth/google/callback?code=xxx

4. Backend: Exchanges code for user info using CLIENT_SECRET
   → Creates new Owner (if first login) or finds existing
   → Sets HTTP-only cookie with JWT
   → Redirects to frontend

5. Frontend: Receives redirect
   → businessId exists? → /dashboard
   → businessId null? → /onboarding
```

### Endpoints

#### Start Google Login

```http
GET /auth/google
```

**Response**: Redirects to Google OAuth consent screen

#### Google Callback (Internal)

```http
GET /auth/google/callback?code={authorization_code}
```

**Response**: Redirects to frontend with JWT cookie set

- If `businessId` exists → Redirects to `/dashboard`
- If `businessId` is null → Redirects to `/onboarding`

---

## Staff Authentication (Username/Password)

### Endpoint

```http
POST /auth/staff/login
```

### Request Body

```json
{
  "username": "john_staff",
  "password": "password123"
}
```

### Response

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "role": "STAFF",
    "businessId": "550e8400-e29b-41d4-a716-446655440001",

  }
}
```

**Note**: Also sets JWT cookie automatically.

### First Login Password Change

Staff accounts are created with a temporary password. The frontend should check the `mustChangePassword` flag (from staff data) and force password change.

---

## Get Current User

Retrieves the authenticated user's information. Identity is derived from the JWT, while current branch access is fetched fresh from the database.

```http
GET /auth/me
```

### Response

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "role": "OWNER",
    "businessId": "550e8400-e29b-41d4-a716-446655440001"
  }
}
```

### User States

| State            | businessId | Redirect        | Access                             |
| ---------------- | ---------- | --------------- | ---------------------------------- |
| New Owner        | `null`     | `/onboarding`   | Only `POST /businesses`            |
| Registered Owner | UUID       | `/dashboard`    | Full dashboard access              |
| Staff            | UUID       | `/appointments` | Appointments for assigned branches |

---

## Logout

```http
POST /auth/logout
```

### Response

```json
{
  "success": true,
  "message": "Success"
}
```

Clears the JWT cookie by setting `maxAge=0`.

---

## JWT Cookie Details

| Property | Value                                  |
| -------- | -------------------------------------- |
| Name     | `token`                                |
| HttpOnly | `true`                                 |
| Secure   | `true`                                 |
| SameSite | `None`                                 |
| Path     | `/`                                    |
| MaxAge   | Configurable (from `application.yaml`) |

---

## Frontend Implementation Notes

### Making Authenticated Requests

Always include credentials in fetch requests:

```javascript
fetch("/api/endpoint", {
  method: "GET",
  credentials: "include", // Required for cookies!
  headers: {
    "Content-Type": "application/json",
  },
});
```

### Checking Authentication Status

```javascript
async function checkAuth() {
  try {
    const response = await fetch("/auth/me", {
      credentials: "include",
    });

    if (response.ok) {
      const { data } = await response.json();
      return data; // { userId, role, businessId, branchIds }
    }

    return null; // Not authenticated
  } catch (error) {
    return null;
  }
}
```

### Handling Onboarding

```javascript
async function handleAuthRedirect() {
  const user = await checkAuth();

  if (!user) {
    // Not logged in, show login page
    return "/login";
  }

  if (user.role === "OWNER" && !user.businessId) {
    // New owner, needs onboarding
    return "/onboarding";
  }

  if (user.role === "STAFF") {
    // Staff goes to appointments view
    return "/appointments";
  }

  // Owner with business
  return "/dashboard";
}
```

# Orasa Backend Documentation

This folder contains comprehensive documentation for the Orasa Backend API.

## Table of Contents

| Document                                        | Description                                   |
| ----------------------------------------------- | --------------------------------------------- |
| [API Overview](./api-overview.md)               | High-level overview of the API structure      |
| [Authentication](./authentication.md)           | Authentication flows for Owner and Staff      |
| [Endpoints Reference](./endpoints-reference.md) | Complete API endpoint documentation           |
| [Frontend Flows](./frontend-flows.md)           | User flow guides for frontend implementation  |
| [Enums & Constants](./enums-constants.md)       | All enum values and constants used in the API |

## Quick Start

1. **Base URL**: `http://localhost:8080` (development)
2. **Authentication**: HTTP-only cookies with JWT
3. **Content-Type**: `application/json`

## Response Format

All API responses follow this standard structure:

```json
{
  "success": true,
  "message": "Success message",
  "data": {
    /* response data */
  }
}
```

## Error Response

```json
{
  "success": false,
  "message": "Error description"
}
```

# Authentication System - High Level Overview

## 1. Initial Login (Frontend → Google → Backend)
```
1. User clicks "Sign in with Google" button
   ↓
2. Frontend redirects to Google's OAuth consent page
   ↓
3. User authorizes the application with their Google account
   ↓
4. Google redirects back with an ID token (JWT)
   ↓
5. Frontend sends POST request to backend:
   POST /auth/google
   Body: { "idToken": "eyJhbGc..." }
```

## 2. Backend Processing - Login Flow (AuthServer → LoginHandler)
```
Request arrives at AuthServer (port 8080)
   ↓
Route: POST /auth/google (public endpoint, no auth required)
   ↓
LoginHandler.handle(exchange)
   ├─ Validates HTTP method is POST
   ├─ Reads request body and parses JSON
   ├─ Extracts idToken from request
   └─ Calls: authService.authenticateWithGoogle(idToken)
       ↓
   AuthService.authenticateWithGoogle(idToken)
       ├─ GoogleOAuthClient.verifyIdToken(idToken)
       │   ├─ Uses Google's verification library
       │   ├─ Checks cryptographic signature (is this really from Google?)
       │   ├─ Checks token expiration (is token still valid?)
       │   ├─ Checks audience claim (is this token for OUR app?)
       │   └─ Returns: GoogleUserInfo {
       │       googleId: "117234567890",
       │       email: "user@gmail.com",
       │       name: "John Doe",
       │       pictureUrl: "https://..."
       │   }
       │
       ├─ UserRepository.findByGoogleId("117234567890")
       │   ├─ Queries: "google_id_index:117234567890" from RocksDB (users CF)
       │   └─ Returns: existing User or null
       │
       ├─ IF user == null (NEW USER - SIGN UP):
       │   ├─ Create: new User(googleId, email, name, pictureUrl)
       │   │   └─ Generates UUID: "user-uuid-abc123"
       │   │   └─ Sets createdAt: Instant.now()
       │   └─ UserRepository.save(user)
       │       ├─ Saves to "users" CF: "user:user-uuid-abc123" → User JSON
       │       └─ Saves to "users" CF: "google_id_index:117234567890" → "user-uuid-abc123"
       │
       ├─ ELSE (EXISTING USER - SIGN IN):
       │   ├─ Updates: user.setLastLoginAt(Instant.now())
       │   └─ UserRepository.save(user)
       │       └─ Overwrites in "users" CF: "user:user-uuid-abc123" → Updated User JSON
       │
       ├─ SessionRepository.save(new Session(user.getId()))
       │   ├─ Generates: sessionId = UUID.randomUUID() → "session-xyz-789"
       │   ├─ Sets: createdAt = now, expiresAt = now + 7 days
       │   └─ Saves to "sessions" CF: "session:session-xyz-789" → Session JSON
       │
       └─ Returns: AuthResult {
           user: User object,
           sessionId: "session-xyz-789"
       }
   
   LoginHandler responds:
   HTTP 200 OK
   {
     "user": {
       "id": "user-uuid-abc123",
       "googleId": "117234567890",
       "email": "user@gmail.com",
       "name": "John Doe",
       "pictureUrl": "https://...",
       "createdAt": "2025-11-26T10:00:00.000Z",
       "lastLoginAt": "2025-11-26T10:00:00.000Z"
     },
     "sessionId": "session-xyz-789"
   }
```

## 3. Frontend Stores Session
```
Frontend receives response
   ↓
Stores sessionId in:
   - localStorage.setItem('sessionId', 'session-xyz-789')
   - OR sets cookie: document.cookie = "sessionId=session-xyz-789"
   ↓
Includes in all future requests as:
   - Authorization: Bearer session-xyz-789
   - OR Cookie: sessionId=session-xyz-789
```

## 4. Protected Request Flow (AuthMiddleware → Handler)
```
User makes authenticated request:
GET /api/profile
Headers: Authorization: Bearer session-xyz-789
   ↓
Request arrives at AuthServer
   ↓
Route: /api/profile (protected endpoint)
   ↓
AuthMiddleware.handle(exchange)  ← Wraps ProfileHandler
   ├─ extractSessionId(exchange)
   │   ├─ Checks Authorization header: "Bearer session-xyz-789"
   │   │   └─ Extracts: "session-xyz-789"
   │   └─ Falls back to Cookie header if Authorization missing
   │
   ├─ IF sessionId == null:
   │   └─ sendUnauthorized(exchange, "Missing session token")
   │       └─ HTTP 401: {"error": "Missing session token"}
   │
   ├─ authService.validateSession("session-xyz-789")
   │   ├─ SessionRepository.findBySessionId("session-xyz-789")
   │   │   ├─ Queries "sessions" CF: "session:session-xyz-789" from RocksDB
   │   │   └─ Returns: Session object or null
   │   │
   │   ├─ IF session == null:
   │   │   └─ throw UnauthorizedException("Session not found")
   │   │
   │   ├─ IF session.isExpired():
   │   │   ├─ SessionRepository.delete("session-xyz-789")
   │   │   └─ throw UnauthorizedException("Session expired")
   │   │
   │   ├─ UserRepository.findById(session.getUserId())
   │   │   ├─ Queries "users" CF: "user:user-uuid-abc123" from RocksDB
   │   │   └─ Returns: User object or null
   │   │
   │   ├─ IF user == null:
   │   │   └─ throw UnauthorizedException("User not found")
   │   │
   │   └─ Returns: User object
   │
   ├─ exchange.setAttribute("user", user)
   ├─ exchange.setAttribute("sessionId", "session-xyz-789")
   │
   └─ handler.handle(exchange)  ← Calls wrapped handler
       ↓
   ProfileHandler.handle(exchange)
       ├─ Validates HTTP method is GET
       ├─ Retrieves: user = exchange.getAttribute("user")
       ├─ Serializes user to JSON using ObjectMapper
       └─ Responds:
           HTTP 200 OK
           {
             "id": "user-uuid-abc123",
             "googleId": "117234567890",
             "email": "user@gmail.com",
             "name": "John Doe",
             "pictureUrl": "https://...",
             "createdAt": "2025-11-26T10:00:00.000Z",
             "lastLoginAt": "2025-11-26T10:00:00.000Z"
           }
```

## 5. Logout Flow
```
User requests logout:
POST /auth/logout
Headers: Authorization: Bearer session-xyz-789
   ↓
Request arrives at AuthServer
   ↓
Route: /auth/logout (protected endpoint)
   ↓
AuthMiddleware.handle(exchange)
   ├─ Validates session (same as step 4)
   ├─ Attaches user to exchange
   └─ Calls: logoutHandler.handle(exchange)
       ↓
   LogoutHandler.handle(exchange)
       ├─ Validates HTTP method is POST
       ├─ Retrieves: sessionId = exchange.getAttribute("sessionId")
       ├─ authService.signOut("session-xyz-789")
       │   └─ SessionRepository.delete("session-xyz-789")
       │       └─ Deletes from "sessions" CF: "session:session-xyz-789" from RocksDB
       │
       └─ Responds:
           HTTP 200 OK
           {"message": "Signed out successfully"}
```

## 6. Database State After Complete Flow

### After Sign Up/Sign In:

**RocksDB Column Family: "users"**
```
Key: "user:user-uuid-abc123"
Value: {
  "id": "user-uuid-abc123",
  "googleId": "117234567890",
  "email": "user@gmail.com",
  "name": "John Doe",
  "pictureUrl": "https://lh3.googleusercontent.com/...",
  "createdAt": "2025-11-26T10:00:00.000Z",
  "lastLoginAt": "2025-11-26T10:00:00.000Z"
}

Key: "google_id_index:117234567890"
Value: "user-uuid-abc123"
↑ Index for fast lookup by Google ID
```

**RocksDB Column Family: "sessions"**
```
Key: "session:session-xyz-789"
Value: {
  "sessionId": "session-xyz-789",
  "userId": "user-uuid-abc123",
  "createdAt": "2025-11-26T10:00:00.000Z",
  "expiresAt": "2025-12-03T10:00:00.000Z"
}
```

### After Logout:

**RocksDB Column Family: "users"**
```
Key: "user:user-uuid-abc123"
Value: { ... }  ← User data persists

Key: "google_id_index:117234567890"
Value: "user-uuid-abc123"  ← Index persists
```

**RocksDB Column Family: "sessions"**
```
Key: "session:session-xyz-789"
Value: [DELETED]  ← Session is removed
```
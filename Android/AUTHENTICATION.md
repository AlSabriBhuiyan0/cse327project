# Authentication in HappyChat AI

This document outlines the authentication flow in the HappyChat AI Android application, focusing on the Google Sign-In integration using both the new Credential Manager API and the legacy Google Sign-In API.

## Overview

The authentication system in HappyChat AI is built with the following components:

1. **GoogleAuthManager**: Central class handling Google authentication
2. **AuthViewModel**: Manages authentication state and business logic
3. **SignInActivity**: Handles the UI for authentication
4. **AuthState/GoogleSignInState**: Sealed classes representing authentication states

## Google Authentication Flow

### Prerequisites

1. Add your `google-services.json` to the `app/` directory
2. Configure your OAuth 2.0 Client ID in the Google Cloud Console
3. Add your web client ID to `res/values/strings.xml`:
   ```xml
   <string name="default_web_client_id">YOUR_WEB_CLIENT_ID</string>
   ```

### Implementation Details

#### GoogleAuthManager

The `GoogleAuthManager` class provides a unified interface for Google authentication, supporting both the new Credential Manager API (API 23+) and falling back to the legacy Google Sign-In API for older devices.

Key methods:
- `signIn(activity, launcher)`: Initiates the sign-in flow
- `handleCredentialManagerResult(result)`: Processes the credential response
- `signOut()`: Signs out the current user
- `isUserSignedIn()`: Checks if a user is currently signed in

#### AuthViewModel

The `AuthViewModel` manages the authentication state and coordinates between the UI and the `GoogleAuthManager`.

Key methods:
- `signInWithGoogle(activity, launcher)`: Initiates Google Sign-In
- `handleGoogleSignInResult(result)`: Handles the sign-in result
- `signOut()`: Signs out the current user

#### SignInActivity

The `SignInActivity` hosts the authentication UI and handles user interactions.

### Testing

#### Unit Tests

Unit tests for `GoogleAuthManager` can be found in:
```
app/src/test/java/com/google/ai/edge/gallery/auth/GoogleAuthManagerTest.kt
```

#### UI Tests

UI tests for the authentication flow can be found in:
```
app/src/androidTest/java/com/google/ai/edge/gallery/ui/auth/SignInActivityTest.kt
```

### Error Handling

The authentication system provides detailed error handling with the following error types:
- `ApiException`: Google API errors
- `FirebaseAuthException`: Firebase authentication errors
- `IllegalStateException`: Invalid state errors
- `UnsupportedOperationException`: Unsupported operations

### Best Practices

1. **Threading**: All authentication operations are performed on the IO dispatcher
2. **State Management**: Use `StateFlow` for reactive state updates
3. **Error Handling**: Always handle authentication errors gracefully
4. **Testing**: Write tests for both success and failure scenarios

## Migration from Legacy Code

To migrate from the old authentication system:

1. Remove references to `GoogleSignInHelper` and `GoogleSignInManager`
2. Update your code to use `GoogleAuthManager`
3. Update your UI to observe the authentication state from `AuthViewModel`

## Troubleshooting

### Common Issues

1. **Missing Web Client ID**:
   Ensure you've added your web client ID to `res/values/strings.xml`

2. **SHA-1 Fingerprint**:
   Make sure your app's SHA-1 fingerprint is configured in the Google Cloud Console

3. **OAuth Consent Screen**:
   Ensure you've configured the OAuth consent screen in the Google Cloud Console

### Debugging

Enable verbose logging by setting:
```kotlin
GoogleAuthManager.TAG = "AuthDebug"
```

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

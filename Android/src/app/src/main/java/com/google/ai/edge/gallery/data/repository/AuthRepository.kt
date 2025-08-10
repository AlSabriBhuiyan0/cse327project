package com.google.ai.edge.gallery.data.repository

import com.google.ai.edge.gallery.data.model.AuthResult
import com.google.ai.edge.gallery.data.model.User
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that handles authentication operations.
 */
@Singleton
class AuthRepository @Inject constructor() {
    private val auth: FirebaseAuth = Firebase.auth

    /**
     * Get the current authenticated user.
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Sign in with email and password.
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): Flow<AuthResult<Unit>> = flow {
        try {
            emit(AuthResult.Loading())
            auth.signInWithEmailAndPassword(email, password).await()
            emit(AuthResult.Success(Unit))
        } catch (e: Exception) {
            emit(AuthResult.Error(e.message ?: "Authentication failed"))
        }
    }

    /**
     * Sign up with email and password.
     */
    suspend fun signUpWithEmailAndPassword(email: String, password: String, name: String): Flow<AuthResult<Unit>> = flow {
        try {
            emit(AuthResult.Loading())
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            
            // Update user profile with display name
            result.user?.updateProfile(
                com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
            )?.await()
            
            // Send email verification
            result.user?.sendEmailVerification()
            
            emit(AuthResult.Success(Unit))
        } catch (e: Exception) {
            emit(AuthResult.Error(e.message ?: "Registration failed"))
        }
    }

    /**
     * Sign in with Google account.
     */
    suspend fun signInWithGoogle(account: GoogleSignInAccount): Flow<AuthResult<Unit>> = flow {
        try {
            emit(AuthResult.Loading())
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).await()
            emit(AuthResult.Success(Unit))
        } catch (e: Exception) {
            emit(AuthResult.Error(e.message ?: "Google sign in failed"))
        }
    }

    /**
     * Sign out the current user.
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Send password reset email.
     */
    suspend fun sendPasswordResetEmail(email: String): Flow<AuthResult<Unit>> = flow {
        try {
            emit(AuthResult.Loading())
            auth.sendPasswordResetEmail(email).await()
            emit(AuthResult.Success(Unit))
        } catch (e: Exception) {
            emit(AuthResult.Error(e.message ?: "Failed to send reset email"))
        }
    }

    /**
     * Get the current user as a User object.
     */
    fun getCurrentUser(): User? {
        return auth.currentUser?.let { firebaseUser ->
            User(
                id = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName,
                photoUrl = firebaseUser.photoUrl?.toString(),
                isEmailVerified = firebaseUser.isEmailVerified
            )
        }
    }

    /**
     * Observe authentication state changes.
     */
    fun observeAuthState(): Flow<Boolean> = flow {
        auth.addAuthStateListener { firebaseAuth ->
            emit(firebaseAuth.currentUser != null)
        }
    }
}

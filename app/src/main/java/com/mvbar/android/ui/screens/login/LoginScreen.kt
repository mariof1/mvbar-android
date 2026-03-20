package com.mvbar.android.ui.screens.login

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.mvbar.android.R
import com.mvbar.android.debug.DebugLog
import com.mvbar.android.ui.theme.*
import com.mvbar.android.viewmodel.AuthState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authState: AuthState,
    onLogin: (server: String, email: String, password: String) -> Unit,
    onGoogleSignIn: (server: String, idToken: String) -> Unit = { _, _ -> },
    onCheckGoogleAuth: (server: String) -> Unit = {}
) {
    var server by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var googleLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Check Google auth when server URL is entered (debounced, only on valid-looking URLs)
    var lastCheckedServer by remember { mutableStateOf("") }
    LaunchedEffect(server) {
        val normalized = server.trim().removeSuffix("/")
        // Must look like a real URL with a TLD (e.g. https://something.com)
        if (normalized.length > 12 && normalized != lastCheckedServer &&
            (normalized.startsWith("http://") || normalized.startsWith("https://")) &&
            normalized.substringAfter("://").contains(".")
        ) {
            kotlinx.coroutines.delay(800) // debounce — wait for user to stop typing
            lastCheckedServer = normalized
            onCheckGoogleAuth(normalized)
        }
    }

    val showGoogleButton = authState.googleEnabled

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(BackgroundDark, Cyan900.copy(alpha = 0.3f), BackgroundDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.mvbar_logo),
                    contentDescription = "mvbar",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "mvbar",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        brush = Brush.linearGradient(listOf(Color(0xFF4DD9FF), Color(0xFF00A3CC)))
                    ),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Sign in to your server",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDim
                )

                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://mvbar.example.com") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Dns, null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { state ->
                            if (!state.isFocused && server.isNotBlank()) {
                                val normalized = server.trim().removeSuffix("/")
                                if (normalized.length > 8) onCheckGoogleAuth(normalized)
                            }
                        },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Email, null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Lock, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                null
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            if (server.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                                onLogin(server, email, password)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(24.dp))

                authState.error?.let { error ->
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                googleError?.let { error ->
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = { onLogin(server, email, password) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                    enabled = !authState.isLoading && !googleLoading && server.isNotBlank() && email.isNotBlank() && password.isNotBlank()
                ) {
                    if (authState.isLoading && !googleLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Sign In", fontWeight = FontWeight.SemiBold)
                    }
                }

                // Google Sign-In button
                AnimatedVisibility(
                    visible = showGoogleButton,
                    enter = fadeIn() + expandVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = OnSurfaceSubtle)
                            Text(
                                "  or  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = OnSurfaceSubtle)
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                if (server.isBlank()) return@OutlinedButton
                                val clientId = authState.googleClientId
                                if (clientId.isNullOrEmpty()) {
                                    googleError = "Server did not provide a Google client ID. Update the server."
                                    return@OutlinedButton
                                }
                                googleLoading = true
                                googleError = null
                                scope.launch {
                                    try {
                                        DebugLog.i("GoogleAuth", "Starting credential request with clientId: ${clientId.take(20)}...")
                                        val credentialManager = CredentialManager.create(context)
                                        val signInOption = GetSignInWithGoogleOption.Builder(clientId)
                                            .build()

                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(signInOption)
                                            .build()

                                        DebugLog.i("GoogleAuth", "Calling getCredential...")
                                        val result = credentialManager.getCredential(
                                            context = context as Activity,
                                            request = request
                                        )

                                        val credential = result.credential
                                        DebugLog.i("GoogleAuth", "Got credential type: ${credential::class.simpleName}, rawType: ${credential.type}")
                                        if (credential is CustomCredential &&
                                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                        ) {
                                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                            DebugLog.i("GoogleAuth", "Got idToken, calling onGoogleSignIn")
                                            onGoogleSignIn(server.trim().removeSuffix("/"), googleIdTokenCredential.idToken)
                                        } else {
                                            DebugLog.e("GoogleAuth", "Unexpected credential: class=${credential::class.qualifiedName} type=${credential.type}")
                                            googleLoading = false
                                            googleError = "Unexpected credential type: ${credential.type}"
                                        }
                                    } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                                        DebugLog.i("GoogleAuth", "Cancelled: type=${e.type}, msg=${e.message}, cause=${e.cause}")
                                        googleLoading = false
                                        // If user selects account but still gets cancelled, it's likely a SHA-1 config issue
                                        googleError = "Google sign-in cancelled. If you selected an account, ensure the app's SHA-1 is registered in Google Cloud Console."
                                    } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                                        DebugLog.e("GoogleAuth", "CredentialException: type=${e.type}, msg=${e.message}, cause=${e.cause}", e)
                                        googleLoading = false
                                        googleError = e.message ?: "Google sign-in failed"
                                    } catch (e: Exception) {
                                        DebugLog.e("GoogleAuth", "Failed: ${e::class.simpleName}: ${e.message}", e)
                                        googleLoading = false
                                        googleError = e.message ?: "Google sign-in failed"
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, OnSurfaceSubtle),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurface),
                            enabled = !authState.isLoading && !googleLoading && server.isNotBlank()
                        ) {
                            if (googleLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = OnSurface,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                "G",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color(0xFF4285F4)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Continue with Google",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

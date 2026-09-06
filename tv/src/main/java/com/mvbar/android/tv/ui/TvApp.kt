package com.mvbar.android.tv.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.mvbar.android.tv.R
import com.mvbar.android.tv.TrackCollection
import com.mvbar.android.tv.TvActionPane
import com.mvbar.android.tv.TvSection
import com.mvbar.android.tv.TvUiState
import com.mvbar.android.tv.TvViewModel
import com.mvbar.android.tv.data.Album
import com.mvbar.android.tv.data.Audiobook
import com.mvbar.android.tv.data.AudiobookChapter
import com.mvbar.android.tv.data.Episode
import com.mvbar.android.tv.data.Podcast
import com.mvbar.android.tv.data.RecommendationBucket
import com.mvbar.android.tv.data.Track
import com.mvbar.android.tv.data.TvPlaylist
import com.mvbar.android.tv.playback.PlaybackSnapshot
import com.mvbar.android.tv.playback.PlaybackKind
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Background = Color(0xFF080D18)
private val Surface = Color(0xFF121C2D)
private val SurfaceRaised = Color(0xFF1B2A40)
private val Accent = Color(0xFF4DDBFF)
private val Muted = Color(0xFFA9B8CC)
private val PlayerHeight = 88.dp
private val ScreenPadding = 40.dp
private val DrawerWidth = 250.dp
private const val RecommendationColumns = 5

private val TvColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF001F27),
    background = Background,
    onBackground = Color.White,
    surface = Surface,
    onSurface = Color.White,
    error = Color(0xFFFF8A80)
)

@Composable
fun MvbarTvApp(viewModel: TvViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme = TvColors) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = Color.White
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF18304C), Background),
                            radius = 1400f
                        )
                    )
            ) {
                when {
                    state.checkingSession -> LoadingScreen("Connecting to MVBar…")
                    !state.signedIn -> LoginScreen(state, viewModel)
                    else -> TvShell(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(state: TvUiState, viewModel: TvViewModel) {
    var serverUrl by rememberSaveable { mutableStateOf(state.serverUrl) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var googleCredentialLoading by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }
    val serverFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val signInFocus = remember { FocusRequester() }
    val googleSignInFocus = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val normalizedServer = serverUrl.trim().trimEnd('/')
    val googleConfigMatches = state.googleAuthServerUrl == normalizedServer
    val showGoogleButton = googleConfigMatches && state.googleAuthEnabled
    val googleBusy = googleCredentialLoading || state.googleSigningIn

    LaunchedEffect(state.serverUrl) {
        if (serverUrl.isBlank()) serverUrl = state.serverUrl
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        if (serverUrl.isBlank()) serverFocus.requestFocus() else emailFocus.requestFocus()
    }
    LaunchedEffect(serverUrl) {
        googleError = null
        delay(650L)
        if (isGoogleAuthServerCandidate(serverUrl)) {
            viewModel.checkGoogleAuth(serverUrl)
        } else {
            viewModel.checkGoogleAuth("")
        }
    }
    LaunchedEffect(state.error) {
        if (state.error != null) googleCredentialLoading = false
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(R.drawable.mvbar_wordmark),
                contentDescription = "MVBar",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(370.dp).height(238.dp)
            )
            Text("Your music, built for the big screen.", fontSize = 24.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Browse your library with the remote and play through your TV or sound system.",
                fontSize = 16.sp,
                lineHeight = 23.sp,
                color = Muted,
                maxLines = 2
            )
        }

        Column(
            modifier = Modifier
                .width(440.dp)
                .background(Surface.copy(alpha = 0.97f), RoundedCornerShape(22.dp))
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Sign in", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            TvTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = "MVBar server",
                placeholder = "https://mvbar.example",
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.focusRequester(serverFocus),
                onNext = { emailFocus.requestFocus() }
            )
            TvTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                keyboardType = KeyboardType.Email,
                modifier = Modifier.focusRequester(emailFocus),
                onNext = { passwordFocus.requestFocus() }
            )
            TvTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordVisibilityChange = { passwordVisible = !passwordVisible },
                imeAction = ImeAction.Done,
                modifier = Modifier.focusRequester(passwordFocus),
                onDone = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        viewModel.signIn(serverUrl, email, password)
                    } else {
                        signInFocus.requestFocus()
                    }
                }
            )
            state.error?.let { ErrorBanner(it, viewModel::dismissError) }
            googleError?.let { ErrorBanner(it) { googleError = null } }
            Button(
                onClick = { viewModel.signIn(serverUrl, email, password) },
                enabled = !state.loading && !googleCredentialLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().focusRequester(signInFocus),
                colors = actionButtonColors()
            ) {
                if (state.loading && !state.googleSigningIn) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp, color = Background)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    if (state.loading && !state.googleSigningIn) "Signing in…" else "Sign in",
                    fontSize = 18.sp
                )
            }

            if (googleConfigMatches && state.checkingGoogleAuth) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                    Spacer(Modifier.width(8.dp))
                    Text("Checking Google sign-in…", color = Muted, fontSize = 13.sp)
                }
            }

            if (showGoogleButton) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = Muted.copy(alpha = 0.35f))
                    Text("  or  ", color = Muted, fontSize = 13.sp)
                    HorizontalDivider(Modifier.weight(1f), color = Muted.copy(alpha = 0.35f))
                }
                Button(
                    onClick = {
                        val clientId = state.googleClientId
                        val activity = context as? Activity
                        when {
                            clientId.isNullOrBlank() -> {
                                googleError = "This server did not provide a Google client ID."
                            }
                            activity == null -> {
                                googleError = "Google sign-in is not available in this screen."
                            }
                            else -> {
                                googleCredentialLoading = true
                                googleError = null
                                scope.launch {
                                    try {
                                        val option = GetSignInWithGoogleOption.Builder(clientId).build()
                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(option)
                                            .build()
                                        val result = CredentialManager.create(context).getCredential(
                                            context = activity,
                                            request = request
                                        )
                                        val credential = result.credential
                                        if (
                                            credential is CustomCredential &&
                                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                        ) {
                                            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                            googleCredentialLoading = false
                                            viewModel.googleSignIn(serverUrl, googleCredential.idToken)
                                        } else {
                                            googleCredentialLoading = false
                                            googleError = "Google returned an unsupported credential."
                                        }
                                    } catch (_: GetCredentialCancellationException) {
                                        googleCredentialLoading = false
                                        googleError = "Google sign-in was cancelled."
                                    } catch (_: NoCredentialException) {
                                        googleCredentialLoading = false
                                        googleError = "No Google account is available. Add one in Android TV settings and try again."
                                    } catch (_: GetCredentialProviderConfigurationException) {
                                        googleCredentialLoading = false
                                        googleError = "Update Google Play services on this TV, then try again."
                                    } catch (_: GetCredentialUnsupportedException) {
                                        googleCredentialLoading = false
                                        googleError = "Google sign-in is not supported by this TV."
                                    } catch (_: GetCredentialException) {
                                        googleCredentialLoading = false
                                        googleError = "Google sign-in could not start. Check the TV app registration and try again."
                                    } catch (error: Exception) {
                                        googleCredentialLoading = false
                                        googleError = error.message ?: "Google sign-in failed."
                                    }
                                }
                            }
                        }
                    },
                    enabled = !state.loading && !googleCredentialLoading,
                    modifier = Modifier.fillMaxWidth().focusRequester(googleSignInFocus),
                    colors = googleButtonColors()
                ) {
                    if (googleBusy) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFF202124))
                        Spacer(Modifier.width(10.dp))
                    } else {
                        Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(if (googleBusy) "Connecting to Google…" else "Continue with Google", fontSize = 17.sp)
                }
            }
        }
    }
}

private fun isGoogleAuthServerCandidate(value: String): Boolean {
    val trimmed = value.trim().trimEnd('/')
    if (trimmed.isBlank()) return false
    val authority = trimmed.substringAfter("://", trimmed).substringBefore('/')
    val host = authority.substringBefore(':')
    return host.equals("localhost", ignoreCase = true) || host.contains('.')
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityChange: () -> Unit = {},
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
    onDone: () -> Unit = {},
    onLeft: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = onPasswordVisibilityChange) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() },
            onDone = { onDone() },
            onSearch = { onDone() }
        ),
        modifier = modifier
            .fillMaxWidth()
            .dpadEdges(onLeft = onLeft, onUp = onUp, onDown = onDown),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            focusedLabelColor = Accent,
            cursorColor = Accent
        )
    )
}

@Composable
private fun TvShell(state: TvUiState, viewModel: TvViewModel) {
    var sidebarOpen by remember { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val firstSearchResultFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.nowPlayingVisible) {
        if (state.nowPlayingVisible) keyboardController?.hide()
    }

    BackHandler(enabled = sidebarOpen) { sidebarOpen = false }
    BackHandler(enabled = state.actionPane != TvActionPane.NONE) { viewModel.navigateActionBack() }
    BackHandler(
        enabled = !sidebarOpen && (
            state.nowPlayingVisible || state.searchVisible || state.trackCollection != null || state.selectedArtist != null ||
                state.selectedPodcast != null || state.selectedAudiobook != null
            )
    ) { viewModel.navigateBack() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (!state.nowPlayingVisible) {
                TopBar(
                    state = state,
                    viewModel = viewModel,
                    searchFocus = searchFocus,
                    firstSearchResultFocus = firstSearchResultFocus,
                    onOpenSidebar = { sidebarOpen = true }
                )
                state.error?.let {
                    Box(Modifier.padding(horizontal = ScreenPadding, vertical = 3.dp)) {
                        ErrorBanner(it, viewModel::dismissError)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        bottom = if (state.displayedPlayback.item != null && !state.nowPlayingVisible) PlayerHeight else 6.dp
                    )
            ) {
                TvContent(
                    state = state,
                    viewModel = viewModel,
                    firstSearchResultFocus = firstSearchResultFocus,
                    searchFocus = searchFocus,
                    onOpenSidebar = { sidebarOpen = true }
                )
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = ScreenPadding),
                        color = Accent,
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        if (state.displayedPlayback.item != null && !state.nowPlayingVisible) {
            NowPlayingBar(
                playback = state.displayedPlayback,
                authToken = state.authToken,
                onPrevious = viewModel::previous,
                onSeekBackward = viewModel::seekBackward,
                onToggle = viewModel::togglePlayPause,
                onSeekForward = viewModel::seekForward,
                onNext = viewModel::next,
                onOpen = viewModel::openNowPlaying,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (sidebarOpen) {
            NavigationSidebar(
                selected = state.selectedSection,
                email = state.email,
                onSelect = {
                    viewModel.selectSection(it)
                    sidebarOpen = false
                },
                onClose = {
                    sidebarOpen = false
                    searchFocus.requestFocus()
                },
                onSignOut = viewModel::signOut
            )
        }

        state.notice?.let { notice ->
            NoticeToast(notice, Modifier.align(Alignment.TopEnd).padding(top = 76.dp, end = ScreenPadding))
        }

        if (state.actionPane != TvActionPane.NONE) {
            TrackActionOverlay(state, viewModel)
        }
    }
}

@Composable
private fun NoticeToast(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xEE173047), RoundedCornerShape(12.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TrackActionOverlay(state: TvUiState, viewModel: TvViewModel) {
    val firstFocus = remember(state.actionPane) { FocusRequester() }
    RequestInitialFocus(firstFocus, true)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(430.dp)
                .background(Color(0xFF111E30), RoundedCornerShape(18.dp))
                .padding(22.dp)
        ) {
            state.error?.let { error ->
                Text(error, color = Color(0xFFFF8A80), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
            }
            when (state.actionPane) {
                TvActionPane.TRACK -> {
                    val track = state.actionTrack ?: return@Column
                    val favorite = state.favorites.any { it.id == track.id }
                    Text(track.displayTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(track.displayArtist, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    ActionRow(
                        if (favorite) Icons.Default.Favorite else Icons.Default.Favorite,
                        if (favorite) "Remove from Favorites" else "Add to Favorites",
                        { viewModel.toggleFavorite(track) },
                        Modifier.focusRequester(firstFocus),
                        enabled = !state.actionLoading
                    )
                    ActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to playlist", viewModel::showPlaylistTargets, enabled = !state.actionLoading)
                    if (state.trackCollection?.playlist?.kind == TvPlaylist.Kind.STANDARD) {
                        ActionRow(Icons.Default.Delete, "Remove from this playlist", viewModel::removeActionTrackFromPlaylist, enabled = !state.actionLoading)
                    }
                    ActionRow(
                        Icons.Default.SkipNext,
                        "Play next",
                        { viewModel.queueTrackNext(track) },
                        enabled = !state.actionLoading && state.playback.item != null && !state.controllingRemote
                    )
                    ActionRow(Icons.Default.Share, "Share with a friend", viewModel::showShareTargets, enabled = !state.actionLoading)
                    ActionRow(Icons.Default.MusicNote, "Start radio", { viewModel.startRadio(track) }, enabled = !state.actionLoading)
                    ActionRow(
                        Icons.Default.Person,
                        "Open artist",
                        { viewModel.openArtist(track.artists.firstOrNull()?.id, track.displayArtist) },
                        enabled = !state.actionLoading
                    )
                    ActionRow(Icons.Default.Close, "Close", viewModel::closeActions, enabled = !state.actionLoading)
                }
                TvActionPane.PLAYLISTS -> {
                    Text("Add to playlist", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    state.actionTrack?.let { Text(it.displayTitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(14.dp))
                    ActionRow(
                        Icons.Default.Add,
                        "New playlist",
                        viewModel::showCreatePlaylist,
                        Modifier.focusRequester(firstFocus)
                    )
                    val playlists = state.playlists.filter { it.kind == TvPlaylist.Kind.STANDARD }
                    LazyColumn(
                        modifier = Modifier.height(250.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            ActionRow(
                                Icons.AutoMirrored.Filled.PlaylistPlay,
                                playlist.name,
                                { viewModel.addActionTrackToPlaylist(playlist) },
                                enabled = !state.actionLoading
                            )
                        }
                    }
                    ActionRow(Icons.AutoMirrored.Filled.ArrowBack, "Back", { viewModel.openTrackActions(state.actionTrack ?: return@ActionRow) })
                }
                TvActionPane.SHARE -> {
                    Text("Share with a friend", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    state.actionTrack?.let { Text(it.displayTitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(14.dp))
                    when {
                        state.actionLoading -> Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Accent)
                        }
                        state.shareTargets.isEmpty() -> Text(
                            "No friends can access this song.",
                            color = Muted,
                            modifier = Modifier.padding(vertical = 28.dp)
                        )
                        else -> LazyColumn(
                            modifier = Modifier.height(230.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(state.shareTargets, key = { _, it -> it.id }) { index, friend ->
                                ActionRow(
                                    Icons.Default.Person,
                                    friend.email,
                                    { viewModel.shareActionTrack(friend) },
                                    Modifier.then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                )
                            }
                        }
                    }
                    ActionRow(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        { viewModel.openTrackActions(state.actionTrack ?: return@ActionRow) },
                        if (state.shareTargets.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
                TvActionPane.CREATE_PLAYLIST -> {
                    var name by rememberSaveable { mutableStateOf("") }
                    val addTrack = state.actionTrack != null
                    Text("New playlist", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    TvTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Playlist name",
                        imeAction = ImeAction.Done,
                        onDone = { viewModel.createPlaylist(name, addTrack) },
                        modifier = Modifier.focusRequester(firstFocus)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallButton(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", if (addTrack) viewModel::showPlaylistTargets else viewModel::closeActions)
                        SmallButton(
                            Icons.Default.Add,
                            "Create",
                            { viewModel.createPlaylist(name, addTrack) },
                            enabled = name.isNotBlank() && !state.actionLoading
                        )
                    }
                }
                TvActionPane.CONNECT -> {
                    Text("MVBar Connect", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Choose where playback happens",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    if (state.connectDevices.isEmpty()) {
                        Text("Looking for signed-in players…", color = Muted, modifier = Modifier.padding(vertical = 24.dp))
                        ActionRow(
                            Icons.Default.Close,
                            "Close",
                            viewModel::closeActions,
                            Modifier.focusRequester(firstFocus)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(state.connectDevices, key = { _, device -> device.id }) { index, device ->
                                val isLocal = device.id == state.localConnectDeviceId
                                val isSelected = device.id == state.selectedConnectDeviceId
                                val status = device.state.track?.let { track ->
                                    "${if (device.state.isPlaying) "Playing" else "Paused"} · ${track.title ?: "Untitled"}"
                                } ?: listOfNotNull(device.type.uppercase(), device.platform).joinToString(" · ")
                                ActionRow(
                                    Icons.Default.Devices,
                                    buildString {
                                        append(if (isSelected) "✓ " else "")
                                        append(device.name)
                                        if (isLocal) append(" · This TV")
                                        if (status.isNotBlank()) append(" — $status")
                                    },
                                    { viewModel.selectConnectDevice(device.id) },
                                    Modifier.then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                )
                            }
                        }
                        Text(
                            "Only players signed in to this MVBar account are visible.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }
                }
                TvActionPane.NONE -> Unit
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = secondaryButtonColors(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(11.dp))
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TopBar(
    state: TvUiState,
    viewModel: TvViewModel,
    searchFocus: FocusRequester,
    firstSearchResultFocus: FocusRequester,
    onOpenSidebar: () -> Unit
) {
    val context = LocalContext.current
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { viewModel.openVoiceSearch(it) }
        }
    }
    val launchVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search your MVBar library")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        if (intent.resolveActivity(context.packageManager) != null) voiceLauncher.launch(intent)
        else viewModel.notifyUser("Voice search is unavailable on this TV")
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = ScreenPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.mvbar_wordmark),
                contentDescription = "MVBar",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(106.dp).height(52.dp)
            )
            Spacer(Modifier.weight(1f))
            SmallButton(
                Icons.Default.Devices,
                state.selectedConnectDevice?.name ?: "Connect",
                viewModel::openConnectPlayers
            )
            Spacer(Modifier.width(8.dp))
            if (state.searchVisible) {
                Box(Modifier.width(490.dp)) {
                    TvTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::updateSearchQuery,
                        label = "Search music, playlists, and podcasts",
                        imeAction = ImeAction.Search,
                        onDone = viewModel::search,
                        onLeft = onOpenSidebar,
                        onDown = if (hasSearchResults(state)) {
                            ({ firstSearchResultFocus.requestFocus() })
                        } else {
                            null
                        },
                        modifier = Modifier.focusRequester(searchFocus)
                    )
                }
                Spacer(Modifier.width(10.dp))
                SmallButton(Icons.Default.Mic, "Voice", launchVoiceSearch)
                Spacer(Modifier.width(8.dp))
                SmallButton(Icons.Default.Close, "Close", viewModel::closeSearch)
                LaunchedEffect(state.focusSearchResults, state.searchResults) {
                    withFrameNanos { }
                    if (state.focusSearchResults && hasSearchResults(state)) {
                        firstSearchResultFocus.requestFocus()
                    } else if (!state.focusSearchResults) {
                        searchFocus.requestFocus()
                    }
                }
            } else {
                Button(
                    onClick = viewModel::openSearch,
                    modifier = Modifier
                        .width(300.dp)
                        .focusRequester(searchFocus)
                        .dpadEdges(onLeft = onOpenSidebar),
                    colors = secondaryButtonColors(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Search your MVBar library", color = Muted, fontSize = 15.sp)
                }
                Spacer(Modifier.width(8.dp))
                SmallButton(Icons.Default.Mic, "Voice", launchVoiceSearch)
            }
        }
        if (state.refreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Accent,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
private fun NavigationSidebar(
    selected: TvSection,
    email: String,
    onSelect: (TvSection) -> Unit,
    onClose: () -> Unit,
    onSignOut: () -> Unit
) {
    val focusRequesters = remember { TvSection.entries.associateWith { FocusRequester() } }
    LaunchedEffect(selected) {
        withFrameNanos { }
        focusRequesters.getValue(selected).requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(DrawerWidth)
                .background(Color(0xFF0D1727))
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.mvbar_wordmark),
                contentDescription = "MVBar",
                contentScale = ContentScale.Fit,
                modifier = Modifier.width(116.dp).height(58.dp)
            )
            Spacer(Modifier.height(8.dp))
            TvSection.entries.forEach { section ->
                val icon = when (section) {
                    TvSection.FOR_YOU -> Icons.Default.Home
                    TvSection.RECENT -> Icons.Default.LibraryMusic
                    TvSection.ALBUMS -> Icons.Default.Album
                    TvSection.PLAYLISTS -> Icons.AutoMirrored.Filled.PlaylistPlay
                    TvSection.FAVORITES -> Icons.Default.Favorite
                    TvSection.PODCASTS -> Icons.Default.Podcasts
                    TvSection.AUDIOBOOKS -> Icons.AutoMirrored.Filled.MenuBook
                }
                val isSelected = section == selected
                Button(
                    onClick = { onSelect(section) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters.getValue(section))
                        .dpadEdges(onRight = onClose),
                    colors = ButtonDefaults.colors(
                        containerColor = if (isSelected) Accent.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = if (isSelected) Accent else Color.White,
                        focusedContainerColor = Accent,
                        focusedContentColor = Background
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(icon, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(section.label, modifier = Modifier.weight(1f), fontSize = 15.sp)
                }
                Spacer(Modifier.height(3.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).background(Accent.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(email.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Text(email, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            SmallButton(
                Icons.AutoMirrored.Filled.Logout,
                "Sign out",
                onSignOut,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TvContent(
    state: TvUiState,
    viewModel: TvViewModel,
    firstSearchResultFocus: FocusRequester,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit
) {
    when {
        state.nowPlayingVisible -> NowPlayingScreen(state, viewModel)
        state.searchVisible -> SearchContent(
            state,
            viewModel,
            firstSearchResultFocus,
            searchFocus,
            onOpenSidebar
        )
        state.selectedArtist != null -> ArtistDetailScreen(state, viewModel)
        state.trackCollection != null -> TrackCollectionDetail(state.trackCollection, state, viewModel)
        state.selectedPodcast != null -> PodcastDetail(state, viewModel)
        state.selectedAudiobook != null -> AudiobookDetail(state, viewModel)
        state.selectedSection == TvSection.FOR_YOU -> ForYouScreen(state, viewModel, searchFocus, onOpenSidebar)
        state.selectedSection == TvSection.RECENT -> TrackGridScreen(
            "Recently Added",
            "The newest music on your server",
            state.recentlyAdded,
            state,
            searchFocus,
            onOpenSidebar,
            viewModel::openTrackActions
        ) { viewModel.play(state.recentlyAdded, it) }
        state.selectedSection == TvSection.ALBUMS -> AlbumGrid(
            state,
            searchFocus,
            onOpenSidebar,
            viewModel::openAlbum
        )
        state.selectedSection == TvSection.PLAYLISTS -> PlaylistGrid(
            state,
            searchFocus,
            onOpenSidebar,
            viewModel::showCreatePlaylist,
            viewModel::openPlaylist
        )
        state.selectedSection == TvSection.FAVORITES -> TrackGridScreen(
            "Favorites",
            "Songs you have saved",
            state.favorites,
            state,
            searchFocus,
            onOpenSidebar,
            viewModel::openTrackActions
        ) { viewModel.play(state.favorites, it) }
        state.selectedSection == TvSection.PODCASTS -> PodcastScreen(
            state,
            viewModel,
            searchFocus,
            onOpenSidebar
        )
        state.selectedSection == TvSection.AUDIOBOOKS -> AudiobookGrid(
            state,
            searchFocus,
            onOpenSidebar,
            viewModel::openAudiobook
        )
    }
}

@Composable
private fun ForYouScreen(
    state: TvUiState,
    viewModel: TvViewModel,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    val buckets = state.recommendations.filter { it.tracks.isNotEmpty() }
    RequestInitialFocus(firstFocus, buckets.isNotEmpty())

    Column(Modifier.fillMaxSize()) {
        ContentTitle("For You", "Personal mixes from your MVBar listening")
        Spacer(Modifier.height(8.dp))
        if (buckets.isEmpty()) {
            EmptyState("Start listening and your personalized mixes will appear here")
        } else {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(RecommendationColumns),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItemsIndexed(buckets, key = { _, bucket -> bucket.key }) { bucketIndex, bucket ->
                    RecommendationBucketCard(
                        bucket = bucket,
                        serverUrl = state.serverUrl,
                        authToken = state.authToken,
                        onClick = { viewModel.playBucket(bucket) },
                        modifier = Modifier
                            .then(if (bucketIndex == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .then(
                                Modifier.dpadEdges(
                                    onLeft = if (isGridLeftEdge(bucketIndex, RecommendationColumns)) onOpenSidebar else null,
                                    onUp = if (isGridTopRow(bucketIndex, RecommendationColumns)) {
                                        ({ searchFocus.requestFocus() })
                                    } else {
                                        null
                                    }
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationBucketCard(
    bucket: RecommendationBucket,
    serverUrl: String,
    authToken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songCount = bucket.count.takeIf { it > 0 } ?: bucket.tracks.size
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${bucket.name}, $songCount songs. Play recommendation mix"
            },
        colors = contentCardColors()
    ) {
        Column {
            RecommendationArtworkGrid(
                bucket = bucket,
                serverUrl = serverUrl,
                authToken = authToken,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    bucket.name,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    bucket.subtitle.orEmpty(),
                    modifier = Modifier.height(17.dp),
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("$songCount songs", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun RecommendationArtworkGrid(
    bucket: RecommendationBucket,
    serverUrl: String,
    authToken: String,
    modifier: Modifier = Modifier
) {
    val paths = bucket.artPaths.take(4)
    Box(modifier = modifier) {
        when (paths.size) {
            0 -> Artwork(null, null, authToken, Modifier.fillMaxSize())
            1 -> Artwork(
                bucketArtworkUrl(serverUrl, paths.first(), bucket.artHashes.firstOrNull()),
                null,
                authToken,
                Modifier.fillMaxSize()
            )
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(2) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        repeat(2) { column ->
                            val index = row * 2 + column
                            val path = paths.getOrNull(index) ?: paths.first()
                            val hash = bucket.artHashes.getOrNull(index) ?: bucket.artHashes.firstOrNull()
                            Artwork(
                                bucketArtworkUrl(serverUrl, path, hash),
                                null,
                                authToken,
                                Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(9.dp)
                .size(34.dp)
                .background(Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Background,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun TrackGridScreen(
    title: String,
    subtitle: String,
    tracks: List<Track>,
    state: TvUiState,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit,
    onOptions: (Track) -> Unit,
    onPlay: (Track) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    RequestInitialFocus(firstFocus, tracks.isNotEmpty())
    Column(Modifier.fillMaxSize()) {
        ContentTitle(title, subtitle)
        Spacer(Modifier.height(8.dp))
        if (tracks.isEmpty()) {
            EmptyState("Nothing to show yet")
        } else {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Adaptive(154.dp),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    TrackCard(
                        track,
                        state.serverUrl,
                        state.authToken,
                        onPlay,
                        cardWidth = 154.dp,
                        onOptions = onOptions,
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .then(
                                if (index == 0) Modifier.dpadEdges(
                                    onLeft = onOpenSidebar,
                                    onUp = { searchFocus.requestFocus() }
                                ) else Modifier
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: Track,
    serverUrl: String,
    authToken: String,
    onClick: (Track) -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
    titleLines: Int = 2,
    onOptions: ((Track) -> Unit)? = null
) {
    Card(
        onClick = { onClick(track) },
        onLongClick = onOptions?.let { { it(track) } },
        modifier = modifier
            .width(cardWidth)
            .semantics { contentDescription = "${track.displayTitle}, ${track.displayArtist}" },
        colors = contentCardColors()
    ) {
        Column {
            Artwork(
                url = trackArtworkUrl(serverUrl, track),
                description = null,
                authToken = authToken,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
                Text(
                    track.displayTitle,
                    modifier = if (titleLines > 1) Modifier.height(36.dp) else Modifier,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = titleLines,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    track.displayArtist,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    state: TvUiState,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit,
    onClick: (Album) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    RequestInitialFocus(firstFocus, state.albums.isNotEmpty())
    Column(Modifier.fillMaxSize()) {
        ContentTitle("Albums", "Browse your complete collection")
        Spacer(Modifier.height(8.dp))
        if (state.albums.isEmpty()) {
            EmptyState("No albums found")
        } else {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Adaptive(158.dp),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItemsIndexed(state.albums, key = { _, album -> "${album.displayName}|${album.artistName}" }) { index, album ->
                    Card(
                        onClick = { onClick(album) },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .then(
                                if (index == 0) Modifier.dpadEdges(
                                    onLeft = onOpenSidebar,
                                    onUp = { searchFocus.requestFocus() }
                                ) else Modifier
                            )
                            .semantics { contentDescription = "${album.displayName}, ${album.artistName}" },
                        colors = contentCardColors()
                    ) {
                        Column {
                            Artwork(
                                url = album.artPath?.let { artPathUrl(state.serverUrl, it) },
                                description = null,
                                authToken = state.authToken,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            )
                            Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
                                Text(
                                    album.displayName,
                                    modifier = Modifier.height(34.dp),
                                    fontSize = 14.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(album.artistName, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGrid(
    state: TvUiState,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit,
    onCreate: () -> Unit,
    onClick: (TvPlaylist) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    RequestInitialFocus(firstFocus, true)
    Column(Modifier.fillMaxSize()) {
        ContentTitle("Playlists", "Your playlists, shared mixes, and smart collections")
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Adaptive(190.dp),
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "create_playlist") {
                Card(
                    onClick = onCreate,
                    modifier = Modifier
                        .height(118.dp)
                        .focusRequester(firstFocus)
                        .dpadEdges(onLeft = onOpenSidebar, onUp = { searchFocus.requestFocus() }),
                    colors = contentCardColors()
                ) {
                    Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(54.dp).background(Accent, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, null, tint = Background, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("New playlist", fontWeight = FontWeight.SemiBold)
                            Text("Create a collection", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            gridItemsIndexed(state.playlists, key = { _, playlist -> "${playlist.kind}:${playlist.id}" }) { _, playlist ->
                    Card(
                        onClick = { onClick(playlist) },
                        modifier = Modifier
                            .height(118.dp)
                            .dpadEdges(onUp = { searchFocus.requestFocus() }),
                        colors = contentCardColors()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(54.dp).background(Accent.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Accent, modifier = Modifier.size(30.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(3.dp))
                                Text(playlistSubtitle(playlist), color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun PodcastScreen(
    state: TvUiState,
    viewModel: TvViewModel,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit
) {
    val continueListening = state.newEpisodes.filter { it.positionMs > 0 && !it.played }
    val newEpisodes = state.newEpisodes.filter { !it.played }
    val firstFocus = remember { FocusRequester() }
    val hasContent = continueListening.isNotEmpty() || newEpisodes.isNotEmpty() || state.podcasts.isNotEmpty()
    RequestInitialFocus(firstFocus, hasContent)
    var firstAssigned = false

    Column(Modifier.fillMaxSize()) {
        ContentTitle("Podcasts", "Continue listening, catch up, or browse your subscriptions")
        if (!hasContent) {
            EmptyState("No podcast subscriptions yet")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (continueListening.isNotEmpty()) {
                    val assignFocus = !firstAssigned
                    firstAssigned = true
                    item(key = "continue") {
                        EpisodeShelf(
                            "Continue Listening",
                            continueListening,
                            state,
                            if (assignFocus) firstFocus else null,
                            searchFocus,
                            onOpenSidebar
                        ) { viewModel.playEpisode(continueListening, it) }
                    }
                }
                if (newEpisodes.isNotEmpty()) {
                    val assignFocus = !firstAssigned
                    firstAssigned = true
                    item(key = "new") {
                        EpisodeShelf(
                            "New Episodes",
                            newEpisodes,
                            state,
                            if (assignFocus) firstFocus else null,
                            searchFocus,
                            onOpenSidebar
                        ) { viewModel.playEpisode(newEpisodes, it) }
                    }
                }
                if (state.podcasts.isNotEmpty()) {
                    val assignFocus = !firstAssigned
                    item(key = "subscriptions") {
                        PodcastShelf(
                            "Subscriptions",
                            state.podcasts,
                            state,
                            if (assignFocus) firstFocus else null,
                            searchFocus,
                            onOpenSidebar,
                            viewModel::openPodcast
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeShelf(
    title: String,
    episodes: List<Episode>,
    state: TvUiState,
    firstFocus: FocusRequester?,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit,
    onPlay: (Episode) -> Unit
) {
    Column {
        Text(title, modifier = Modifier.padding(horizontal = ScreenPadding), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(episodes, key = { _, episode -> episode.id }) { index, episode ->
                EpisodeCard(
                    episode,
                    state,
                    onPlay,
                    modifier = Modifier
                        .then(if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                        .then(
                            if (index == 0) Modifier.dpadEdges(
                                onLeft = onOpenSidebar,
                                onUp = if (firstFocus != null) ({ searchFocus.requestFocus() }) else null
                            ) else Modifier
                        )
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(episode: Episode, state: TvUiState, onClick: (Episode) -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = { onClick(episode) }, modifier = modifier.width(250.dp), colors = contentCardColors()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(
                episodeArtworkUrl(state.serverUrl, episode),
                null,
                state.authToken,
                Modifier.size(86.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(episode.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(episodeSubtitle(episode), color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PodcastShelf(
    title: String,
    podcasts: List<Podcast>,
    state: TvUiState,
    firstFocus: FocusRequester?,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit,
    onClick: (Podcast) -> Unit
) {
    Column {
        Text(title, modifier = Modifier.padding(horizontal = ScreenPadding), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(podcasts, key = { _, podcast -> podcast.id }) { index, podcast ->
                PodcastCard(
                    podcast,
                    state,
                    onClick,
                    modifier = Modifier
                        .then(if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                        .then(
                            if (index == 0) Modifier.dpadEdges(
                                onLeft = onOpenSidebar,
                                onUp = if (firstFocus != null) ({ searchFocus.requestFocus() }) else null
                            ) else Modifier
                        )
                )
            }
        }
    }
}

@Composable
private fun PodcastCard(podcast: Podcast, state: TvUiState, onClick: (Podcast) -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = { onClick(podcast) }, modifier = modifier.width(154.dp), colors = contentCardColors()) {
        Column {
            Artwork(
                podcastArtworkUrl(state.serverUrl, podcast),
                null,
                state.authToken,
                Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Column(Modifier.padding(9.dp)) {
                Text(podcast.title, modifier = Modifier.height(34.dp), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (podcast.unplayedCount > 0) "${podcast.unplayedCount} unplayed" else podcast.author.orEmpty(),
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AudiobookGrid(
    state: TvUiState,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit,
    onClick: (Audiobook) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    RequestInitialFocus(firstFocus, state.audiobooks.isNotEmpty())
    Column(Modifier.fillMaxSize()) {
        ContentTitle("Audiobooks", "Continue a book or choose something new")
        Spacer(Modifier.height(8.dp))
        if (state.audiobooks.isEmpty()) {
            EmptyState("No audiobooks found")
        } else {
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItemsIndexed(state.audiobooks, key = { _, book -> book.id }) { index, book ->
                    Card(
                        onClick = { onClick(book) },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .then(
                                if (index == 0) Modifier.dpadEdges(
                                    onLeft = onOpenSidebar,
                                    onUp = { searchFocus.requestFocus() }
                                ) else Modifier
                            ),
                        colors = contentCardColors()
                    ) {
                        Column {
                            Artwork(
                                audiobookArtworkUrl(state.serverUrl, book.id),
                                null,
                                state.authToken,
                                Modifier.fillMaxWidth().aspectRatio(0.78f)
                            )
                            Column(Modifier.padding(9.dp)) {
                                Text(book.title, modifier = Modifier.height(34.dp), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(book.author ?: "Unknown author", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailScreen(state: TvUiState, viewModel: TvViewModel) {
    val screen = state.selectedArtist ?: return
    val artist = screen.artist
    val firstFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    RequestInitialFocus(if (screen.tracks.isEmpty()) backFocus else firstFocus, true)
    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding, vertical = 8.dp)) {
        DetailHeader(
            artist.name,
            "${artist.albumCount.coerceAtLeast(screen.albums.size)} albums",
            formatTrackCount(screen.tracks.size),
            backFocus,
            viewModel::navigateBack
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item("artist_hero") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(
                        artist.artPath?.let { artistArtworkUrl(state.serverUrl, it, artist.artHash) },
                        artist.name,
                        state.authToken,
                        Modifier.size(112.dp)
                    )
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(artist.name, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        if (screen.tracks.isNotEmpty()) {
                            SmallButton(
                                Icons.Default.PlayArrow,
                                "Play all",
                                { viewModel.play(screen.tracks, screen.tracks.first()) },
                                modifier = Modifier.focusRequester(firstFocus)
                            )
                        }
                    }
                }
            }
            if (screen.albums.isNotEmpty()) {
                item("artist_albums") {
                    ArtistAlbumShelf("Albums", screen.albums, state, viewModel::openAlbum)
                }
            }
            if (screen.appearsOn.isNotEmpty()) {
                item("artist_appears_on") {
                    ArtistAlbumShelf("Appears On", screen.appearsOn, state, viewModel::openAlbum)
                }
            }
            if (screen.tracks.isNotEmpty()) {
                item("artist_songs_title") {
                    Text("Songs", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                }
                itemsIndexed(screen.tracks, key = { _, track -> track.id }) { _, track ->
                    Card(
                        onClick = { viewModel.play(screen.tracks, track) },
                        onLongClick = { viewModel.openTrackActions(track) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = contentCardColors()
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(track.displayTitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.displayAlbum, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(formatPlaybackTime(track.durationMs?.toLong() ?: 0L), color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.width(12.dp))
                            Icon(Icons.Default.PlayArrow, "Play", tint = Accent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistAlbumShelf(
    title: String,
    albums: List<Album>,
    state: TvUiState,
    onClick: (Album) -> Unit
) {
    Column {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(albums, key = { index, album -> "$index:${album.displayName}" }) { _, album ->
                Card(onClick = { onClick(album) }, modifier = Modifier.width(132.dp), colors = contentCardColors()) {
                    Column {
                        Artwork(
                            album.artPath?.let { artPathUrl(state.serverUrl, it) },
                            album.displayName,
                            state.authToken,
                            Modifier.fillMaxWidth().aspectRatio(1f)
                        )
                        Column(Modifier.padding(8.dp)) {
                            Text(album.displayName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(formatTrackCount(album.trackCount), color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCollectionDetail(collection: TrackCollection, state: TvUiState, viewModel: TvViewModel) {
    val firstFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    RequestInitialFocus(if (collection.tracks.isEmpty()) backFocus else firstFocus, true)
    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding, vertical = 8.dp)) {
        DetailHeader(collection.title, collection.subtitle, formatTrackCount(collection.tracks.size), backFocus, viewModel::navigateBack)
        collection.playlist?.let { playlist ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val collaboration = state.playlistCollaboration
                val contributors = collaboration?.collaborators?.map { it.user.email }.orEmpty()
                val owner = collaboration?.owner?.email ?: playlist.ownerEmail
                Text(
                    buildString {
                        if (!owner.isNullOrBlank()) append("Owner: $owner")
                        if (contributors.isNotEmpty()) {
                            if (isNotEmpty()) append("  •  ")
                            append("Contributors: ${contributors.joinToString()}")
                        }
                    },
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist.kind == TvPlaylist.Kind.STANDARD && playlist.isOwner) {
                    SmallButton(Icons.Default.Delete, "Delete playlist", viewModel::deleteOpenPlaylist)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (collection.tracks.isEmpty()) {
            EmptyState("This collection is empty")
        } else {
            TrackRows(
                collection.tracks,
                firstFocus,
                { viewModel.play(collection.tracks, it) },
                viewModel::openTrackActions
            )
        }
    }
}

@Composable
private fun PodcastDetail(state: TvUiState, viewModel: TvViewModel) {
    val podcast = state.selectedPodcast ?: return
    val firstFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    RequestInitialFocus(if (state.podcastEpisodes.isEmpty()) backFocus else firstFocus, true)
    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding, vertical = 8.dp)) {
        DetailHeader(
            podcast.title,
            podcast.author ?: "Podcast",
            "${state.podcastEpisodes.size} episodes",
            backFocus,
            viewModel::navigateBack
        )
        Spacer(Modifier.height(10.dp))
        if (state.podcastEpisodes.isEmpty()) {
            EmptyState("No episodes found")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(state.podcastEpisodes, key = { _, episode -> episode.id }) { index, episode ->
                    EpisodeRow(
                        episode,
                        onClick = { viewModel.playEpisode(state.podcastEpisodes, episode, podcast) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiobookDetail(state: TvUiState, viewModel: TvViewModel) {
    val book = state.selectedAudiobook ?: return
    val firstFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    RequestInitialFocus(if (state.audiobookChapters.isEmpty()) backFocus else firstFocus, true)
    Column(Modifier.fillMaxSize().padding(horizontal = ScreenPadding, vertical = 8.dp)) {
        DetailHeader(
            book.title,
            book.author ?: book.narrator ?: "Audiobook",
            "${state.audiobookChapters.size} chapters",
            backFocus,
            viewModel::navigateBack
        )
        Spacer(Modifier.height(10.dp))
        if (state.audiobookChapters.isEmpty()) {
            EmptyState("No chapters found")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(state.audiobookChapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                    ChapterRow(
                        chapter,
                        onClick = { viewModel.playChapter(book, state.audiobookChapters, chapter) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    title: String,
    subtitle: String,
    count: String,
    backFocus: FocusRequester,
    onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SmallButton(
            Icons.AutoMirrored.Filled.ArrowBack,
            "Back",
            onBack,
            modifier = Modifier.focusRequester(backFocus)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(16.dp))
        Text(count, color = Muted, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun TrackRows(
    tracks: List<Track>,
    firstFocus: FocusRequester,
    onClick: (Track) -> Unit,
    onOptions: (Track) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            Card(
                onClick = { onClick(track) },
                onLongClick = { onOptions(track) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    .semantics { contentDescription = "Play ${track.displayTitle} by ${track.displayArtist}" },
                colors = contentCardColors()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatTrackNumber(track), color = Muted, modifier = Modifier.width(48.dp), fontSize = 13.sp)
                    Column(Modifier.weight(1f)) {
                        Text(track.displayTitle, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.displayArtist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.PlayArrow, "Play", tint = Accent)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = contentCardColors()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Podcasts, null, tint = if (episode.played) Muted else Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(episode.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(episodeSubtitle(episode), color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.PlayArrow, "Play", tint = Accent)
        }
    }
}

@Composable
private fun ChapterRow(chapter: AudiobookChapter, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = contentCardColors()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${chapter.position + 1}", color = Muted, modifier = Modifier.width(42.dp), fontSize = 13.sp)
            Column(Modifier.weight(1f)) {
                Text(chapter.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDuration(chapter.durationMs ?: 0L), color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.PlayArrow, "Play", tint = Accent)
        }
    }
}

@Composable
private fun SearchContent(
    state: TvUiState,
    viewModel: TvViewModel,
    firstResultFocus: FocusRequester,
    searchFocus: FocusRequester,
    onOpenSidebar: () -> Unit
) {
    val results = state.searchResults
    val tracks = results?.hits.orEmpty()
    val podcasts = results?.podcasts.orEmpty()
    val episodes = results?.podcastEpisodes.orEmpty()
    val playlists = results?.playlists.orEmpty()
    val hasResults = tracks.isNotEmpty() || podcasts.isNotEmpty() || episodes.isNotEmpty() || playlists.isNotEmpty()
    var firstAssigned = false

    when {
        state.searchQuery.isBlank() -> EmptyState("Search songs, playlists, podcasts, and episodes from the top bar")
        !state.loading && state.searchedQuery == state.searchQuery.trim() && !hasResults -> EmptyState("No matching results")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (tracks.isNotEmpty()) {
                val assign = !firstAssigned
                firstAssigned = true
                item("search_tracks") {
                    Column {
                        Text("Songs", modifier = Modifier.padding(horizontal = ScreenPadding), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                                TrackCard(
                                    track,
                                    state.serverUrl,
                                    state.authToken,
                                    { viewModel.play(tracks, it) },
                                    138.dp,
                                    titleLines = 1,
                                    onOptions = viewModel::openTrackActions,
                                    modifier = Modifier
                                        .then(if (assign && index == 0) Modifier.focusRequester(firstResultFocus) else Modifier)
                                        .then(
                                            if (index == 0) Modifier.dpadEdges(
                                                onLeft = onOpenSidebar,
                                                onUp = { searchFocus.requestFocus() }
                                            ) else Modifier
                                        )
                                )
                            }
                        }
                    }
                }
            }
            if (podcasts.isNotEmpty()) {
                val assign = !firstAssigned
                firstAssigned = true
                item("search_podcasts") {
                    PodcastShelf(
                        "Podcasts",
                        podcasts,
                        state,
                        if (assign) firstResultFocus else null,
                        searchFocus,
                        onOpenSidebar,
                        viewModel::openPodcast
                    )
                }
            }
            if (episodes.isNotEmpty()) {
                val assign = !firstAssigned
                firstAssigned = true
                item("search_episodes") {
                    EpisodeShelf(
                        "Episodes",
                        episodes,
                        state,
                        if (assign) firstResultFocus else null,
                        searchFocus,
                        onOpenSidebar
                    ) { viewModel.playEpisode(episodes, it) }
                }
            }
            if (playlists.isNotEmpty()) {
                val assign = !firstAssigned
                item("search_playlists") {
                    Column {
                        Text("Playlists", modifier = Modifier.padding(horizontal = ScreenPadding), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(playlists, key = { _, playlist -> "${playlist.kind}:${playlist.id}" }) { index, playlist ->
                                Button(
                                    onClick = {
                                        val known = state.playlists.firstOrNull { it.id == playlist.id }
                                            ?: TvPlaylist(
                                                playlist.id,
                                                playlist.name,
                                                0,
                                                if (playlist.kind == "smart") TvPlaylist.Kind.SMART else TvPlaylist.Kind.STANDARD
                                            )
                                        viewModel.openPlaylist(known)
                                    },
                                    modifier = Modifier
                                        .width(210.dp)
                                        .then(if (assign && index == 0) Modifier.focusRequester(firstResultFocus) else Modifier)
                                        .then(
                                            if (index == 0) Modifier.dpadEdges(
                                                onLeft = onOpenSidebar,
                                                onUp = { searchFocus.requestFocus() }
                                            ) else Modifier
                                        ),
                                    colors = secondaryButtonColors(),
                                    contentPadding = PaddingValues(14.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = Accent)
                                    Spacer(Modifier.width(10.dp))
                                    Text(playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingScreen(state: TvUiState, viewModel: TvViewModel) {
    val playback = state.displayedPlayback
    val item = playback.item ?: return
    val playFocus = remember { FocusRequester() }
    RequestInitialFocus(playFocus, true)
    val progress = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", viewModel::closeNowPlaying)
            Spacer(Modifier.width(14.dp))
            Text("Now Playing", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                if (state.controllingRemote) {
                    "On ${state.selectedConnectDevice?.name} · ${state.selectedConnectDevice?.state?.queueLength ?: 0} queued"
                } else if (playback.currentIndex >= 0) {
                    "${playback.currentIndex + 1} of ${playback.queue.size}"
                } else {
                    "${playback.queue.size} queued"
                },
                color = Muted,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Artwork(
                item.artworkUrl,
                item.title,
                state.authToken,
                Modifier.size(278.dp)
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    item.title,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                Text(item.artist, color = Accent, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.album, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(22.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = Accent,
                    trackColor = SurfaceRaised
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatPlaybackTime(playback.positionMs), color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatPlaybackTime(playback.durationMs), color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerButton(Icons.Default.SkipPrevious, "Previous", viewModel::previous, enabled = playback.hasPrevious)
                    PlayerButton(Icons.Default.Replay10, "Back 10 seconds", viewModel::seekBackward)
                    PlayerButton(
                        if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playback.isPlaying) "Pause" else "Play",
                        viewModel::togglePlayPause,
                        primary = true,
                        modifier = Modifier.focusRequester(playFocus)
                    )
                    PlayerButton(Icons.Default.Forward10, "Forward 10 seconds", viewModel::seekForward)
                    PlayerButton(Icons.Default.SkipNext, "Next", viewModel::next, enabled = playback.hasNext)
                }
                Spacer(Modifier.height(12.dp))
                if (!state.controllingRemote) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SmallButton(
                            Icons.Default.Shuffle,
                            if (playback.shuffleEnabled) "Shuffle on" else "Shuffle off",
                            viewModel::toggleShuffle
                        )
                        SmallButton(
                            if (playback.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            repeatModeLabel(playback.repeatMode),
                            viewModel::cycleRepeatMode
                        )
                    }
                }
                if (item.kind == PlaybackKind.MUSIC) {
                    Spacer(Modifier.height(9.dp))
                    SmallButton(Icons.Default.MoreVert, "Song actions", viewModel::openCurrentTrackActions)
                }
            }
            Column(Modifier.width(260.dp).fillMaxHeight()) {
                Text("Queue", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(playback.queue, key = { index, queued -> "$index:${queued.mediaId}" }) { index, queued ->
                        Card(
                            onClick = { viewModel.playQueueIndex(index) },
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "${index + 1}. ${queued.title}, ${queued.artist}"
                            },
                            colors = CardDefaults.colors(
                                containerColor = if (index == playback.currentIndex) Accent.copy(alpha = 0.2f) else SurfaceRaised,
                                contentColor = if (index == playback.currentIndex) Accent else Color.White,
                                focusedContainerColor = Accent,
                                focusedContentColor = Background
                            )
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", color = Muted, fontSize = 11.sp, modifier = Modifier.width(24.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(queued.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(queued.artist, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    playback: PlaybackSnapshot,
    authToken: String,
    onPrevious: () -> Unit,
    onSeekBackward: () -> Unit,
    onToggle: () -> Unit,
    onSeekForward: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val item = playback.item ?: return
    val progress = if (playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerHeight)
            .background(SurfaceRaised)
            .padding(horizontal = ScreenPadding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(item.artworkUrl, null, authToken, Modifier.size(58.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(190.dp)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatPlaybackTime(playback.positionMs), color = Muted, fontSize = 10.sp, modifier = Modifier.width(50.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(3.dp),
                    color = Accent,
                    trackColor = Surface
                )
                Text(formatPlaybackTime(playback.durationMs), color = Muted, fontSize = 10.sp, modifier = Modifier.width(54.dp).padding(start = 5.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        SmallButton(Icons.AutoMirrored.Filled.QueueMusic, "Queue", onOpen)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerButton(Icons.Default.SkipPrevious, "Previous", onPrevious, enabled = playback.hasPrevious)
            PlayerButton(Icons.Default.Replay10, "Back 10 seconds", onSeekBackward)
            PlayerButton(
                if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playback.isPlaying) "Pause" else "Play",
                onToggle,
                primary = true
            )
            PlayerButton(Icons.Default.Forward10, "Forward 10 seconds", onSeekForward)
            PlayerButton(Icons.Default.SkipNext, "Next", onNext, enabled = playback.hasNext)
        }
    }
}

@Composable
private fun Artwork(url: String?, description: String?, authToken: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(Color(0xFF203552), Color(0xFF132139))),
            RoundedCornerShape(8.dp)
        ),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Default.MusicNote, description, tint = Muted, modifier = Modifier.size(32.dp))
        } else {
            val context = LocalContext.current
            val request = remember(url, authToken) {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .apply {
                        if (authToken.isNotBlank()) {
                            addHeader("Authorization", "Bearer $authToken")
                            addHeader("Cookie", "mvbar_token=$authToken")
                        }
                    }
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun SmallButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.focusProperties { canFocus = enabled },
        colors = secondaryButtonColors(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(icon, label, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 14.sp)
    }
}

@Composable
private fun PlayerButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    selected: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(46.dp).focusProperties { canFocus = enabled },
        colors = if (primary || selected) actionButtonColors() else secondaryButtonColors(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(icon, label, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun actionButtonColors() = ButtonDefaults.colors(
    containerColor = Accent,
    contentColor = Background,
    focusedContainerColor = Color(0xFF8BEAFF),
    focusedContentColor = Background
)

@Composable
private fun secondaryButtonColors() = ButtonDefaults.colors(
    containerColor = Surface,
    contentColor = Color.White,
    focusedContainerColor = Accent,
    focusedContentColor = Background,
    disabledContainerColor = Surface.copy(alpha = 0.45f),
    disabledContentColor = Muted.copy(alpha = 0.45f)
)

@Composable
private fun googleButtonColors() = ButtonDefaults.colors(
    containerColor = Color.White,
    contentColor = Color(0xFF202124),
    focusedContainerColor = Color(0xFFE8F0FE),
    focusedContentColor = Color(0xFF202124),
    disabledContainerColor = Color.White.copy(alpha = 0.45f),
    disabledContentColor = Color(0xFF5F6368).copy(alpha = 0.65f)
)

@Composable
private fun contentCardColors() = CardDefaults.colors(
    containerColor = SurfaceRaised,
    focusedContainerColor = Color(0xFF2A4260)
)

@Composable
private fun ContentTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = ScreenPadding, vertical = 5.dp)) {
        Text(title, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        onClick = onDismiss,
        colors = CardDefaults.colors(
            containerColor = Color(0xFF52262A),
            focusedContainerColor = Color(0xFF71343A)
        )
    ) {
        Text(message, color = Color(0xFFFFC9C5), modifier = Modifier.padding(10.dp), fontSize = 13.sp)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(message, color = Muted, fontSize = 17.sp)
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.mvbar_wordmark),
            contentDescription = "MVBar",
            modifier = Modifier.width(230.dp).height(120.dp),
            contentScale = ContentScale.Fit
        )
        CircularProgressIndicator(color = Accent)
        Spacer(Modifier.height(16.dp))
        Text(message, color = Muted)
    }
}

@Composable
private fun RequestInitialFocus(requester: FocusRequester, enabled: Boolean) {
    LaunchedEffect(requester, enabled) {
        if (enabled) {
            withFrameNanos { }
            requester.requestFocus()
        }
    }
}

private fun Modifier.dpadEdges(
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionLeft -> onLeft?.let { it(); true } ?: false
        Key.DirectionRight -> onRight?.let { it(); true } ?: false
        Key.DirectionUp -> onUp?.let { it(); true } ?: false
        Key.DirectionDown -> onDown?.let { it(); true } ?: false
        else -> false
    }
}

private fun hasSearchResults(state: TvUiState): Boolean = state.searchResults?.let {
    it.hits.isNotEmpty() || it.playlists.isNotEmpty() || it.podcasts.isNotEmpty() || it.podcastEpisodes.isNotEmpty()
} == true

private fun trackArtworkUrl(serverUrl: String, track: Track): String =
    track.artPath?.let { artPathUrl(serverUrl, it) }
        ?: "${serverUrl.trimEnd('/')}/api/library/tracks/${track.id}/art"

private fun artPathUrl(serverUrl: String, path: String): String =
    "${serverUrl.trimEnd('/')}/api/art/${Uri.encode(path, "/")}"

private fun bucketArtworkUrl(serverUrl: String, path: String, hash: String?): String =
    artPathUrl(serverUrl, path) + hash?.takeIf { it.isNotBlank() }?.let { "?h=${Uri.encode(it)}" }.orEmpty()

private fun artistArtworkUrl(serverUrl: String, path: String, hash: String?): String =
    bucketArtworkUrl(serverUrl, path, hash)

private fun podcastArtworkUrl(serverUrl: String, podcast: Podcast): String =
    podcast.imagePath?.let { "${serverUrl.trimEnd('/')}/api/podcast-art/${Uri.encode(it, "/")}" }
        ?: "${serverUrl.trimEnd('/')}/api/podcasts/${podcast.id}/art"

private fun episodeArtworkUrl(serverUrl: String, episode: Episode): String =
    episode.imagePath?.let { "${serverUrl.trimEnd('/')}/api/podcast-art/${Uri.encode(it, "/")}" }
        ?: episode.podcastImagePath?.let { "${serverUrl.trimEnd('/')}/api/podcast-art/${Uri.encode(it, "/")}" }
        ?: "${serverUrl.trimEnd('/')}/api/podcasts/episodes/${episode.id}/art"

private fun audiobookArtworkUrl(serverUrl: String, audiobookId: Int): String =
    "${serverUrl.trimEnd('/')}/api/audiobook-art/$audiobookId"

private fun playlistSubtitle(playlist: TvPlaylist): String = when {
    playlist.kind == TvPlaylist.Kind.SMART -> "Smart playlist"
    playlist.collaborative -> "Collaborative • ${playlist.itemCount} songs"
    playlist.itemCount == 1 -> "1 song"
    else -> "${playlist.itemCount} songs"
}

private fun episodeSubtitle(episode: Episode): String = buildString {
    episode.podcastTitle?.takeIf { it.isNotBlank() }?.let(::append)
    if (episode.positionMs > 0 && (episode.durationMs ?: 0L) > 0) {
        if (isNotEmpty()) append(" • ")
        append(((episode.positionMs * 100) / (episode.durationMs ?: 1L)).coerceIn(0, 100))
        append("% played")
    } else if ((episode.durationMs ?: 0L) > 0) {
        if (isNotEmpty()) append(" • ")
        append(formatDuration(episode.durationMs ?: 0L))
    }
    episode.publishedAt?.take(10)?.takeIf { it.isNotBlank() }?.let {
        if (isNotEmpty()) append(" • ")
        append(it)
    }
}

internal fun formatTrackCount(count: Int): String = if (count == 1) "1 track" else "$count tracks"

internal fun formatTrackNumber(track: Track): String = when {
    track.discNumber != null && track.trackNumber != null -> "${track.discNumber}.${track.trackNumber}"
    track.trackNumber != null -> track.trackNumber.toString()
    else -> "•"
}

internal fun formatPlaybackTime(milliseconds: Long): String = formatDuration(milliseconds)

internal fun repeatModeLabel(mode: Int): String = when (mode) {
    Player.REPEAT_MODE_ALL -> "Repeat all"
    Player.REPEAT_MODE_ONE -> "Repeat one"
    else -> "Repeat off"
}

internal fun isGridLeftEdge(index: Int, columns: Int): Boolean = columns > 0 && index >= 0 && index % columns == 0

internal fun isGridTopRow(index: Int, columns: Int): Boolean = columns > 0 && index in 0 until columns

private fun formatDuration(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val totalSeconds = (milliseconds / 1_000.0).roundToInt().coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "$hours:%02d:%02d".format(minutes, seconds)
    else "$minutes:%02d".format(seconds)
}

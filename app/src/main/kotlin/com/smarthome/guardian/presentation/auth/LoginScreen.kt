package com.smarthome.guardian.presentation.auth

import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── 品牌色彩（深色科技感）────────────────────────────────────────────────────
private val BackgroundStart  = Color(0xFF0A0E1A)
private val BackgroundEnd    = Color(0xFF121827)
private val PrimaryBlue      = Color(0xFF00D4FF)
private val SecondaryPurple  = Color(0xFF7B2FBE)
private val ErrorRed         = Color(0xFFFF4444)
private val TextSecondary    = Color(0xFF8899AA)

/**
 * 登入畫面。
 *
 * 支援三種驗證方式：
 * 1. Email + 密碼
 * 2. 生物辨識
 * 3. PIN 碼（備援）
 *
 * @param onAuthenticated 驗證成功後的導航回呼
 * @param viewModel       由 Hilt 提供的 [AuthViewModel]
 */
@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val context     = LocalContext.current
    val authState   by viewModel.authState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // FLAG_SECURE：防止螢幕截圖與錄影
    LaunchedEffect(Unit) {
        (context as? FragmentActivity)?.window
            ?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // 狀態監聽：成功 → 導航；錯誤 → Snackbar
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.Authenticated -> onAuthenticated()
            is AuthState.Error -> snackbarHostState.showSnackbar(
                message     = state.message,
                duration    = SnackbarDuration.Short,
            )
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData    = data,
                    containerColor  = ErrorRed,
                    contentColor    = Color.White,
                )
            }
        },
        containerColor = BackgroundStart,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(BackgroundStart, BackgroundEnd))
                )
                .padding(padding),
        ) {
            LoginContent(
                authState = authState,
                onLoginWithCredentials = viewModel::loginWithCredentials,
                onLoginWithBiometric = {
                    (context as? FragmentActivity)?.let { viewModel.loginWithBiometric(it) }
                },
                onLoginWithPin = viewModel::switchToPin,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun LoginContent(
    authState: AuthState,
    onLoginWithCredentials: (String, String) -> Unit,
    onLoginWithBiometric: () -> Unit,
    onLoginWithPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email          by remember { mutableStateOf("") }
    var password       by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var emailError     by remember { mutableStateOf<String?>(null) }
    val focusManager   = LocalFocusManager.current
    val isLoading      = authState is AuthState.Loading

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(40.dp))

        // ── Logo ──────────────────────────────────────────────────────────────
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = PrimaryBlue,
            modifier = Modifier.size(72.dp),
        )

        Text(
            text       = "SmartHome Guardian",
            color      = PrimaryBlue,
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text     = "智慧家庭安全守護",
            color    = TextSecondary,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(24.dp))

        // ── Email 欄位 ────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = email,
            onValueChange = { email = it; emailError = null },
            label         = { Text("Email") },
            leadingIcon   = { Icon(Icons.Filled.Email, null, tint = PrimaryBlue) },
            isError       = emailError != null,
            supportingText = emailError?.let { { Text(it, color = ErrorRed) } },
            singleLine    = true,
            enabled       = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction    = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            colors = outlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        // ── 密碼欄位 ──────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = password,
            onValueChange = { password = it },
            label         = { Text("密碼") },
            leadingIcon   = { Icon(Icons.Filled.Lock, null, tint = PrimaryBlue) },
            trailingIcon  = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                                      else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "隱藏密碼" else "顯示密碼",
                        tint = TextSecondary,
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            singleLine  = true,
            enabled     = !isLoading,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (email.isNotBlank() && password.isNotBlank()) {
                        onLoginWithCredentials(email.trim(), password)
                    }
                }
            ),
            colors   = outlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        // ── 登入按鈕 ──────────────────────────────────────────────────────────
        Button(
            onClick = {
                focusManager.clearFocus()
                emailError = if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
                    "請輸入有效的 Email 格式" else null
                if (emailError == null && password.isNotBlank()) {
                    onLoginWithCredentials(email.trim(), password)
                }
            },
            enabled  = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape  = RoundedCornerShape(8.dp),
        ) {
            AnimatedContent(targetState = isLoading, label = "login_btn") { loading ->
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color    = Color.Black,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("登入", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── 分隔線 ────────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.3f))
            Text("  或  ", color = TextSecondary, fontSize = 12.sp)
            HorizontalDivider(modifier = Modifier.weight(1f), color = TextSecondary.copy(alpha = 0.3f))
        }

        // ── 生物辨識按鈕 ──────────────────────────────────────────────────────
        OutlinedButton(
            onClick  = onLoginWithBiometric,
            enabled  = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue),
            shape  = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Filled.Fingerprint, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("使用生物辨識登入")
        }

        // ── PIN 備援 ──────────────────────────────────────────────────────────
        TextButton(
            onClick  = onLoginWithPin,
            enabled  = !isLoading,
        ) {
            Icon(Icons.Filled.Pin, null,
                tint = SecondaryPurple,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("使用 PIN 碼登入", color = SecondaryPurple, fontSize = 14.sp)
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = PrimaryBlue,
    unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
    focusedLabelColor    = PrimaryBlue,
    unfocusedLabelColor  = TextSecondary,
    cursorColor          = PrimaryBlue,
    focusedTextColor     = Color.White,
    unfocusedTextColor   = Color.White,
)

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A, name = "LoginScreen")
@Composable
private fun LoginScreenPreview() {
    MaterialTheme {
        LoginContent(
            authState              = AuthState.Idle,
            onLoginWithCredentials = { _, _ -> },
            onLoginWithBiometric   = {},
            onLoginWithPin         = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0E1A, name = "LoginScreen Loading")
@Composable
private fun LoginScreenLoadingPreview() {
    MaterialTheme {
        LoginContent(
            authState              = AuthState.Loading,
            onLoginWithCredentials = { _, _ -> },
            onLoginWithBiometric   = {},
            onLoginWithPin         = {},
        )
    }
}

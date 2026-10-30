package cl.kcorp.syncpass.ui.login

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.kcorp.syncpass.data.auth.AuthTokens
import cl.kcorp.syncpass.data.auth.CognitoAuthManager
import cl.kcorp.syncpass.data.auth.NewPasswordRequiredException
import cl.kcorp.syncpass.ui.theme.BrandPrimary
import cl.kcorp.syncpass.ui.theme.BrandPrimaryDark
import cl.kcorp.syncpass.ui.theme.SuccessSoftLight
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (AuthTokens) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { CognitoAuthManager(context) }
    val clipboard = LocalClipboard.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("+56912345678") }
    var passwordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }

    var isNewPasswordRequired by remember { mutableStateOf(false) }
    var wasPasswordChangedSuccessfully by remember { mutableStateOf(false) }
    var isNameRequired by remember { mutableStateOf(false) }
    var isPhoneRequired by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var authTokens by remember { mutableStateOf<AuthTokens?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 30.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF7A70), BrandPrimary, BrandPrimaryDark),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "S",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "SyncPass",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = "GESTIÓN COMUNITARIA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(modifier = Modifier.height(22.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (authTokens != null) {
                        AuthSuccessContent(
                            tokens = authTokens!!,
                            wasPasswordChangedSuccessfully = wasPasswordChangedSuccessfully,
                            onCopyAccessToken = {
                                authTokens?.accessToken?.let { token ->
                                    coroutineScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("Access Token", token)),
                                        )
                                    }
                                }
                            },
                            onContinue = { authTokens?.let { onLoginSuccess(it) } },
                            onLogout = {
                                authTokens = null
                                errorMessage = null
                                isNewPasswordRequired = false
                                wasPasswordChangedSuccessfully = false
                                password = ""
                                newPassword = ""
                            },
                        )
                    } else {
                        Text(
                            text = if (isNewPasswordRequired) "Actualiza tu contraseña" else "Bienvenido de nuevo",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (isNewPasswordRequired) {
                                "Tu cuenta requiere una contraseña permanente para continuar."
                            } else {
                                "Ingresa con tu cuenta institucional para continuar."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
                        )

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Usuario o email") },
                            singleLine = true,
                            enabled = !isNewPasswordRequired,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = {
                                Text(
                                    if (isNewPasswordRequired) "Contraseña actual (temporal)" else "Contraseña",
                                )
                            },
                            singleLine = true,
                            enabled = !isNewPasswordRequired,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (isNewPasswordRequired) ImeAction.Next else ImeAction.Done,
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Text(if (passwordVisible) "Ocultar" else "Ver", fontSize = 10.sp)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (isNewPasswordRequired) {
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("Nueva contraseña permanente") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                visualTransformation = if (newPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = if (isNameRequired || isPhoneRequired) {
                                        ImeAction.Next
                                    } else {
                                        ImeAction.Done
                                    },
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                        Text(if (newPasswordVisible) "Ocultar" else "Ver", fontSize = 10.sp)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (isNameRequired) {
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("Nombre completo") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = if (isPhoneRequired) ImeAction.Next else ImeAction.Done,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (isPhoneRequired) {
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = phoneNumber,
                                    onValueChange = { phoneNumber = it },
                                    label = { Text("Teléfono (+569...)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp),
                                color = if (isNewPasswordRequired) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.errorContainer
                                },
                                contentColor = if (isNewPasswordRequired) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                shape = RoundedCornerShape(11.dp),
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    errorMessage = "Por favor ingresa usuario y contraseña"
                                    return@Button
                                }

                                if (isNewPasswordRequired && newPassword.isBlank()) {
                                    errorMessage = "Ingresa tu nueva contraseña para actualizar la cuenta"
                                    return@Button
                                }

                                isLoading = true
                                errorMessage = null

                                coroutineScope.launch {
                                    val result = authManager.login(
                                        username = username.trim(),
                                        password = password.trim(),
                                        newPassword = if (isNewPasswordRequired) newPassword.trim() else null,
                                        fullName = if (isNameRequired) fullName.trim() else null,
                                        phoneNumber = if (isPhoneRequired) phoneNumber.trim() else null,
                                    )
                                    isLoading = false
                                    result.onSuccess { tokens ->
                                        authTokens = tokens
                                        wasPasswordChangedSuccessfully = isNewPasswordRequired
                                        isNewPasswordRequired = false
                                        isNameRequired = false
                                        isPhoneRequired = false
                                        onLoginSuccess(tokens)
                                    }.onFailure { ex ->
                                        if (ex is NewPasswordRequiredException) {
                                            isNewPasswordRequired = true
                                            isNameRequired = ex.requiredAttributes.contains("name")
                                            isPhoneRequired = ex.requiredAttributes.contains("phone_number")
                                            errorMessage = ex.message
                                        } else {
                                            errorMessage = ex.localizedMessage
                                                ?: "Error al iniciar sesión en Cognito"
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    if (isNewPasswordRequired) {
                                        "Establecer nueva contraseña"
                                    } else {
                                        "Iniciar sesión"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "Acceso seguro mediante AWS Cognito",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = "Desarrollado por Duoc UC",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AuthSuccessContent(
    tokens: AuthTokens,
    wasPasswordChangedSuccessfully: Boolean,
    onCopyAccessToken: () -> Unit,
    onContinue: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(SuccessSoftLight),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", color = Color(0xFF159A63), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = if (wasPasswordChangedSuccessfully) {
                "Contraseña actualizada"
            } else {
                "Sesión iniciada"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Bienvenido, ${tokens.username}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        Text(
            text = "Access token",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = tokens.accessToken,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 3,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        )
        TextButton(onClick = onCopyAccessToken, modifier = Modifier.align(Alignment.Start)) {
            Text("Copiar token", style = MaterialTheme.typography.labelMedium)
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Continuar", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(onClick = onLogout) {
            Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
        }
    }
}

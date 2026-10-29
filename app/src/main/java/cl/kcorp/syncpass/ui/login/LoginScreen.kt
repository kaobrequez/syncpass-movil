package cl.kcorp.syncpass.ui.login

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SyncPass",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = if (authTokens != null) "Sesión Activa" else "Iniciar Sesión con AWS Cognito",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (authTokens != null) {
                // PANTALLA DE SESIÓN ACTIVA Y ÉXITO
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = if (wasPasswordChangedSuccessfully)
                                "¡Cambio de Contraseña Exitoso!"
                            else
                                "¡Inicio de Sesión Exitoso!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Estás dentro de la sesión del usuario: ${authTokens?.username}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = "Estado en Cognito: CONFIRMED (Confirmado)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Access Token (JWT para tu API/EC2):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = authTokens?.accessToken ?: "",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 4,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "ID Token (Identidad de Usuario):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = authTokens?.idToken ?: "",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 4,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = {
                                authTokens?.accessToken?.let { token ->
                                    coroutineScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText("Access Token", token)),
                                        )
                                    }
                                }
                            }) {
                                Text("Copiar Access Token")
                            }

                            Button(onClick = {
                                authTokens?.let { onLoginSuccess(it) }
                            }) {
                                Text("Continuar")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        authTokens = null
                        errorMessage = null
                        isNewPasswordRequired = false
                        wasPasswordChangedSuccessfully = false
                        password = ""
                        newPassword = ""
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cerrar Sesión")
                }

            } else {
                // FORMULARIO DE INICIO DE SESIÓN / CAMBIO OBLIGATORIO DE CLAVE
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario o Email") },
                    singleLine = true,
                    enabled = !isNewPasswordRequired,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isNewPasswordRequired) "Contraseña Actual (Temporal)" else "Contraseña") },
                    singleLine = true,
                    enabled = !isNewPasswordRequired,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (isNewPasswordRequired) ImeAction.Next else ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "🙈" else "👁️")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isNewPasswordRequired) {
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Nueva Contraseña Permanente") },
                        singleLine = true,
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (isNameRequired || isPhoneRequired) ImeAction.Next else ImeAction.Done
                        ),
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Text(if (newPasswordVisible) "🙈" else "👁️")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isNameRequired) {
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Nombre Completo") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = if (isPhoneRequired) ImeAction.Next else ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isPhoneRequired) {
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Teléfono (+569...)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNewPasswordRequired)
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = if (isNewPasswordRequired)
                                MaterialTheme.colorScheme.onTertiaryContainer
                            else
                                MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp
                        )
                    }
                }

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
                                phoneNumber = if (isPhoneRequired) phoneNumber.trim() else null
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
                                    errorMessage = ex.localizedMessage ?: "Error al iniciar sesión en Cognito"
                                }
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (isNewPasswordRequired) "Establecer Nueva Contraseña" else "Iniciar Sesión",
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
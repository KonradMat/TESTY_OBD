package com.obdreader.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Ekran Auth (Login / Rejestracja) ─────────────────────────────────────────

@Composable
fun AuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (email: String, firstName: String, lastName: String, password: String) -> Unit,
    onGuestContinue: () -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }

    // Pola formularza
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    // Walidacja
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current

    // Reset błędów przy zmianie trybu
    LaunchedEffect(isRegisterMode) {
        emailError = null; passwordError = null; nameError = null
        passwordConfirm = ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF060D1A), Color(0xFF0A1628), DarkBackground)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // ── Logo / tytuł ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(AccentGreen.copy(0.3f), Color.Transparent))
                    )
                    .border(1.5.dp, AccentGreen.copy(0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "OBD2 Reader",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary,
                letterSpacing = 0.5.sp
            )
            Text(
                "Diagnostyka pojazdu",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(Modifier.height(36.dp))

            // ── Przełącznik Login / Rejestracja ───────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBackground)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Logowanie" to false, "Rejestracja" to true).forEach { (label, isReg) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isRegisterMode == isReg) AccentGreen.copy(0.15f)
                                else Color.Transparent
                            )
                            .border(
                                if (isRegisterMode == isReg) BorderStroke(1.dp, AccentGreen.copy(0.5f))
                                else BorderStroke(0.dp, Color.Transparent),
                                RoundedCornerShape(9.dp)
                            )
                            .clickable { isRegisterMode = isReg }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            fontWeight = if (isRegisterMode == isReg) FontWeight.Bold else FontWeight.Normal,
                            color = if (isRegisterMode == isReg) AccentGreen else TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Formularz ─────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Imię + Nazwisko (tylko rejestracja)
                    AnimatedVisibility(
                        visible = isRegisterMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AuthTextField(
                                    value = firstName,
                                    onValueChange = { firstName = it; nameError = null },
                                    label = "Imię",
                                    error = if (nameError != null && firstName.isBlank()) nameError else null,
                                    modifier = Modifier.weight(1f),
                                    imeAction = ImeAction.Next,
                                    onNext = { focusManager.moveFocus(FocusDirection.Right) }
                                )
                                AuthTextField(
                                    value = lastName,
                                    onValueChange = { lastName = it; nameError = null },
                                    label = "Nazwisko",
                                    modifier = Modifier.weight(1f),
                                    imeAction = ImeAction.Next,
                                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                )
                            }
                        }
                    }

                    // Email
                    AuthTextField(
                        value = email,
                        onValueChange = { email = it; emailError = null },
                        label = "Email",
                        error = emailError,
                        keyboardType = KeyboardType.Email,
                        leadingIcon = {
                            Icon(Icons.Default.Email, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        },
                        imeAction = ImeAction.Next,
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )

                    // Hasło
                    AuthTextField(
                        value = password,
                        onValueChange = { password = it; passwordError = null },
                        label = "Hasło",
                        error = passwordError,
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null, tint = TextSecondary, modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        imeAction = if (isRegisterMode) ImeAction.Next else ImeAction.Done,
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                        onDone = {
                            focusManager.clearFocus()
                            if (!isRegisterMode) {
                                val err = validateLogin(email, password)
                                if (err == null) onLogin(email, password)
                                else emailError = err
                            }
                        }
                    )

                    // Potwierdź hasło (tylko rejestracja)
                    AnimatedVisibility(
                        visible = isRegisterMode,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        AuthTextField(
                            value = passwordConfirm,
                            onValueChange = { passwordConfirm = it; passwordError = null },
                            label = "Potwierdź hasło",
                            error = if (passwordError != null && passwordConfirm != password) passwordError else null,
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(Icons.Default.LockOpen, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            },
                            imeAction = ImeAction.Done,
                            onDone = { focusManager.clearFocus() }
                        )
                    }

                    // Błąd z serwera
                    if (errorMessage != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(AccentRed.copy(0.1f))
                                .border(1.dp, AccentRed.copy(0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(16.dp))
                            Text(errorMessage, fontSize = 13.sp, color = AccentRed)
                        }
                    }

                    // Przycisk główny
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (isRegisterMode) {
                                val err = validateRegister(email, firstName, lastName, password, passwordConfirm)
                                when (err?.first) {
                                    "email" -> emailError = err.second
                                    "password" -> passwordError = err.second
                                    "name" -> nameError = err.second
                                    null -> onRegister(email, firstName, lastName, password)
                                }
                            } else {
                                val err = validateLogin(email, password)
                                if (err != null) emailError = err
                                else onLogin(email, password)
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            disabledContainerColor = AccentGreen.copy(0.4f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                if (isRegisterMode) "Utwórz konto" else "Zaloguj się",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Separator ─────────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Divider(modifier = Modifier.weight(1f), color = TextSecondary.copy(0.2f))
                Text(
                    "  lub  ",
                    fontSize = 12.sp,
                    color = TextSecondary.copy(0.5f)
                )
                Divider(modifier = Modifier.weight(1f), color = TextSecondary.copy(0.2f))
            }

            Spacer(Modifier.height(16.dp))

            // ── Przycisk gościa ────────────────────────────────────────────────
            OutlinedButton(
                onClick = onGuestContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, TextSecondary.copy(0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) {
                Icon(Icons.Default.PersonOutline, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Przejdź bez logowania", fontSize = 14.sp)
            }

            Spacer(Modifier.height(12.dp))

            Text(
                "Bez konta dane telemetryczne nie będą synchronizowane z serwerem",
                fontSize = 11.sp,
                color = TextSecondary.copy(0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Reużywalny TextField ──────────────────────────────────────────────────────

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onNext: (() -> Unit)? = null,
    onDone: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 13.sp) },
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            visualTransformation = visualTransformation,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { onNext?.invoke() },
                onDone = { onDone?.invoke() }
            ),
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGreen,
                unfocusedBorderColor = TextSecondary.copy(0.25f),
                errorBorderColor = AccentRed,
                focusedLabelColor = AccentGreen,
                unfocusedLabelColor = TextSecondary,
                errorLabelColor = AccentRed,
                cursorColor = AccentGreen,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                errorTextColor = TextPrimary
            )
        )
        if (error != null) {
            Text(
                error,
                fontSize = 11.sp,
                color = AccentRed,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

// ─── Walidacja ────────────────────────────────────────────────────────────────

private fun validateLogin(email: String, password: String): String? {
    if (email.isBlank()) return "Podaj email"
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return "Nieprawidłowy format email"
    if (password.isBlank()) return "Podaj hasło"
    return null
}

private fun validateRegister(
    email: String,
    firstName: String,
    lastName: String,
    password: String,
    passwordConfirm: String
): Pair<String, String>? {
    if (firstName.isBlank() || lastName.isBlank()) return Pair("name", "Podaj imię i nazwisko")
    if (email.isBlank()) return Pair("email", "Podaj email")
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) return Pair("email", "Nieprawidłowy format email")
    if (password.length < 6) return Pair("password", "Hasło musi mieć min. 6 znaków")
    if (password != passwordConfirm) return Pair("password", "Hasła nie są identyczne")
    return null
}
package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesContaScreen(
    aoVoltar: () -> Unit,
    aoSairDaConta: () -> Unit
) {
    var mostrarConfirmarSairDaConta by remember { mutableStateOf(false) }
    var mostrarDialogoSenhaEmail by remember { mutableStateOf(false) }
    val usuarioAtual = FirebaseAuth.getInstance().currentUser
    val jaTemLoginPorEmail = usuarioAtual?.providerData?.any {
        it.providerId == EmailAuthProvider.PROVIDER_ID
    } ?: false
    val escopo = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Conta") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    if (!usuarioAtual?.displayName.isNullOrBlank()) {
                        Text(usuarioAtual?.displayName ?: "", style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        usuarioAtual?.email ?: "E-mail não disponível",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (jaTemLoginPorEmail)
                    "Você também pode entrar com e-mail e senha, além do Google."
                else
                    "Hoje você só consegue entrar com a conta Google. Defina uma senha " +
                        "pra também poder entrar com e-mail e senha, sem depender do Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { mostrarDialogoSenhaEmail = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (jaTemLoginPorEmail) "Alterar senha de acesso por e-mail" else "Definir senha de acesso por e-mail")
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { mostrarConfirmarSairDaConta = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sair da conta")
            }
        }
    }

    if (mostrarConfirmarSairDaConta) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarSairDaConta = false },
            title = { Text("Sair da conta") },
            text = { Text("Você vai precisar entrar de novo com sua conta Google pra usar o app.") },
            confirmButton = {
                TextButton(onClick = {
                    mostrarConfirmarSairDaConta = false
                    aoSairDaConta()
                }) { Text("Sair") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmarSairDaConta = false }) { Text("Cancelar") }
            }
        )
    }

    if (mostrarDialogoSenhaEmail) {
        DialogoSenhaEmail(
            emailAtual = usuarioAtual?.email ?: "",
            jaTemLoginPorEmail = jaTemLoginPorEmail,
            escopo = escopo,
            onDismiss = { mostrarDialogoSenhaEmail = false }
        )
    }
}

private fun mensagemErroSenha(e: Exception): String = when (e) {
    is FirebaseAuthWeakPasswordException ->
        "Senha muito fraca. Use pelo menos 6 caracteres."
    is FirebaseAuthInvalidCredentialsException ->
        "E-mail em formato inválido."
    is FirebaseAuthUserCollisionException ->
        "Já existe outra conta usando esse e-mail. Use um e-mail diferente, ou entre " +
            "com esse e-mail e a senha dele diretamente na tela de login."
    is FirebaseAuthRecentLoginRequiredException ->
        "Por segurança, saia da conta e entre de novo com o Google antes de alterar a senha."
    else -> "Não foi possível salvar: ${e.message ?: "erro desconhecido"}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogoSenhaEmail(
    emailAtual: String,
    jaTemLoginPorEmail: Boolean,
    escopo: kotlinx.coroutines.CoroutineScope,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf(emailAtual) }
    var senha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }
    var mostrarSenha by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(false) }
    var erroLocal by remember { mutableStateOf<String?>(null) }
    var sucesso by remember { mutableStateOf(false) }

    fun salvar() {
        erroLocal = null
        if (email.isBlank()) {
            erroLocal = "Digite o e-mail."
            return
        }
        if (senha.length < 6) {
            erroLocal = "A senha precisa ter pelo menos 6 caracteres."
            return
        }
        if (senha != confirmarSenha) {
            erroLocal = "As senhas digitadas são diferentes."
            return
        }
        carregando = true
        escopo.launch {
            try {
                val usuario = FirebaseAuth.getInstance().currentUser
                    ?: error("Sessão expirada. Saia e entre de novo.")
                if (jaTemLoginPorEmail) {
                    // já tinha login por e-mail — isso aqui troca a senha
                    usuario.updatePassword(senha).await()
                } else {
                    // ainda não tinha — vincula e-mail/senha à MESMA conta que já está
                    // logada com Google (não cria uma conta nova/separada)
                    val credential = EmailAuthProvider.getCredential(email.trim(), senha)
                    usuario.linkWithCredential(credential).await()
                }
                sucesso = true
            } catch (e: Exception) {
                erroLocal = mensagemErroSenha(e)
            } finally {
                carregando = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (jaTemLoginPorEmail) "Alterar senha" else "Definir senha de acesso") },
        text = {
            if (sucesso) {
                Text(
                    if (jaTemLoginPorEmail)
                        "Senha alterada! Da próxima vez pode entrar com e-mail e senha também."
                    else
                        "Pronto! Agora você pode entrar tanto pelo Google quanto com esse e-mail e senha."
                )
            } else {
                Column {
                    if (!jaTemLoginPorEmail) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("E-mail") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            enabled = !carregando,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = senha,
                        onValueChange = { senha = it },
                        label = { Text("Senha nova") },
                        singleLine = true,
                        enabled = !carregando,
                        visualTransformation = if (mostrarSenha) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { mostrarSenha = !mostrarSenha }) {
                                Text(if (mostrarSenha) "Ocultar" else "Ver")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmarSenha,
                        onValueChange = { confirmarSenha = it },
                        label = { Text("Confirmar senha") },
                        singleLine = true,
                        enabled = !carregando,
                        visualTransformation = if (mostrarSenha) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (erroLocal != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(erroLocal!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (carregando) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (sucesso) {
                TextButton(onClick = onDismiss) { Text("Fechar") }
            } else {
                TextButton(onClick = { salvar() }, enabled = !carregando) { Text("Salvar") }
            }
        },
        dismissButton = {
            if (!sucesso) {
                TextButton(onClick = onDismiss, enabled = !carregando) { Text("Cancelar") }
            }
        }
    )
}

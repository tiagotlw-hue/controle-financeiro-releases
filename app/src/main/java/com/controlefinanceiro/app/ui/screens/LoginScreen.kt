package com.controlefinanceiro.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

@Composable
fun LoginScreen(webClientId: String, aoLogar: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var erro by remember { mutableStateOf<String?>(null) }
    var mensagemSucesso by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var criandoConta by remember { mutableStateOf(false) }
    var lembrarLogin by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("login_salvo", android.content.Context.MODE_PRIVATE) }

    // Ao abrir a tela, se a pessoa tinha marcado "lembrar" da última vez, já preenche
    // o e-mail sozinho — assim ela só precisa digitar a senha e tocar em "Entrar".
    // Propositalmente NÃO guarda a senha: SharedPreferences comum fica salva em texto
    // puro no aparelho, então guardar a senha aqui seria um risco de segurança real
    // (ex: dá pra ler o conteúdo com um backup do app ou num aparelho com root). O
    // login "fica lembrado de verdade" entre uma abertura e outra do app por conta do
    // próprio Firebase Auth (`FirebaseAuth.getInstance().currentUser`, checado na
    // MainActivity) — isso aqui só faz diferença depois de um logoff explícito.
    LaunchedEffect(Unit) {
        val emailSalvo = prefs.getString("email", null)
        if (!emailSalvo.isNullOrBlank()) {
            email = emailSalvo
            lembrarLogin = true
        }
    }

    fun mensagemDeErroFirebase(e: Exception): String = when (e) {
        is FirebaseAuthInvalidUserException -> "Não existe conta com esse e-mail."
        is FirebaseAuthInvalidCredentialsException -> "E-mail ou senha inválidos."
        is FirebaseAuthUserCollisionException ->
            "Já existe uma conta com esse e-mail. Toque em \"Entrar\" em vez de criar conta."
        is FirebaseAuthWeakPasswordException -> "Senha muito fraca. Use pelo menos 6 caracteres."
        else -> if (e.message?.contains("operation-not-allowed", ignoreCase = true) == true ||
            e.message?.contains("not allowed", ignoreCase = true) == true
        ) {
            "Esse provedor de login não está habilitado no projeto Firebase. Vá em " +
                    "Firebase Console > Authentication > Sign-in method e habilite-o."
        } else {
            "Falha ao autenticar: ${e.message}"
        }
    }

    // ---------- Google ----------

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // IMPORTANTE: mesmo quando resultCode não é RESULT_OK (o que inclui erros de
        // configuração, não só quando a pessoa cancela), o motivo real do problema
        // ainda vem dentro do próprio result.data — então sempre tentamos extrair
        // esse motivo, em vez de descartar a informação e mostrar uma mensagem
        // genérica sem dizer o que houve de fato.
        carregando = true
        erro = null
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken == null) {
                erro = "Não foi possível obter o idToken do Google. Verifique se o " +
                        "\"Web client ID\" configurado no app é o do tipo 3 (Web) do " +
                        "mesmo projeto Firebase, e não o do tipo 1 (Android)."
                carregando = false
                return@rememberLauncherForActivityResult
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            scope.launch {
                try {
                    withTimeout(20_000) {
                        FirebaseAuth.getInstance().signInWithCredential(credential).await()
                    }
                    aoLogar()
                } catch (e: TimeoutCancellationException) {
                    erro = "Tempo esgotado ao autenticar no Firebase. Verifique sua " +
                            "conexão com a internet e tente novamente."
                } catch (e: Exception) {
                    erro = mensagemDeErroFirebase(e)
                } finally {
                    carregando = false
                }
            }
        } catch (e: ApiException) {
            erro = when (e.statusCode) {
                10 -> "Erro de configuração (DEVELOPER_ERROR). O SHA-1 do app e/ou o " +
                        "\"Web client ID\" não conferem com o projeto Firebase. Confira o " +
                        "google-services.json e as impressões digitais SHA-1/SHA-256 " +
                        "cadastradas no Firebase Console."
                12501 -> null // a pessoa realmente cancelou (apertou voltar/fora do seletor) — não é erro, fica quieto
                12500 -> "Falha interna do Google Play Services (código 12500). Tente " +
                        "atualizar o app \"Google Play Services\" na Play Store e tente de novo."
                8 -> "Erro interno do Google (código 8). Tente novamente em alguns instantes."
                7 -> "Sem conexão com a internet."
                else -> "Falha no login Google (código ${e.statusCode}): ${e.message}"
            }
            carregando = false
        }
    }

    fun iniciarLoginGoogle() {
        carregando = true
        erro = null
        mensagemSucesso = null
        // Desloga qualquer sessão antiga do Google guardada no aparelho antes de abrir
        // o seletor — isso evita o problema clássico de "toca em Entrar com Google, dá
        // um flash na tela e volta sozinho pro login sem explicação", causado por uma
        // sessão anterior travada/corrompida sendo reaproveitada silenciosamente.
        googleClient.signOut().addOnCompleteListener {
            launcher.launch(googleClient.signInIntent)
        }
    }

    // ---------- E-mail/senha ----------

    fun autenticarComEmailSenha() {
        erro = null
        mensagemSucesso = null
        if (email.isBlank() || senha.isBlank()) {
            erro = "Preencha e-mail e senha."
            return
        }
        carregando = true
        scope.launch {
            try {
                withTimeout(20_000) {
                    if (criandoConta) {
                        FirebaseAuth.getInstance()
                            .createUserWithEmailAndPassword(email.trim(), senha)
                            .await()
                    } else {
                        FirebaseAuth.getInstance()
                            .signInWithEmailAndPassword(email.trim(), senha)
                            .await()
                    }
                }
                aoLogar()
                // Só mexe no que está salvo DEPOIS de confirmar que o login deu certo
                // (senão salvaria um e-mail errado digitado por engano). Guarda só o
                // e-mail — nunca a senha, veja o comentário lá em cima do LaunchedEffect.
                if (lembrarLogin) {
                    prefs.edit().putString("email", email.trim()).apply()
                } else {
                    prefs.edit().clear().apply()
                }
            } catch (e: TimeoutCancellationException) {
                erro = "Tempo esgotado ao acessar o servidor. Verifique sua conexão e tente novamente."
            } catch (e: Exception) {
                erro = mensagemDeErroFirebase(e)
            } finally {
                carregando = false
            }
        }
    }

    fun recuperarSenha() {
        erro = null
        mensagemSucesso = null
        if (email.isBlank()) {
            erro = "Digite seu e-mail no campo acima primeiro, depois toque em \"Esqueci minha senha\"."
            return
        }
        carregando = true
        scope.launch {
            try {
                withTimeout(20_000) {
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).await()
                }
                mensagemSucesso = "Enviamos um e-mail pra ${email.trim()} com um link pra você criar uma senha nova. Confira a caixa de entrada (e o spam)."
            } catch (e: TimeoutCancellationException) {
                erro = "Tempo esgotado ao acessar o servidor. Verifique sua conexão e tente novamente."
            } catch (e: Exception) {
                erro = mensagemDeErroFirebase(e)
            } finally {
                carregando = false
            }
        }
    }

    // ---------- UI ----------

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Controle Financeiro", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Clientes, compras e parcelas na nuvem", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        if (carregando) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { iniciarLoginGoogle() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Entrar com Google")
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "  ou  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = senha,
                onValueChange = { senha = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { lembrarLogin = !lembrarLogin }
            ) {
                Checkbox(checked = lembrarLogin, onCheckedChange = { lembrarLogin = it })
                Text("Lembrar meu e-mail neste aparelho", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))

            OutlinedButton(onClick = { autenticarComEmailSenha() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (criandoConta) "Criar conta" else "Entrar")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { criandoConta = !criandoConta; erro = null; mensagemSucesso = null }) {
                Text(
                    if (criandoConta) "Já tenho conta, quero entrar"
                    else "Ainda não tenho conta, quero criar"
                )
            }
            if (!criandoConta) {
                TextButton(onClick = { recuperarSenha() }) {
                    Text("Esqueci minha senha")
                }
            }
        }

        erro?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        mensagemSucesso?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

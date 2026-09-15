package com.controlefinanceiro.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Cliente
import com.controlefinanceiro.app.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClienteFormScreen(
    viewModel: AppViewModel,
    clienteExistente: Cliente?,
    aoVoltar: () -> Unit
) {
    var nome by remember { mutableStateOf(clienteExistente?.nome ?: "") }
    var telefone by remember { mutableStateOf(clienteExistente?.telefone ?: "") }
    var email by remember { mutableStateOf(clienteExistente?.email ?: "") }
    var endereco by remember { mutableStateOf(clienteExistente?.endereco ?: "") }
    var tentouSalvar by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }
    var mostrarConfirmarDescarte by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val erroViewModel = viewModel.erro.value

    // Pra saber se a pessoa mexeu em algo e avisar antes de descartar sem querer.
    val houveAlteracao = nome != (clienteExistente?.nome ?: "") ||
        telefone != (clienteExistente?.telefone ?: "") ||
        email != (clienteExistente?.email ?: "") ||
        endereco != (clienteExistente?.endereco ?: "")
    val tentarSair: () -> Unit = { if (houveAlteracao) mostrarConfirmarDescarte = true else aoVoltar() }
    BackHandler(onBack = tentarSair)

    LaunchedEffect(erroViewModel) {
        if (erroViewModel != null) {
            salvando = false
            snackbarHostState.showSnackbar(erroViewModel)
            viewModel.limparErro()
        }
    }

    val nomeInvalido = tentouSalvar && nome.isBlank()

    fun labelObrigatorio(texto: String) = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(texto) }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(" *") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (clienteExistente == null) "Novo cliente" else "Editar cliente") },
                navigationIcon = {
                    IconButton(onClick = tentarSair) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = nome,
                onValueChange = { nome = it },
                label = { Text(labelObrigatorio("Nome")) },
                isError = nomeInvalido,
                supportingText = {
                    if (nomeInvalido) Text("Campo obrigatório")
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = telefone, onValueChange = { telefone = it },
                label = { Text("Telefone") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = endereco, onValueChange = { endereco = it },
                label = { Text("Endereço") }, modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "* Campo obrigatório",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    tentouSalvar = true
                    if (nome.isBlank()) return@Button
                    salvando = true
                    val cliente = (clienteExistente ?: Cliente()).copy(
                        nome = nome, telefone = telefone, email = email, endereco = endereco
                    )
                    viewModel.salvarCliente(cliente) {
                        salvando = false
                        aoVoltar()
                    }
                },
                enabled = !salvando
            ) {
                if (salvando) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Salvar")
            }
        }
    }

    if (mostrarConfirmarDescarte) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarDescarte = false },
            title = { Text("Descartar alterações?") },
            text = { Text("Você preencheu campos que ainda não foram salvos. Se sair agora, vai perder o que digitou.") },
            confirmButton = {
                TextButton(onClick = { mostrarConfirmarDescarte = false; aoVoltar() }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmarDescarte = false }) { Text("Continuar editando") }
            }
        )
    }
}

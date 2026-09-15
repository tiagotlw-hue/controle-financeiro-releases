package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Lista
import com.controlefinanceiro.app.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListasScreen(
    viewModel: AppViewModel,
    aoAbrirLista: (Lista) -> Unit,
    aoVoltar: () -> Unit
) {
    val listas by viewModel.listas
    var mostrarFormNovaLista by remember { mutableStateOf(false) }
    var mostrarFormEntrar by remember { mutableStateOf(false) }
    var listaParaExcluir by remember { mutableStateOf<Lista?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val erro = viewModel.erro.value
    val meuUid = viewModel.usuarioLogado()?.uid

    LaunchedEffect(Unit) { viewModel.iniciarEscutaListas() }

    LaunchedEffect(erro) {
        if (erro != null) {
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minhas listas") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarFormEntrar = true }) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "Entrar numa lista com código")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarFormNovaLista = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nova lista")
            }
        }
    ) { padding ->
        if (listas.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Nenhuma lista ainda. Toque em + para criar, ou no ícone acima para " +
                        "entrar numa lista que alguém compartilhou com você.",
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(listas, key = { it.id }) { lista ->
                    val compartilhada = lista.membros.size > 1
                    ListItem(
                        headlineContent = { Text(lista.nome) },
                        supportingContent = {
                            Text(
                                if (compartilhada) "Compartilhada · código ${lista.id}" else "Só sua"
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (compartilhada) Icons.Default.Group else Icons.Default.Person,
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { listaParaExcluir = lista }) {
                                Icon(Icons.Default.Delete, contentDescription = "Sair/excluir lista")
                            }
                        },
                        modifier = Modifier.clickable { aoAbrirLista(lista) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (mostrarFormNovaLista) {
        var nome by remember { mutableStateOf("") }
        var tentouSalvar by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { mostrarFormNovaLista = false },
            title = { Text("Nova lista") },
            text = {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome da lista *") },
                    placeholder = { Text("Ex: Mercado do mês") },
                    isError = tentouSalvar && nome.isBlank(),
                    supportingText = { if (tentouSalvar && nome.isBlank()) Text("Campo obrigatório") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    tentouSalvar = true
                    if (nome.isNotBlank()) {
                        viewModel.criarLista(nome.trim()) { mostrarFormNovaLista = false }
                    }
                }) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarFormNovaLista = false }) { Text("Cancelar") }
            }
        )
    }

    if (mostrarFormEntrar) {
        var codigo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mostrarFormEntrar = false },
            title = { Text("Entrar numa lista") },
            text = {
                Column {
                    Text(
                        "Digite o código que alguém compartilhou com você:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { codigo = it.uppercase() },
                        label = { Text("Código") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.entrarLista(codigo) { mostrarFormEntrar = false }
                    },
                    enabled = codigo.isNotBlank()
                ) { Text("Entrar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarFormEntrar = false }) { Text("Cancelar") }
            }
        )
    }

    listaParaExcluir?.let { lista ->
        val souOUnicoMembro = lista.membros.size <= 1 || (meuUid != null && lista.membros.all { it == meuUid })
        AlertDialog(
            onDismissRequest = { listaParaExcluir = null },
            title = { Text(if (souOUnicoMembro) "Excluir lista" else "Sair da lista") },
            text = {
                Text(
                    if (souOUnicoMembro)
                        "Tem certeza que deseja excluir \"${lista.nome}\" e todos os itens dela? Essa ação não pode ser desfeita."
                    else
                        "Você vai deixar de ver \"${lista.nome}\". Ela continua existindo pras outras pessoas que têm acesso."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (souOUnicoMembro) {
                        viewModel.excluirLista(lista.id)
                    } else {
                        viewModel.sairLista(lista.id)
                    }
                    listaParaExcluir = null
                }) { Text(if (souOUnicoMembro) "Excluir" else "Sair") }
            },
            dismissButton = {
                TextButton(onClick = { listaParaExcluir = null }) { Text("Cancelar") }
            }
        )
    }
}

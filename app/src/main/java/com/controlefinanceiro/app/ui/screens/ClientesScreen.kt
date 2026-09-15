package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Cliente
import com.controlefinanceiro.app.ui.components.IconeDirecaoSaldo
import com.controlefinanceiro.app.viewmodel.AppViewModel
import com.controlefinanceiro.app.ui.theme.VerdeSaldo
import com.controlefinanceiro.app.ui.theme.VermelhoSaldo
import com.controlefinanceiro.app.data.formatarMoeda

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientesScreen(
    viewModel: AppViewModel,
    aoAbrirCliente: (Cliente) -> Unit,
    aoNovoCliente: () -> Unit,
    aoEditarCliente: (Cliente) -> Unit,
    aoAbrirVisaoGeral: () -> Unit,
    aoAbrirConfiguracoes: () -> Unit,
    aoAbrirLista: () -> Unit
) {
    val clientes by viewModel.clientes
    var clienteParaExcluir by remember { mutableStateOf<Cliente?>(null) }
    var termoBusca by remember { mutableStateOf("") }
    val clientesFiltrados = remember(clientes, termoBusca) {
        if (termoBusca.isBlank()) clientes
        else clientes.filter {
            it.nome.contains(termoBusca, ignoreCase = true) || it.telefone.contains(termoBusca)
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val erro = viewModel.erro.value

    LaunchedEffect(erro) {
        if (erro != null) {
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clientes") },
                actions = {
                    IconButton(onClick = aoAbrirLista) {
                        Icon(Icons.Default.Checklist, contentDescription = "Lista de compras")
                    }
                    IconButton(onClick = aoAbrirVisaoGeral) {
                        Icon(Icons.Default.BarChart, contentDescription = "Visão geral")
                    }
                    IconButton(onClick = aoAbrirConfiguracoes) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurações")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = aoNovoCliente) {
                Icon(Icons.Default.Add, contentDescription = "Novo cliente")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (clientes.isNotEmpty()) {
                OutlinedTextField(
                    value = termoBusca,
                    onValueChange = { termoBusca = it },
                    placeholder = { Text("Buscar por nome ou telefone") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (termoBusca.isNotEmpty()) {
                            IconButton(onClick = { termoBusca = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (clientes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Nenhum cliente cadastrado ainda. Toque em + para adicionar.")
                }
            } else if (clientesFiltrados.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(
                        "Nenhum cliente encontrado para \"$termoBusca\".",
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(clientesFiltrados, key = { it.id }) { cliente ->
                        ListItem(
                            headlineContent = { Text(cliente.nome) },
                            supportingContent = { Text(cliente.telefone) },
                            trailingContent = {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    IconeDirecaoSaldo(positivo = cliente.saldo >= 0)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        formatarMoeda(cliente.saldo),
                                        color = if (cliente.saldo >= 0) VerdeSaldo else VermelhoSaldo,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    IconButton(onClick = { aoEditarCliente(cliente) }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar cliente")
                                    }
                                    IconButton(onClick = { clienteParaExcluir = cliente }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir cliente")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { aoAbrirCliente(cliente) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    clienteParaExcluir?.let { cliente ->
        AlertDialog(
            onDismissRequest = { clienteParaExcluir = null },
            title = { Text("Excluir cliente") },
            text = { Text("Tem certeza que deseja excluir \"${cliente.nome}\"? Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.excluirCliente(cliente.id)
                    clienteParaExcluir = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { clienteParaExcluir = null }) { Text("Cancelar") }
            }
        )
    }
}

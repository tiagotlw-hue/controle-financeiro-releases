package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Cartao
import com.controlefinanceiro.app.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesCartoesScreen(
    viewModel: AppViewModel,
    aoVoltar: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.iniciarEscutaCartoes()
    }
    val cartoes by viewModel.cartoes
    val erro = viewModel.erro.value
    val snackbarHostState = remember { SnackbarHostState() }

    var mostrarNovoCartao by remember { mutableStateOf(false) }
    var cartaoParaEditar by remember { mutableStateOf<Cartao?>(null) }
    var cartaoParaExcluir by remember { mutableStateOf<Cartao?>(null) }

    LaunchedEffect(erro) {
        if (erro != null) {
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cartões") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarNovoCartao = true }) {
                Icon(Icons.Default.Add, contentDescription = "Novo cartão")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (cartoes.isEmpty()) {
                Text(
                    "Nenhum cartão ainda. Toque em + pra cadastrar (ex: melhor dia 6, vencimento dia 15).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(cartoes, key = { it.id }) { cartao ->
                        ListItem(
                            headlineContent = { Text(cartao.nome) },
                            supportingContent = { Text("Melhor dia ${cartao.melhorDia} • Vence dia ${cartao.diaVencimento}") },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { cartaoParaEditar = cartao }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar cartão")
                                    }
                                    IconButton(onClick = { cartaoParaExcluir = cartao }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir cartão")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { cartaoParaEditar = cartao }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (mostrarNovoCartao) {
        CartaoFormDialog(
            cartaoExistente = null,
            onConfirmar = { cartao ->
                viewModel.salvarCartao(cartao) { mostrarNovoCartao = false }
            },
            onCancelar = { mostrarNovoCartao = false }
        )
    }

    cartaoParaEditar?.let { cartao ->
        CartaoFormDialog(
            cartaoExistente = cartao,
            onConfirmar = { atualizado ->
                viewModel.salvarCartao(atualizado) { cartaoParaEditar = null }
            },
            onCancelar = { cartaoParaEditar = null }
        )
    }

    cartaoParaExcluir?.let { cartao ->
        AlertDialog(
            onDismissRequest = { cartaoParaExcluir = null },
            title = { Text("Excluir cartão") },
            text = { Text("Tem certeza que deseja excluir \"${cartao.nome}\"? Cobranças já criadas com ele não serão alteradas.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.excluirCartao(cartao.id)
                    cartaoParaExcluir = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { cartaoParaExcluir = null }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartaoFormDialog(
    cartaoExistente: Cartao?,
    onConfirmar: (Cartao) -> Unit,
    onCancelar: () -> Unit
) {
    var nome by remember { mutableStateOf(cartaoExistente?.nome ?: "") }
    var melhorDiaTexto by remember { mutableStateOf(cartaoExistente?.melhorDia?.toString() ?: "1") }
    var diaVencimentoTexto by remember { mutableStateOf(cartaoExistente?.diaVencimento?.toString() ?: "10") }
    var tentouSalvar by remember { mutableStateOf(false) }
    val nomeInvalido = tentouSalvar && nome.isBlank()

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (cartaoExistente == null) "Novo cartão" else "Editar cartão") },
        text = {
            Column {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome (ex: Nubank) *") },
                    isError = nomeInvalido,
                    supportingText = { if (nomeInvalido) Text("Campo obrigatório") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = melhorDiaTexto,
                        onValueChange = { melhorDiaTexto = it },
                        label = { Text("Melhor dia") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = diaVencimentoTexto,
                        onValueChange = { diaVencimentoTexto = it },
                        label = { Text("Vencimento") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Compras a partir do \"melhor dia\" caem na fatura que vence no mês seguinte.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tentouSalvar = true
                if (nome.isBlank()) return@TextButton
                val melhorDia = melhorDiaTexto.toIntOrNull()?.coerceIn(1, 31) ?: 1
                val diaVencimento = diaVencimentoTexto.toIntOrNull()?.coerceIn(1, 31) ?: 10
                onConfirmar(
                    (cartaoExistente ?: Cartao()).copy(
                        nome = nome,
                        melhorDia = melhorDia,
                        diaVencimento = diaVencimento
                    )
                )
            }) { Text(if (cartaoExistente == null) "Criar" else "Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}

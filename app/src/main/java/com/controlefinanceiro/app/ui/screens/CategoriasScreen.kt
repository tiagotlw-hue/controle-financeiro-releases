package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Categoria
import com.controlefinanceiro.app.ui.components.IconeDirecaoSaldo
import com.controlefinanceiro.app.data.Cliente
import com.controlefinanceiro.app.data.TipoMovimentacao
import com.controlefinanceiro.app.data.compartilharArquivo
import com.controlefinanceiro.app.data.compartilharNoWhatsApp
import com.controlefinanceiro.app.data.gerarLegendaCompartilhamento
import com.controlefinanceiro.app.viewmodel.AppViewModel
import java.util.Locale
import com.controlefinanceiro.app.ui.theme.AzulFluxo
import com.controlefinanceiro.app.ui.theme.VerdeSaldo
import com.controlefinanceiro.app.ui.theme.VermelhoSaldo
import com.controlefinanceiro.app.data.formatarMoeda

/** Primeira tela ao entrar num cliente: lista as "contas" (categorias) dele, tipo
 *  "Construção a pagar", "Mercado a pagar", "Empréstimo a receber". Cada categoria
 *  agrupa várias movimentações. Essa tela NÃO cria movimentação nenhuma — só cria
 *  a categoria em si; as movimentações são criadas de dentro da categoria. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriasScreen(
    viewModel: AppViewModel,
    cliente: Cliente,
    aoAbrirCategoria: (Categoria) -> Unit,
    aoVoltar: () -> Unit
) {
    LaunchedEffect(cliente.id) { viewModel.observarCategorias(cliente.id) }
    val categorias by viewModel.categorias
    val clientesAtualizados by viewModel.clientes
    // Igual foi feito na tela de listas: o "cliente" recebido é só uma foto de quando
    // essa tela foi aberta. O saldo muda toda vez que uma movimentação ou baixa é
    // registrada em outra tela, então buscamos sempre a versão mais atual na lista
    // que já está sendo escutada ao vivo — senão, ao voltar pra cá, o saldo continua
    // mostrando o valor de antes.
    val clienteAtual = clientesAtualizados.firstOrNull { it.id == cliente.id } ?: cliente
    val snackbarHostState = remember { SnackbarHostState() }
    val erro = viewModel.erro.value
    var mostrarNovaCategoria by remember { mutableStateOf(false) }
    var categoriaParaEditar by remember { mutableStateOf<Categoria?>(null) }
    var categoriaParaExcluir by remember { mutableStateOf<Categoria?>(null) }
    var exportando by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(erro) {
        if (erro != null) {
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(clienteAtual.nome) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    var mostrarMenuExportar by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { if (!exportando) mostrarMenuExportar = true },
                            enabled = !exportando
                        ) {
                            if (exportando) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Share, contentDescription = "Exportar extrato (CSV)")
                            }
                        }
                        DropdownMenu(
                            expanded = mostrarMenuExportar,
                            onDismissRequest = { mostrarMenuExportar = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Enviar no WhatsApp") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                                onClick = {
                                    mostrarMenuExportar = false
                                    exportando = true
                                    viewModel.exportarClienteCsv(context, clienteAtual) { arquivo ->
                                        exportando = false
                                        compartilharNoWhatsApp(context, arquivo, gerarLegendaCompartilhamento(clienteAtual))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Outro app…") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    mostrarMenuExportar = false
                                    exportando = true
                                    viewModel.exportarClienteCsv(context, clienteAtual) { arquivo ->
                                        exportando = false
                                        compartilharArquivo(
                                            context, arquivo,
                                            "Compartilhar extrato de ${clienteAtual.nome}",
                                            gerarLegendaCompartilhamento(clienteAtual)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarNovaCategoria = true }) {
                Icon(Icons.Default.Add, contentDescription = "Nova categoria")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Saldo do cliente", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconeDirecaoSaldo(positivo = clienteAtual.saldo >= 0)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            formatarMoeda(clienteAtual.saldo),
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (clienteAtual.saldo >= 0) VerdeSaldo else VermelhoSaldo
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("A receber: R$ %.2f".format(Locale("pt", "BR"), clienteAtual.totalAReceber), color = VerdeSaldo, style = MaterialTheme.typography.bodyMedium)
                        Text("A pagar: R$ %.2f".format(Locale("pt", "BR"), clienteAtual.totalAPagar), color = VermelhoSaldo, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (categorias.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma categoria ainda. Toque em + para criar, ex: \"Construção a pagar\".",
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(categorias, key = { it.id }) { categoria ->
                        val cor = when (categoria.tipo) {
                            TipoMovimentacao.RECEBER -> VerdeSaldo
                            TipoMovimentacao.PAGAR -> VermelhoSaldo
                            else -> AzulFluxo
                        }
                        ListItem(
                            headlineContent = { Text(categoria.nome) },
                            supportingContent = {
                                Text(
                                    when (categoria.tipo) {
                                        TipoMovimentacao.RECEBER -> "A receber"
                                        TipoMovimentacao.PAGAR -> "A pagar"
                                        else -> "Fluxo de caixa"
                                    },
                                    color = cor
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Numa categoria de fluxo o "totalAberto" denormalizado é sempre
                                    // 0 (tudo já entra quitado) — não faz sentido mostrar aqui, quem
                                    // quiser ver o saldo do fluxo entra na categoria.
                                    if (categoria.tipo != TipoMovimentacao.FLUXO) {
                                        Text(
                                            formatarMoeda(categoria.totalAberto),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    IconButton(onClick = { categoriaParaEditar = categoria }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar nome")
                                    }
                                    IconButton(onClick = { categoriaParaExcluir = categoria }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Excluir categoria")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { aoAbrirCategoria(categoria) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (mostrarNovaCategoria) {
        NovaCategoriaDialog(
            categoriaExistente = null,
            onConfirmar = { nome, tipo ->
                viewModel.criarCategoria(cliente.id, nome, tipo) { categoriaCriada ->
                    mostrarNovaCategoria = false
                    aoAbrirCategoria(categoriaCriada)
                }
            },
            onCancelar = { mostrarNovaCategoria = false }
        )
    }

    categoriaParaEditar?.let { categoria ->
        NovaCategoriaDialog(
            categoriaExistente = categoria,
            onConfirmar = { nome, _ ->
                viewModel.editarCategoria(cliente.id, categoria.copy(nome = nome)) {
                    categoriaParaEditar = null
                }
            },
            onCancelar = { categoriaParaEditar = null }
        )
    }

    categoriaParaExcluir?.let { categoria ->
        AlertDialog(
            onDismissRequest = { categoriaParaExcluir = null },
            title = { Text("Excluir categoria") },
            text = {
                Text(
                    "Tem certeza que deseja excluir \"${categoria.nome}\"? Todas as movimentações, " +
                        "duplicatas e baixas dentro dela serão perdidas. Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.excluirCategoria(cliente.id, categoria.id)
                    categoriaParaExcluir = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { categoriaParaExcluir = null }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaCategoriaDialog(
    categoriaExistente: Categoria?,
    onConfirmar: (nome: String, tipo: String) -> Unit,
    onCancelar: () -> Unit
) {
    var nome by remember { mutableStateOf(categoriaExistente?.nome ?: "") }
    var tipo by remember { mutableStateOf(categoriaExistente?.tipo ?: TipoMovimentacao.RECEBER) }
    var tentouSalvar by remember { mutableStateOf(false) }
    val nomeInvalido = tentouSalvar && nome.isBlank()
    val tipoTravado = categoriaExistente != null // não dá pra mudar o tipo depois de criada

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (categoriaExistente == null) "Nova categoria" else "Editar categoria") },
        text = {
            Column {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome (ex: Construção) *") },
                    isError = nomeInvalido,
                    supportingText = { if (nomeInvalido) Text("Campo obrigatório") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tipo == TipoMovimentacao.RECEBER,
                        onClick = { if (!tipoTravado) tipo = TipoMovimentacao.RECEBER },
                        enabled = !tipoTravado,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) { Text("A receber") }
                    SegmentedButton(
                        selected = tipo == TipoMovimentacao.PAGAR,
                        onClick = { if (!tipoTravado) tipo = TipoMovimentacao.PAGAR },
                        enabled = !tipoTravado,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) { Text("A pagar") }
                    SegmentedButton(
                        selected = tipo == TipoMovimentacao.FLUXO,
                        onClick = { if (!tipoTravado) tipo = TipoMovimentacao.FLUXO },
                        enabled = !tipoTravado,
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) { Text("Fluxo") }
                }
                if (tipo == TipoMovimentacao.FLUXO && !tipoTravado) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Cada movimentação já entra quitada na hora (recebida ou paga), sem " +
                            "duplicata em aberto — um fluxo de caixa simples, tipo livro caixa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (tipoTravado) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "O tipo não pode ser alterado depois de criada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tentouSalvar = true
                if (nome.isNotBlank()) onConfirmar(nome, tipo)
            }) { Text(if (categoriaExistente == null) "Criar" else "Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}

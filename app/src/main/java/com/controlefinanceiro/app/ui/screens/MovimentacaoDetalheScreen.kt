package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.FormaPagamento
import com.controlefinanceiro.app.data.Movimentacao
import com.controlefinanceiro.app.data.TipoMovimentacao
import com.controlefinanceiro.app.viewmodel.AppViewModel
import java.util.Locale
import com.controlefinanceiro.app.ui.theme.VerdeSaldo
import com.controlefinanceiro.app.ui.theme.VermelhoSaldo
import com.controlefinanceiro.app.data.formatarData
import com.controlefinanceiro.app.data.formatarMoeda


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimentacaoDetalheScreen(
    viewModel: AppViewModel,
    clienteId: String,
    categoriaId: String,
    movimentacaoId: String,
    aoBaixar: () -> Unit,
    aoNovaMovimentacaoMesmoTipo: (String) -> Unit,
    aoEditar: (Movimentacao) -> Unit,
    aoExcluida: () -> Unit,
    aoVoltar: () -> Unit
) {
    LaunchedEffect(clienteId, categoriaId) { viewModel.observarMovimentacoes(clienteId, categoriaId) }
    LaunchedEffect(clienteId) { viewModel.observarCategorias(clienteId) }
    val movimentacoesAtuais by viewModel.movimentacoes
    val categoriasAtuais by viewModel.categorias
    // Busca sempre a versão mais atual na lista ao vivo em vez de depender de um objeto
    // fixo recebido na navegação — senão, ao voltar pra essa tela depois de editar ou
    // dar baixa em outro lugar, ela continuaria mostrando os dados de antes da mudança.
    val movimentacaoOriginal = movimentacoesAtuais.firstOrNull { it.id == movimentacaoId }
    // Se não achou (foi excluída em outro lugar enquanto essa tela estava aberta em
    // background), sai sozinho da tela em vez de travar mostrando um objeto vazio.
    LaunchedEffect(movimentacaoOriginal) {
        if (movimentacaoOriginal == null && movimentacoesAtuais.isNotEmpty()) aoVoltar()
    }
    val movimentacao = movimentacaoOriginal ?: return
    val ehFluxo = categoriasAtuais.firstOrNull { it.id == categoriaId }?.tipo == TipoMovimentacao.FLUXO
    val cor = if (movimentacao.tipo == TipoMovimentacao.RECEBER) VerdeSaldo else VermelhoSaldo
    val rotuloTipo = when {
        ehFluxo && movimentacao.tipo == TipoMovimentacao.RECEBER -> "recebido"
        ehFluxo -> "pago"
        movimentacao.tipo == TipoMovimentacao.RECEBER -> "a receber"
        else -> "a pagar"
    }
    var mostrarConfirmacaoExclusao by remember { mutableStateOf(false) }
    var excluindo by remember { mutableStateOf(false) }
    var detalharDuplicatas by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val erro = viewModel.erro.value

    LaunchedEffect(erro) {
        if (erro != null) {
            excluindo = false
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(movimentacao.descricao) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { aoEditar(movimentacao) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar movimentação")
                    }
                    IconButton(
                        onClick = { mostrarConfirmacaoExclusao = true },
                        // Numa categoria de fluxo não existe baixa pra proteger — sempre dá
                        // pra excluir. Fora dela, só se ainda não tiver nenhum pagamento.
                        enabled = ehFluxo || movimentacao.podeEditarOuExcluir
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir movimentação")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick = { aoNovaMovimentacaoMesmoTipo(movimentacao.tipo) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) { Text("Novo $rotuloTipo") }
                // Numa categoria de fluxo tudo já nasce quitado — não existe o que baixar.
                if (!ehFluxo) {
                    Button(
                        onClick = aoBaixar,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        enabled = movimentacao.valorEmAberto > 0.01
                    ) { Text("Baixar") }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(rotuloTipo.replaceFirstChar { it.uppercase() }, color = cor, style = MaterialTheme.typography.labelLarge)
            Text("Total: R$ %.2f".format(Locale("pt", "BR"), movimentacao.valorTotal), style = MaterialTheme.typography.titleMedium)
            if (!ehFluxo) {
                Text("Em aberto: R$ %.2f".format(Locale("pt", "BR"), movimentacao.valorEmAberto))
            }
            Text(
                "Desde ${formatarData(movimentacao.dataCriacao)} • " +
                    if (movimentacao.formaPagamento == FormaPagamento.CARTAO) "Cartão" else "Dinheiro",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!ehFluxo && !movimentacao.podeEditarOuExcluir) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Já tem baixa registrada: dá pra editar a descrição e a data (os vencimentos das " +
                        "duplicatas são recalculados), mas não os valores nem excluir a movimentação inteira.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("Duplicatas", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { detalharDuplicatas = !detalharDuplicatas }) {
                    Icon(
                        if (detalharDuplicatas) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewAgenda,
                        contentDescription = if (detalharDuplicatas) "Ocultar detalhes" else "Detalhar"
                    )
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                items(movimentacao.parcelas) { p ->
                    val travada = p.valorPago > 0.0
                    if (!detalharDuplicatas) {
                        ListItem(
                            headlineContent = { Text("Duplicata ${p.numero}/${movimentacao.numParcelas}") },
                            supportingContent = {
                                Text(
                                    "Vence ${formatarData(p.dataVencimento)} — " +
                                        when (p.status) {
                                            "paga" -> "paga"
                                            "parcial" -> "parcialmente paga (R$ %.2f de R$ %.2f)".format(Locale("pt", "BR"), p.valorPago, p.valorOriginal)
                                            else -> "em aberto"
                                        }
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    if (travada) {
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Não pode mais ser alterada (já recebeu pagamento)",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(formatarMoeda(p.valorOriginal))
                                }
                            }
                        )
                    } else {
                        val valorQueFalta = (p.valorOriginal - p.valorPago).coerceAtLeast(0.0)
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("Duplicata ${p.numero}/${movimentacao.numParcelas}")
                                    if (travada) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Lock,
                                            contentDescription = "Não pode mais ser alterada (já recebeu pagamento)",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            supportingContent = {
                                Column {
                                    Text("Vence ${formatarData(p.dataVencimento)}")
                                    Text("Valor original: R$ %.2f".format(Locale("pt", "BR"), p.valorOriginal))
                                }
                            },
                            trailingContent = {
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                    Text(
                                        "Pago: R$ %.2f".format(Locale("pt", "BR"), p.valorPago),
                                        color = VerdeSaldo,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (valorQueFalta > 0.01) {
                                        Text(
                                            "Falta: R$ %.2f".format(Locale("pt", "BR"), valorQueFalta),
                                            color = VermelhoSaldo,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        Text(
                                            "Quitada",
                                            color = VerdeSaldo,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        )
                    }
                    HorizontalDivider()
                }
            }

            if (movimentacao.historicoPagamentos.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Baixas já feitas", style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.weight(1f)) {
                    items(movimentacao.historicoPagamentos) { pag ->
                        ListItem(
                            headlineContent = { Text(formatarMoeda(pag.valorPago)) },
                            supportingContent = {
                                Text(
                                    "${formatarData(pag.data)} — ${pag.descricao}"
                                )
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (mostrarConfirmacaoExclusao) {
        AlertDialog(
            onDismissRequest = { if (!excluindo) mostrarConfirmacaoExclusao = false },
            title = { Text("Excluir movimentação") },
            text = {
                Text(
                    "Tem certeza que deseja excluir \"${movimentacao.descricao}\"? " +
                        "Todas as duplicatas dela serão perdidas. Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        excluindo = true
                        viewModel.excluirMovimentacao(clienteId, categoriaId, movimentacao.id) {
                            excluindo = false
                            mostrarConfirmacaoExclusao = false
                            aoExcluida()
                        }
                    },
                    enabled = !excluindo
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(
                    onClick = { mostrarConfirmacaoExclusao = false },
                    enabled = !excluindo
                ) { Text("Cancelar") }
            }
        )
    }
}

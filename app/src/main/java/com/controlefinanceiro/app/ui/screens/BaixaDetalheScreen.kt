package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Movimentacao
import com.controlefinanceiro.app.data.Pagamento
import com.controlefinanceiro.app.viewmodel.AppViewModel
import java.util.Locale
import com.controlefinanceiro.app.ui.theme.VerdeSaldo
import com.controlefinanceiro.app.ui.theme.VermelhoSaldo
import com.controlefinanceiro.app.data.formatarData
import com.controlefinanceiro.app.data.formatarDataHora
import com.controlefinanceiro.app.data.formatarMoeda


/** Tela que mostra o detalhe de uma baixa (aberta a partir da lista de movimentações
 *  ou do extrato do cliente), com opção de excluí-la (devolve as duplicatas afetadas
 *  ao estado de antes).
 *
 *  Uma baixa normalmente afeta uma só movimentação, mas quando o valor pago é dividido
 *  entre duplicatas de mais de uma movimentação de uma vez (ex: R$ 200 cobrindo uma
 *  duplicata de R$ 120 de uma cobrança e uma de R$ 80 de outra), [itens] tem mais de um
 *  par (movimentação + pagamento) — aqui ela aparece com o total combinado no topo e uma
 *  seção detalhada por movimentação logo abaixo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaixaDetalheScreen(
    viewModel: AppViewModel,
    clienteId: String,
    categoriaId: String,
    itens: List<Pair<Movimentacao, Pagamento>>,
    aoExcluida: () -> Unit,
    aoVoltar: () -> Unit
) {
    val valorTotal = itens.sumOf { it.second.valorPago }
    val dataBaixa = itens.firstOrNull()?.second?.data ?: System.currentTimeMillis()
    var mostrarConfirmacaoExclusao by remember { mutableStateOf(false) }
    var excluindo by remember { mutableStateOf(false) }
    var mostrarEdicao by remember { mutableStateOf(false) }
    var salvandoEdicao by remember { mutableStateOf(false) }
    var dataEditada by remember(dataBaixa) { mutableStateOf(dataBaixa) }
    val snackbarHostState = remember { SnackbarHostState() }
    val erro = viewModel.erro.value

    LaunchedEffect(erro) {
        if (erro != null) {
            excluindo = false
            salvandoEdicao = false
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhe da baixa") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        dataEditada = dataBaixa
                        mostrarEdicao = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar data e hora da baixa")
                    }
                    IconButton(onClick = { mostrarConfirmacaoExclusao = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir baixa")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Quando a baixa cobriu mais de uma movimentação, o cabeçalho mostra os nomes
            // delas juntos; quando é só uma, mostra como sempre foi.
            val descricoes = itens.map { it.first.descricao }.distinct()
            Text(descricoes.joinToString(", "), style = MaterialTheme.typography.titleMedium)
            Text(
                formatarMoeda(valorTotal),
                style = MaterialTheme.typography.headlineMedium,
                color = VerdeSaldo
            )
            Text(
                formatarDataHora(dataBaixa),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val observacoes = itens.mapNotNull { it.second.descricao.takeIf { d -> d.isNotBlank() } }.distinct()
            if (observacoes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                observacoes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }

            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.weight(1f)) {
                itens.forEach { (movimentacao, pagamento) ->
                    val duplicatasQuitadas = movimentacao.parcelas
                        .filter { it.numero in pagamento.parcelasNumeros }
                        .sortedBy { it.numero }

                    item {
                        // Só mostra o nome da movimentação como cabeçalho de seção quando
                        // houver mais de uma envolvida — com uma só, fica redundante.
                        if (itens.size > 1) {
                            Text(
                                movimentacao.descricao,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                    }

                    if (duplicatasQuitadas.isEmpty()) {
                        item {
                            Text(
                                "Nenhuma duplicata encontrada para esta baixa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(duplicatasQuitadas) { p ->
                            val valorAplicadoNestaBaixa = pagamento.parcelasAplicadas[p.numero.toString()] ?: 0.0
                            val valorQueFalta = (p.valorOriginal - p.valorPago).coerceAtLeast(0.0)
                            ListItem(
                                headlineContent = { Text("Duplicata ${p.numero}/${movimentacao.numParcelas}") },
                                supportingContent = {
                                    Column {
                                        Text("Vencia ${formatarData(p.dataVencimento)}")
                                        Text("Valor da duplicata: R$ %.2f".format(Locale("pt", "BR"), p.valorOriginal))
                                    }
                                },
                                trailingContent = {
                                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                        Text(
                                            "Pago: R$ %.2f".format(Locale("pt", "BR"), valorAplicadoNestaBaixa),
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
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (mostrarConfirmacaoExclusao) {
        AlertDialog(
            onDismissRequest = { if (!excluindo) mostrarConfirmacaoExclusao = false },
            title = { Text("Excluir baixa") },
            text = {
                Text(
                    "Tem certeza que deseja excluir essa baixa de R$ %.2f? ".format(Locale("pt", "BR"), valorTotal) +
                        "As duplicatas afetadas voltam a ficar em aberto (ou parciais). Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        excluindo = true
                        viewModel.excluirBaixaGrupo(
                            clienteId,
                            categoriaId,
                            itens.map { it.first.id to it.second }
                        ) {
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

    if (mostrarEdicao) {
        AlertDialog(
            onDismissRequest = { if (!salvandoEdicao) mostrarEdicao = false },
            title = { Text("Editar data e hora da baixa") },
            text = {
                Column {
                    Text(
                        "O valor e as duplicatas pagas continuam os mesmos — só a data/hora " +
                            "registrada dessa baixa muda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    SeletorDataHora(
                        dataHoraMillis = dataEditada,
                        onDataHoraChange = { dataEditada = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        salvandoEdicao = true
                        viewModel.editarDataBaixaGrupo(
                            clienteId,
                            categoriaId,
                            itens.map { it.first.id to it.second },
                            dataEditada
                        ) {
                            salvandoEdicao = false
                            mostrarEdicao = false
                            aoVoltar()
                        }
                    },
                    enabled = !salvandoEdicao
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(
                    onClick = { mostrarEdicao = false },
                    enabled = !salvandoEdicao
                ) { Text("Cancelar") }
            }
        )
    }
}

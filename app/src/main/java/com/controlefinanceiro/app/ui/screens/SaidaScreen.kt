package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.controlefinanceiro.app.data.ParcelaRef
import com.controlefinanceiro.app.data.ParcelaUtils
import com.controlefinanceiro.app.data.TipoMovimentacao
import com.controlefinanceiro.app.viewmodel.AppViewModel
import java.util.Locale
import com.controlefinanceiro.app.data.formatarData
import com.controlefinanceiro.app.data.formatarMoeda
import com.controlefinanceiro.app.data.parsearValorMonetario

private val PERCENTUAIS_RAPIDOS = listOf(100, 75, 50, 25)

/** Dá baixa (pagamento/recebimento) nas duplicatas em aberto de uma categoria.
 *  O tipo vem fixo da categoria; opcionalmente pode ser filtrado para uma única
 *  movimentação (quando aberto a partir do detalhe dela). Tem um seletor de
 *  percentual (padrão 100%) que calcula o valor automaticamente como esse
 *  percentual do total em aberto das duplicatas visíveis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaidaScreen(
    viewModel: AppViewModel,
    clienteId: String,
    categoriaId: String,
    tipo: String,
    movimentacaoIdFiltro: String? = null,
    aoVoltar: () -> Unit
) {
    // Não depende só da tela anterior já ter deixado isso escutando — chama aqui
    // também, senão corre o risco de aplicar a baixa em cima de dados de outra
    // categoria caso essa tela seja aberta antes do snapshot da anterior chegar.
    LaunchedEffect(clienteId, categoriaId) { viewModel.observarMovimentacoes(clienteId, categoriaId) }
    val movimentacoes by viewModel.movimentacoes
    var valorTexto by remember { mutableStateOf("") }
    var percentualTexto by remember { mutableStateOf("100") }
    var descricaoManual by remember { mutableStateOf("") }
    var dataBaixaMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var salvando by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val erro = viewModel.erro.value

    LaunchedEffect(erro) {
        if (erro != null) {
            salvando = false
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    // Junta as duplicatas em aberto de todas as movimentações da categoria (mais antigas
    // primeiro) — ou só da movimentação específica, se veio da tela de detalhe dela.
    val abertas = remember(movimentacoes, movimentacaoIdFiltro) {
        movimentacoes
            .filter { movimentacaoIdFiltro == null || it.id == movimentacaoIdFiltro }
            .flatMap { mov ->
                mov.parcelas.filter { it.status != "paga" }
                    .map { p -> ParcelaRef(mov.id, mov.descricao, mov.numParcelas, p) }
            }
            .sortedBy { it.parcela.dataVencimento }
    }

    val totalEmAbertoTudo = abertas.sumOf { it.parcela.valorOriginal - it.parcela.valorPago }

    val valorPago = parsearValorMonetario(valorTexto) ?: 0.0

    var selecaoManualAtiva by remember { mutableStateOf(false) }
    var selecionadas by remember { mutableStateOf<Set<Pair<String, Int>>>(emptySet()) }

    // Modo 1: a pessoa digita um valor -> seleciona automaticamente as duplicatas
    // mais antigas até completar esse valor.
    LaunchedEffect(valorPago, selecaoManualAtiva, abertas) {
        if (!selecaoManualAtiva) {
            selecionadas = ParcelaUtils.selecionarMaisAntigasAte(abertas, valorPago)
        }
    }

    val totalSelecionado = abertas.filter { (it.movimentacaoId to it.parcela.numero) in selecionadas }
        .sumOf { it.parcela.valorOriginal - it.parcela.valorPago }

    // Modo 2: a pessoa marca/desmarca duplicatas manualmente -> o campo de valor é
    // recalculado sozinho como o percentual ativo (ex: 50%) do total das duplicatas
    // que estão marcadas — sem nunca mexer em quais estão marcadas. Assim, aplicar um
    // percentual novo (inclusive 100%) só recalcula o valor e mantém a seleção como está.
    LaunchedEffect(selecionadas, selecaoManualAtiva, percentualTexto) {
        if (selecaoManualAtiva) {
            val percentual = (percentualTexto.toIntOrNull() ?: 100).coerceIn(0, 100)
            val valorCalculado = (percentual / 100.0) * totalSelecionado
            valorTexto = if (valorCalculado > 0.0) {
                "%.2f".format(Locale("pt", "BR"), valorCalculado).replace(".", ",")
            } else ""
        }
    }

    fun aplicarPercentual(percentual: Int) {
        percentualTexto = percentual.toString()
        // Só recalcula o valor com base no total geral em aberto (e deixa o efeito acima
        // auto-selecionar as mais antigas) quando NÃO há uma seleção manual ativa. Se a
        // pessoa já marcou duplicatas na lista, a seleção fica intacta e o efeito acima
        // recalcula o valor como esse percentual do que já está marcado.
        if (!selecaoManualAtiva) {
            val valorCalculado = (percentual / 100.0) * totalEmAbertoTudo
            valorTexto = if (valorCalculado > 0.0) {
                "%.2f".format(Locale("pt", "BR"), valorCalculado).replace(".", ",")
            } else ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tipo == TipoMovimentacao.RECEBER) "Registrar recebimento" else "Registrar pagamento") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                "Total em aberto: R$ %.2f".format(Locale("pt", "BR"), totalEmAbertoTudo),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))

            Text(
                if (selecaoManualAtiva) "Percentual das duplicatas selecionadas" else "Percentual do total em aberto",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        BasicTextField(
                            value = percentualTexto,
                            onValueChange = { texto ->
                                percentualTexto = texto
                                val percentual = texto.toIntOrNull()?.coerceIn(0, 100)
                                if (percentual != null && !selecaoManualAtiva) {
                                    val valorCalculado = (percentual / 100.0) * totalEmAbertoTudo
                                    valorTexto = if (valorCalculado > 0.0) "%.2f".format(Locale("pt", "BR"), valorCalculado).replace(".", ",") else ""
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.width(28.dp)
                        )
                        Text("%", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.width(8.dp))
                PERCENTUAIS_RAPIDOS.forEach { p ->
                    FilterChip(
                        selected = percentualTexto.toIntOrNull() == p,
                        onClick = { aplicarPercentual(p) },
                        label = { Text("$p%") },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = valorTexto,
                onValueChange = { texto ->
                    valorTexto = texto
                    val novoValor = parsearValorMonetario(texto) ?: 0.0
                    // Se já existe uma seleção manual e o valor digitado ainda cabe dentro
                    // do total dela, mantém a seleção e aplica esse valor só nas duplicatas
                    // já marcadas (em vez de trocar pra seleção automática das mais antigas).
                    // Só volta pro modo automático se não tiver seleção manual, ou se o valor
                    // passar do total selecionado (aí não dá mais pra cobrir só com elas).
                    val cabeNaSelecao = selecaoManualAtiva && selecionadas.isNotEmpty() && novoValor <= totalSelecionado + 0.001
                    if (!cabeNaSelecao) {
                        selecaoManualAtiva = false
                    }
                },
                label = { Text("Valor (R$)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = descricaoManual,
                onValueChange = { descricaoManual = it },
                label = { Text("Observação (opcional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            Text("Data e hora da baixa", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            SeletorDataHora(
                dataHoraMillis = dataBaixaMillis,
                onDataHoraChange = { dataBaixaMillis = it }
            )

            Spacer(Modifier.height(12.dp))
            if (abertas.isEmpty()) {
                Text(
                    "Não há duplicatas em aberto para dar baixa.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "Digite um valor, escolha um percentual, ou toque nas duplicatas abaixo para selecionar manualmente:",
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(abertas, key = { it.movimentacaoId + "_" + it.parcela.numero }) { ref ->
                        val chave = ref.movimentacaoId to ref.parcela.numero
                        val marcada = chave in selecionadas
                        val p = ref.parcela
                        val faltante = p.valorOriginal - p.valorPago
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selecaoManualAtiva = true
                                    selecionadas = if (marcada) selecionadas - chave else selecionadas + chave
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = marcada,
                                onCheckedChange = { checado ->
                                    selecaoManualAtiva = true
                                    selecionadas = if (checado) selecionadas + chave else selecionadas - chave
                                },
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                "${ref.parcela.numero}/${ref.movimentacaoNumParcelas}",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(40.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(ref.movimentacaoDescricao, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    formatarData(p.dataVencimento) +
                                        if (p.status == "parcial") " · parcial" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (p.status == "parcial")
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                Text(
                                    formatarMoeda(faltante),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (p.status == "parcial") {
                                    Text(
                                        "de R$ %.2f".format(Locale("pt", "BR"), p.valorOriginal),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Total das duplicatas selecionadas: R$ %.2f".format(Locale("pt", "BR"), totalSelecionado))
                if (valorPago > 0 && valorPago < totalSelecionado - 0.01) {
                    Text(
                        if (selecaoManualAtiva)
                            "O valor é menor que o total selecionado: cada duplicata marcada recebe a mesma proporção (ex: 50% do valor = 50% em cada uma), ficando parcial."
                        else
                            "O valor é menor que o total selecionado: a última duplicata ficará parcialmente paga.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = aoVoltar,
                        enabled = !salvando,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !salvando && valorPago > 0 && selecionadas.isNotEmpty(),
                        onClick = {
                            salvando = true
                            val selecionadasOrdenadas = abertas.filter {
                                (it.movimentacaoId to it.parcela.numero) in selecionadas
                            }
                            viewModel.darBaixa(
                                clienteId = clienteId,
                                categoriaId = categoriaId,
                                selecionadas = selecionadasOrdenadas,
                                valorPago = valorPago,
                                descricaoManual = descricaoManual,
                                dataBaixa = dataBaixaMillis,
                                proporcional = selecaoManualAtiva
                            ) {
                                salvando = false
                                aoVoltar()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        if (salvando) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Confirmar baixa")
                    }
                }
            }
        }
    }
}

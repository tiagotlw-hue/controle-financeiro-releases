package com.controlefinanceiro.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Cartao
import com.controlefinanceiro.app.data.FormaPagamento
import com.controlefinanceiro.app.data.Movimentacao
import com.controlefinanceiro.app.data.ParcelaUtils
import com.controlefinanceiro.app.data.TipoMovimentacao
import com.controlefinanceiro.app.viewmodel.AppViewModel
import java.util.Calendar
import java.util.Locale
import com.controlefinanceiro.app.ui.theme.VerdeSaldo
import com.controlefinanceiro.app.ui.theme.VermelhoSaldo
import com.controlefinanceiro.app.data.formatarData
import com.controlefinanceiro.app.data.formatarMoeda
import com.controlefinanceiro.app.data.parsearValorMonetario


/** Cria uma nova movimentação (cobrança parcelada) dentro de uma categoria já existente,
 *  ou edita uma já existente se `movimentacaoParaEditar` for passada. Se essa movimentação
 *  já tiver alguma baixa registrada, só dá pra editar a descrição e a data — os vencimentos
 *  das duplicatas são recalculados a partir dela, mas os valores continuam travados. O tipo
 *  (a pagar/a receber) vem fixo da categoria. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntradaScreen(
    viewModel: AppViewModel,
    clienteId: String,
    categoriaId: String,
    tipo: String,
    movimentacaoParaEditar: Movimentacao? = null,
    tipoFluxo: String? = null,
    aoVoltar: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.iniciarEscutaCartoes()
        viewModel.iniciarEscutaConfiguracoes()
    }
    val cartoesDisponiveis by viewModel.cartoes
    val configuracoes by viewModel.configuracoes

    var descricao by remember { mutableStateOf(movimentacaoParaEditar?.descricao ?: "") }
    var usarQuantidade by remember { mutableStateOf(false) }
    var valorTexto by remember { mutableStateOf(movimentacaoParaEditar?.valorTotal?.let { if (it > 0) "%.2f".format(Locale("pt", "BR"), it).replace(".", ",") else "" } ?: "") }
    // Guarda os valores de quando a tela abriu, pra saber se a pessoa preencheu/mudou
    // algo e avisar antes de descartar sem querer.
    val descricaoInicial = descricao
    val valorInicial = valorTexto
    var quantidadeTexto by remember { mutableStateOf("1") }
    var valorUnitarioTexto by remember { mutableStateOf("") }
    var numParcelasTexto by remember { mutableStateOf(movimentacaoParaEditar?.numParcelas?.toString() ?: "1") }
    var numeroEditando by remember { mutableStateOf<Int?>(null) }
    var diaVencimentoTexto by remember { mutableStateOf(movimentacaoParaEditar?.diaVencimento?.toString() ?: "15") }
    var dataHoraMillis by remember { mutableStateOf(movimentacaoParaEditar?.dataCriacao ?: System.currentTimeMillis()) }
    var formaPagamento by remember {
        mutableStateOf(movimentacaoParaEditar?.formaPagamento ?: configuracoes.formaPagamentoPadrao)
    }
    var cartaoIdSelecionado by remember {
        mutableStateOf(movimentacaoParaEditar?.cartaoId?.takeIf { it.isNotBlank() } ?: configuracoes.cartaoPadraoId)
    }
    var expandirDropdownCartao by remember { mutableStateOf(false) }
    val ehFluxo = tipo == TipoMovimentacao.FLUXO
    // Numa categoria de fluxo não existe tipo fixo (a categoria mistura os dois). Ao
    // criar, o tipo já vem escolhido pelo botão que a pessoa tocou (Recebido/Pago) —
    // só precisa de um seletor dentro do formulário quando está editando um lançamento
    // já existente, pra poder corrigir a direção se precisar.
    var tipoMovimento by remember {
        mutableStateOf(movimentacaoParaEditar?.tipo ?: tipoFluxo ?: TipoMovimentacao.RECEBER)
    }
    val mostrarSeletorTipoFluxo = ehFluxo && movimentacaoParaEditar != null
    var salvando by remember { mutableStateOf(false) }
    var tentouSalvar by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val erro = viewModel.erro.value
    var mostrarConfirmarDescarte by remember { mutableStateOf(false) }
    val houveAlteracao = descricao != descricaoInicial || valorTexto != valorInicial
    val tentarSair: () -> Unit = { if (houveAlteracao) mostrarConfirmarDescarte = true else aoVoltar() }
    BackHandler(onBack = tentarSair)
    val focusRequesterDescricao = remember { FocusRequester() }

    // Abre a tela já com o teclado focado na descrição, pra digitar direto (só na criação).
    LaunchedEffect(Unit) {
        if (movimentacaoParaEditar == null) focusRequesterDescricao.requestFocus()
    }

    LaunchedEffect(erro) {
        if (erro != null) {
            salvando = false
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    val quantidade = parsearValorMonetario(quantidadeTexto) ?: 0.0
    val valorUnitario = parsearValorMonetario(valorUnitarioTexto) ?: 0.0
    val valorTotal = if (usarQuantidade) {
        quantidade * valorUnitario
    } else {
        parsearValorMonetario(valorTexto) ?: 0.0
    }
    val numParcelas = if (formaPagamento == FormaPagamento.DINHEIRO) 1 else numParcelasTexto.toIntOrNull()?.coerceIn(1, 360) ?: 1
    val diaVencimento = diaVencimentoTexto.toIntOrNull()?.coerceIn(1, 31) ?: 15
    val cartaoSelecionado = cartoesDisponiveis.firstOrNull { it.id == cartaoIdSelecionado }

    val descricaoInvalida = tentouSalvar && descricao.isBlank()
    val valorInvalido = tentouSalvar && valorTotal <= 0
    val cartaoObrigatorioFaltando = tentouSalvar && !ehFluxo && formaPagamento == FormaPagamento.CARTAO && cartaoSelecionado == null
    val valido = descricao.isNotBlank() && valorTotal > 0 &&
        (ehFluxo || formaPagamento == FormaPagamento.DINHEIRO || cartaoSelecionado != null)

    // Prévia ao vivo: recalcula as duplicatas geradas conforme a pessoa digita.
    val previa = remember(valorTotal, numParcelas, diaVencimento, dataHoraMillis, formaPagamento, cartaoSelecionado) {
        if (valorTotal > 0) {
            try {
                when {
                    formaPagamento == FormaPagamento.CARTAO && cartaoSelecionado != null ->
                        ParcelaUtils.gerarParcelasCartao(valorTotal, numParcelas, dataHoraMillis, cartaoSelecionado.melhorDia, cartaoSelecionado.diaVencimento)
                    formaPagamento == FormaPagamento.DINHEIRO ->
                        ParcelaUtils.gerarParcelaAvista(valorTotal, dataHoraMillis)
                    else ->
                        ParcelaUtils.gerarParcelas(valorTotal, numParcelas, dataHoraMillis, diaVencimento)
                }
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    // Valores digitados manualmente pra cada duplicata (menos a última, que é sempre
    // recalculada sozinha pra fechar o total). Some novo (reseta as edições) só quando
    // algo que muda a QUANTIDADE ou o TOTAL a distribuir entre as duplicatas muda —
    // trocar só a data/hora da compra ou o dia de vencimento desloca as datas de
    // vencimento (a prévia já recalcula isso sozinha), mas não muda quantas duplicatas
    // existem nem quanto cada uma deveria valer, então não tem motivo pra apagar um
    // valor que a pessoa acabou de digitar só porque ajustou a hora, por exemplo.
    var valoresManuaisTexto by remember(valorTotal, numParcelas, formaPagamento, cartaoSelecionado) {
        mutableStateOf(mapOf<Int, String>())
    }
    val valoresManuaisParcelas = valoresManuaisTexto.mapNotNull { (numero, texto) ->
        val valor = parsearValorMonetario(texto)
        if (valor != null && valor >= 0) numero to valor else null
    }.toMap()
    val previaFinal = remember(previa, valoresManuaisParcelas, valorTotal) {
        ParcelaUtils.aplicarValoresManuais(previa, valoresManuaisParcelas, valorTotal)
    }

    val ehEdicao = movimentacaoParaEditar != null
    // Quando a movimentação já tem alguma baixa registrada, só dá pra editar a
    // descrição e a data (que recalcula os vencimentos das duplicatas) — valor,
    // quantidade, número de parcelas, forma de pagamento e cartão ficam travados,
    // pra não bagunçar o que já foi pago.
    // Numa categoria de fluxo, "podeEditarOuExcluir" do model sempre dá false (o
    // lançamento já nasce quitado, valorPagoTotal > 0 por definição) — mas isso não
    // tem nada a ver com "tem baixa registrada" nesse contexto, então não deve travar
    // a edição do valor nem mostrar o aviso de baixa.
    val bloqueadoPorBaixa = !ehFluxo && ehEdicao && movimentacaoParaEditar?.podeEditarOuExcluir == false

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            ehEdicao && ehFluxo -> "Editar lançamento"
                            ehEdicao -> "Editar cobrança"
                            ehFluxo && tipoMovimento == TipoMovimentacao.RECEBER -> "Novo recebimento"
                            ehFluxo -> "Novo pagamento"
                            tipo == TipoMovimentacao.RECEBER -> "Nova cobrança (a receber)"
                            else -> "Nova cobrança (a pagar)"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = tentarSair) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (bloqueadoPorBaixa) {
                Text(
                    "Essa movimentação já tem baixa registrada: só dá pra editar a descrição e a " +
                        "data (os vencimentos das duplicatas são recalculados a partir dela). O que já " +
                        "foi pago em cada uma continua exatamente igual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                value = descricao, onValueChange = { descricao = it },
                label = { Text("Descrição *") },
                isError = descricaoInvalida,
                supportingText = { if (descricaoInvalida) Text("Campo obrigatório") },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequesterDescricao)
            )
            Spacer(Modifier.height(4.dp))

            if (mostrarSeletorTipoFluxo) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = tipoMovimento == TipoMovimentacao.RECEBER,
                        onClick = { tipoMovimento = TipoMovimentacao.RECEBER },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Recebido") }
                    SegmentedButton(
                        selected = tipoMovimento == TipoMovimentacao.PAGAR,
                        onClick = { tipoMovimento = TipoMovimentacao.PAGAR },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Pago") }
                }
                Spacer(Modifier.height(6.dp))
            } else if (ehFluxo) {
                // Na criação o tipo já veio definido pelo botão que a pessoa tocou —
                // só mostra como confirmação visual, sem poder trocar aqui.
                Text(
                    if (tipoMovimento == TipoMovimentacao.RECEBER) "Recebido" else "Pago",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tipoMovimento == TipoMovimentacao.RECEBER) VerdeSaldo else VermelhoSaldo
                )
                Spacer(Modifier.height(6.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { usarQuantidade = !usarQuantidade },
                    enabled = !bloqueadoPorBaixa
                ) {
                    Icon(
                        if (usarQuantidade) Icons.Default.Numbers else Icons.Default.AttachMoney,
                        contentDescription = if (usarQuantidade)
                            "Alternar para valor total"
                        else
                            "Alternar para quantidade × valor unitário"
                    )
                }
                if (usarQuantidade) {
                    OutlinedTextField(
                        value = quantidadeTexto, onValueChange = { quantidadeTexto = it },
                        label = { Text("Qtd") },
                        enabled = !bloqueadoPorBaixa,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = valorUnitarioTexto, onValueChange = { valorUnitarioTexto = it },
                        label = { Text("Valor unit.") },
                        prefix = { Text("R$") },
                        isError = valorInvalido,
                        enabled = !bloqueadoPorBaixa,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    OutlinedTextField(
                        value = valorTexto, onValueChange = { valorTexto = it },
                        label = { Text("Valor total *") },
                        prefix = { Text("R$") },
                        isError = valorInvalido,
                        enabled = !bloqueadoPorBaixa,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (usarQuantidade) {
                Text(
                    "Valor total: R$ %.2f".format(Locale("pt", "BR"), valorTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valorInvalido) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (valorInvalido) {
                Text(
                    if (usarQuantidade) "Informe quantidade e valor unitário maiores que zero" else "Informe um valor maior que zero",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(6.dp))
            if (!ehFluxo) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        formaPagamento = if (formaPagamento == FormaPagamento.DINHEIRO)
                            FormaPagamento.CARTAO
                        else
                            FormaPagamento.DINHEIRO
                    },
                    enabled = !bloqueadoPorBaixa
                ) {
                    Icon(
                        if (formaPagamento == FormaPagamento.CARTAO) Icons.Default.CreditCard else Icons.Default.Payments,
                        contentDescription = if (formaPagamento == FormaPagamento.CARTAO)
                            "Alternar para dinheiro"
                        else
                            "Alternar para cartão"
                    )
                }
                if (formaPagamento == FormaPagamento.CARTAO) {
                    OutlinedTextField(
                        value = numParcelasTexto, onValueChange = { numParcelasTexto = it },
                        label = { Text("Nº de duplicatas") },
                        enabled = !bloqueadoPorBaixa,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        "Dinheiro à vista — vence em ${formatarData(dataHoraMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (formaPagamento == FormaPagamento.CARTAO) {
                Spacer(Modifier.height(6.dp))
                if (cartoesDisponiveis.isEmpty()) {
                    Text(
                        "Nenhum cartão cadastrado. Cadastre um nas Configurações antes de continuar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expandirDropdownCartao && !bloqueadoPorBaixa,
                        onExpandedChange = { if (!bloqueadoPorBaixa) expandirDropdownCartao = it }
                    ) {
                        OutlinedTextField(
                            value = cartaoSelecionado?.let { "${it.nome} (fecha dia ${it.melhorDia}, vence dia ${it.diaVencimento})" }
                                ?: "Selecione um cartão",
                            onValueChange = {},
                            readOnly = true,
                            enabled = !bloqueadoPorBaixa,
                            isError = cartaoObrigatorioFaltando,
                            supportingText = { if (cartaoObrigatorioFaltando) Text("Selecione um cartão") },
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirDropdownCartao) }
                        )
                        ExposedDropdownMenu(
                            expanded = expandirDropdownCartao,
                            onDismissRequest = { expandirDropdownCartao = false }
                        ) {
                            cartoesDisponiveis.forEach { cartao ->
                                DropdownMenuItem(
                                    text = { Text("${cartao.nome} (fecha dia ${cartao.melhorDia}, vence dia ${cartao.diaVencimento})") },
                                    onClick = {
                                        cartaoIdSelecionado = cartao.id
                                        expandirDropdownCartao = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }

            Spacer(Modifier.height(6.dp))
            Text("Data e hora", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            SeletorDataHora(
                dataHoraMillis = dataHoraMillis,
                onDataHoraChange = { dataHoraMillis = it }
            )

            Spacer(Modifier.height(10.dp))
            if (!ehFluxo) {
            Text("Prévia das duplicatas", style = MaterialTheme.typography.labelLarge)
            if (!bloqueadoPorBaixa && previa.size > 1) {
                Text(
                    "Pode ajustar o valor de cada duplicata individualmente — a última é sempre " +
                        "recalculada sozinha pra fechar certinho com o valor total.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            if (previaFinal.isEmpty()) {
                Text(
                    "Digite um valor para ver a prévia.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(previaFinal) { p ->
                        val ehUltima = p.numero == numParcelas
                        val editavel = !bloqueadoPorBaixa && !ehUltima && numParcelas > 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                "${p.numero}/$numParcelas",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.width(40.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    formatarData(p.dataVencimento),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (ehUltima && numParcelas > 1) {
                                    Text(
                                        "ajustada automaticamente",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (editavel && numeroEditando == p.numero) {
                                OutlinedTextField(
                                    value = valoresManuaisTexto[p.numero]
                                        ?: "%.2f".format(Locale("pt", "BR"), p.valorOriginal).replace(".", ","),
                                    onValueChange = { texto ->
                                        valoresManuaisTexto = valoresManuaisTexto + (p.numero to texto)
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { numeroEditando = null },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Confirmar valor",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(48.dp)
                                )
                            } else {
                                Text(formatarMoeda(p.valorOriginal), style = MaterialTheme.typography.bodyMedium)
                                if (editavel) {
                                    IconButton(
                                        onClick = { numeroEditando = p.numero },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Editar valor da duplicata ${p.numero}",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
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
                    onClick = {
                        tentouSalvar = true
                        if (!valido) return@Button
                        salvando = true
                        if (ehFluxo) {
                            if (ehEdicao) {
                                viewModel.editarMovimentacaoFluxo(
                                    movimentacaoExistente = movimentacaoParaEditar!!,
                                    tipo = tipoMovimento,
                                    descricao = descricao,
                                    valorTotal = valorTotal,
                                    dataCriacao = dataHoraMillis
                                ) {
                                    salvando = false
                                    aoVoltar()
                                }
                            } else {
                                viewModel.criarMovimentacaoFluxo(
                                    clienteId = clienteId,
                                    categoriaId = categoriaId,
                                    tipo = tipoMovimento,
                                    descricao = descricao,
                                    valorTotal = valorTotal,
                                    dataCriacao = dataHoraMillis
                                ) {
                                    salvando = false
                                    aoVoltar()
                                }
                            }
                        } else if (ehEdicao) {
                            if (bloqueadoPorBaixa) {
                                viewModel.editarDataMovimentacao(
                                    movimentacaoExistente = movimentacaoParaEditar!!,
                                    novaDescricao = descricao,
                                    novaDataCriacao = dataHoraMillis
                                ) {
                                    salvando = false
                                    aoVoltar()
                                }
                            } else {
                                viewModel.editarMovimentacao(
                                    movimentacaoExistente = movimentacaoParaEditar!!,
                                    descricao = descricao,
                                    valorTotal = valorTotal,
                                    numParcelas = numParcelas,
                                    diaVencimento = diaVencimento,
                                    formaPagamento = formaPagamento,
                                    cartao = cartaoSelecionado,
                                    dataCriacao = dataHoraMillis,
                                    valoresManuaisParcelas = valoresManuaisParcelas
                                ) {
                                    salvando = false
                                    aoVoltar()
                                }
                            }
                        } else {
                            viewModel.criarMovimentacao(
                                clienteId = clienteId,
                                categoriaId = categoriaId,
                                tipo = tipo,
                                descricao = descricao,
                                valorTotal = valorTotal,
                                numParcelas = numParcelas,
                                diaVencimento = diaVencimento,
                                formaPagamento = formaPagamento,
                                cartao = cartaoSelecionado,
                                dataCriacao = dataHoraMillis,
                                valoresManuaisParcelas = valoresManuaisParcelas
                            ) {
                                salvando = false
                                aoVoltar()
                            }
                        }
                    },
                    enabled = !salvando,
                    modifier = Modifier.weight(1f)
                ) {
                    if (salvando) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (ehEdicao) "Salvar" else "Confirmar")
                }
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

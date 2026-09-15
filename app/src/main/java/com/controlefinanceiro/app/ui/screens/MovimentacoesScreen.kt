package com.controlefinanceiro.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Categoria
import com.controlefinanceiro.app.ui.components.IconeDirecaoSaldo
import com.controlefinanceiro.app.data.Cliente
import com.controlefinanceiro.app.data.LinhaCsvFluxo
import com.controlefinanceiro.app.data.Movimentacao
import com.controlefinanceiro.app.data.Pagamento
import com.controlefinanceiro.app.data.Parcela
import com.controlefinanceiro.app.data.TipoMovimentacao
import com.controlefinanceiro.app.data.parsearCsvFluxo
import com.controlefinanceiro.app.viewmodel.AppViewModel
import java.util.Locale
import com.controlefinanceiro.app.ui.theme.AzulFluxo
import com.controlefinanceiro.app.ui.theme.VerdeSaldo
import com.controlefinanceiro.app.ui.theme.VermelhoSaldo
import com.controlefinanceiro.app.data.formatarData
import com.controlefinanceiro.app.data.formatarDataHora
import com.controlefinanceiro.app.data.formatarMoeda


private enum class AbaCategoria { MOVIMENTACOES, EXTRATO }

/** Nível de detalhe mostrado na aba Movimentações:
 *  - PRINCIPAL: só a linha resumo de cada movimentação (com o contador de parcelas).
 *  - PRINCIPAL_BAIXADO: resumo + apenas as duplicatas que já têm alguma baixa
 *    registrada (parcial ou paga), detalhadas e coloridas (valor da duplicata,
 *    valor pago e saldo). Duplicatas ainda em aberto (sem nenhuma baixa) não
 *    aparecem nesse modo.
 *  - PRINCIPAL_PARCELAS: resumo + cada duplicata numa listagem simples (só
 *    número, vencimento e status), sem cores e sem as baixas individualmente. */
private enum class ModoDetalheMovimentacoes(val rotulo: String) {
    PRINCIPAL("Principal"),
    PRINCIPAL_BAIXADO("Principal + baixados"),
    PRINCIPAL_PARCELAS("Principal + parcelas")
}

// Evento de extrato: pode ser a criação de uma cobrança ou uma baixa registrada nela
// — juntos formam a linha do tempo completa da categoria.
private sealed class EventoExtrato(val data: Long) {
    class Criacao(data: Long, val mov: Movimentacao) : EventoExtrato(data)
    /** Uma baixa registrada. Normalmente afeta duplicatas de uma só movimentação, mas
     *  quando o valor pago é dividido entre duplicatas de mais de uma movimentação de
     *  uma vez, [itens] tem mais de um par (mov + pagamento) — todos compartilham o
     *  mesmo baixaId e aparecem juntos aqui, como uma única linha no extrato. */
    class Baixa(data: Long, val itens: List<Pair<Movimentacao, Pagamento>>) : EventoExtrato(data) {
        val valorTotal: Double get() = itens.sumOf { it.second.valorPago }
    }
}

/** Tela "do meio": fica dentro de uma categoria específica (ex: "Construção a pagar")
 *  e lista as movimentações (cobranças parceladas) dela — é aqui que se cria uma nova
 *  movimentação e se registra baixa, sempre dentro do tipo fixo da categoria. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovimentacoesScreen(
    viewModel: AppViewModel,
    cliente: Cliente,
    categoria: Categoria,
    aoAbrirMovimentacao: (Movimentacao) -> Unit,
    aoAbrirBaixa: (List<Pair<Movimentacao, Pagamento>>) -> Unit,
    aoNovaMovimentacao: (tipoFluxo: String?) -> Unit,
    aoDarBaixa: () -> Unit,
    aoVoltar: () -> Unit
) {
    LaunchedEffect(cliente.id, categoria.id) { viewModel.observarMovimentacoes(cliente.id, categoria.id) }
    LaunchedEffect(cliente.id) { viewModel.observarCategorias(cliente.id) }
    val movimentacoes by viewModel.movimentacoes
    val clientesAtuais by viewModel.clientes
    val categoriasAtuais by viewModel.categorias
    // Nome do cliente/categoria podem ter sido editados enquanto essa tela estava em
    // segundo plano (ex: renomeados a partir da tela de categorias) — busca sempre a
    // versão mais atual em vez do que foi recebido na navegação.
    val clienteAtual = clientesAtuais.firstOrNull { it.id == cliente.id } ?: cliente
    val categoriaAtual = categoriasAtuais.firstOrNull { it.id == categoria.id } ?: categoria
    val snackbarHostState = remember { SnackbarHostState() }
    val erro = viewModel.erro.value
    var aba by remember { mutableStateOf(AbaCategoria.EXTRATO) }
    var mostrarQuitadas by remember { mutableStateOf(false) }
    var modoDetalhe by remember { mutableStateOf(ModoDetalheMovimentacoes.PRINCIPAL) }
    var expandirMenuDetalhe by remember { mutableStateOf(false) }
    var detalharExtrato by remember { mutableStateOf(false) }

    // Importação de CSV (só faz sentido em categoria de fluxo).
    val context = LocalContext.current
    var linhasImportadas by remember { mutableStateOf<List<LinhaCsvFluxo>?>(null) }
    var erroLeituraArquivo by remember { mutableStateOf<String?>(null) }
    var importando by remember { mutableStateOf(false) }
    val seletorArquivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val texto = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            if (texto.isNullOrBlank()) {
                erroLeituraArquivo = "O arquivo está vazio."
            } else {
                linhasImportadas = parsearCsvFluxo(texto)
            }
        } catch (e: Exception) {
            erroLeituraArquivo = "Não foi possível ler o arquivo: ${e.message}"
        }
    }

    LaunchedEffect(erro) {
        if (erro != null) {
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    val corCategoria = if (categoriaAtual.tipo == TipoMovimentacao.RECEBER) VerdeSaldo else VermelhoSaldo
    val ehFluxo = categoriaAtual.tipo == TipoMovimentacao.FLUXO
    val totalEmAberto = movimentacoes.sumOf { it.valorEmAberto }
    // Numa categoria de fluxo não existe "em aberto" (tudo já nasce quitado) — o que
    // interessa é o total recebido, o total pago e o saldo líquido entre os dois,
    // calculado na hora a partir da lista de movimentações que já está sendo escutada
    // ao vivo (não precisa de nenhum campo denormalizado à parte pra isso).
    val totalRecebidoFluxo = movimentacoes.filter { it.tipo == TipoMovimentacao.RECEBER }.sumOf { it.valorTotal }
    val totalPagoFluxo = movimentacoes.filter { it.tipo == TipoMovimentacao.PAGAR }.sumOf { it.valorTotal }
    val saldoFluxo = totalRecebidoFluxo - totalPagoFluxo

    val movimentacoesVisiveis = remember(movimentacoes, mostrarQuitadas) {
        if (mostrarQuitadas) movimentacoes else movimentacoes.filterNot { it.quitada }
    }

    val eventosExtrato = remember(movimentacoes) {
        val lista = mutableListOf<EventoExtrato>()
        movimentacoes.forEach { mov -> lista += EventoExtrato.Criacao(mov.dataCriacao, mov) }

        // Agrupa as baixas de todas as movimentações pelo baixaId — assim, uma baixa que
        // cobriu duplicatas de mais de uma movimentação de uma vez aparece como uma única
        // linha no extrato, em vez de uma linha separada por movimentação. Baixas antigas
        // sem baixaId (dados de antes dessa mudança) usam o próprio id do pagamento, então
        // continuam aparecendo individualmente, como já apareciam.
        val baixasPorGrupo = mutableMapOf<String, MutableList<Pair<Movimentacao, Pagamento>>>()
        movimentacoes.forEach { mov ->
            mov.historicoPagamentos.forEach { pag ->
                val chaveGrupo = pag.baixaId.ifBlank { pag.id }
                baixasPorGrupo.getOrPut(chaveGrupo) { mutableListOf() } += mov to pag
            }
        }
        baixasPorGrupo.values.forEach { itens ->
            lista += EventoExtrato.Baixa(itens.first().second.data, itens)
        }

        // Mais recente primeiro.
        lista.sortedByDescending { it.data }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoriaAtual.nome) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (ehFluxo) {
                        IconButton(onClick = {
                            erroLeituraArquivo = null
                            seletorArquivo.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                        }) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Importar CSV")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomAppBar {
                if (ehFluxo) {
                    // Duas ações diretas em vez de uma só com um seletor dentro do formulário
                    // — já abre a tela certa, sem passo extra.
                    Button(
                        onClick = { aoNovaMovimentacao(TipoMovimentacao.RECEBER) },
                        colors = ButtonDefaults.buttonColors(containerColor = VerdeSaldo),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Recebido")
                    }
                    Button(
                        onClick = { aoNovaMovimentacao(TipoMovimentacao.PAGAR) },
                        colors = ButtonDefaults.buttonColors(containerColor = VermelhoSaldo),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Pago")
                    }
                } else {
                    Button(
                        onClick = { aoNovaMovimentacao(null) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Nova cobrança")
                    }
                    Button(
                        onClick = aoDarBaixa,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        enabled = movimentacoes.any { it.valorEmAberto > 0.01 }
                    ) {
                        Text("Dar baixa")
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                clienteAtual.nome,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    if (ehFluxo) {
                        Text("Fluxo de caixa", style = MaterialTheme.typography.labelLarge, color = AzulFluxo)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconeDirecaoSaldo(positivo = saldoFluxo >= 0)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                formatarMoeda(saldoFluxo),
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (saldoFluxo >= 0) VerdeSaldo else VermelhoSaldo
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recebido: R$ %.2f".format(Locale("pt", "BR"), totalRecebidoFluxo), color = VerdeSaldo, style = MaterialTheme.typography.bodySmall)
                            Text("Pago: R$ %.2f".format(Locale("pt", "BR"), totalPagoFluxo), color = VermelhoSaldo, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(
                            if (categoriaAtual.tipo == TipoMovimentacao.RECEBER) "A receber" else "A pagar",
                            style = MaterialTheme.typography.labelLarge,
                            color = corCategoria
                        )
                        Text(
                            "R$ %.2f em aberto".format(Locale("pt", "BR"), totalEmAberto),
                            style = MaterialTheme.typography.headlineMedium,
                            color = corCategoria
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (!ehFluxo) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SegmentedButton(
                        selected = aba == AbaCategoria.EXTRATO,
                        onClick = { aba = AbaCategoria.EXTRATO },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Extrato") }
                    SegmentedButton(
                        selected = aba == AbaCategoria.MOVIMENTACOES,
                        onClick = { aba = AbaCategoria.MOVIMENTACOES },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Movimentações") }
                }
                Spacer(Modifier.height(4.dp))

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (aba == AbaCategoria.MOVIMENTACOES) {
                        Box {
                            TextButton(onClick = { expandirMenuDetalhe = true }) {
                                Text(modoDetalhe.rotulo, style = MaterialTheme.typography.bodySmall)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Nível de detalhe")
                            }
                            DropdownMenu(
                                expanded = expandirMenuDetalhe,
                                onDismissRequest = { expandirMenuDetalhe = false }
                            ) {
                                ModoDetalheMovimentacoes.values().forEach { modo ->
                                    DropdownMenuItem(
                                        text = { Text(modo.rotulo) },
                                        onClick = {
                                            modoDetalhe = modo
                                            expandirMenuDetalhe = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { mostrarQuitadas = !mostrarQuitadas }) {
                            Icon(
                                if (mostrarQuitadas) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewAgenda,
                                contentDescription = if (mostrarQuitadas) "Ocultar quitadas" else "Mostrar quitadas"
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                        IconButton(onClick = { detalharExtrato = !detalharExtrato }) {
                            Icon(
                                if (detalharExtrato) Icons.AutoMirrored.Filled.ViewList else Icons.Default.ViewAgenda,
                                contentDescription = if (detalharExtrato) "Ocultar detalhes" else "Detalhar"
                            )
                        }
                    }
                }
            }

            val abaEfetiva = if (ehFluxo) AbaCategoria.EXTRATO else aba
            when (abaEfetiva) {
                AbaCategoria.MOVIMENTACOES -> {
                    if (movimentacoesVisiveis.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (movimentacoes.isEmpty())
                                    "Nenhuma movimentação ainda. Toque em \"Nova cobrança\" para lançar a primeira."
                                else
                                    "Nenhuma movimentação em aberto. Marque \"Mostrar quitadas\" para ver as já quitadas.",
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(movimentacoesVisiveis, key = { it.id }) { mov ->
                                val parcelasPagas = mov.parcelas.count { it.status == "paga" }
                                ListItem(
                                    headlineContent = { Text(mov.descricao) },
                                    supportingContent = {
                                        Column {
                                            Text("Desde ${formatarData(mov.dataCriacao)}")
                                            Text(
                                                if (mov.quitada) "Quitada — R$ %.2f".format(Locale("pt", "BR"), mov.valorTotal)
                                                else "Em aberto: R$ %.2f de R$ %.2f".format(Locale("pt", "BR"), mov.valorEmAberto, mov.valorTotal)
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Text(
                                            "$parcelasPagas/${mov.numParcelas}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (mov.quitada) VerdeSaldo else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.clickable { aoAbrirMovimentacao(mov) }
                                )
                                HorizontalDivider()

                                // Duplicatas detalhadas e coloridas (modo "Principal + baixados"),
                                // mostrando só as que já receberam alguma baixa (parcial ou paga) —
                                // as que ainda estão em aberto (sem nenhum pagamento) ficam de fora.
                                if (modoDetalhe == ModoDetalheMovimentacoes.PRINCIPAL_BAIXADO) {
                                    mov.parcelas
                                        .filter { it.valorPago > 0.0 }
                                        .sortedBy { it.numero }
                                        .forEach { p ->
                                            LinhaDuplicataDetalhada(parcela = p, numParcelas = mov.numParcelas)
                                            HorizontalDivider()
                                        }
                                }

                                // Duplicatas em listagem simples, sem cores (modo "Principal + parcelas"),
                                // igual à listagem que já existia antes — sem mostrar as baixas individualmente.
                                if (modoDetalhe == ModoDetalheMovimentacoes.PRINCIPAL_PARCELAS) {
                                    mov.parcelas.sortedBy { it.numero }.forEach { p ->
                                        LinhaDuplicataSimples(parcela = p, numParcelas = mov.numParcelas)
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                AbaCategoria.EXTRATO -> {
                    if (eventosExtrato.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Nenhuma movimentação registrada ainda.")
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(eventosExtrato) { evento ->
                                when (evento) {
                                    is EventoExtrato.Criacao -> {
                                        val mov = evento.mov
                                        val valorTexto = formatarMoeda(mov.valorTotal)
                                        if (ehFluxo) {
                                            // Numa categoria de fluxo não existe o conceito de "crédito
                                            // concedido" — RECEBER aqui é literalmente dinheiro que entrou
                                            // (verde, alinhado à direita, como um recebimento) e PAGAR é
                                            // dinheiro que saiu (vermelho, à esquerda, como uma despesa).
                                            val recebido = mov.tipo == TipoMovimentacao.RECEBER
                                            LinhaEventoExtrato(
                                                titulo = mov.descricao,
                                                subtitulo = if (recebido) "Recebido" else "Pago",
                                                valorTexto = valorTexto,
                                                cor = if (recebido) VerdeSaldo else VermelhoSaldo,
                                                dataTexto = formatarDataHora(evento.data),
                                                alinharDireita = recebido,
                                                onClick = { aoAbrirMovimentacao(mov) }
                                            )
                                        } else {
                                            // Criação de uma cobrança: do ponto de vista do fluxo de caixa,
                                            // "a receber" é uma Saída (você concedeu o crédito, vermelho e
                                            // negativo) e "a pagar" é uma Entrada (você recebeu o crédito,
                                            // verde e positivo). Sempre alinhada à esquerda, como uma "compra".
                                            val ehSaida = mov.tipo == TipoMovimentacao.RECEBER
                                            val corEvento = if (ehSaida) VermelhoSaldo else VerdeSaldo
                                            LinhaEventoExtrato(
                                                titulo = mov.descricao,
                                                valorTexto = valorTexto,
                                                cor = corEvento,
                                                dataTexto = formatarDataHora(evento.data),
                                                alinharDireita = false,
                                                onClick = { aoAbrirMovimentacao(mov) }
                                            )
                                        }
                                        HorizontalDivider()
                                    }
                                    is EventoExtrato.Baixa -> {
                                        // Baixa/pagamento registrado: inverte em relação à criação —
                                        // baixa de "a receber" é Entrada (dinheiro chegando, verde e
                                        // positivo), baixa de "a pagar" é Saída (dinheiro saindo,
                                        // vermelho e negativo). Sempre alinhada à direita, como um
                                        // "pagamento". Clicável: abre o detalhe com tudo que essa baixa
                                        // quitou (em uma ou mais movimentações).
                                        val primeiraMov = evento.itens.first().first
                                        val ehSaida = primeiraMov.tipo != TipoMovimentacao.RECEBER
                                        val corEvento = if (ehSaida) VermelhoSaldo else VerdeSaldo
                                        val valorTexto = formatarMoeda(evento.valorTotal)
                                        // Mostra o(s) nome(s) da(s) movimentação(ões) quitada(s) por essa
                                        // baixa, menorzinho, embaixo do valor — sempre, mesmo quando é
                                        // uma única movimentação envolvida.
                                        val descricoes = evento.itens.map { it.first.descricao }.distinct()
                                        val subtitulo = descricoes.joinToString(", ")
                                        LinhaEventoExtrato(
                                            titulo = null,
                                            subtitulo = subtitulo,
                                            valorTexto = valorTexto,
                                            cor = corEvento,
                                            dataTexto = formatarDataHora(evento.data),
                                            alinharDireita = true,
                                            onClick = { aoAbrirBaixa(evento.itens) }
                                        )
                                        HorizontalDivider()

                                        // Duplicatas quitadas por essa baixa, detalhadas (só com "Detalhar" ligado),
                                        // percorrendo cada movimentação envolvida nela.
                                        if (detalharExtrato) {
                                            evento.itens.forEach { (mov, pagamento) ->
                                                val numerosQuitados = pagamento.parcelasNumeros.toSet()
                                                mov.parcelas
                                                    .filter { it.numero in numerosQuitados }
                                                    .sortedBy { it.numero }
                                                    .forEach { p ->
                                                        LinhaDuplicataDetalhada(
                                                            parcela = p,
                                                            numParcelas = mov.numParcelas,
                                                            valorPagoNestaBaixa = pagamento.parcelasAplicadas[p.numero.toString()],
                                                            nomeMovimentacao = mov.descricao
                                                        )
                                                        HorizontalDivider()
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (erroLeituraArquivo != null) {
        AlertDialog(
            onDismissRequest = { erroLeituraArquivo = null },
            title = { Text("Não deu pra importar") },
            text = { Text(erroLeituraArquivo!!) },
            confirmButton = {
                TextButton(onClick = { erroLeituraArquivo = null }) { Text("Entendi") }
            }
        )
    }

    linhasImportadas?.let { linhas ->
        val validas = linhas.filter { it.valida }
        val invalidas = linhas.filter { !it.valida }
        AlertDialog(
            onDismissRequest = { if (!importando) linhasImportadas = null },
            title = { Text("Revisar importação") },
            text = {
                Column {
                    Text(
                        if (invalidas.isEmpty())
                            "${validas.size} lançamento(s) prontos pra importar."
                        else
                            "${validas.size} prontos, ${invalidas.size} com problema (serão ignorados).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(linhas, key = { it.numeroLinha }) { linha ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    if (linha.valida) Icons.Default.Check else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (linha.valida) VerdeSaldo else VermelhoSaldo,
                                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Linha ${linha.numeroLinha}: ${linha.descricao.ifBlank { "(sem descrição)" }}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (linha.valida) {
                                        Text(
                                            "${if (linha.tipo == TipoMovimentacao.RECEBER) "Recebido" else "Pago"} • " +
                                                "R$ ${"%.2f".format(Locale("pt", "BR"), linha.valor)} • ${linha.dataTexto}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            linha.erro.orEmpty(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VermelhoSaldo
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importando = true
                        viewModel.importarLancamentosFluxo(clienteAtual.id, categoriaAtual.id, validas) {
                            importando = false
                            linhasImportadas = null
                        }
                    },
                    enabled = !importando && validas.isNotEmpty()
                ) { Text(if (importando) "Importando…" else "Importar ${validas.size}") }
            },
            dismissButton = {
                TextButton(onClick = { linhasImportadas = null }, enabled = !importando) { Text("Cancelar") }
            }
        )
    }
}

/** Uma linha do extrato no formato "compra/pagamento": título (opcional) e valor
 *  empilhados e alinhados à esquerda (compra) ou à direita (pagamento), com a data
 *  sempre centralizada embaixo, ocupando a largura toda — mesmo padrão visual de
 *  extratos de fiado/caderneta. */
@Composable
private fun LinhaEventoExtrato(
    titulo: String?,
    valorTexto: String,
    cor: Color,
    dataTexto: String,
    alinharDireita: Boolean,
    subtitulo: String? = null,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                horizontalAlignment = if (alinharDireita) Alignment.End else Alignment.Start,
                modifier = Modifier.align(if (alinharDireita) Alignment.CenterEnd else Alignment.CenterStart)
            ) {
                if (titulo != null) {
                    Text(titulo, color = cor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Text(valorTexto, color = cor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (subtitulo != null) {
                    Text(
                        subtitulo,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = if (alinharDireita) TextAlign.End else TextAlign.Start
                    )
                }
            }
        }
        Text(
            dataTexto,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Linha simples de uma duplicata, sem cores nem valor pago/falta — só número,
 *  vencimento, status textual e o valor original. É a listagem "como já era antes",
 *  usada no modo "Principal + parcelas". */
@Composable
private fun LinhaDuplicataSimples(parcela: Parcela, numParcelas: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${parcela.numero}/$numParcelas — ${formatarData(parcela.dataVencimento)} — " +
                when (parcela.status) {
                    "paga" -> "paga"
                    "parcial" -> "parcial (R$ %.2f/R$ %.2f)".format(Locale("pt", "BR"), parcela.valorPago, parcela.valorOriginal)
                    else -> "em aberto"
                },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(formatarMoeda(parcela.valorOriginal), style = MaterialTheme.typography.bodySmall)
    }
}

/** Linha indentada com o detalhamento colorido de uma duplicata: valor original,
 *  quanto já foi pago (verde) e quanto falta (vermelho), ou "Quitada" (verde) se
 *  não sobrar nada. Se `valorPagoNestaBaixa` for passado (visão do extrato), mostra
 *  quanto especificamente essa baixa aplicou, em vez do total pago na duplicata.
 *  Se `nomeMovimentacao` for passado, mostra o nome da cobrança junto (útil no
 *  extrato, onde a linha não está mais aninhada visualmente sob a movimentação). */
@Composable
private fun LinhaDuplicataDetalhada(
    parcela: Parcela,
    numParcelas: Int,
    valorPagoNestaBaixa: Double? = null,
    nomeMovimentacao: String? = null
) {
    val valorPagoExibido = valorPagoNestaBaixa ?: parcela.valorPago
    val valorQueFalta = (parcela.valorOriginal - parcela.valorPago).coerceAtLeast(0.0)
    Row(
        Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (nomeMovimentacao != null) "$nomeMovimentacao — parcela ${parcela.numero}/$numParcelas"
                else "Duplicata ${parcela.numero}/$numParcelas",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Vence ${formatarData(parcela.dataVencimento)} • R$ %.2f".format(Locale("pt", "BR"), parcela.valorOriginal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "Pago: R$ %.2f".format(Locale("pt", "BR"), valorPagoExibido),
                color = VerdeSaldo,
                style = MaterialTheme.typography.bodySmall
            )
            if (valorQueFalta > 0.01) {
                Text(
                    "Falta: R$ %.2f".format(Locale("pt", "BR"), valorQueFalta),
                    color = VermelhoSaldo,
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                val textoQuitada = parcela.dataUltimoPagamento?.let {
                    "Paga em ${formatarData(it)}"
                } ?: "Paga"
                Text(textoQuitada, color = VerdeSaldo, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

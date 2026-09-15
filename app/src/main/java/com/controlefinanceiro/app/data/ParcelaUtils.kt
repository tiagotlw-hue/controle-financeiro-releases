package com.controlefinanceiro.app.data

import java.util.Calendar
import java.util.UUID
import kotlin.math.min
import kotlin.math.round
import java.util.Locale

object ParcelaUtils {

    /**
     * Gera a duplicata única de uma movimentação de FLUXO DE CAIXA: já nasce quitada,
     * com o valor cheio pago na hora da criação — não fica em aberto, não aparece pra
     * dar baixa depois. É só um registro de "isso entrou/saiu agora".
     */
    fun gerarParcelaQuitada(valorTotal: Double, dataCriacao: Long): List<Parcela> {
        val valor = round(valorTotal * 100) / 100.0
        return listOf(
            Parcela(
                numero = 1,
                valorOriginal = valor,
                valorPago = valor,
                dataVencimento = dataCriacao,
                status = "paga",
                dataUltimoPagamento = dataCriacao
            )
        )
    }

    /**
     * Gera a duplicata única de uma movimentação em dinheiro à vista: vence no mesmo
     * dia da data da cobrança (sem mês seguinte, sem "dia de vencimento" configurável).
     */
    fun gerarParcelaAvista(valorTotal: Double, dataCriacao: Long): List<Parcela> {
        return listOf(
            Parcela(
                numero = 1,
                valorOriginal = round(valorTotal * 100) / 100.0,
                dataVencimento = dataCriacao
            )
        )
    }

    /**
     * Gera as parcelas/duplicatas de uma movimentação paga em dinheiro.
     * Cada parcela vence sempre no mesmo "diaVencimento" (ex: dia 15),
     * começando no mês seguinte à criação. Se o mês não tiver esse dia
     * (ex: dia 31 em fevereiro), usa o último dia do mês.
     * A diferença de arredondamento é jogada na última parcela.
     */
    fun gerarParcelas(
        valorTotal: Double,
        numParcelas: Int,
        dataCriacao: Long,
        diaVencimento: Int
    ): List<Parcela> {
        // O limite de 360 (30 anos de mensalidades — bem mais que qualquer parcelamento
        // real) existia só como ">= 1" antes, sem teto: um erro de digitação (dedo
        // gordo adicionando um zero a mais) ou um número absurdo digitado no campo
        // gerava uma lista enorme de parcelas — nada trava na hora de gerar em si, mas
        // o documento resultante nem cabe no limite de tamanho do Firestore, e a tela
        // de prévia das duplicatas tentaria renderizar tudo isso de uma vez.
        require(numParcelas in 1..360) { "Número de parcelas deve estar entre 1 e 360 (recebido: $numParcelas)" }
        // O java.util.Calendar é "lenient" por padrão: set(DAY_OF_MONTH, 0) não dá erro,
        // ele silenciosamente rola pro ÚLTIMO DIA DO MÊS ANTERIOR (e valores negativos
        // rolam ainda mais pra trás). Sem essa validação, um diaVencimento <= 0 gera uma
        // duplicata com vencimento num mês errado — não trava, só fica silenciosamente
        // errado, o pior tipo de bug numa data financeira. As telas que chamam essa
        // função já limitam com .coerceIn(1, 31) antes de chegar aqui, mas essa função
        // não deveria confiar cegamente nisso.
        require(diaVencimento in 1..31) { "Dia de vencimento deve estar entre 1 e 31 (recebido: $diaVencimento)" }
        val calendarBase = Calendar.getInstance().apply { timeInMillis = dataCriacao }
        return gerarParcelasAPartirDoMesBase(valorTotal, numParcelas, calendarBase, diaVencimento, offsetMeses = 1)
    }

    /**
     * Gera as parcelas/duplicatas de uma movimentação paga no cartão, seguindo a regra
     * de fechamento de fatura: se o dia da compra for >= "melhorDia" (dia de fechamento),
     * a primeira parcela vai pra fatura que vence no "diaVencimento" do mês SEGUINTE ao da
     * compra. Se for antes do "melhorDia", vai pra fatura que vence no "diaVencimento" do
     * MESMO mês da compra. As parcelas seguintes vencem sempre um mês depois da anterior.
     */
    fun gerarParcelasCartao(
        valorTotal: Double,
        numParcelas: Int,
        dataCriacao: Long,
        melhorDia: Int,
        diaVencimentoCartao: Int
    ): List<Parcela> {
        require(numParcelas in 1..360) { "Número de parcelas deve estar entre 1 e 360 (recebido: $numParcelas)" }
        require(melhorDia in 1..31) { "Melhor dia do cartão deve estar entre 1 e 31 (recebido: $melhorDia)" }
        require(diaVencimentoCartao in 1..31) { "Dia de vencimento do cartão deve estar entre 1 e 31 (recebido: $diaVencimentoCartao)" }
        val calCompra = Calendar.getInstance().apply { timeInMillis = dataCriacao }
        val diaCompra = calCompra.get(Calendar.DAY_OF_MONTH)
        val calBase = calCompra.clone() as Calendar
        if (diaCompra >= melhorDia) {
            calBase.add(Calendar.MONTH, 1)
        }
        return gerarParcelasAPartirDoMesBase(valorTotal, numParcelas, calBase, diaVencimentoCartao, offsetMeses = 0)
    }

    private fun gerarParcelasAPartirDoMesBase(
        valorTotal: Double,
        numParcelas: Int,
        calendarBase: Calendar,
        diaVencimento: Int,
        offsetMeses: Int
    ): List<Parcela> {
        val valorParcelaBase = round((valorTotal / numParcelas) * 100) / 100.0
        val parcelas = mutableListOf<Parcela>()
        var somaAcumulada = 0.0

        for (i in 1..numParcelas) {
            val cal = calendarBase.clone() as Calendar
            cal.add(Calendar.MONTH, offsetMeses + (i - 1))
            val ultimoDiaDoMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, min(diaVencimento, ultimoDiaDoMes))
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val valorDaParcela = if (i == numParcelas) {
                round((valorTotal - somaAcumulada) * 100) / 100.0
            } else {
                valorParcelaBase
            }
            somaAcumulada += valorDaParcela

            parcelas.add(
                Parcela(
                    numero = i,
                    valorOriginal = valorDaParcela,
                    dataVencimento = cal.timeInMillis
                )
            )
        }
        return parcelas
    }

    /**
     * Aplica valores digitados manualmente pra cada duplicata (exceto a última), sobre
     * uma lista já gerada por [gerarParcelas]/[gerarParcelasCartao] — só troca o
     * `valorOriginal` de cada uma, mantendo data de vencimento e tudo mais como estava.
     * A ÚLTIMA duplicata nunca é editada diretamente: o valor dela é sempre recalculado
     * como "o que sobra" (valorTotal menos a soma de todas as outras), pra garantir que a
     * soma de todas as duplicatas sempre feche certinho com o valor total da cobrança.
     *
     * `valoresManuais` é um mapa número da parcela -> valor digitado; parcelas que não
     * estão no mapa continuam com o valor gerado automaticamente (split igual).
     */
    fun aplicarValoresManuais(
        parcelasBase: List<Parcela>,
        valoresManuais: Map<Int, Double>,
        valorTotal: Double
    ): List<Parcela> {
        if (parcelasBase.isEmpty()) return parcelasBase
        val numUltima = parcelasBase.maxOf { it.numero }
        var somaOutras = 0.0
        val comValorManual = parcelasBase.map { p ->
            if (p.numero == numUltima) {
                p // a última é ajustada depois, abaixo
            } else {
                val valor = round((valoresManuais[p.numero] ?: p.valorOriginal) * 100) / 100.0
                somaOutras += valor
                p.copy(valorOriginal = valor)
            }
        }
        return comValorManual.map { p ->
            if (p.numero == numUltima) {
                p.copy(valorOriginal = round((valorTotal - somaOutras) * 100) / 100.0)
            } else {
                p
            }
        }
    }

    /**
     * Dado o valor digitado, seleciona automaticamente as parcelas em aberto mais antigas
     * (já ordenadas) até esse valor se esgotar. Usado tanto para pré-seleção automática
     * quanto para saber o total das parcelas marcadas manualmente.
     */
    fun selecionarMaisAntigasAte(abertasOrdenadas: List<ParcelaRef>, valor: Double): Set<Pair<String, Int>> {
        val selecionadas = mutableSetOf<Pair<String, Int>>()
        var restante = valor
        for (ref in abertasOrdenadas) {
            if (restante <= 0.0) break
            val faltante = round((ref.parcela.valorOriginal - ref.parcela.valorPago) * 100) / 100.0
            // Antes: selecionava a parcela sem checar isso, confiando cegamente que quem
            // chamou já tinha filtrado só parcelas realmente em aberto. Na prática, quem
            // chama filtra por status != "paga" — mas status e o valor pago de verdade
            // podem, em teoria, ficar dessincronizados (dado antigo, editado na mão,
            // etc). Sem essa checagem, uma parcela já quitada acabava "selecionada" do
            // mesmo jeito, só sem consumir nada do valor disponível (`faltante` = 0).
            if (faltante <= 0.0) continue
            selecionadas.add(ref.movimentacaoId to ref.parcela.numero)
            restante -= faltante
        }
        return selecionadas
    }

    /**
     * Aplica uma baixa (pagamento/recebimento) espalhando o valor pelas parcelas
     * selecionadas. Parcelas de várias movimentações podem estar envolvidas (por isso
     * o retorno é um mapa por movimentação). Guarda quanto foi aplicado em cada parcela
     * (parcelasAplicadas), pra dar pra reverter a baixa depois.
     *
     * Duas formas de distribuir o valor entre as parcelas selecionadas:
     * - `proporcional = false` (padrão): preenche na ordem em que aparecem em
     *   `selecionadas`, quitando cada uma por completo antes de passar pra próxima —
     *   se o valor não cobrir tudo, a última parcela tocada fica "parcial". É o
     *   comportamento certo quando a pessoa digitou um valor livre (ou um percentual
     *   sem ter marcado duplicatas específicas): quita as mais antigas primeiro.
     * - `proporcional = true`: aplica a MESMA proporção do valor total em cada parcela
     *   selecionada (ex: se o valor é 50% da soma das selecionadas, cada uma recebe
     *   50% do que falta dela, não só a primeira). É o comportamento certo quando a
     *   pessoa marcou duplicatas específicas e escolheu um percentual: todas elas devem
     *   ficar igualmente parciais, na mesma proporção.
     */
    fun aplicarBaixaConsolidada(
        movimentacoesPorId: Map<String, Movimentacao>,
        selecionadas: List<ParcelaRef>,
        valorPago: Double,
        descricaoManual: String = "",
        dataBaixa: Long = System.currentTimeMillis(),
        proporcional: Boolean = false
    ): Map<String, Movimentacao> {
        // Garante que a mesma parcela não seja processada duas vezes — se por algum
        // motivo (ex: bug de seleção na tela) a mesma referência vier repetida em
        // `selecionadas`, aplicar a baixa duas vezes nela faria a parcela ficar com
        // valorPago MAIOR que o valorOriginal dela, que é sempre um estado inválido.
        // (Confirmado com um teste antes desse fix: no modo proporcional, uma parcela
        // de R$ 100 duplicada na seleção terminava com valorPago = R$ 133,34.)
        val selecionadasSemDuplicata = selecionadas.distinctBy { it.movimentacaoId to it.parcela.numero }

        val agora = dataBaixa
        // Um único id pra essa transação de baixa inteira, mesmo que ela acabe sendo
        // dividida entre duplicatas de várias movimentações diferentes — é o que permite
        // depois juntar tudo numa linha só no extrato.
        val baixaId = UUID.randomUUID().toString()
        val resultado = mutableMapOf<String, Movimentacao>()
        // agrupa números de parcela e valor aplicado por movimentação, para montar o Pagamento
        val aplicadoPorMovimentacao = mutableMapOf<String, MutableMap<Int, Double>>()

        fun registrarAplicacao(movimentacaoId: String, numeroParcela: Int, valorAplicado: Double, mov: Movimentacao, parcelaAtual: Parcela) {
            val novoValorPago = round((parcelaAtual.valorPago + valorAplicado) * 100) / 100.0
            val novoStatus = if (novoValorPago >= parcelaAtual.valorOriginal - 0.005) "paga" else "parcial"
            val novasParcelas = mov.parcelas.map { p ->
                if (p.numero == numeroParcela) p.copy(valorPago = novoValorPago, status = novoStatus, dataUltimoPagamento = agora) else p
            }
            aplicadoPorMovimentacao.getOrPut(movimentacaoId) { mutableMapOf() }[numeroParcela] = valorAplicado
            resultado[movimentacaoId] = mov.copy(parcelas = novasParcelas)
        }

        if (proporcional) {
            // Descobre o quanto falta em cada parcela selecionada (ignorando as que já
            // estão pagas) e a mesma proporção (valorPago / total faltante) é aplicada
            // em todas elas.
            data class Alvo(val movimentacaoId: String, val numero: Int, val faltante: Double)
            val alvos = selecionadasSemDuplicata.mapNotNull { ref ->
                val mov = movimentacoesPorId[ref.movimentacaoId] ?: return@mapNotNull null
                val parcelaAtual = mov.parcelas.firstOrNull { it.numero == ref.parcela.numero } ?: return@mapNotNull null
                if (parcelaAtual.status == "paga") return@mapNotNull null
                val faltante = round((parcelaAtual.valorOriginal - parcelaAtual.valorPago) * 100) / 100.0
                if (faltante <= 0.0) return@mapNotNull null
                Alvo(ref.movimentacaoId, parcelaAtual.numero, faltante)
            }
            val totalFaltante = alvos.sumOf { it.faltante }
            if (totalFaltante > 0.0) {
                val proporcao = (valorPago / totalFaltante).coerceIn(0.0, 1.0)
                var somaAplicada = 0.0
                alvos.forEachIndexed { index, alvo ->
                    val mov = resultado[alvo.movimentacaoId] ?: movimentacoesPorId[alvo.movimentacaoId] ?: return@forEachIndexed
                    val parcelaAtual = mov.parcelas.firstOrNull { it.numero == alvo.numero } ?: return@forEachIndexed
                    // Na última duplicata da lista, joga a diferença de arredondamento nela,
                    // pra a soma aplicada fechar certinho com o valor total informado.
                    val valorAplicado = (if (index == alvos.lastIndex) {
                        round((valorPago - somaAplicada) * 100) / 100.0
                    } else {
                        round(alvo.faltante * proporcao * 100) / 100.0
                    }).coerceIn(0.0, alvo.faltante)
                    somaAplicada = round((somaAplicada + valorAplicado) * 100) / 100.0
                    registrarAplicacao(alvo.movimentacaoId, alvo.numero, valorAplicado, mov, parcelaAtual)
                }
            }
        } else {
            // Preenche na ordem em que aparecem em `selecionadas`, quitando cada uma por
            // completo antes de passar pra próxima.
            var restante = valorPago
            for (ref in selecionadasSemDuplicata) {
                if (restante <= 0.0) break
                val mov = resultado[ref.movimentacaoId] ?: movimentacoesPorId[ref.movimentacaoId] ?: continue
                val parcelaAtual = mov.parcelas.firstOrNull { it.numero == ref.parcela.numero } ?: continue
                if (parcelaAtual.status == "paga") continue

                val faltante = round((parcelaAtual.valorOriginal - parcelaAtual.valorPago) * 100) / 100.0
                val valorAplicado = min(restante, faltante)
                restante = round((restante - valorAplicado) * 100) / 100.0
                registrarAplicacao(ref.movimentacaoId, parcelaAtual.numero, valorAplicado, mov, parcelaAtual)
            }
        }

        // adiciona o histórico de pagamento em cada movimentação afetada
        return resultado.mapValues { (movId, mov) ->
            val aplicado = aplicadoPorMovimentacao[movId].orEmpty()
            val numeros = aplicado.keys.sorted()
            // Valor que ESSA movimentação recebeu — quando a baixa é dividida entre
            // várias movimentações (baixa múltipla), isso é só uma fatia do `valorPago`
            // total da transação, não o total inteiro. Usar `valorPago` aqui direto seria
            // enganoso: quem olha o histórico de uma movimentação específica veria "Baixa
            // de R$ 200,00" nela mesmo que só R$ 50 tenham ido pra essa movimentação —
            // já aconteceu antes desse fix.
            val valorRecebidoAqui = round(aplicado.values.sum() * 100) / 100.0
            val descricaoAuto = buildString {
                append("Baixa de R$ %.2f".format(Locale("pt", "BR"), valorRecebidoAqui))
                if (descricaoManual.isNotBlank()) append(" - $descricaoManual")
                append(". Parcelas ${numeros.joinToString(", ")} desta movimentação.")
            }
            mov.copy(
                historicoPagamentos = mov.historicoPagamentos + Pagamento(
                    id = UUID.randomUUID().toString(),
                    data = agora,
                    valorPago = valorRecebidoAqui,
                    parcelasNumeros = numeros,
                    parcelasAplicadas = aplicado.mapKeys { it.key.toString() },
                    descricao = descricaoAuto,
                    baixaId = baixaId
                )
            )
        }
    }

    /**
     * Recalcula só as datas de vencimento das parcelas já existentes, a partir de uma
     * lista "modelo" gerada com a nova data de criação (mesmo algoritmo de sempre) —
     * sem tocar em valor, status ou no que já foi pago em cada uma. Usado pra editar a
     * data de uma movimentação que já tem baixa: nesse caso não dá pra mexer em mais
     * nada além da data (e da descrição), só os vencimentos mudam.
     */
    fun reaplicarVencimentos(parcelasAtuais: List<Parcela>, parcelasComNovasDatas: List<Parcela>): List<Parcela> {
        val novaDataPorNumero = parcelasComNovasDatas.associateBy { it.numero }
        return parcelasAtuais.map { atual ->
            val novaData = novaDataPorNumero[atual.numero]?.dataVencimento ?: atual.dataVencimento
            atual.copy(dataVencimento = novaData)
        }
    }

    /**
     * Edita apenas a data/hora de uma baixa já registrada (não mexe em valores nem em
     * quais parcelas foram afetadas) — atualiza tanto o registro da baixa no histórico
     * quanto o `dataUltimoPagamento` de cada parcela que ela tocou.
     */
    fun editarDataBaixa(movimentacao: Movimentacao, pagamento: Pagamento, novaData: Long): Movimentacao {
        require(movimentacao.historicoPagamentos.any { it.id == pagamento.id }) {
            "O pagamento informado (id=${pagamento.id}) não pertence a esta movimentação " +
                "(id=${movimentacao.id}) — provavelmente um erro de quem chamou essa função, " +
                "passando o Pagamento de uma movimentação errada. Editar a data mesmo assim " +
                "mexeria no dataUltimoPagamento de parcelas que não têm nada a ver com esse " +
                "pagamento, corrompendo o histórico silenciosamente."
        }
        val novasParcelas = movimentacao.parcelas.map { p ->
            if (pagamento.parcelasAplicadas.containsKey(p.numero.toString())) {
                p.copy(dataUltimoPagamento = novaData)
            } else {
                p
            }
        }
        val novoHistorico = movimentacao.historicoPagamentos.map { pag ->
            if (pag.id == pagamento.id) pag.copy(data = novaData) else pag
        }
        return movimentacao.copy(parcelas = novasParcelas, historicoPagamentos = novoHistorico)
    }

    /**
     * Reverte uma baixa específica: tira exatamente o valor que ela aplicou de cada
     * parcela afetada (usando parcelasAplicadas) e remove o registro do histórico.
     */
    fun reverterBaixa(movimentacao: Movimentacao, pagamento: Pagamento): Movimentacao {
        require(movimentacao.historicoPagamentos.any { it.id == pagamento.id }) {
            "O pagamento informado (id=${pagamento.id}) não pertence a esta movimentação " +
                "(id=${movimentacao.id}) — mesmo motivo do editarDataBaixa: reverter um " +
                "pagamento que não está no histórico dessa movimentação subtrairia valor de " +
                "parcelas que nunca receberam essa baixa de verdade."
        }
        val novasParcelas = movimentacao.parcelas.map { p ->
            val valorAplicado = pagamento.parcelasAplicadas[p.numero.toString()] ?: 0.0
            if (valorAplicado > 0.0) {
                val novoValorPago = (round((p.valorPago - valorAplicado) * 100) / 100.0).coerceAtLeast(0.0)
                val novoStatus = when {
                    novoValorPago <= 0.005 -> "aberta"
                    novoValorPago < p.valorOriginal - 0.005 -> "parcial"
                    else -> "paga"
                }
                p.copy(valorPago = novoValorPago, status = novoStatus)
            } else p
        }
        return movimentacao.copy(
            parcelas = novasParcelas,
            historicoPagamentos = movimentacao.historicoPagamentos.filterNot { it.id == pagamento.id }
        )
    }
}

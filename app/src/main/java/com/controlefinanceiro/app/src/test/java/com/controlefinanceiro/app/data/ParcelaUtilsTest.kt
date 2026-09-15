package com.controlefinanceiro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Testes da lógica de parcelamento/baixa (ParcelaUtils) — o coração financeiro do
 * app. Todos giram em torno de duas garantias que NUNCA podem quebrar:
 *   1) a soma das parcelas geradas sempre bate exatamente com o valor total pedido
 *      (mesmo com arredondamento de centavos);
 *   2) uma baixa aplicada sempre pode ser revertida, voltando ao estado exato de antes.
 */
class ParcelaUtilsTest {

    private fun data(ano: Int, mes: Int, dia: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(ano, mes - 1, dia, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun diaDoMes(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun mesDoAno(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.MONTH) + 1
    }

    // ---------- gerarParcelaAvista ----------

    @Test
    fun `avista gera uma unica parcela com o valor total`() {
        val parcelas = ParcelaUtils.gerarParcelaAvista(150.0, data(2026, 8, 10))
        assertEquals(1, parcelas.size)
        assertEquals(150.0, parcelas[0].valorOriginal, 0.001)
        assertEquals(1, parcelas[0].numero)
    }

    @Test
    fun `avista arredonda pra duas casas decimais`() {
        val parcelas = ParcelaUtils.gerarParcelaAvista(19.999, data(2026, 8, 10))
        assertEquals(20.0, parcelas[0].valorOriginal, 0.001)
    }

    // ---------- gerarParcelaQuitada (fluxo de caixa) ----------

    @Test
    fun `parcela quitada nasce paga com o valor cheio`() {
        val criada = data(2026, 8, 12)
        val parcelas = ParcelaUtils.gerarParcelaQuitada(540.0, criada)
        assertEquals(1, parcelas.size)
        val p = parcelas[0]
        assertEquals(540.0, p.valorOriginal, 0.001)
        assertEquals(540.0, p.valorPago, 0.001)
        assertEquals("paga", p.status)
        assertEquals(criada, p.dataUltimoPagamento)
    }

    @Test
    fun `movimentacao de fluxo fica com valorEmAberto zero`() {
        val mov = Movimentacao(
            valorTotal = 540.0,
            parcelas = ParcelaUtils.gerarParcelaQuitada(540.0, data(2026, 8, 12))
        )
        assertEquals(0.0, mov.valorEmAberto, 0.001)
        assertTrue(mov.quitada)
    }

    // ---------- gerarParcelas (dinheiro, parcelado) ----------

    @Test
    fun `soma das parcelas geradas bate exatamente com o valor total (divisao exata)`() {
        val parcelas = ParcelaUtils.gerarParcelas(300.0, 3, data(2026, 8, 10), diaVencimento = 15)
        assertEquals(3, parcelas.size)
        val soma = parcelas.sumOf { it.valorOriginal }
        assertEquals(300.0, soma, 0.001)
        parcelas.forEach { assertEquals(100.0, it.valorOriginal, 0.001) }
    }

    @Test
    fun `soma das parcelas bate mesmo quando a divisao nao eh exata (resto vai pra ultima)`() {
        // 100 / 3 = 33.33333... -> 33.33 + 33.33 + 33.34 = 100.00
        val parcelas = ParcelaUtils.gerarParcelas(100.0, 3, data(2026, 8, 10), diaVencimento = 15)
        val soma = parcelas.sumOf { it.valorOriginal }
        assertEquals(100.0, soma, 0.001)
        assertEquals(33.33, parcelas[0].valorOriginal, 0.001)
        assertEquals(33.33, parcelas[1].valorOriginal, 0.001)
        assertEquals(33.34, parcelas[2].valorOriginal, 0.001) // a diferença fica na última
    }

    @Test
    fun `parcelas em dinheiro comecam no mes seguinte a criacao`() {
        val parcelas = ParcelaUtils.gerarParcelas(300.0, 3, data(2026, 8, 10), diaVencimento = 15)
        assertEquals(9, mesDoAno(parcelas[0].dataVencimento)) // setembro
        assertEquals(10, mesDoAno(parcelas[1].dataVencimento))
        assertEquals(11, mesDoAno(parcelas[2].dataVencimento))
        parcelas.forEach { assertEquals(15, diaDoMes(it.dataVencimento)) }
    }

    @Test
    fun `dia de vencimento maior que os dias do mes cai no ultimo dia (fevereiro)`() {
        // Compra em 15/01/2026, vencimento configurado pro dia 31. A parcela cai no mês
        // seguinte à compra (fevereiro) -> fevereiro de 2026 só tem 28 dias.
        val parcelas = ParcelaUtils.gerarParcelas(100.0, 2, data(2026, 1, 15), diaVencimento = 31)
        assertEquals(2, mesDoAno(parcelas[0].dataVencimento)) // fevereiro
        assertEquals(28, diaDoMes(parcelas[0].dataVencimento)) // 2026 não é bissexto
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelas rejeita numero de parcelas menor que 1`() {
        ParcelaUtils.gerarParcelas(100.0, 0, data(2026, 8, 10), diaVencimento = 15)
    }

    // ---------- gerarParcelasCartao (regra de fechamento de fatura) ----------

    @Test
    fun `compra antes do fechamento cai na fatura do mesmo mes`() {
        // Fechamento (melhorDia) = dia 20, compra dia 10 -> ainda entra na fatura de agosto.
        val parcelas = ParcelaUtils.gerarParcelasCartao(
            valorTotal = 100.0, numParcelas = 1, dataCriacao = data(2026, 8, 10),
            melhorDia = 20, diaVencimentoCartao = 5
        )
        assertEquals(8, mesDoAno(parcelas[0].dataVencimento))
        assertEquals(5, diaDoMes(parcelas[0].dataVencimento))
    }

    @Test
    fun `compra no dia do fechamento ou depois cai na fatura do mes seguinte`() {
        // Fechamento = dia 20, compra dia 20 -> já fechou, vai pra fatura de setembro.
        val parcelas = ParcelaUtils.gerarParcelasCartao(
            valorTotal = 100.0, numParcelas = 1, dataCriacao = data(2026, 8, 20),
            melhorDia = 20, diaVencimentoCartao = 5
        )
        assertEquals(9, mesDoAno(parcelas[0].dataVencimento))
    }

    @Test
    fun `parcelas do cartao seguem uma por mes a partir da primeira fatura`() {
        val parcelas = ParcelaUtils.gerarParcelasCartao(
            valorTotal = 300.0, numParcelas = 3, dataCriacao = data(2026, 8, 10),
            melhorDia = 20, diaVencimentoCartao = 5
        )
        assertEquals(8, mesDoAno(parcelas[0].dataVencimento))
        assertEquals(9, mesDoAno(parcelas[1].dataVencimento))
        assertEquals(10, mesDoAno(parcelas[2].dataVencimento))
    }

    // ---------- aplicarValoresManuais ----------

    @Test
    fun `valores manuais sobrescrevem as parcelas exceto a ultima, que absorve o resto`() {
        val base = ParcelaUtils.gerarParcelas(300.0, 3, data(2026, 8, 10), diaVencimento = 15)
        val ajustadas = ParcelaUtils.aplicarValoresManuais(base, mapOf(1 to 50.0), valorTotal = 300.0)
        assertEquals(50.0, ajustadas[0].valorOriginal, 0.001)
        assertEquals(100.0, ajustadas[1].valorOriginal, 0.001) // não editada, mantém o valor gerado
        assertEquals(150.0, ajustadas[2].valorOriginal, 0.001) // última = 300 - 50 - 100
        assertEquals(300.0, ajustadas.sumOf { it.valorOriginal }, 0.001)
    }

    @Test
    fun `editar a ultima parcela manualmente eh ignorado (ela sempre eh recalculada)`() {
        val base = ParcelaUtils.gerarParcelas(300.0, 3, data(2026, 8, 10), diaVencimento = 15)
        val numUltima = base.maxOf { it.numero }
        val ajustadas = ParcelaUtils.aplicarValoresManuais(base, mapOf(numUltima to 999.0), valorTotal = 300.0)
        // mesmo tentando forçar 999 na última, ela continua sendo "o que sobra"
        assertEquals(100.0, ajustadas.last().valorOriginal, 0.001)
        assertEquals(300.0, ajustadas.sumOf { it.valorOriginal }, 0.001)
    }

    // ---------- selecionarMaisAntigasAte ----------

    @Test
    fun `seleciona as parcelas mais antigas ate o valor se esgotar`() {
        val refs = listOf(
            ParcelaRef("m1", "Compra 1", 1, Parcela(numero = 1, valorOriginal = 100.0)),
            ParcelaRef("m2", "Compra 2", 1, Parcela(numero = 1, valorOriginal = 100.0)),
            ParcelaRef("m3", "Compra 3", 1, Parcela(numero = 1, valorOriginal = 100.0))
        )
        val selecionadas = ParcelaUtils.selecionarMaisAntigasAte(refs, 150.0)
        // 150 cobre a 1ª inteira (100) e ainda mexe na 2ª (50 restante) -> as duas primeiras entram
        assertEquals(setOf("m1" to 1, "m2" to 1), selecionadas)
    }

    @Test
    fun `nao seleciona nada quando o valor eh zero`() {
        val refs = listOf(ParcelaRef("m1", "Compra 1", 1, Parcela(numero = 1, valorOriginal = 100.0)))
        assertTrue(ParcelaUtils.selecionarMaisAntigasAte(refs, 0.0).isEmpty())
    }

    // ---------- aplicarBaixaConsolidada ----------

    @Test
    fun `baixa sequencial quita a mais antiga por completo antes de seguir pra proxima`() {
        val mov = Movimentacao(
            id = "m1", valorTotal = 200.0,
            parcelas = listOf(
                Parcela(numero = 1, valorOriginal = 100.0),
                Parcela(numero = 2, valorOriginal = 100.0)
            )
        )
        val selecionadas = listOf(
            ParcelaRef("m1", "Compra", 2, mov.parcelas[0]),
            ParcelaRef("m1", "Compra", 2, mov.parcelas[1])
        )
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            movimentacoesPorId = mapOf("m1" to mov),
            selecionadas = selecionadas,
            valorPago = 150.0
        )
        val atualizada = resultado.getValue("m1")
        assertEquals("paga", atualizada.parcelas[0].status)
        assertEquals(100.0, atualizada.parcelas[0].valorPago, 0.001)
        assertEquals("parcial", atualizada.parcelas[1].status)
        assertEquals(50.0, atualizada.parcelas[1].valorPago, 0.001)
        assertEquals(150.0, atualizada.valorPagoTotal, 0.001)
    }

    @Test
    fun `baixa proporcional divide o valor na mesma proporcao entre as selecionadas`() {
        val mov = Movimentacao(
            id = "m1", valorTotal = 200.0,
            parcelas = listOf(
                Parcela(numero = 1, valorOriginal = 100.0),
                Parcela(numero = 2, valorOriginal = 100.0)
            )
        )
        val selecionadas = listOf(
            ParcelaRef("m1", "Compra", 2, mov.parcelas[0]),
            ParcelaRef("m1", "Compra", 2, mov.parcelas[1])
        )
        // 50% do total faltante (200) = 100 -> cada parcela recebe 50% do que falta nela (50 cada)
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            movimentacoesPorId = mapOf("m1" to mov),
            selecionadas = selecionadas,
            valorPago = 100.0,
            proporcional = true
        )
        val atualizada = resultado.getValue("m1")
        assertEquals(50.0, atualizada.parcelas[0].valorPago, 0.001)
        assertEquals(50.0, atualizada.parcelas[1].valorPago, 0.001)
        assertEquals("parcial", atualizada.parcelas[0].status)
        assertEquals("parcial", atualizada.parcelas[1].status)
    }

    @Test
    fun `baixa gera um registro de pagamento no historico com o baixaId compartilhado entre movimentacoes`() {
        val mov1 = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val mov2 = Movimentacao(id = "m2", valorTotal = 80.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 80.0)))
        val selecionadas = listOf(
            ParcelaRef("m1", "Compra 1", 1, mov1.parcelas[0]),
            ParcelaRef("m2", "Compra 2", 1, mov2.parcelas[0])
        )
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            movimentacoesPorId = mapOf("m1" to mov1, "m2" to mov2),
            selecionadas = selecionadas,
            valorPago = 180.0
        )
        val baixaId1 = resultado.getValue("m1").historicoPagamentos.last().baixaId
        val baixaId2 = resultado.getValue("m2").historicoPagamentos.last().baixaId
        assertTrue(baixaId1.isNotBlank())
        assertEquals(baixaId1, baixaId2) // mesma transação de baixa, id compartilhado
    }

    @Test
    fun `baixa ignora parcelas ja pagas mesmo se vierem selecionadas`() {
        val mov = Movimentacao(
            id = "m1", valorTotal = 200.0,
            parcelas = listOf(
                Parcela(numero = 1, valorOriginal = 100.0, valorPago = 100.0, status = "paga"),
                Parcela(numero = 2, valorOriginal = 100.0)
            )
        )
        val selecionadas = listOf(
            ParcelaRef("m1", "Compra", 2, mov.parcelas[0]),
            ParcelaRef("m1", "Compra", 2, mov.parcelas[1])
        )
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            movimentacoesPorId = mapOf("m1" to mov),
            selecionadas = selecionadas,
            valorPago = 100.0
        )
        val atualizada = resultado.getValue("m1")
        assertEquals(100.0, atualizada.parcelas[0].valorPago, 0.001) // não dobrou
        assertEquals(100.0, atualizada.parcelas[1].valorPago, 0.001) // recebeu o valor certo
    }

    // ---------- reverterBaixa: a garantia mais importante do módulo ----------

    @Test
    fun `reverter uma baixa devolve a movimentacao exatamente ao estado de antes`() {
        val original = Movimentacao(
            id = "m1", valorTotal = 200.0,
            parcelas = listOf(
                Parcela(numero = 1, valorOriginal = 100.0),
                Parcela(numero = 2, valorOriginal = 100.0)
            )
        )
        val selecionadas = listOf(
            ParcelaRef("m1", "Compra", 2, original.parcelas[0]),
            ParcelaRef("m1", "Compra", 2, original.parcelas[1])
        )
        val comBaixa = ParcelaUtils.aplicarBaixaConsolidada(
            movimentacoesPorId = mapOf("m1" to original),
            selecionadas = selecionadas,
            valorPago = 150.0
        ).getValue("m1")
        val pagamento = comBaixa.historicoPagamentos.single()

        val revertida = ParcelaUtils.reverterBaixa(comBaixa, pagamento)

        assertEquals(0.0, revertida.valorPagoTotal, 0.001)
        assertTrue(revertida.historicoPagamentos.isEmpty())
        revertida.parcelas.forEach { assertEquals("aberta", it.status) }
        assertEquals(original.parcelas.map { it.valorOriginal }, revertida.parcelas.map { it.valorOriginal })
    }

    @Test
    fun `reverter uma baixa parcial entre duas volta o status pra parcial, nao aberta`() {
        // Parcela de 100 recebe duas baixas de 40 cada (fica parcial, 80 pago). Revertendo
        // só a segunda, deve voltar pra 40 pago, ainda "parcial" (não "aberta").
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])

        val apos1a = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref), 40.0).getValue("m1")
        val refAtualizada = ref.copy(parcela = apos1a.parcelas[0])
        val apos2a = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to apos1a), listOf(refAtualizada), 40.0).getValue("m1")
        assertEquals(80.0, apos2a.valorPagoTotal, 0.001)

        val segundoPagamento = apos2a.historicoPagamentos.last()
        val revertida = ParcelaUtils.reverterBaixa(apos2a, segundoPagamento)

        assertEquals(40.0, revertida.parcelas[0].valorPago, 0.001)
        assertEquals("parcial", revertida.parcelas[0].status)
        assertEquals(1, revertida.historicoPagamentos.size) // só sobrou o primeiro pagamento
    }

    // ---------- editarDataBaixa ----------

    @Test
    fun `editar data da baixa atualiza o historico e o dataUltimoPagamento das parcelas afetadas`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])
        val comBaixa = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref), 100.0, dataBaixa = data(2026, 8, 1))
            .getValue("m1")
        val pagamento = comBaixa.historicoPagamentos.single()
        val novaData = data(2026, 8, 5)

        val editada = ParcelaUtils.editarDataBaixa(comBaixa, pagamento, novaData)

        assertEquals(novaData, editada.historicoPagamentos.single().data)
        assertEquals(novaData, editada.parcelas[0].dataUltimoPagamento)
        // valor pago não muda, só a data
        assertEquals(100.0, editada.parcelas[0].valorPago, 0.001)
    }

    // ---------- reaplicarVencimentos ----------

    @Test
    fun `reaplicar vencimentos so troca a data, mantem valor e status`() {
        val atuais = listOf(Parcela(numero = 1, valorOriginal = 100.0, valorPago = 40.0, status = "parcial", dataVencimento = data(2026, 8, 15)))
        val novasDatas = listOf(Parcela(numero = 1, valorOriginal = 999.0, dataVencimento = data(2026, 9, 20)))

        val resultado = ParcelaUtils.reaplicarVencimentos(atuais, novasDatas)

        assertEquals(data(2026, 9, 20), resultado[0].dataVencimento)
        assertEquals(100.0, resultado[0].valorOriginal, 0.001) // NÃO pega o 999 da lista modelo
        assertEquals(40.0, resultado[0].valorPago, 0.001)
        assertEquals("parcial", resultado[0].status)
    }

    // =====================================================================
    // Bateria mais profunda: limites, casos extremos e cenários compostos
    // =====================================================================

    // ---------- selecionarMaisAntigasAte: limites exatos ----------

    @Test
    fun `valor exatamente igual a primeira parcela seleciona so ela`() {
        val refs = listOf(
            ParcelaRef("m1", "Compra 1", 1, Parcela(numero = 1, valorOriginal = 100.0)),
            ParcelaRef("m2", "Compra 2", 1, Parcela(numero = 1, valorOriginal = 100.0))
        )
        val selecionadas = ParcelaUtils.selecionarMaisAntigasAte(refs, 100.0)
        assertEquals(setOf("m1" to 1), selecionadas)
    }

    @Test
    fun `valor maior que a soma de tudo seleciona todas as parcelas disponiveis`() {
        val refs = listOf(
            ParcelaRef("m1", "Compra 1", 1, Parcela(numero = 1, valorOriginal = 100.0)),
            ParcelaRef("m2", "Compra 2", 1, Parcela(numero = 1, valorOriginal = 100.0))
        )
        val selecionadas = ParcelaUtils.selecionarMaisAntigasAte(refs, 999.0)
        assertEquals(2, selecionadas.size)
    }

    @Test
    fun `parcela ja parcialmente paga conta só o valor que falta, nao o valor original`() {
        val refs = listOf(
            ParcelaRef("m1", "Compra 1", 1, Parcela(numero = 1, valorOriginal = 100.0, valorPago = 90.0)), // falta só 10
            ParcelaRef("m2", "Compra 2", 1, Parcela(numero = 1, valorOriginal = 100.0))
        )
        // 15 cobre os 10 que faltam na primeira e ainda avança 5 na segunda -> as duas entram
        val selecionadas = ParcelaUtils.selecionarMaisAntigasAte(refs, 15.0)
        assertEquals(2, selecionadas.size)
    }

    // ---------- aplicarBaixaConsolidada: estouro de valor ----------

    @Test
    fun `baixa sequencial com valor maior que o total das selecionadas nao ultrapassa o total de cada parcela`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])
        // Paga 500 numa duplicata de 100 -> não pode ficar com valorPago > valorOriginal
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref), 500.0)
        val atualizada = resultado.getValue("m1")
        assertEquals(100.0, atualizada.parcelas[0].valorPago, 0.001)
        assertEquals("paga", atualizada.parcelas[0].status)
        // o valor "sobrando" (400) não é aplicado em lugar nenhum — não inventa uma parcela nova
        assertEquals(100.0, atualizada.historicoPagamentos.single().valorPago, 0.001)
    }

    @Test
    fun `baixa proporcional com valor maior que o total faltante quita tudo sem estourar 100%`() {
        val mov = Movimentacao(
            id = "m1", valorTotal = 200.0,
            parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0), Parcela(numero = 2, valorOriginal = 100.0))
        )
        val selecionadas = listOf(ParcelaRef("m1", "Compra", 2, mov.parcelas[0]), ParcelaRef("m1", "Compra", 2, mov.parcelas[1]))
        // pede pra pagar 999 num total faltante de 200 -> proporção tem que ficar travada em 100%, não 499%
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), selecionadas, 999.0, proporcional = true)
        val atualizada = resultado.getValue("m1")
        assertEquals(100.0, atualizada.parcelas[0].valorPago, 0.001)
        assertEquals(100.0, atualizada.parcelas[1].valorPago, 0.001)
        assertTrue(atualizada.quitada)
    }

    @Test
    fun `baixa com valor zero nao altera nenhuma parcela`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref), 0.0)
        // como nada foi de fato aplicado, a movimentação nem aparece no resultado
        assertTrue(!resultado.containsKey("m1") || resultado.getValue("m1").parcelas[0].valorPago == 0.0)
    }

    @Test
    fun `baixa ignora selecionadas de movimentacao que nao existe no mapa (id invalido)`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val refValida = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])
        val refFantasma = ParcelaRef("m-nao-existe", "Fantasma", 1, Parcela(numero = 1, valorOriginal = 50.0))
        // não pode lançar exceção nem quebrar por causa da referência fantasma
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            mapOf("m1" to mov), listOf(refFantasma, refValida), 100.0
        )
        assertEquals(1, resultado.size)
        assertEquals(100.0, resultado.getValue("m1").parcelas[0].valorPago, 0.001)
    }

    @Test
    fun `baixa distribuida entre tres movimentacoes diferentes soma certo em cada uma`() {
        val mov1 = Movimentacao(id = "m1", valorTotal = 50.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 50.0)))
        val mov2 = Movimentacao(id = "m2", valorTotal = 30.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 30.0)))
        val mov3 = Movimentacao(id = "m3", valorTotal = 20.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 20.0)))
        val selecionadas = listOf(
            ParcelaRef("m1", "A", 1, mov1.parcelas[0]),
            ParcelaRef("m2", "B", 1, mov2.parcelas[0]),
            ParcelaRef("m3", "C", 1, mov3.parcelas[0])
        )
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            mapOf("m1" to mov1, "m2" to mov2, "m3" to mov3), selecionadas, valorPago = 100.0
        )
        assertEquals(50.0, resultado.getValue("m1").parcelas[0].valorPago, 0.001)
        assertEquals(30.0, resultado.getValue("m2").parcelas[0].valorPago, 0.001)
        assertEquals(20.0, resultado.getValue("m3").parcelas[0].valorPago, 0.001)
        // todas as três compartilham o mesmo baixaId (é uma baixa só, dividida em três)
        val ids = setOf(
            resultado.getValue("m1").historicoPagamentos.single().baixaId,
            resultado.getValue("m2").historicoPagamentos.single().baixaId,
            resultado.getValue("m3").historicoPagamentos.single().baixaId
        )
        assertEquals(1, ids.size)
    }

    // ---------- reverterBaixa: isolamento entre baixas/movimentações ----------

    @Test
    fun `reverter uma baixa nao mexe nas outras movimentacoes da mesma transacao`() {
        val mov1 = Movimentacao(id = "m1", valorTotal = 50.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 50.0)))
        val mov2 = Movimentacao(id = "m2", valorTotal = 30.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 30.0)))
        val selecionadas = listOf(ParcelaRef("m1", "A", 1, mov1.parcelas[0]), ParcelaRef("m2", "B", 1, mov2.parcelas[0]))
        val comBaixa = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov1, "m2" to mov2), selecionadas, 80.0)

        val pagamentoM1 = comBaixa.getValue("m1").historicoPagamentos.single()
        val m1Revertida = ParcelaUtils.reverterBaixa(comBaixa.getValue("m1"), pagamentoM1)

        assertEquals(0.0, m1Revertida.parcelas[0].valorPago, 0.001)
        // m2 nem foi tocada nessa chamada — continua com a baixa normalmente
        assertEquals(30.0, comBaixa.getValue("m2").parcelas[0].valorPago, 0.001)
    }

    @Test
    fun `reverter duas baixas separadas da mesma parcela, uma de cada vez, e o historico encolhe certo`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])

        val apos1a = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref), 30.0).getValue("m1")
        val apos2a = ParcelaUtils.aplicarBaixaConsolidada(
            mapOf("m1" to apos1a), listOf(ref.copy(parcela = apos1a.parcelas[0])), 30.0
        ).getValue("m1")
        assertEquals(2, apos2a.historicoPagamentos.size)
        assertEquals(60.0, apos2a.valorPagoTotal, 0.001)

        // reverte a PRIMEIRA baixa (não a mais recente)
        val primeiraBaixa = apos2a.historicoPagamentos.first()
        val revertida = ParcelaUtils.reverterBaixa(apos2a, primeiraBaixa)

        assertEquals(30.0, revertida.valorPagoTotal, 0.001) // só sobrou o valor da segunda baixa
        assertEquals(1, revertida.historicoPagamentos.size)
        assertEquals("parcial", revertida.parcelas[0].status)
    }

    @Test
    fun `reverter uma baixa que quitou totalmente volta o status pra aberta, nao parcial`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])
        val quitada = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref), 100.0).getValue("m1")
        assertEquals("paga", quitada.parcelas[0].status)

        val revertida = ParcelaUtils.reverterBaixa(quitada, quitada.historicoPagamentos.single())
        assertEquals("aberta", revertida.parcelas[0].status)
        assertEquals(0.0, revertida.parcelas[0].valorPago, 0.001)
    }

    // ---------- editarDataBaixa: isolamento entre pagamentos ----------

    @Test
    fun `editar data de uma baixa nao mexe na data das outras baixas da mesma movimentacao`() {
        val mov = Movimentacao(
            id = "m1", valorTotal = 100.0,
            parcelas = listOf(Parcela(numero = 1, valorOriginal = 50.0), Parcela(numero = 2, valorOriginal = 50.0))
        )
        val ref1 = ParcelaRef("m1", "Compra", 2, mov.parcelas[0])
        val ref2 = ParcelaRef("m1", "Compra", 2, mov.parcelas[1])
        val comDuasBaixas = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref1), 50.0, dataBaixa = data(2026, 8, 1))
            .let { r1 -> ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to r1.getValue("m1")), listOf(ref2), 50.0, dataBaixa = data(2026, 8, 2)) }
            .getValue("m1")

        val primeiraBaixa = comDuasBaixas.historicoPagamentos.first { it.parcelasNumeros == listOf(1) }
        val editada = ParcelaUtils.editarDataBaixa(comDuasBaixas, primeiraBaixa, data(2026, 8, 10))

        val baixa1Editada = editada.historicoPagamentos.first { it.parcelasNumeros == listOf(1) }
        val baixa2Intacta = editada.historicoPagamentos.first { it.parcelasNumeros == listOf(2) }
        assertEquals(data(2026, 8, 10), baixa1Editada.data)
        assertEquals(data(2026, 8, 2), baixa2Intacta.data) // não mudou
        assertEquals(data(2026, 8, 10), editada.parcelas[0].dataUltimoPagamento)
        assertEquals(data(2026, 8, 2), editada.parcelas[1].dataUltimoPagamento) // não mudou
    }

    // ---------- gerarParcelas / gerarParcelasCartao: mais quinas de calendário ----------

    @Test
    fun `gerarParcelas com uma unica parcela ainda pula pro mes seguinte`() {
        val parcelas = ParcelaUtils.gerarParcelas(100.0, 1, data(2026, 8, 10), diaVencimento = 15)
        assertEquals(1, parcelas.size)
        assertEquals(100.0, parcelas[0].valorOriginal, 0.001)
        assertEquals(9, mesDoAno(parcelas[0].dataVencimento))
    }

    @Test
    fun `compra no cartao exatamente no dia do fechamento conta como ja fechada`() {
        // diaCompra >= melhorDia -> mesmo no dia exato do fechamento já cai na fatura seguinte
        val noFechamento = ParcelaUtils.gerarParcelasCartao(100.0, 1, data(2026, 8, 20), melhorDia = 20, diaVencimentoCartao = 5)
        val umDiaAntes = ParcelaUtils.gerarParcelasCartao(100.0, 1, data(2026, 8, 19), melhorDia = 20, diaVencimentoCartao = 5)
        assertEquals(9, mesDoAno(noFechamento[0].dataVencimento))
        assertEquals(8, mesDoAno(umDiaAntes[0].dataVencimento))
    }

    @Test
    fun `parcelamento longo (12x) com resto de centavos ainda fecha o total exato`() {
        val parcelas = ParcelaUtils.gerarParcelas(1000.0, 12, data(2026, 8, 10), diaVencimento = 15)
        assertEquals(12, parcelas.size)
        assertEquals(1000.0, parcelas.sumOf { it.valorOriginal }, 0.001)
        // 1000/12 = 83.333... -> as 11 primeiras batem 83.33 e a diferença cai na última
        parcelas.dropLast(1).forEach { assertEquals(83.33, it.valorOriginal, 0.001) }
    }

    @Test
    fun `vencimento em ano bissexto aceita 29 de fevereiro`() {
        // 2028 é bissexto. Compra em janeiro, vencimento configurado pro dia 29.
        val parcelas = ParcelaUtils.gerarParcelas(100.0, 1, data(2028, 1, 15), diaVencimento = 29)
        assertEquals(2, mesDoAno(parcelas[0].dataVencimento))
        assertEquals(29, diaDoMes(parcelas[0].dataVencimento))
    }

    @Test
    fun `vencimento dia 30 em mes de 31 dias nao vira dia 31`() {
        // pediu vencimento dia 30 -> não deve "arredondar pra cima", só limita quando o
        // mês É MENOR que o dia pedido (aqui agosto tem 31, então dia 30 é normal).
        val parcelas = ParcelaUtils.gerarParcelas(100.0, 1, data(2026, 7, 10), diaVencimento = 30)
        assertEquals(30, diaDoMes(parcelas[0].dataVencimento))
    }

    // ---------- aplicarValoresManuais: mais casos ----------

    @Test
    fun `aplicarValoresManuais com mapa vazio mantem os valores originais gerados`() {
        val base = ParcelaUtils.gerarParcelas(300.0, 3, data(2026, 8, 10), diaVencimento = 15)
        val resultado = ParcelaUtils.aplicarValoresManuais(base, emptyMap(), valorTotal = 300.0)
        assertEquals(base.map { it.valorOriginal }, resultado.map { it.valorOriginal })
    }

    @Test
    fun `aplicarValoresManuais com uma unica parcela joga o valor total inteiro nela`() {
        val base = ParcelaUtils.gerarParcelaAvista(150.0, data(2026, 8, 10))
        val resultado = ParcelaUtils.aplicarValoresManuais(base, mapOf(1 to 999.0), valorTotal = 150.0)
        // parcela única = sempre "a última" -> ignora o valor manual, vira o total inteiro
        assertEquals(150.0, resultado[0].valorOriginal, 0.001)
    }

    @Test
    fun `aplicarValoresManuais ignora numero de parcela que nao existe na lista`() {
        val base = ParcelaUtils.gerarParcelas(300.0, 3, data(2026, 8, 10), diaVencimento = 15)
        val resultado = ParcelaUtils.aplicarValoresManuais(base, mapOf(99 to 500.0), valorTotal = 300.0)
        assertEquals(300.0, resultado.sumOf { it.valorOriginal }, 0.001)
    }

    // ---------- Cenário composto: ciclo de vida completo de uma movimentação ----------

    @Test
    fun `ciclo completo - criar parcelado, dar duas baixas parciais, reverter a ultima, volta pro estado da primeira`() {
        val criada = Movimentacao(
            id = "m1", valorTotal = 300.0,
            parcelas = ParcelaUtils.gerarParcelas(300.0, 3, data(2026, 8, 1), diaVencimento = 10)
        )
        assertEquals(300.0, criada.valorEmAberto, 0.001)
        assertTrue(criada.podeEditarOuExcluir)

        // primeira baixa: paga a primeira parcela inteira (100) + um pouco da segunda
        val refsOrdenadas = criada.parcelas.sortedBy { it.numero }.map { ParcelaRef("m1", "Compra", 3, it) }
        val apos1a = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to criada), refsOrdenadas, 130.0).getValue("m1")
        assertEquals(170.0, apos1a.valorEmAberto, 0.001)
        assertFalse(apos1a.podeEditarOuExcluir) // já tem pagamento, não pode mais excluir livremente

        // segunda baixa: quita o resto de tudo
        val refsAtualizadas = apos1a.parcelas.sortedBy { it.numero }.map { ParcelaRef("m1", "Compra", 3, it) }
        val apos2a = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to apos1a), refsAtualizadas, 170.0).getValue("m1")
        assertEquals(0.0, apos2a.valorEmAberto, 0.001)
        assertTrue(apos2a.quitada)
        assertEquals(2, apos2a.historicoPagamentos.size)

        // reverte só a segunda baixa -> volta exatamente pro estado de "apos1a"
        val segundaBaixa = apos2a.historicoPagamentos.last()
        val revertida = ParcelaUtils.reverterBaixa(apos2a, segundaBaixa)
        assertEquals(170.0, revertida.valorEmAberto, 0.001)
        assertEquals(1, revertida.historicoPagamentos.size)
        assertFalse(revertida.quitada)
    }
}

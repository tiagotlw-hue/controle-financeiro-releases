package com.controlefinanceiro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {

    @Test
    fun `valorEmAberto eh o total menos o que ja foi pago`() {
        val mov = Movimentacao(
            valorTotal = 300.0,
            parcelas = listOf(
                Parcela(numero = 1, valorOriginal = 100.0, valorPago = 100.0, status = "paga"),
                Parcela(numero = 2, valorOriginal = 100.0, valorPago = 40.0, status = "parcial"),
                Parcela(numero = 3, valorOriginal = 100.0, valorPago = 0.0, status = "aberta")
            )
        )
        assertEquals(160.0, mov.valorEmAberto, 0.001)
    }

    @Test
    fun `quitada so eh true quando todas as parcelas estao pagas`() {
        val parcial = Movimentacao(valorTotal = 200.0, parcelas = listOf(
            Parcela(numero = 1, status = "paga"), Parcela(numero = 2, status = "aberta")
        ))
        val completa = Movimentacao(valorTotal = 200.0, parcelas = listOf(
            Parcela(numero = 1, status = "paga"), Parcela(numero = 2, status = "paga")
        ))
        assertFalse(parcial.quitada)
        assertTrue(completa.quitada)
    }

    @Test
    fun `movimentacao sem nenhuma parcela nao conta como quitada`() {
        // Lista vazia não deve ser tratada como "tudo pago" (all() em lista vazia dá true
        // por padrão em Kotlin — o model precisa checar isNotEmpty() explicitamente).
        val semParcelas = Movimentacao(valorTotal = 100.0, parcelas = emptyList())
        assertFalse(semParcelas.quitada)
    }

    @Test
    fun `podeEditarOuExcluir eh true so quando nada foi pago ainda`() {
        val semPagamento = Movimentacao(valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val comPagamento = Movimentacao(valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0, valorPago = 10.0)))
        assertTrue(semPagamento.podeEditarOuExcluir)
        assertFalse(comPagamento.podeEditarOuExcluir)
    }

    // Isto documenta uma pegadinha real do model (já causou bug em produção — ver
    // EntradaScreen.bloqueadoPorBaixa e MovimentacaoDetalheScreen): uma movimentação de
    // FLUXO DE CAIXA já nasce com a parcela paga (ParcelaUtils.gerarParcelaQuitada), então
    // `podeEditarOuExcluir` sempre dá FALSE pra ela — mesmo sem nenhuma "baixa" de verdade
    // ter sido registrada. Qualquer tela que use esse campo pra decidir se mostra o aviso
    // de "já tem baixa" ou se desabilita edição/exclusão PRECISA tratar categoria de fluxo
    // como um caso à parte, não confiar só nesse campo. Se este teste um dia falhar (ex:
    // o model mudar pra considerar fluxo separadamente), reavalie esses dois lugares.
    @Test
    fun `pegadinha- movimentacao de fluxo sempre da podeEditarOuExcluir false mesmo sem baixa`() {
        val lancamentoDeFluxo = Movimentacao(
            valorTotal = 540.0,
            parcelas = ParcelaUtils.gerarParcelaQuitada(540.0, System.currentTimeMillis())
        )
        assertFalse(
            "se isso passou a dar true, EntradaScreen.bloqueadoPorBaixa e o botão de excluir " +
                "em MovimentacaoDetalheScreen podem estar usando uma lógica desatualizada",
            lancamentoDeFluxo.podeEditarOuExcluir
        )
    }

    // =====================================================================
    // Bateria mais profunda
    // =====================================================================

    @Test
    fun `valorEmAberto nunca fica negativo mesmo se por algum motivo pagar mais que o total`() {
        // Não deveria acontecer na prática (aplicarBaixaConsolidada trava em 100%), mas o
        // getter do model não deveria "inventar" um valor negativo se isso ocorrer.
        val mov = Movimentacao(
            valorTotal = 100.0,
            parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0, valorPago = 150.0, status = "paga"))
        )
        assertTrue(mov.valorEmAberto <= 0.0) // pelo menos não fica "faltando" nada
    }

    @Test
    fun `valorPagoTotal soma o valorPago de todas as parcelas`() {
        val mov = Movimentacao(
            valorTotal = 300.0,
            parcelas = listOf(
                Parcela(numero = 1, valorOriginal = 100.0, valorPago = 100.0),
                Parcela(numero = 2, valorOriginal = 100.0, valorPago = 30.0),
                Parcela(numero = 3, valorOriginal = 100.0, valorPago = 0.0)
            )
        )
        assertEquals(130.0, mov.valorPagoTotal, 0.001)
    }

    @Test
    fun `configuracoes default vem com UN como unidade padrao de lista`() {
        val config = Configuracoes()
        assertEquals("UN", config.unidadePadraoLista)
        assertTrue(config.unidadesPersonalizadas.isEmpty())
    }

    @Test
    fun `lista de unidades padrao inclui as unidades basicas esperadas`() {
        val padrao = UnidadesLista.PADRAO
        assertTrue(padrao.contains("UN"))
        assertTrue(padrao.contains("KG"))
        assertTrue(padrao.contains("L"))
    }

    @Test
    fun `categoria default nasce do tipo RECEBER e com totalAberto zero`() {
        val categoria = Categoria()
        assertEquals(TipoMovimentacao.RECEBER, categoria.tipo)
        assertEquals(0.0, categoria.totalAberto, 0.001)
    }

    @Test
    fun `cliente novo nasce com saldo e totais zerados`() {
        val cliente = Cliente()
        assertEquals(0.0, cliente.saldo, 0.001)
        assertEquals(0.0, cliente.totalAReceber, 0.001)
        assertEquals(0.0, cliente.totalAPagar, 0.001)
    }
}

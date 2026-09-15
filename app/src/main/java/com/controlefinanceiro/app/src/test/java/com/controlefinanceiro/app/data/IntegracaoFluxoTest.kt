package com.controlefinanceiro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes de integração: em vez de testar cada função isolada, simulam o caminho
 * completo que os dados percorrem de verdade no app — do CSV até os totais que
 * aparecem na tela. Isso pega bugs que só aparecem na "costura" entre os módulos,
 * que os testes unitários (um por função) não veem sozinhos.
 *
 * A conversão de LinhaCsvFluxo -> Movimentacao aqui espelha exatamente o que
 * AppViewModel.importarLancamentosFluxo faz (essa função não dá pra testar direto
 * porque depende do Firebase/Android) — se um dia mudar como o ViewModel monta essa
 * Movimentacao, atualize aqui junto pra esse teste continuar valendo.
 */
class IntegracaoFluxoTest {

    private fun linhaParaMovimentacao(linha: LinhaCsvFluxo, clienteId: String, categoriaId: String): Movimentacao {
        require(linha.valida)
        return Movimentacao(
            clienteId = clienteId,
            categoriaId = categoriaId,
            tipo = linha.tipo!!,
            descricao = linha.descricao,
            valorTotal = linha.valor!!,
            numParcelas = 1,
            dataCriacao = linha.data!!,
            formaPagamento = FormaPagamento.DINHEIRO,
            parcelas = ParcelaUtils.gerarParcelaQuitada(linha.valor, linha.data)
        )
    }

    @Test
    fun `importar um CSV de fluxo gera movimentacoes ja quitadas com os valores certos`() {
        val csv = """
            Data;Tipo;Descrição;Valor
            12/08/2026;Recebido;Venda balcão;540,00
            12/08/2026;Pago;Compra de material;200,00
        """.trimIndent()

        val linhas = parsearCsvFluxo(csv)
        assertTrue(linhas.all { it.valida })

        val movimentacoes = linhas.map { linhaParaMovimentacao(it, "cliente1", "cat1") }

        assertEquals(2, movimentacoes.size)
        movimentacoes.forEach {
            assertEquals(0.0, it.valorEmAberto, 0.001) // nasce tudo quitado
            assertTrue(it.quitada)
        }
        assertEquals(540.0, movimentacoes[0].valorTotal, 0.001)
        assertEquals(200.0, movimentacoes[1].valorTotal, 0.001)
    }

    @Test
    fun `linhas invalidas do CSV nao viram movimentacao (sao filtradas antes de importar)`() {
        val csv = """
            12/08/2026;Recebido;Venda ok;100,00
            data-quebrada;Recebido;Essa nao entra;50,00
            13/08/2026;Pago;Outra venda ok;30,00
        """.trimIndent()

        val linhas = parsearCsvFluxo(csv)
        val validas = linhas.filter { it.valida }
        val movimentacoes = validas.map { linhaParaMovimentacao(it, "cliente1", "cat1") }

        assertEquals(2, movimentacoes.size) // só as duas válidas
        assertTrue(movimentacoes.none { it.descricao == "Essa nao entra" })
    }

    @Test
    fun `saldo do fluxo calculado a partir das movimentacoes importadas bate com o esperado`() {
        // Mesma conta que MovimentacoesScreen faz pra mostrar o card de saldo do fluxo.
        val csv = """
            12/08/2026;Recebido;Venda 1;300,00
            12/08/2026;Recebido;Venda 2;150,50
            13/08/2026;Pago;Compra 1;80,25
            13/08/2026;Pago;Compra 2;40,00
        """.trimIndent()

        val movimentacoes = parsearCsvFluxo(csv).map { linhaParaMovimentacao(it, "cliente1", "cat1") }

        val totalRecebido = movimentacoes.filter { it.tipo == TipoMovimentacao.RECEBER }.sumOf { it.valorTotal }
        val totalPago = movimentacoes.filter { it.tipo == TipoMovimentacao.PAGAR }.sumOf { it.valorTotal }
        val saldo = totalRecebido - totalPago

        assertEquals(450.50, totalRecebido, 0.001)
        assertEquals(120.25, totalPago, 0.001)
        assertEquals(330.25, saldo, 0.001)
    }

    @Test
    fun `movimentacoes importadas nao contribuem para o total em aberto da categoria`() {
        // Confirma que importar fluxo não infla o "totalAberto" denormalizado, já que
        // toda movimentação de fluxo nasce com valorEmAberto zero (mesma conta que
        // FirebaseRepository.recalcularSaldoCategoria faz: somar valorEmAberto de todas).
        val csv = "12/08/2026;Recebido;Venda grande;5000,00"
        val movimentacoes = parsearCsvFluxo(csv).map { linhaParaMovimentacao(it, "cliente1", "cat1") }
        val totalAberto = movimentacoes.sumOf { it.valorEmAberto }
        assertEquals(0.0, totalAberto, 0.001)
    }

    @Test
    fun `ida e volta - movimentacoes importadas de um CSV, exportadas de novo, mantem tipo e valor por linha`() {
        // Importa um CSV de fluxo, gera as Movimentacao, e confirma que exportá-las de
        // volta (gerarCsvCliente) preserva o tipo individual de cada uma — é exatamente
        // o bug que existiu antes (rótulo fixo por categoria em vez de por lançamento).
        val csvOriginal = """
            12/08/2026;Recebido;Venda balcão;540,00
            12/08/2026;Pago;Compra material;200,00
        """.trimIndent()

        val categoria = Categoria(id = "cat1", tipo = TipoMovimentacao.FLUXO, nome = "Caixa do dia")
        val cliente = Cliente(id = "cliente1", nome = "Teste")
        val movimentacoes = parsearCsvFluxo(csvOriginal).mapIndexed { i, linha ->
            linhaParaMovimentacao(linha, cliente.id, categoria.id).copy(id = "m$i")
        }

        val csvExportado = gerarCsvCliente(cliente, listOf(categoria), mapOf(categoria.id to movimentacoes))

        val linhaRecebido = csvExportado.lines().first { it.contains("Venda balcão") }
        val linhaPago = csvExportado.lines().first { it.contains("Compra material") }
        assertTrue(linhaRecebido.contains(";Recebido;"))
        assertTrue(linhaPago.contains(";Pago;"))
        assertTrue(linhaRecebido.contains("540,00"))
        assertTrue(linhaPago.contains("200,00"))
    }
}

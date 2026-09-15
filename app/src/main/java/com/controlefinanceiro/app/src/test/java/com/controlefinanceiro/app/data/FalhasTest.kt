package com.controlefinanceiro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes de CAMINHOS DE FALHA — não "isso funciona quando tudo dá certo", mas
 * "isso não corrompe dados nem derruba o app quando algo dá errado": entrada
 * inconsistente entre telas, dados que não deveriam existir mas podem existir por um
 * bug em outro lugar, valores extremos, CSV malicioso/malformado de propósito.
 */
class FalhasTest {

    // ---------- reverterBaixa / editarDataBaixa: pagamento de outra movimentação ----------

    @Test(expected = IllegalArgumentException::class)
    fun `reverterBaixa recusa um pagamento que nao pertence aquela movimentacao`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val pagamentoDeOutraMovimentacao = Pagamento(
            id = "pagamento-fantasma", valorPago = 100.0,
            parcelasAplicadas = mapOf("1" to 100.0)
        )
        ParcelaUtils.reverterBaixa(mov, pagamentoDeOutraMovimentacao)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `editarDataBaixa recusa um pagamento que nao pertence aquela movimentacao`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val pagamentoFantasma = Pagamento(id = "pagamento-fantasma", parcelasAplicadas = mapOf("1" to 100.0))
        ParcelaUtils.editarDataBaixa(mov, pagamentoFantasma, System.currentTimeMillis())
    }

    @Test
    fun `reverterBaixa aceita normalmente um pagamento que realmente pertence a movimentacao`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])
        val comBaixa = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref), 100.0).getValue("m1")
        val pagamentoReal = comBaixa.historicoPagamentos.single()
        val revertida = ParcelaUtils.reverterBaixa(comBaixa, pagamentoReal)
        assertEquals(0.0, revertida.parcelas[0].valorPago, 0.001)
    }

    // ---------- aplicarBaixaConsolidada: dados inconsistentes nas selecionadas ----------

    @Test
    fun `baixa ignora referencia a numero de parcela que nao existe na movimentacao`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val refParaParcelaInexistente = ParcelaRef("m1", "Compra", 1, Parcela(numero = 99, valorOriginal = 50.0))
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(refParaParcelaInexistente), 50.0)
        assertTrue(resultado.isEmpty() || resultado.getValue("m1").parcelas.size == 1)
    }

    @Test
    fun `baixa proporcional tambem ignora numero de parcela que nao existe`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val refParaParcelaInexistente = ParcelaRef("m1", "Compra", 1, Parcela(numero = 99, valorOriginal = 50.0))
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            mapOf("m1" to mov), listOf(refParaParcelaInexistente), 50.0, proporcional = true
        )
        assertTrue(resultado.isEmpty() || resultado.getValue("m1").parcelas[0].valorPago == 0.0)
    }

    @Test
    fun `lista de selecionadas vazia nao lanca excecao, so nao aplica nada`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), emptyList(), 100.0)
        assertTrue(resultado.isEmpty())
    }

    @Test
    fun `mapa de movimentacoes vazio com selecionadas nao vazias nao lanca excecao`() {
        val refOrfa = ParcelaRef("m-nao-existe", "Compra", 1, Parcela(numero = 1, valorOriginal = 50.0))
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(emptyMap(), listOf(refOrfa), 50.0)
        assertTrue(resultado.isEmpty())
    }

    // ---------- aplicarValoresManuais: valor negativo digitado por engano ----------

    @Test
    fun `valor manual negativo nao quebra a soma total, mas produz uma duplicata negativa (limitacao conhecida)`() {
        val base = ParcelaUtils.gerarParcelas(300.0, 3, System.currentTimeMillis(), diaVencimento = 10)
        val ajustadas = ParcelaUtils.aplicarValoresManuais(base, mapOf(1 to -50.0), valorTotal = 300.0)
        assertEquals(-50.0, ajustadas[0].valorOriginal, 0.001)
        assertEquals(300.0, ajustadas.sumOf { it.valorOriginal }, 0.001)
    }

    @Test
    fun `valor manual maior que o total deixa a ultima parcela negativa (limitacao conhecida)`() {
        val base = ParcelaUtils.gerarParcelas(300.0, 3, System.currentTimeMillis(), diaVencimento = 10)
        val ajustadas = ParcelaUtils.aplicarValoresManuais(base, mapOf(1 to 999.0), valorTotal = 300.0)
        assertTrue("última parcela deveria ficar negativa neste cenário: ${ajustadas.last().valorOriginal}",
            ajustadas.last().valorOriginal < 0)
    }

    // ---------- gerarParcelas / gerarParcelasCartao: parâmetros inválidos ----------

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelas rejeita numero de parcelas negativo`() {
        ParcelaUtils.gerarParcelas(100.0, -1, System.currentTimeMillis(), diaVencimento = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelasCartao rejeita numero de parcelas zero`() {
        ParcelaUtils.gerarParcelasCartao(100.0, 0, System.currentTimeMillis(), melhorDia = 10, diaVencimentoCartao = 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelasCartao rejeita numero de parcelas negativo`() {
        ParcelaUtils.gerarParcelasCartao(100.0, -3, System.currentTimeMillis(), melhorDia = 10, diaVencimentoCartao = 5)
    }

    @Test
    fun `gerarParcelas com valorTotal zero nao lanca excecao, gera parcelas zeradas`() {
        val parcelas = ParcelaUtils.gerarParcelas(0.0, 3, System.currentTimeMillis(), diaVencimento = 10)
        assertEquals(3, parcelas.size)
        assertEquals(0.0, parcelas.sumOf { it.valorOriginal }, 0.001)
    }

    @Test
    fun `gerarParcelas com numero de parcelas muito grande (100x) ainda fecha a soma exata`() {
        val parcelas = ParcelaUtils.gerarParcelas(1000.0, 100, System.currentTimeMillis(), diaVencimento = 10)
        assertEquals(100, parcelas.size)
        assertEquals(1000.0, parcelas.sumOf { it.valorOriginal }, 0.001)
    }

    // ---------- formatarMoeda / valores extremos ou inválidos ----------

    @Test
    fun `formatarMoeda com valor NaN nao lanca excecao`() {
        val resultado = formatarMoeda(Double.NaN)
        assertTrue(resultado.isNotBlank())
    }

    @Test
    fun `formatarMoeda com infinito nao lanca excecao`() {
        val resultado = formatarMoeda(Double.POSITIVE_INFINITY)
        assertTrue(resultado.isNotBlank())
    }

    @Test
    fun `formatarMoeda com valor extremamente grande nao lanca excecao`() {
        val resultado = formatarMoeda(999999999999.99)
        assertTrue(resultado.isNotBlank())
    }

    @Test
    fun `formatarMoeda com valor extremamente pequeno arredonda pra zero, nao quebra`() {
        assertEquals("R$ 0,00", formatarMoeda(0.0000001))
    }

    // ---------- Movimentacao com dados vazios/inconsistentes ----------

    @Test
    fun `movimentacao sem nenhuma parcela tem valorEmAberto igual ao valorTotal`() {
        val mov = Movimentacao(valorTotal = 100.0, parcelas = emptyList())
        assertEquals(100.0, mov.valorEmAberto, 0.001)
        assertEquals(0.0, mov.valorPagoTotal, 0.001)
        assertFalse(mov.quitada)
    }

    @Test
    fun `movimentacao com valorTotal zero e sem parcelas nao lanca excecao em nenhum getter`() {
        val mov = Movimentacao(valorTotal = 0.0, parcelas = emptyList())
        assertEquals(0.0, mov.valorEmAberto, 0.001)
        assertTrue(mov.podeEditarOuExcluir)
    }

    // ---------- ImportarDados: entradas adversariais/maliciosas ----------

    @Test
    fun `csv com caracteres de controle no meio da linha nao derruba o parser`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda\u0000estranha;100,00")
        assertEquals(1, linhas.size)
    }

    @Test
    fun `csv com uma linha absurdamente longa nao trava nem estoura memoria`() {
        val descricaoGigante = "A".repeat(50000)
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;$descricaoGigante;100,00")
        assertEquals(1, linhas.size)
        assertTrue(linhas[0].valida)
    }

    @Test
    fun `csv onde toda linha eh so ponto e virgula nao trava o parser`() {
        val csv = List(20) { ";;;" }.joinToString("\n")
        val linhas = parsearCsvFluxo(csv)
        assertEquals(20, linhas.size)
        assertTrue(linhas.all { !it.valida })
    }

    @Test
    fun `csv com valor absurdamente grande nao lanca excecao`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda gigante;99999999999999999999999999,00")
        assertTrue(linhas[0].valida || linhas[0].erro != null)
    }

    @Test
    fun `csv com aspas desbalanceadas nao trava o parser`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;\"Venda sem fechar aspas;100,00")
        assertEquals(1, linhas.size)
    }

    @Test
    fun `csv totalmente vazio devolve lista vazia, nao null nem excecao`() {
        val linhas = parsearCsvFluxo("")
        assertTrue(linhas.isEmpty())
    }

    @Test
    fun `csv so com quebras de linha devolve lista vazia`() {
        val linhas = parsearCsvFluxo("\n\n\n\n")
        assertTrue(linhas.isEmpty())
    }

    // ---------- ExportarDados: dados extremos na exportação ----------

    @Test
    fun `exportar cliente com nome vazio nao lanca excecao`() {
        val clienteSemNome = Cliente(id = "c1", nome = "")
        val csv = gerarCsvCliente(clienteSemNome, emptyList(), emptyMap())
        assertTrue(csv.isNotEmpty())
    }

    @Test
    fun `exportar categoria com muitas movimentacoes (1000) nao trava`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Categoria grande")
        val muitasMovimentacoes = (1..1000).map { i ->
            Movimentacao(id = "m$i", categoriaId = "c1", descricao = "Venda $i", valorTotal = i.toDouble())
        }
        val cliente = Cliente(id = "c1", nome = "Teste")
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("c1" to muitasMovimentacoes))
        assertEquals(1000, csv.lines().count { it.contains("Venda ") })
    }

    @Test
    fun `descricao com quebra de linha embutida nao quebra a estrutura do csv`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(id = "m1", categoriaId = "c1", descricao = "Venda\ncom quebra de linha", valorTotal = 10.0)
        val cliente = Cliente(id = "c1", nome = "Teste")
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("c1" to listOf(mov)))
        assertTrue(csv.contains("\"Venda\ncom quebra de linha\""))
    }

    // =====================================================================
    // aplicarBaixaConsolidada: a mesma parcela selecionada duas vezes
    // (bug real encontrado e corrigido — ver comentário em ParcelaUtils.kt)
    // =====================================================================

    @Test
    fun `baixa proporcional com a mesma parcela selecionada duas vezes nao paga mais que o valor original`() {
        // Este é o teste de regressão do bug: antes da correção, uma parcela de R$ 100
        // selecionada duas vezes na mesma baixa proporcional terminava com
        // valorPago = R$ 133,34 — pagando mais do que ela vale.
        val mov1 = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val mov2 = Movimentacao(id = "m2", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref1 = ParcelaRef("m1", "Compra 1", 1, mov1.parcelas[0])
        val ref2 = ParcelaRef("m2", "Compra 2", 1, mov2.parcelas[0])
        val selecionadasComDuplicata = listOf(ref1, ref1, ref2) // ref1 repetida de propósito

        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            mapOf("m1" to mov1, "m2" to mov2), selecionadasComDuplicata, valorPago = 200.0, proporcional = true
        )

        val parcela1 = resultado.getValue("m1").parcelas[0]
        assertTrue(
            "parcela pagou mais que o valor original: valorPago=${parcela1.valorPago}, valorOriginal=${parcela1.valorOriginal}",
            parcela1.valorPago <= parcela1.valorOriginal + 0.01
        )
    }

    @Test
    fun `baixa proporcional com parcela duplicada 3 vezes ainda fecha certo`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            mapOf("m1" to mov), listOf(ref, ref, ref), valorPago = 100.0, proporcional = true
        )
        val parcela = resultado.getValue("m1").parcelas[0]
        assertEquals(100.0, parcela.valorPago, 0.01)
        assertFalse(parcela.valorPago > parcela.valorOriginal)
    }

    @Test
    fun `baixa sequencial com a mesma parcela selecionada duas vezes tambem nao paga mais que o valor original`() {
        // O modo sequencial já era seguro contra isso antes da correção (por causa do
        // "if (parcelaAtual.status == paga) continue"), mas confirma que continua
        // seguro depois da desduplicação também.
        val mov1 = Movimentacao(id = "m1", valorTotal = 50.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 50.0)))
        val mov2 = Movimentacao(id = "m2", valorTotal = 50.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 50.0)))
        val ref1 = ParcelaRef("m1", "Compra 1", 1, mov1.parcelas[0])
        val ref2 = ParcelaRef("m2", "Compra 2", 1, mov2.parcelas[0])
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            mapOf("m1" to mov1, "m2" to mov2), listOf(ref1, ref1, ref2), valorPago = 100.0
        )
        val parcela1 = resultado.getValue("m1").parcelas[0]
        val parcela2 = resultado.getValue("m2").parcelas[0]
        assertEquals(50.0, parcela1.valorPago, 0.01)
        assertEquals(50.0, parcela2.valorPago, 0.01) // a segunda não pode ter ficado sem nada
    }

    @Test
    fun `duplicata na selecao gera so um registro de pagamento por parcela no historico, nao dois`() {
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref, ref), valorPago = 100.0)
        val pagamento = resultado.getValue("m1").historicoPagamentos.single()
        assertEquals(listOf(1), pagamento.parcelasNumeros) // não vira [1, 1]
    }

    // =====================================================================
    // Mais cenários de falha
    // =====================================================================

    @Test
    fun `parcelamento atravessando virada de ano fecha no mes certo`() {
        // Compra em dezembro/2026, parcelada em 3x -> primeira parcela cai em
        // janeiro/2027, não "mês 13 de 2026".
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.DECEMBER, 15, 12, 0, 0)
        val parcelas = ParcelaUtils.gerarParcelas(300.0, 3, cal.timeInMillis, diaVencimento = 10)
        val calResultado = java.util.Calendar.getInstance().apply { timeInMillis = parcelas[0].dataVencimento }
        assertEquals(2027, calResultado.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.JANUARY, calResultado.get(java.util.Calendar.MONTH))
    }

    @Test
    fun `avaliarExpressao com expressao muito longa (50 operacoes) nao trava`() {
        val expressaoLonga = (1..50).joinToString("+") { "1" } // "1+1+1+...+1" (50 vezes)
        val resultado = avaliarExpressao(expressaoLonga)
        assertEquals(50.0, resultado!!, 0.001)
    }

    @Test
    fun `csv de fluxo com emoji e acentos na descricao nao quebra o parser nem a exportacao`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda de açúcar 🍬 pro João;100,00")
        assertTrue(linhas[0].valida)
        assertEquals("Venda de açúcar 🍬 pro João", linhas[0].descricao)
    }

    @Test
    fun `gerarCsvCliente com nome de cliente contendo emoji nao lanca excecao`() {
        val cliente = Cliente(id = "c1", nome = "João 😊 Silva")
        val csv = gerarCsvCliente(cliente, emptyList(), emptyMap())
        assertTrue(csv.contains("João 😊 Silva"))
    }

    @Test
    fun `data em millis negativo (antes de 1970) nao lanca excecao na formatacao`() {
        // Não deveria acontecer na prática, mas um bug em outro lugar poderia gerar isso
        // (ex: subtração de datas mal feita). formatarData não pode quebrar por causa disso.
        val resultado = formatarData(-86400000L) // um dia antes de 1/1/1970
        assertTrue(resultado.isNotBlank())
    }

    // =====================================================================
    // gerarParcelas / gerarParcelasCartao: diaVencimento zero ou negativo
    // (bug real encontrado — ver comentário em ParcelaUtils.kt)
    // =====================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelas rejeita diaVencimento zero`() {
        // Antes da validação: diaVencimento=0 fazia o Calendar rolar silenciosamente
        // pro ÚLTIMO DIA DO MÊS ANTERIOR (comportamento "lenient" do java.util.Calendar),
        // gerando uma parcela com vencimento num mês inteiro errado, sem lançar erro
        // nenhum. Compra em 10/08/2026 devia vencer em setembro; dava 31/08 (mês errado).
        ParcelaUtils.gerarParcelas(100.0, 1, System.currentTimeMillis(), diaVencimento = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelas rejeita diaVencimento negativo`() {
        ParcelaUtils.gerarParcelas(100.0, 1, System.currentTimeMillis(), diaVencimento = -5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelas rejeita diaVencimento maior que 31`() {
        ParcelaUtils.gerarParcelas(100.0, 1, System.currentTimeMillis(), diaVencimento = 32)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelasCartao rejeita diaVencimentoCartao zero`() {
        ParcelaUtils.gerarParcelasCartao(100.0, 1, System.currentTimeMillis(), melhorDia = 10, diaVencimentoCartao = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelasCartao rejeita melhorDia zero`() {
        ParcelaUtils.gerarParcelasCartao(100.0, 1, System.currentTimeMillis(), melhorDia = 0, diaVencimentoCartao = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelasCartao rejeita melhorDia negativo`() {
        ParcelaUtils.gerarParcelasCartao(100.0, 1, System.currentTimeMillis(), melhorDia = -1, diaVencimentoCartao = 10)
    }

    @Test
    fun `gerarParcelas aceita diaVencimento nos limites validos (1 e 31)`() {
        val comDia1 = ParcelaUtils.gerarParcelas(100.0, 1, System.currentTimeMillis(), diaVencimento = 1)
        val comDia31 = ParcelaUtils.gerarParcelas(100.0, 1, System.currentTimeMillis(), diaVencimento = 31)
        assertEquals(1, comDia1.size)
        assertEquals(1, comDia31.size)
    }

    // =====================================================================
    // Descrição automática da baixa mostrando o total errado quando dividida
    // entre movimentações (bug real encontrado — ver comentário em ParcelaUtils.kt)
    // =====================================================================

    @Test
    fun `descricao da baixa dividida mostra o valor que ESSA movimentacao recebeu, nao o total da transacao inteira`() {
        // Este é o teste de regressão: antes da correção, uma baixa de R$ 200 dividida
        // em R$ 50 (mov1) + R$ 150 (mov2) fazia a descrição de mov1 dizer "Baixa de
        // R$ 200,00" — o total da transação inteira — mesmo ela tendo recebido só R$ 50.
        val mov1 = Movimentacao(id = "m1", valorTotal = 50.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 50.0)))
        val mov2 = Movimentacao(id = "m2", valorTotal = 150.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 150.0)))
        val ref1 = ParcelaRef("m1", "Compra 1", 1, mov1.parcelas[0])
        val ref2 = ParcelaRef("m2", "Compra 2", 1, mov2.parcelas[0])

        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            mapOf("m1" to mov1, "m2" to mov2), listOf(ref1, ref2), valorPago = 200.0
        )

        val pag1 = resultado.getValue("m1").historicoPagamentos.single()
        val pag2 = resultado.getValue("m2").historicoPagamentos.single()

        assertTrue("descrição deveria mostrar R$ 50,00 (a parte de mov1): ${pag1.descricao}", pag1.descricao.contains("R$ 50,00"))
        assertTrue("descrição não deveria mostrar o total de R$ 200,00 na mov1: ${pag1.descricao}", !pag1.descricao.contains("R$ 200,00"))
        assertTrue("descrição deveria mostrar R$ 150,00 (a parte de mov2): ${pag2.descricao}", pag2.descricao.contains("R$ 150,00"))

        // e o campo numérico (valorPago) continua batendo com o que a descrição diz
        assertEquals(50.0, pag1.valorPago, 0.001)
        assertEquals(150.0, pag2.valorPago, 0.001)
    }

    @Test
    fun `descricao da baixa continua mostrando o valor certo quando NAO eh dividida (uma so movimentacao)`() {
        // Confirma que a correção não quebrou o caso comum (baixa numa única movimentação,
        // onde o valor recebido por ela É o total da transação mesmo).
        val mov = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref = ParcelaRef("m1", "Compra", 1, mov.parcelas[0])
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(mapOf("m1" to mov), listOf(ref), valorPago = 100.0)
        val pagamento = resultado.getValue("m1").historicoPagamentos.single()
        assertTrue(pagamento.descricao.contains("R$ 100,00"))
    }

    @Test
    fun `descricao da baixa proporcional dividida tambem mostra o valor certo por movimentacao`() {
        val mov1 = Movimentacao(id = "m1", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val mov2 = Movimentacao(id = "m2", valorTotal = 100.0, parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0)))
        val ref1 = ParcelaRef("m1", "Compra 1", 1, mov1.parcelas[0])
        val ref2 = ParcelaRef("m2", "Compra 2", 1, mov2.parcelas[0])
        // 100 dividido igualmente (50/50) entre as duas, proporcional
        val resultado = ParcelaUtils.aplicarBaixaConsolidada(
            mapOf("m1" to mov1, "m2" to mov2), listOf(ref1, ref2), valorPago = 100.0, proporcional = true
        )
        val pag1 = resultado.getValue("m1").historicoPagamentos.single()
        assertTrue("esperava R$ 50,00 na descrição: ${pag1.descricao}", pag1.descricao.contains("R$ 50,00"))
        assertTrue(!pag1.descricao.contains("R$ 100,00"))
    }

    // =====================================================================
    // CSV: ano com 2 dígitos vira ano 26 d.C. em vez de ser rejeitado
    // (bug real encontrado — ver comentário em ImportarDados.kt)
    // =====================================================================

    @Test
    fun `data com ano de 2 digitos eh rejeitada, nao interpretada como ano 26 dC`() {
        // Este é o teste de regressão do bug: o java.util.SimpleDateFormat aceita ano
        // com menos de 4 dígitos e interpreta literalmente — "12/08/26" virava o ano
        // 26 d.C. (não 2026!), silenciosamente aceito como uma linha "válida".
        val linhas = parsearCsvFluxo("12/08/26;Recebido;Venda;100,00")
        assertFalse("data com ano de 2 dígitos deveria ser rejeitada: ${linhas[0]}", linhas[0].valida)
    }

    @Test
    fun `data com ano de 3 digitos tambem eh rejeitada`() {
        val linhas = parsearCsvFluxo("12/08/226;Recebido;Venda;100,00")
        assertFalse(linhas[0].valida)
    }

    @Test
    fun `data com ano de 4 digitos continua funcionando normalmente`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda;100,00")
        assertTrue(linhas[0].valida)
    }

    @Test
    fun `data com dia e mes de um digito so (sem zero a esquerda) continua aceita`() {
        // A correção do bug do ano não pode restringir sem necessidade o dia/mês, que
        // sempre foram aceitos com 1 ou 2 dígitos.
        val linhas = parsearCsvFluxo("5/8/2026;Recebido;Venda;100,00")
        assertTrue(linhas[0].valida)
    }

    @Test
    fun `data com hora e ano de 2 digitos tambem eh rejeitada`() {
        val linhas = parsearCsvFluxo("12/08/26 14:30;Recebido;Venda;100,00")
        assertFalse(linhas[0].valida)
    }

    // =====================================================================
    // CSV: ponto como separador de milhar (sem vírgula) virava valor 1000x menor
    // (bug real encontrado — ver comentário em ImportarDados.kt)
    // =====================================================================

    @Test
    fun `valor com ponto de milhar sem virgula eh interpretado corretamente, nao como decimal`() {
        // Este é o teste de regressão do bug: "1.234" (mil duzentos e trinta e quatro,
        // formato comum de planilha) virava R$ 1,23 — um erro de 1000x, silencioso.
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda grande;1.234")
        assertTrue(linhas[0].valida)
        assertEquals(1234.0, linhas[0].valor!!, 0.001)
    }

    @Test
    fun `valor com dois grupos de milhar (1_234_567) eh interpretado corretamente`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda gigante;1.234.567")
        assertTrue(linhas[0].valida)
        assertEquals(1234567.0, linhas[0].valor!!, 0.001)
    }

    @Test
    fun `valor decimal americano com duas casas continua funcionando depois da correcao`() {
        // Confirma que a correção do bug do milhar não quebrou o caso "99.90" (decimal
        // americano de verdade, 2 casas) que já funcionava antes.
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda;99.90")
        assertTrue(linhas[0].valida)
        assertEquals(99.90, linhas[0].valor!!, 0.001)
    }

    @Test
    fun `valor decimal americano com uma casa so continua funcionando`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda;99.9")
        assertTrue(linhas[0].valida)
        assertEquals(99.9, linhas[0].valor!!, 0.001)
    }

    @Test
    fun `valor com milhar e virgula decimal juntos continua funcionando (1_234,56)`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda;1.234,56")
        assertTrue(linhas[0].valida)
        assertEquals(1234.56, linhas[0].valor!!, 0.001)
    }

    // =====================================================================
    // gerarParcelas / gerarParcelasCartao: numero de parcelas sem limite maximo
    // (bug real encontrado — ver comentário em ParcelaUtils.kt)
    // =====================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelas rejeita numero de parcelas absurdamente grande`() {
        // Antes da validação: só existia "numParcelas >= 1", sem teto. Um erro de
        // digitação (um zero a mais por engano) gerava uma lista enorme de parcelas —
        // que nem cabe no limite de tamanho de documento do Firestore.
        ParcelaUtils.gerarParcelas(1000.0, 100000, System.currentTimeMillis(), diaVencimento = 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelas rejeita numero de parcelas logo acima do limite (361)`() {
        ParcelaUtils.gerarParcelas(1000.0, 361, System.currentTimeMillis(), diaVencimento = 10)
    }

    @Test
    fun `gerarParcelas aceita exatamente o limite maximo (360)`() {
        val parcelas = ParcelaUtils.gerarParcelas(360.0, 360, System.currentTimeMillis(), diaVencimento = 10)
        assertEquals(360, parcelas.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `gerarParcelasCartao tambem rejeita numero de parcelas absurdamente grande`() {
        ParcelaUtils.gerarParcelasCartao(1000.0, 100000, System.currentTimeMillis(), melhorDia = 10, diaVencimentoCartao = 5)
    }

    // =====================================================================
    // gerarCsvCliente: inconsistência entre a lista de categorias e o mapa
    // de movimentações (documenta comportamento, não é um bug corrigido)
    // =====================================================================

    @Test
    fun `movimentacao cuja categoria nao esta na lista de categorias eh silenciosamente omitida da exportacao`() {
        // Isso não deveria acontecer na prática (o ViewModel sempre busca as duas listas
        // juntas, de forma consistente), mas documenta o comportamento pra o caso de uma
        // inconsistência escapar por algum motivo (ex: uma condição de corrida rara) —
        // a movimentação "órfã" some silenciosamente do CSV, sem erro nem aviso.
        val categoriaReal = Categoria(id = "cat1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val movDaCategoriaReal = Movimentacao(id = "m1", categoriaId = "cat1", descricao = "Venda visível", valorTotal = 10.0)
        val movOrfa = Movimentacao(id = "m2", categoriaId = "cat-fantasma", descricao = "Venda invisível", valorTotal = 20.0)
        val cliente = Cliente(id = "c1", nome = "Teste")

        val csv = gerarCsvCliente(
            cliente, listOf(categoriaReal),
            mapOf("cat1" to listOf(movDaCategoriaReal), "cat-fantasma" to listOf(movOrfa))
        )

        assertTrue(csv.contains("Venda visível"))
        assertTrue("uma movimentação de categoria não listada não deveria aparecer no CSV", !csv.contains("Venda invisível"))
    }

    @Test
    fun `categoria sem entrada nenhuma no mapa de movimentacoes ainda aparece no csv (sem crash)`() {
        // O oposto do teste acima: categoria existe na lista, mas o mapa nem tem uma
        // entrada (nem vazia) pra ela — .orEmpty() deveria cobrir isso sem exceção.
        val categoria = Categoria(id = "cat1", tipo = TipoMovimentacao.RECEBER, nome = "Categoria sem entrada no mapa")
        val cliente = Cliente(id = "c1", nome = "Teste")
        val csv = gerarCsvCliente(cliente, listOf(categoria), emptyMap())
        assertTrue(csv.contains("Categoria sem entrada no mapa"))
    }

    // =====================================================================
    // Interação entre fluxo de caixa e o mecanismo de baixa: um lançamento de
    // fluxo nasce quitado e nunca passa por aplicarBaixaConsolidada, então
    // não deveria ter nada pra "reverter" ou "editar data de baixa".
    // =====================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `reverterBaixa num lancamento de fluxo eh recusado (nunca teve baixa de verdade)`() {
        val lancamentoFluxo = Movimentacao(
            id = "m1", valorTotal = 540.0,
            parcelas = ParcelaUtils.gerarParcelaQuitada(540.0, System.currentTimeMillis())
        )
        val pagamentoQualquer = Pagamento(id = "x", parcelasAplicadas = mapOf("1" to 540.0))
        // historicoPagamentos do lançamento de fluxo está sempre vazio -> a validação
        // de "pagamento pertence a essa movimentação" recusa corretamente.
        ParcelaUtils.reverterBaixa(lancamentoFluxo, pagamentoQualquer)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `editarDataBaixa num lancamento de fluxo tambem eh recusado`() {
        val lancamentoFluxo = Movimentacao(
            id = "m1", valorTotal = 540.0,
            parcelas = ParcelaUtils.gerarParcelaQuitada(540.0, System.currentTimeMillis())
        )
        val pagamentoQualquer = Pagamento(id = "x", parcelasAplicadas = mapOf("1" to 540.0))
        ParcelaUtils.editarDataBaixa(lancamentoFluxo, pagamentoQualquer, System.currentTimeMillis())
    }

    @Test
    fun `lancamento de fluxo nao aparece como alvo valido pra selecionarMaisAntigasAte (ja nasce sem faltante)`() {
        // Como a parcela do fluxo já nasce paga, o "faltante" dela é zero — não devia
        // nunca ser oferecida como algo em aberto pra selecionar numa baixa.
        val lancamentoFluxo = Movimentacao(
            id = "m1", valorTotal = 540.0,
            parcelas = ParcelaUtils.gerarParcelaQuitada(540.0, System.currentTimeMillis())
        )
        val ref = ParcelaRef("m1", "Recebido", 1, lancamentoFluxo.parcelas[0])
        val selecionadas = ParcelaUtils.selecionarMaisAntigasAte(listOf(ref), 1000.0)
        assertTrue("uma parcela já quitada não devia ser selecionável pra baixa", selecionadas.isEmpty())
    }
}

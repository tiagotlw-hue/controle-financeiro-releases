package com.controlefinanceiro.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportarDadosTest {

    private val cliente = Cliente(id = "c1", nome = "João da Silva", saldo = 100.0, totalAReceber = 300.0, totalAPagar = 200.0)

    @Test
    fun `csv contem o nome e o saldo do cliente`() {
        val csv = gerarCsvCliente(cliente, categorias = emptyList(), movimentacoesPorCategoria = emptyMap())
        assertTrue(csv.contains("João da Silva"))
        assertTrue(csv.contains("R$ 100,00") || csv.contains("R$ 100.00"))
    }

    @Test
    fun `categoria a receber exporta com o rotulo certo`() {
        val categoria = Categoria(id = "cat1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(id = "m1", categoriaId = "cat1", tipo = TipoMovimentacao.RECEBER, descricao = "Venda", valorTotal = 100.0)
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("cat1" to listOf(mov)))
        val linha = csv.lines().first { it.contains("Venda") }
        assertTrue(linha.contains("A receber"))
    }

    @Test
    fun `categoria a pagar exporta com o rotulo certo`() {
        val categoria = Categoria(id = "cat1", tipo = TipoMovimentacao.PAGAR, nome = "Fornecedor")
        val mov = Movimentacao(id = "m1", categoriaId = "cat1", tipo = TipoMovimentacao.PAGAR, descricao = "Compra", valorTotal = 100.0)
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("cat1" to listOf(mov)))
        val linha = csv.lines().first { it.contains("Compra") }
        assertTrue(linha.contains("A pagar"))
    }

    // Teste de regressão: antes da correção, TODA linha de uma categoria de fluxo saía
    // com o rótulo fixo "A pagar" (bug), porque o rótulo era calculado uma vez por
    // categoria, ignorando o tipo de cada lançamento individual (Recebido/Pago).
    @Test
    fun `categoria de fluxo exporta cada lancamento com o proprio tipo (nao um rotulo fixo da categoria)`() {
        val categoria = Categoria(id = "cat1", tipo = TipoMovimentacao.FLUXO, nome = "Caixa do dia")
        val recebido = Movimentacao(id = "m1", categoriaId = "cat1", tipo = TipoMovimentacao.RECEBER, descricao = "Venda balcão", valorTotal = 540.0)
        val pago = Movimentacao(id = "m2", categoriaId = "cat1", tipo = TipoMovimentacao.PAGAR, descricao = "Compra material", valorTotal = 200.0)
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("cat1" to listOf(recebido, pago)))

        val linhaRecebido = csv.lines().first { it.contains("Venda balcão") }
        val linhaPago = csv.lines().first { it.contains("Compra material") }

        assertTrue("linha de recebimento deveria dizer 'Recebido': $linhaRecebido", linhaRecebido.contains("Recebido"))
        assertTrue("linha de pagamento deveria dizer 'Pago': $linhaPago", linhaPago.contains(";Pago;"))
        // nenhuma das duas pode ter o rótulo genérico de categoria (o bug antigo)
        assertTrue(!linhaRecebido.contains("A pagar"))
        assertTrue(!linhaPago.contains("A receber"))
    }

    @Test
    fun `categoria de fluxo sem movimentacoes usa o rotulo Fluxo de caixa`() {
        val categoria = Categoria(id = "cat1", tipo = TipoMovimentacao.FLUXO, nome = "Caixa vazio")
        val csv = gerarCsvCliente(cliente, listOf(categoria), emptyMap())
        val linha = csv.lines().first { it.contains("Caixa vazio") }
        assertTrue(linha.contains("Fluxo de caixa"))
    }

    @Test
    fun `status quitada quando nao ha valor em aberto`() {
        val categoria = Categoria(id = "cat1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(
            id = "m1", categoriaId = "cat1", valorTotal = 100.0,
            parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0, valorPago = 100.0, status = "paga"))
        )
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("cat1" to listOf(mov)))
        assertTrue(csv.contains("Quitada"))
    }

    @Test
    fun `descricao com ponto e virgula fica entre aspas no csv`() {
        val categoria = Categoria(id = "cat1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(id = "m1", categoriaId = "cat1", descricao = "Venda; item extra", valorTotal = 50.0)
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("cat1" to listOf(mov)))
        assertTrue(csv.contains("\"Venda; item extra\""))
    }

    @Test
    fun `csv comeca com o BOM UTF-8 para abrir certo no excel`() {
        val csv = gerarCsvCliente(cliente, emptyList(), emptyMap())
        assertTrue(csv.startsWith("\uFEFF"))
    }

    // =====================================================================
    // Bateria mais profunda
    // =====================================================================

    @Test
    fun `varias categorias de tipos diferentes aparecem todas, cada uma com seu rotulo`() {
        val receber = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val pagar = Categoria(id = "c2", tipo = TipoMovimentacao.PAGAR, nome = "Fornecedor")
        val fluxo = Categoria(id = "c3", tipo = TipoMovimentacao.FLUXO, nome = "Caixa")
        val movs = mapOf(
            "c1" to listOf(Movimentacao(id = "m1", categoriaId = "c1", tipo = TipoMovimentacao.RECEBER, descricao = "Venda fiado", valorTotal = 10.0)),
            "c2" to listOf(Movimentacao(id = "m2", categoriaId = "c2", tipo = TipoMovimentacao.PAGAR, descricao = "Compra fornecedor", valorTotal = 20.0)),
            "c3" to listOf(Movimentacao(id = "m3", categoriaId = "c3", tipo = TipoMovimentacao.RECEBER, descricao = "Venda caixa", valorTotal = 30.0))
        )
        val csv = gerarCsvCliente(cliente, listOf(receber, pagar, fluxo), movs)
        assertTrue(csv.contains("Venda fiado") && csv.lines().first { it.contains("Venda fiado") }.contains("A receber"))
        assertTrue(csv.contains("Compra fornecedor") && csv.lines().first { it.contains("Compra fornecedor") }.contains("A pagar"))
        assertTrue(csv.contains("Venda caixa") && csv.lines().first { it.contains("Venda caixa") }.contains(";Recebido;"))
    }

    @Test
    fun `categoria sem nenhuma movimentacao ainda aparece no csv (linha so com o nome)`() {
        val vazia = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Categoria nova")
        val csv = gerarCsvCliente(cliente, listOf(vazia), emptyMap())
        assertTrue(csv.contains("Categoria nova"))
    }

    @Test
    fun `status parcial e em aberto aparecem corretamente conforme o valor pago`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val parcial = Movimentacao(
            id = "m1", categoriaId = "c1", descricao = "Parcialmente paga", valorTotal = 100.0,
            parcelas = listOf(Parcela(numero = 1, valorOriginal = 100.0, valorPago = 40.0, status = "parcial"))
        )
        val aberta = Movimentacao(
            id = "m2", categoriaId = "c1", descricao = "Totalmente em aberto", valorTotal = 50.0,
            parcelas = listOf(Parcela(numero = 1, valorOriginal = 50.0))
        )
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("c1" to listOf(parcial, aberta)))
        assertTrue(csv.lines().first { it.contains("Parcialmente paga") }.contains("Parcial"))
        assertTrue(csv.lines().first { it.contains("Totalmente em aberto") }.contains("Em aberto"))
    }

    @Test
    fun `nome de categoria com ponto e virgula tambem fica entre aspas`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Vendas; Serviços")
        val csv = gerarCsvCliente(cliente, listOf(categoria), emptyMap())
        assertTrue(csv.contains("\"Vendas; Serviços\""))
    }

    @Test
    fun `campo sem ponto e virgula nem aspas nao ganha aspas desnecessarias`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(id = "m1", categoriaId = "c1", descricao = "Venda simples", valorTotal = 10.0)
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("c1" to listOf(mov)))
        assertTrue(csv.contains("Venda simples"))
        assertTrue(!csv.contains("\"Venda simples\""))
    }

    @Test
    fun `numero de parcelas e valores aparecem formatados com duas casas decimais`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(id = "m1", categoriaId = "c1", descricao = "Venda", valorTotal = 99.9, numParcelas = 3,
            parcelas = listOf(Parcela(numero = 1, valorOriginal = 33.3)))
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("c1" to listOf(mov)))
        assertTrue(csv.contains("R$ 99,90") || csv.contains("R$ 99.90"))
        assertTrue(csv.contains(";3\n") || csv.contains(";3\r\n"))
    }

    // Teste de regressão: os valores em R$ têm que sair com vírgula decimal SEMPRE,
    // não importa o idioma configurado no aparelho da pessoa. Antes da correção, o
    // código usava "%.2f".format(valor) sem especificar o Locale, então o separador
    // decimal (vírgula ou ponto) dependia do idioma padrão do aparelho — um telefone
    // configurado em inglês exportava "540.00" em vez de "540,00", inconsistente com
    // as datas do mesmo arquivo (que já eram sempre em pt-BR). Esse teste força o
    // Locale padrão da JVM pra inglês de propósito, exatamente pra travar se esse tipo
    // de formatação sem Locale explícito for reintroduzido em qualquer lugar.
    @Test
    fun `valores em R$ sempre usam virgula decimal, mesmo com o idioma do aparelho em ingles`() {
        val localeOriginal = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            val clienteComValor = cliente.copy(saldo = 1234.5)
            val csv = gerarCsvCliente(clienteComValor, emptyList(), emptyMap())
            assertTrue("esperava vírgula decimal mesmo com locale en_US: $csv", csv.contains("R$ 1234,50"))
            assertTrue("não devia ter ponto decimal (formato americano) vazando aqui: $csv", !csv.contains("1234.50"))
        } finally {
            java.util.Locale.setDefault(localeOriginal) // não pode vazar pros outros testes
        }
    }

    // ---------- gerarLegendaCompartilhamento ----------

    @Test
    fun `legenda de compartilhamento inclui o nome do cliente`() {
        val data = 1755000000000L // qualquer timestamp fixo, só pra ser determinístico
        val legenda = gerarLegendaCompartilhamento(cliente, data)
        assertTrue(legenda.contains(cliente.nome))
    }

    @Test
    fun `legenda de compartilhamento inclui a data no formato brasileiro`() {
        // 12/08/2026 12:00:00 UTC-3 (horário de Brasília)
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.AUGUST, 12, 12, 0, 0)
        val legenda = gerarLegendaCompartilhamento(cliente, cal.timeInMillis)
        assertTrue("esperava dd/MM/aaaa na legenda: $legenda", legenda.contains("12/08/2026"))
    }

    @Test
    fun `legenda de compartilhamento nao quebra com nome de cliente vazio`() {
        val semNome = cliente.copy(nome = "")
        val legenda = gerarLegendaCompartilhamento(semNome)
        assertTrue(legenda.isNotBlank()) // não deve virar uma string vazia/estranha
    }

    // =====================================================================
    // Proteção contra CSV Injection / Formula Injection (bug real encontrado)
    // =====================================================================
    //
    // Se um campo começa com =, +, -, @ (ou tab/CR), o Excel e outras planilhas
    // podem interpretar o conteúdo como FÓRMULA ao abrir o arquivo, não como texto.
    // Um nome de cliente ou descrição nesse formato — de propósito malicioso, ou até
    // sem querer, tipo "-50 desconto" ou "@joão" — podia virar um link clicável ou,
    // em versões antigas do Excel, chegar a executar comando externo via DDE.

    @Test
    fun `nome de cliente comecando com igual eh neutralizado com aspas simples`() {
        val clienteMalicioso = cliente.copy(nome = "=HYPERLINK(\"http://evil.com\",\"clique\")")
        val csv = gerarCsvCliente(clienteMalicioso, emptyList(), emptyMap())
        val linhaCliente = csv.lines().first { it.contains("Cliente;") }
        assertTrue("deveria começar com aspas simples depois do escape: $linhaCliente", linhaCliente.contains("'="))
        assertTrue("não pode sobrar um = no início do valor do campo", !linhaCliente.contains("Cliente;=") && !linhaCliente.contains("Cliente;\"="))
    }

    @Test
    fun `descricao comecando com mais eh neutralizada (padrao classico de injecao DDE)`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(id = "m1", categoriaId = "c1", descricao = "+cmd|'/c calc'!A1", valorTotal = 10.0)
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("c1" to listOf(mov)))
        assertTrue(csv.contains("'+cmd"))
        assertFalse(csv.contains(";+cmd")) // sem o apóstrofo de proteção na frente
    }

    @Test
    fun `descricao comecando com menos eh neutralizada`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(id = "m1", categoriaId = "c1", descricao = "-50+30", valorTotal = 10.0)
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("c1" to listOf(mov)))
        assertTrue(csv.contains("'-50+30"))
    }

    @Test
    fun `descricao comecando com arroba eh neutralizada`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(id = "m1", categoriaId = "c1", descricao = "@joão pediu fiado", valorTotal = 10.0)
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("c1" to listOf(mov)))
        assertTrue(csv.contains("'@joão"))
    }

    @Test
    fun `descricao normal sem caractere de formula no inicio nao ganha aspas simples desnecessarias`() {
        val categoria = Categoria(id = "c1", tipo = TipoMovimentacao.RECEBER, nome = "Fiado")
        val mov = Movimentacao(id = "m1", categoriaId = "c1", descricao = "Venda normal", valorTotal = 10.0)
        val csv = gerarCsvCliente(cliente, listOf(categoria), mapOf("c1" to listOf(mov)))
        assertTrue(csv.contains("Venda normal"))
        assertFalse(csv.contains("'Venda normal"))
    }

    @Test
    fun `nome de cliente com ponto e virgula no cabecalho nao quebra a estrutura do csv`() {
        // Bug relacionado: o nome do cliente no cabeçalho ("Cliente;...") nem passava
        // pelo escape antes — um nome com ; quebrava a linha em colunas erradas.
        val clienteComPontoVirgula = cliente.copy(nome = "João; Pedro")
        val csv = gerarCsvCliente(clienteComPontoVirgula, emptyList(), emptyMap())
        val linhaCliente = csv.lines().first { it.contains("Cliente;") }
        assertTrue("nome com ; devia ficar entre aspas: $linhaCliente", linhaCliente.contains("\"João; Pedro\""))
    }
}

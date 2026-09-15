package com.controlefinanceiro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportarDadosTest {

    @Test
    fun `linha valida com Recebido eh reconhecida corretamente`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda balcão;540,00")
        assertEquals(1, linhas.size)
        val l = linhas[0]
        assertTrue(l.valida)
        assertNull(l.erro)
        assertEquals(TipoMovimentacao.RECEBER, l.tipo)
        assertEquals("Venda balcão", l.descricao)
        assertEquals(540.0, l.valor!!, 0.001)
    }

    @Test
    fun `linha valida com Pago eh reconhecida corretamente`() {
        val linhas = parsearCsvFluxo("12/08/2026;Pago;Compra de material;200,50")
        assertEquals(TipoMovimentacao.PAGAR, linhas[0].tipo)
        assertEquals(200.50, linhas[0].valor!!, 0.001)
    }

    @Test
    fun `cabecalho na primeira linha eh ignorado automaticamente`() {
        val csv = "Data;Tipo;Descrição;Valor\n12/08/2026;Recebido;Venda balcão;540,00"
        val linhas = parsearCsvFluxo(csv)
        assertEquals(1, linhas.size) // o cabeçalho não vira uma linha de dado
        assertEquals(1, linhas[0].numeroLinha) // numeração continua a partir de 1
    }

    @Test
    fun `sem cabecalho todas as linhas sao processadas`() {
        val csv = "12/08/2026;Recebido;Venda 1;100,00\n13/08/2026;Pago;Compra 1;50,00"
        val linhas = parsearCsvFluxo(csv)
        assertEquals(2, linhas.size)
    }

    @Test
    fun `BOM do excel no inicio do arquivo nao atrapalha o parse`() {
        val csv = "\uFEFFData;Tipo;Descrição;Valor\n12/08/2026;Recebido;Venda;100,00"
        val linhas = parsearCsvFluxo(csv)
        assertEquals(1, linhas.size)
        assertTrue(linhas[0].valida)
    }

    @Test
    fun `linhas em branco sao ignoradas`() {
        val csv = "12/08/2026;Recebido;Venda 1;100,00\n\n\n13/08/2026;Pago;Compra 1;50,00\n"
        val linhas = parsearCsvFluxo(csv)
        assertEquals(2, linhas.size)
    }

    @Test
    fun `aceita variacoes de tipo - R, P, entrada, saida, maiusculas`() {
        val csv = listOf(
            "12/08/2026;R;A;10,00",
            "12/08/2026;p;B;10,00",
            "12/08/2026;ENTRADA;C;10,00",
            "12/08/2026;Saída;D;10,00",
            "12/08/2026;recebido;E;10,00"
        ).joinToString("\n")
        val linhas = parsearCsvFluxo(csv)
        assertEquals(TipoMovimentacao.RECEBER, linhas[0].tipo)
        assertEquals(TipoMovimentacao.PAGAR, linhas[1].tipo)
        assertEquals(TipoMovimentacao.RECEBER, linhas[2].tipo)
        assertEquals(TipoMovimentacao.PAGAR, linhas[3].tipo)
        assertEquals(TipoMovimentacao.RECEBER, linhas[4].tipo)
        linhas.forEach { assertTrue("linha ${it.numeroLinha} deveria ser válida", it.valida) }
    }

    @Test
    fun `tipo desconhecido gera erro e a linha fica invalida`() {
        val linhas = parsearCsvFluxo("12/08/2026;Talvez;Venda;100,00")
        assertTrue(!linhas[0].valida)
        assertNotNull(linhas[0].erro)
    }

    @Test
    fun `aceita valor com R$ na frente e ponto de milhar`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda grande;R$ 1.234,56")
        assertTrue(linhas[0].valida)
        assertEquals(1234.56, linhas[0].valor!!, 0.001)
    }

    @Test
    fun `aceita valor com ponto decimal (formato americano) quando nao tem virgula`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda;99.90")
        assertTrue(linhas[0].valida)
        assertEquals(99.90, linhas[0].valor!!, 0.001)
    }

    @Test
    fun `valor zero ou negativo eh invalido`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda;0,00\n12/08/2026;Recebido;Venda;-10,00")
        assertTrue(!linhas[0].valida)
        assertTrue(!linhas[1].valida)
    }

    @Test
    fun `valor nao numerico eh invalido, nao quebra o parser`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda;abc")
        assertTrue(!linhas[0].valida)
        assertEquals("Valor inválido", linhas[0].erro)
    }

    @Test
    fun `data com hora eh aceita`() {
        val linhas = parsearCsvFluxo("12/08/2026 14:30;Recebido;Venda;100,00")
        assertTrue(linhas[0].valida)
        assertNotNull(linhas[0].data)
    }

    @Test
    fun `data em formato invalido gera erro`() {
        val linhas = parsearCsvFluxo("2026-08-12;Recebido;Venda;100,00")
        assertTrue(!linhas[0].valida)
        assertEquals("Data inválida (use dd/MM/aaaa)", linhas[0].erro)
    }

    @Test
    fun `data invalida no calendario (31 de fevereiro) eh rejeitada`() {
        val linhas = parsearCsvFluxo("31/02/2026;Recebido;Venda;100,00")
        assertTrue(!linhas[0].valida)
    }

    @Test
    fun `descricao em branco eh invalida`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;;100,00")
        assertTrue(!linhas[0].valida)
        assertEquals("Descrição em branco", linhas[0].erro)
    }

    @Test
    fun `descricao com ponto e virgula entre aspas nao quebra as colunas`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;\"Venda; balcão\";100,00")
        assertTrue(linhas[0].valida)
        assertEquals("Venda; balcão", linhas[0].descricao)
    }

    @Test
    fun `linha com poucas colunas eh invalida mas nao derruba as outras`() {
        val csv = "12/08/2026;Recebido;Venda ok;100,00\n12/08/2026;Recebido\n13/08/2026;Pago;Outra venda;50,00"
        val linhas = parsearCsvFluxo(csv)
        assertEquals(3, linhas.size)
        assertTrue(linhas[0].valida)
        assertTrue(!linhas[1].valida)
        assertTrue(linhas[2].valida) // continua processando depois da linha ruim
    }

    @Test
    fun `numeracao das linhas ignora o cabecalho mas conta as em branco puladas corretamente`() {
        val csv = "Data;Tipo;Descrição;Valor\n12/08/2026;Recebido;A;10,00\n13/08/2026;Pago;B;20,00"
        val linhas = parsearCsvFluxo(csv)
        assertEquals(1, linhas[0].numeroLinha)
        assertEquals(2, linhas[1].numeroLinha)
    }

    // =====================================================================
    // Bateria mais profunda
    // =====================================================================

    @Test
    fun `colunas extras alem da quarta sao ignoradas, nao quebram o parse`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda;100,00;observação extra;outra coisa")
        assertTrue(linhas[0].valida)
        assertEquals(100.0, linhas[0].valor!!, 0.001)
    }

    @Test
    fun `campos com espacos em volta sao aparados (trim)`() {
        val linhas = parsearCsvFluxo("  12/08/2026  ;  Recebido  ;  Venda balcão  ;  100,00  ")
        assertTrue(linhas[0].valida)
        assertEquals("Venda balcão", linhas[0].descricao)
        assertEquals(100.0, linhas[0].valor!!, 0.001)
    }

    @Test
    fun `linha so com espacos em branco eh tratada como linha vazia (ignorada)`() {
        val csv = "12/08/2026;Recebido;A;10,00\n     \n13/08/2026;Pago;B;20,00"
        val linhas = parsearCsvFluxo(csv)
        assertEquals(2, linhas.size)
    }

    @Test
    fun `descricao so com espacos conta como em branco`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;   ;100,00")
        assertTrue(!linhas[0].valida)
        assertEquals("Descrição em branco", linhas[0].erro)
    }

    @Test
    fun `valor com varios pontos e virgula misturados (malformado) fica invalido, nao quebra o parser`() {
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;Venda;1,2,3,45")
        assertTrue(!linhas[0].valida)
    }

    @Test
    fun `linhas com terminador CRLF (padrao Windows) sao lidas igual`() {
        val csv = "12/08/2026;Recebido;Venda A;100,00\r\n13/08/2026;Pago;Venda B;50,00\r\n"
        val linhas = parsearCsvFluxo(csv)
        assertEquals(2, linhas.size)
        assertEquals("Venda A", linhas[0].descricao)
        assertEquals("Venda B", linhas[1].descricao)
    }

    @Test
    fun `arquivo grande (500 linhas) eh todo processado sem perder nenhuma`() {
        val linhas500 = (1..500).joinToString("\n") { i ->
            "%02d/08/2026;Recebido;Item $i;${i},00".format((i % 28) + 1)
        }
        val resultado = parsearCsvFluxo(linhas500)
        assertEquals(500, resultado.size)
        assertTrue(resultado.all { it.valida })
    }

    @Test
    fun `cada linha invalida carrega o proprio motivo do erro, nao um erro generico`() {
        val csv = listOf(
            "31/02/2026;Recebido;Data ruim;10,00",       // data inválida
            "12/08/2026;Sei_la;Tipo ruim;10,00",          // tipo inválido
            "12/08/2026;Recebido;;10,00",                 // descrição em branco
            "12/08/2026;Recebido;Valor ruim;abc"          // valor inválido
        ).joinToString("\n")
        val linhas = parsearCsvFluxo(csv)
        assertTrue(linhas[0].erro!!.contains("Data"))
        assertTrue(linhas[1].erro!!.contains("Tipo"))
        assertTrue(linhas[2].erro!!.contains("Descrição"))
        assertTrue(linhas[3].erro!!.contains("Valor"))
    }

    @Test
    fun `uma linha invalida no meio nao contamina a numeracao nem os dados das linhas validas ao redor`() {
        val csv = listOf(
            "12/08/2026;Recebido;Primeira;10,00",
            "data-invalida;Recebido;Segunda;20,00",
            "13/08/2026;Pago;Terceira;30,00"
        ).joinToString("\n")
        val linhas = parsearCsvFluxo(csv)
        assertEquals(3, linhas.size)
        assertEquals("Primeira", linhas[0].descricao)
        assertTrue(linhas[0].valida)
        assertEquals("Segunda", linhas[1].descricao)
        assertTrue(!linhas[1].valida)
        assertEquals("Terceira", linhas[2].descricao)
        assertTrue(linhas[2].valida)
        assertEquals(30.0, linhas[2].valor!!, 0.001)
    }

    @Test
    fun `linha totalmente vazia entre ponto e virgula (todos os campos em branco) eh invalida`() {
        val linhas = parsearCsvFluxo(";;;")
        assertTrue(!linhas[0].valida)
    }

    @Test
    fun `aspas duplas escapadas dentro do campo (padrao CSV) sao decodificadas`() {
        // campo CSV escrito como "Cliente ""VIP"" especial" deve virar: Cliente "VIP" especial
        val linhas = parsearCsvFluxo("12/08/2026;Recebido;\"Cliente \"\"VIP\"\" especial\";100,00")
        assertTrue(linhas[0].valida)
        assertEquals("Cliente \"VIP\" especial", linhas[0].descricao)
    }

    @Test
    fun `soma dos valores importados bate com a soma esperada (sanity check financeiro)`() {
        val csv = listOf(
            "12/08/2026;Recebido;A;100,50",
            "12/08/2026;Pago;B;30,25",
            "13/08/2026;Recebido;C;20,00"
        ).joinToString("\n")
        val linhas = parsearCsvFluxo(csv)
        val somaRecebido = linhas.filter { it.tipo == TipoMovimentacao.RECEBER }.sumOf { it.valor!! }
        val somaPago = linhas.filter { it.tipo == TipoMovimentacao.PAGAR }.sumOf { it.valor!! }
        assertEquals(120.50, somaRecebido, 0.001)
        assertEquals(30.25, somaPago, 0.001)
    }
}

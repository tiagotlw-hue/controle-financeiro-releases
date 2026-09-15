package com.controlefinanceiro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes do parser aritmético da mini calculadora flutuante (avaliarExpressao) e das
 * funções de formatação de número que ficam ao lado dela. É lógica escrita à mão (sem
 * usar nenhuma lib de parsing), então merece a mesma atenção que o resto da lógica
 * financeira — é fácil errar precedência de operador ou um caso de borda aqui.
 */
class FormatacaoUtilsTest {

    // ---------- avaliarExpressao: operações básicas ----------

    @Test
    fun `soma simples`() {
        assertEquals(8.0, avaliarExpressao("5+3")!!, 0.0001)
    }

    @Test
    fun `subtracao simples`() {
        assertEquals(2.0, avaliarExpressao("5-3")!!, 0.0001)
    }

    @Test
    fun `multiplicacao simples`() {
        assertEquals(15.0, avaliarExpressao("5*3")!!, 0.0001)
    }

    @Test
    fun `divisao simples`() {
        assertEquals(2.5, avaliarExpressao("5/2")!!, 0.0001)
    }

    // ---------- precedência de operadores ----------

    @Test
    fun `multiplicacao tem precedencia sobre soma`() {
        // 2+3*4 = 2+12 = 14 (não 20, que seria se somasse antes de multiplicar)
        assertEquals(14.0, avaliarExpressao("2+3*4")!!, 0.0001)
    }

    @Test
    fun `divisao tem precedencia sobre subtracao`() {
        // 20-8/2 = 20-4 = 16 (não 6)
        assertEquals(16.0, avaliarExpressao("20-8/2")!!, 0.0001)
    }

    @Test
    fun `mistura de todos os operadores respeita precedencia`() {
        // 10+2*3-4/2 = 10+6-2 = 14
        assertEquals(14.0, avaliarExpressao("10+2*3-4/2")!!, 0.0001)
    }

    @Test
    fun `varias multiplicacoes seguidas resolvem da esquerda pra direita`() {
        // 2*3*4 = 24
        assertEquals(24.0, avaliarExpressao("2*3*4")!!, 0.0001)
    }

    @Test
    fun `divisao encadeada resolve da esquerda pra direita, nao da direita pra esquerda`() {
        // 100/10/2 = (100/10)/2 = 5, NÃO 100/(10/2) = 20
        assertEquals(5.0, avaliarExpressao("100/10/2")!!, 0.0001)
    }

    @Test
    fun `subtracao encadeada resolve da esquerda pra direita`() {
        // 10-2-3 = (10-2)-3 = 5, NÃO 10-(2-3) = 11
        assertEquals(5.0, avaliarExpressao("10-2-3")!!, 0.0001)
    }

    // ---------- número negativo no início ----------

    @Test
    fun `numero negativo no comeco eh aceito`() {
        assertEquals(-2.0, avaliarExpressao("-5+3")!!, 0.0001)
    }

    @Test
    fun `numero negativo no comeco combinado com multiplicacao`() {
        // -5*3 = -15
        assertEquals(-15.0, avaliarExpressao("-5*3")!!, 0.0001)
    }

    // ---------- decimais e vírgula brasileira ----------

    @Test
    fun `aceita virgula como separador decimal (padrao brasileiro)`() {
        assertEquals(12.5, avaliarExpressao("10,5+2")!!, 0.0001)
    }

    @Test
    fun `aceita ponto como separador decimal tambem`() {
        assertEquals(12.5, avaliarExpressao("10.5+2")!!, 0.0001)
    }

    @Test
    fun `espacos na expressao sao ignorados`() {
        assertEquals(8.0, avaliarExpressao(" 5 + 3 ")!!, 0.0001)
    }

    // ---------- divisão por zero ----------

    @Test
    fun `divisao por zero devolve null em vez de infinito ou crash`() {
        assertNull(avaliarExpressao("5/0"))
    }

    @Test
    fun `divisao por zero no meio de uma expressao maior tambem devolve null`() {
        assertNull(avaliarExpressao("10+5/0-3"))
    }

    // ---------- entradas inválidas: tem que devolver null, nunca lançar exceção ----------

    @Test
    fun `expressao vazia devolve null`() {
        assertNull(avaliarExpressao(""))
    }

    @Test
    fun `so espacos devolve null`() {
        assertNull(avaliarExpressao("   "))
    }

    @Test
    fun `letras na expressao devolvem null`() {
        assertNull(avaliarExpressao("5+abc"))
    }

    @Test
    fun `operador no final sem numero depois devolve null`() {
        assertNull(avaliarExpressao("5+"))
    }

    @Test
    fun `dois operadores seguidos (5+-3) devolve null`() {
        // essa calculadora não suporta "número negativo depois de um operador" —
        // só no começo da expressão. É uma limitação conhecida, não bug.
        assertNull(avaliarExpressao("5+-3"))
    }

    @Test
    fun `parenteses nao sao suportados e devolvem null`() {
        assertNull(avaliarExpressao("(5+3)*2"))
    }

    @Test
    fun `virgula ou ponto sozinho sem digitos devolve null`() {
        assertNull(avaliarExpressao("5+."))
    }

    @Test
    fun `numero com dois pontos decimais devolve null`() {
        assertNull(avaliarExpressao("5.5.5+3"))
    }

    // ---------- números "estranhos" mas válidos ----------

    @Test
    fun `zero sozinho eh uma expressao valida`() {
        assertEquals(0.0, avaliarExpressao("0")!!, 0.0001)
    }

    @Test
    fun `um unico numero sem operador eh uma expressao valida`() {
        assertEquals(42.0, avaliarExpressao("42")!!, 0.0001)
    }

    @Test
    fun `zeros a esquerda nao atrapalham`() {
        assertEquals(7.0, avaliarExpressao("007")!!, 0.0001)
    }

    // ---------- precisão de ponto flutuante em valores "de dinheiro" ----------

    @Test
    fun `soma de decimais tipicos de preco fecha certo, sem ruido de ponto flutuante`() {
        // 0.1+0.2 classicamente dá 0.30000000000000004 em ponto flutuante — o
        // resultado bruto pode ter ruído, mas formatarResultadoCalculadora precisa
        // arredondar isso pra "0,30" igual, então testamos os dois juntos aqui.
        val resultado = avaliarExpressao("0,1+0,2")!!
        assertEquals("0,30", formatarResultadoCalculadora(resultado))
    }

    @Test
    fun `multiplicacao de preco tipico (19,99 vezes 3) fecha certo`() {
        val resultado = avaliarExpressao("19,99*3")!!
        assertEquals("59,97", formatarResultadoCalculadora(resultado))
    }

    @Test
    fun `conta longa tipica de venda com quantidade e desconto`() {
        // 3 unidades de 15,50 mais 2 de 8,90: 3*15,50+2*8,90 = 46,50+17,80 = 64,30
        val resultado = avaliarExpressao("3*15,50+2*8,90")!!
        assertEquals("64,30", formatarResultadoCalculadora(resultado))
    }

    // ---------- tokenizarExpressao ----------

    @Test
    fun `tokenizar separa numeros e operadores corretamente`() {
        assertEquals(listOf("120", "+", "35", "*", "2"), tokenizarExpressao("120+35*2"))
    }

    @Test
    fun `tokenizar nao separa o sinal de menos inicial`() {
        assertEquals(listOf("-5", "+", "3"), tokenizarExpressao("-5+3"))
    }

    @Test
    fun `tokenizar preserva pontos decimais dentro dos numeros`() {
        assertEquals(listOf("10.5", "+", "2.3"), tokenizarExpressao("10.5+2.3"))
    }

    // ---------- formatarResultadoCalculadora ----------

    @Test
    fun `resultado inteiro nao mostra casas decimais`() {
        assertEquals("10", formatarResultadoCalculadora(10.0))
    }

    @Test
    fun `resultado com decimais mostra ate duas casas`() {
        assertEquals("10,33", formatarResultadoCalculadora(10.333333))
    }

    @Test
    fun `resultado negativo formata igual, com o sinal`() {
        assertEquals("-5", formatarResultadoCalculadora(-5.0))
    }

    // ---------- formatarQuantidadeLista ----------

    @Test
    fun `quantidade inteira nao mostra casas decimais`() {
        assertEquals("2", formatarQuantidadeLista(2.0))
    }

    @Test
    fun `quantidade quebrada mostra so as casas necessarias, com virgula`() {
        assertEquals("1,5", formatarQuantidadeLista(1.5))
    }

    @Test
    fun `quantidade com duas casas decimais nao perde a segunda casa`() {
        assertEquals("1,25", formatarQuantidadeLista(1.25))
    }

    @Test
    fun `quantidade com zero a direita eh cortado (1,20 vira 1,2)`() {
        assertEquals("1,2", formatarQuantidadeLista(1.20))
    }

    @Test
    fun `quantidade zero formata como 0, nao como string vazia`() {
        assertEquals("0", formatarQuantidadeLista(0.0))
    }

    // ---------- formatarMoeda ----------

    @Test
    fun `formatarMoeda usa virgula decimal e duas casas`() {
        assertEquals("R$ 540,00", formatarMoeda(540.0))
        assertEquals("R$ 85,50", formatarMoeda(85.5))
    }

    @Test
    fun `formatarMoeda arredonda pra duas casas`() {
        assertEquals("R$ 10,33", formatarMoeda(10.333333))
    }

    @Test
    fun `formatarMoeda funciona com valor negativo`() {
        assertEquals("R$ -50,00", formatarMoeda(-50.0))
    }

    @Test
    fun `formatarMoeda ignora o idioma padrao do aparelho (mesmo teste de regressao do gerarCsvCliente)`() {
        val localeOriginal = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.US)
            assertEquals("R$ 1234,50", formatarMoeda(1234.5))
        } finally {
            java.util.Locale.setDefault(localeOriginal)
        }
    }

    // ---------- formatarData / formatarDataHora ----------

    @Test
    fun `formatarData usa o formato brasileiro dd-MM-aaaa`() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.AUGUST, 12, 9, 5, 0)
        assertEquals("12/08/2026", formatarData(cal.timeInMillis))
    }

    @Test
    fun `formatarDataHora inclui hora e minuto`() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.AUGUST, 12, 9, 5, 0)
        assertEquals("12/08/2026 09:05", formatarDataHora(cal.timeInMillis))
    }

    @Test
    fun `formatarData preenche dia e mes com zero a esquerda quando necessario`() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.JANUARY, 5, 0, 0, 0)
        assertEquals("05/01/2026", formatarData(cal.timeInMillis))
    }

    // =====================================================================
    // parsearValorMonetario: usado em toda tela que aceita valor digitado
    // (Entrada, Saída, Lista) — bug real encontrado, ver comentário na função.
    // =====================================================================

    @Test
    fun `valor digitado no formato brasileiro completo (milhar e decimal) eh interpretado certo`() {
        // Este é o teste de regressão do bug: antes da extração dessa função, cada tela
        // fazia só texto.replace(",", ".") — "1.500,00" virava "1.500.00" (dois pontos
        // decimais) e toDoubleOrNull() rejeitava como inválido. A pessoa digitava um
        // valor perfeitamente normal (mil e quinhentos reais) e a tela recusava.
        assertEquals(1500.0, parsearValorMonetario("1.500,00")!!, 0.001)
    }

    @Test
    fun `valor digitado so com virgula decimal, sem milhar, continua funcionando`() {
        assertEquals(1500.0, parsearValorMonetario("1500,00")!!, 0.001)
    }

    @Test
    fun `valor digitado com ponto de milhar sem decimais eh interpretado certo`() {
        assertEquals(1500.0, parsearValorMonetario("1.500")!!, 0.001)
    }

    @Test
    fun `valor digitado sem nenhum separador continua funcionando`() {
        assertEquals(150.0, parsearValorMonetario("150")!!, 0.001)
    }

    @Test
    fun `texto vazio ou em branco devolve null, nao zero`() {
        assertNull(parsearValorMonetario(""))
        assertNull(parsearValorMonetario("   "))
    }

    @Test
    fun `texto nao numerico devolve null`() {
        assertNull(parsearValorMonetario("abc"))
    }

    @Test
    fun `aceita o prefixo R$ na frente do valor digitado`() {
        assertEquals(1500.0, parsearValorMonetario("R$ 1.500,00")!!, 0.001)
    }

    // ---------- robustez com digitação em andamento (texto incompleto) ----------
    // Importante porque EntradaScreen/SaidaScreen recalculam o valor a CADA tecla
    // digitada — não pode travar nem lançar exceção enquanto a pessoa ainda está no
    // meio de digitar um número (antes de terminar de escrever a parte decimal, etc).

    @Test
    fun `virgula ou ponto no comeco (sem digito antes) nao lanca excecao`() {
        assertEquals(0.5, parsearValorMonetario(",50")!!, 0.001)
        assertEquals(0.5, parsearValorMonetario(".50")!!, 0.001)
    }

    @Test
    fun `virgula ou ponto no final (sem digito depois, digitacao em andamento) nao lanca excecao`() {
        assertEquals(50.0, parsearValorMonetario("50,")!!, 0.001)
        assertEquals(50.0, parsearValorMonetario("50.")!!, 0.001)
    }

    @Test
    fun `so um sinal de menos ou uma virgula sozinha devolve null, nao lanca excecao`() {
        assertNull(parsearValorMonetario("-"))
        assertNull(parsearValorMonetario(","))
    }

    @Test
    fun `zeros a esquerda antes da virgula nao atrapalham`() {
        assertEquals(0.5, parsearValorMonetario("00,50")!!, 0.001)
    }

    @Test
    fun `texto patologico com milhares de grupos separados por ponto nao trava nem demora`() {
        // Não deveria acontecer na prática (ninguém digita isso), mas garante que a
        // regex de detecção de milhar não tem um comportamento exponencial (ReDoS)
        // se um texto malformado gigante chegar até aqui por algum outro caminho.
        val textoGigante = (1..1000).joinToString(".") { "123" }
        val inicio = System.currentTimeMillis()
        parsearValorMonetario(textoGigante) // não pode travar nem lançar exceção
        val duracao = System.currentTimeMillis() - inicio
        assertTrue("levou tempo demais: ${duracao}ms", duracao < 2000)
    }

    // =====================================================================
    // paraMillisUtcDoDia / aplicarDataUtcNoFusoLocal: conversão de fuso do
    // DatePicker do Compose (que sempre trabalha em UTC). Bug clássico e comum
    // em apps Android: sem essa conversão, escolher uma data num fuso "atrás"
    // de UTC (como o do Brasil) faz a data voltar um dia — principalmente
    // perto da meia-noite. Testado forçando o fuso horário do teste pra não
    // depender de em qual fuso a máquina que roda os testes está.
    // =====================================================================

    private fun comFusoDoBrasil(bloco: () -> Unit) {
        val fusoOriginal = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Sao_Paulo"))
            bloco()
        } finally {
            java.util.TimeZone.setDefault(fusoOriginal) // não pode vazar pros outros testes
        }
    }

    @Test
    fun `ida e volta preserva o dia mesmo as 23h30, o caso mais propenso a virar o dia errado`() = comFusoDoBrasil {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.AUGUST, 12, 23, 30, 0)
        val original = cal.timeInMillis

        val millisUtc = paraMillisUtcDoDia(original)
        val millisFinal = aplicarDataUtcNoFusoLocal(millisUtc, original)

        val resultado = java.util.Calendar.getInstance().apply { timeInMillis = millisFinal }
        assertEquals(12, resultado.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(java.util.Calendar.AUGUST, resultado.get(java.util.Calendar.MONTH))
        assertEquals(2026, resultado.get(java.util.Calendar.YEAR))
    }

    @Test
    fun `ida e volta preserva o dia logo depois da meia-noite tambem`() = comFusoDoBrasil {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.AUGUST, 12, 0, 30, 0)
        val original = cal.timeInMillis

        val millisFinal = aplicarDataUtcNoFusoLocal(paraMillisUtcDoDia(original), original)

        val resultado = java.util.Calendar.getInstance().apply { timeInMillis = millisFinal }
        assertEquals(12, resultado.get(java.util.Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `ida e volta preserva a virada de ano (31 de dezembro para 1 de janeiro)`() = comFusoDoBrasil {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.DECEMBER, 31, 23, 0, 0)
        val original = cal.timeInMillis

        val millisFinal = aplicarDataUtcNoFusoLocal(paraMillisUtcDoDia(original), original)

        val resultado = java.util.Calendar.getInstance().apply { timeInMillis = millisFinal }
        assertEquals(31, resultado.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(java.util.Calendar.DECEMBER, resultado.get(java.util.Calendar.MONTH))
        assertEquals(2026, resultado.get(java.util.Calendar.YEAR))
    }

    @Test
    fun `aplicarDataUtcNoFusoLocal preserva a hora e o minuto originais, so troca a data`() = comFusoDoBrasil {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.AUGUST, 12, 14, 45, 0)
        val original = cal.timeInMillis

        val calNovaData = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calNovaData.clear()
        calNovaData.set(2026, java.util.Calendar.AUGUST, 20)

        val millisFinal = aplicarDataUtcNoFusoLocal(calNovaData.timeInMillis, original)
        val resultado = java.util.Calendar.getInstance().apply { timeInMillis = millisFinal }

        assertEquals(20, resultado.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(14, resultado.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(45, resultado.get(java.util.Calendar.MINUTE))
    }

    @Test
    fun `ida e volta tambem funciona num fuso horario a frente de UTC`() {
        val fusoOriginal = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Auckland"))
            val cal = java.util.Calendar.getInstance()
            cal.set(2026, java.util.Calendar.AUGUST, 12, 1, 0, 0)
            val original = cal.timeInMillis

            val millisFinal = aplicarDataUtcNoFusoLocal(paraMillisUtcDoDia(original), original)
            val resultado = java.util.Calendar.getInstance().apply { timeInMillis = millisFinal }
            assertEquals(12, resultado.get(java.util.Calendar.DAY_OF_MONTH))
        } finally {
            java.util.TimeZone.setDefault(fusoOriginal)
        }
    }
}

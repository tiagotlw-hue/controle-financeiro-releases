package com.controlefinanceiro.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LOCALE_BR = Locale("pt", "BR")

/** Formata um valor em reais, sempre com vírgula decimal (independente do idioma
 *  configurado no aparelho — ver o teste de regressão em ExportarDadosTest). Um
 *  lugar só pra essa regra, em vez de "R$ %.2f".format(Locale("pt","BR"), x)
 *  repetido em dezenas de telas — reduz a chance de alguém esquecer o Locale numa
 *  ocorrência nova e reintroduzir aquele bug. */
fun formatarMoeda(valor: Double): String = "R$ %.2f".format(LOCALE_BR, valor)

/** Formata uma data (dd/MM/aaaa), sempre em português — mesmo motivo do
 *  formatarMoeda: um lugar só, em vez de cada tela declarar seu próprio
 *  `SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))`. */
fun formatarData(dataMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", LOCALE_BR).format(Date(dataMillis))

/** Igual formatarData, mas com hora e minuto também (dd/MM/aaaa HH:mm). */
fun formatarDataHora(dataMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", LOCALE_BR).format(Date(dataMillis))

/** O DatePicker do Compose sempre trabalha com os milissegundos em UTC (meia-noite
 *  UTC do dia escolhido) — não no fuso horário do aparelho. Se a gente simplesmente
 *  jogar esse valor num Calendar no fuso local sem converter, em fusos "atrás" de UTC
 *  (como o do Brasil) a data acaba voltando um dia. Essas duas funções cuidam da
 *  conversão nos dois sentidos, pra a data escolhida ser sempre exatamente a que a
 *  pessoa tocou no calendário, independente do fuso do aparelho. */
fun paraMillisUtcDoDia(dataHoraMillisLocal: Long): Long {
    val calLocal = java.util.Calendar.getInstance().apply { timeInMillis = dataHoraMillisLocal }
    val calUtc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(calLocal.get(java.util.Calendar.YEAR), calLocal.get(java.util.Calendar.MONTH), calLocal.get(java.util.Calendar.DAY_OF_MONTH))
    }
    return calUtc.timeInMillis
}

fun aplicarDataUtcNoFusoLocal(dataMillisUtc: Long, dataHoraMillisAtual: Long): Long {
    val calUtc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = dataMillisUtc }
    val calLocal = java.util.Calendar.getInstance().apply { timeInMillis = dataHoraMillisAtual }
    calLocal.set(java.util.Calendar.YEAR, calUtc.get(java.util.Calendar.YEAR))
    calLocal.set(java.util.Calendar.MONTH, calUtc.get(java.util.Calendar.MONTH))
    calLocal.set(java.util.Calendar.DAY_OF_MONTH, calUtc.get(java.util.Calendar.DAY_OF_MONTH))
    return calLocal.timeInMillis
}

/**
 * Interpreta um valor em reais digitado pela pessoa num campo de texto (ou vindo de
 * um CSV importado), aceitando tanto o formato brasileiro completo ("1.500,00") quanto
 * só a vírgula decimal sem milhar ("1500,00"), o formato americano ("1500.00"), e um
 * valor "puro" agrupado por milhar sem decimais ("1.500"). Devolve null se o texto não
 * for um número válido em nenhum desses formatos.
 *
 * Um lugar só pra essa regra — antes, cada tela (Entrada, Saída, Lista) tinha sua
 * própria versão simplificada (`texto.replace(",", ".").toDoubleOrNull()`), que
 * quebrava justamente no formato mais natural de digitar um valor acima de mil reais:
 * "1.500,00" virava "1.500.00" depois do replace (dois pontos decimais), e
 * toDoubleOrNull() rejeitava como inválido — a pessoa digitava um valor perfeitamente
 * normal e a tela recusava sem dizer por quê.
 */
fun parsearValorMonetario(texto: String): Double? {
    if (texto.isBlank()) return null
    val limpo = texto.replace("R$", "", ignoreCase = true).trim()
    val resultado = when {
        limpo.contains(",") -> {
            // vírgula decimal (padrão BR): qualquer ponto antes dela é separador de
            // milhar, remove os pontos e troca a vírgula por ponto decimal.
            limpo.replace(".", "").replace(",", ".")
        }
        // Sem vírgula, mas o texto inteiro é dígitos agrupados de 3 em 3 separados por
        // ponto (ex: "1.500" ou "1.234.567") — isso é separador de milhar, não decimal
        // (um valor decimal de verdade não tem exatamente 3 casas depois do ponto).
        Regex("^-?\\d{1,3}(\\.\\d{3})+$").matches(limpo) -> limpo.replace(".", "")
        else -> limpo
    }
    return resultado.toDoubleOrNull()
}

/**
 * Avalia uma expressão aritmética simples digitada pela pessoa na mini calculadora
 * flutuante (ex: "120+35*2"). Suporta só +, -, *, / (sem parênteses), respeitando a
 * precedência normal (multiplicação/divisão antes de soma/subtração). Devolve null
 * pra qualquer coisa que não seja uma expressão válida, em vez de lançar exceção —
 * a tela mostra "erro" nesse caso e não trava.
 */
fun avaliarExpressao(expressaoOriginal: String): Double? {
    val expr = expressaoOriginal.replace(" ", "").replace(",", ".")
    if (expr.isEmpty()) return null
    // Só números (com ponto decimal opcional) separados por um único operador +-*/,
    // com sinal de menos opcional só no começo (número negativo inicial).
    if (!expr.matches(Regex("^-?\\d+(\\.\\d+)?([+\\-*/]\\d+(\\.\\d+)?)*$"))) return null

    return try {
        val tokens = tokenizarExpressao(expr)

        // 1ª passada: resolve * e / da esquerda para a direita.
        val semMulDiv = mutableListOf(tokens[0])
        var i = 1
        while (i < tokens.size) {
            val op = tokens[i]
            val num = tokens[i + 1].toDouble()
            if (op == "*" || op == "/") {
                val anterior = semMulDiv.removeAt(semMulDiv.size - 1).toDouble()
                if (op == "/" && num == 0.0) return null
                val resultado = if (op == "*") anterior * num else anterior / num
                semMulDiv.add(resultado.toString())
            } else {
                semMulDiv.add(op)
                semMulDiv.add(tokens[i + 1])
            }
            i += 2
        }

        // 2ª passada: soma e subtração, da esquerda para a direita.
        var total = semMulDiv[0].toDouble()
        var j = 1
        while (j < semMulDiv.size) {
            val op = semMulDiv[j]
            val num = semMulDiv[j + 1].toDouble()
            total = if (op == "+") total + num else total - num
            j += 2
        }
        total
    } catch (e: Exception) {
        null
    }
}

/** Separa a expressão em números e operadores, tomando cuidado para não confundir
 *  um "-" de número negativo no início com um operador de subtração. */
fun tokenizarExpressao(expr: String): List<String> {
    val tokens = mutableListOf<String>()
    var atual = StringBuilder()
    expr.forEachIndexed { indice, c ->
        if (c in "+-*/" && !(c == '-' && indice == 0)) {
            tokens.add(atual.toString())
            tokens.add(c.toString())
            atual = StringBuilder()
        } else {
            atual.append(c)
        }
    }
    tokens.add(atual.toString())
    return tokens
}

/** Formata o resultado da calculadora sem casas decimais desnecessárias
 *  (ex: 10.0 -> "10"), senão com até 2 casas decimais (ex: 10.333... -> "10,33"). */
fun formatarResultadoCalculadora(valor: Double): String {
    return if (valor == valor.toLong().toDouble()) {
        valor.toLong().toString()
    } else {
        "%.2f".format(Locale("pt", "BR"), valor)
    }
}

/** Formata uma quantidade (na lista de compras) sem casas decimais desnecessárias:
 *  2.0 -> "2", 1.5 -> "1,5". */
fun formatarQuantidadeLista(quantidade: Double): String {
    return if (quantidade == quantidade.toLong().toDouble()) {
        quantidade.toLong().toString()
    } else {
        String.format(Locale.US, "%.2f", quantidade).trimEnd('0').trimEnd('.').replace(".", ",")
    }
}

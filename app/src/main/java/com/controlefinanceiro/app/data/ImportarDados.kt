package com.controlefinanceiro.app.data

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Formato esperado do CSV de importação de fluxo de caixa (separado por ; igual o
 * CSV exportado, pra dar pra editar num Excel/Planilhas e trazer de volta):
 *
 *   Data;Tipo;Descrição;Valor
 *   12/08/2026;Recebido;Venda balcão;540,00
 *   12/08/2026 08:15;Pago;Compra de material;200,00
 *
 * - Data aceita dd/MM/yyyy ou dd/MM/yyyy HH:mm (sem hora, assume meio-dia).
 * - Tipo aceita Recebido/Pago (ou R/P, Entrada/Saída), sem diferenciar maiúsculas.
 * - Valor aceita R$, ponto de milhar e vírgula decimal (540,00) ou só ponto (540.00).
 * - Primeira linha é ignorada automaticamente se for um cabeçalho (começa com "Data").
 */
data class LinhaCsvFluxo(
    val numeroLinha: Int,
    val dataTexto: String,
    val tipoTexto: String,
    val descricao: String,
    val valorTexto: String,
    val data: Long?,
    val tipo: String?,
    val valor: Double?,
    val erro: String?
) {
    val valida: Boolean get() = erro == null
}

fun parsearCsvFluxo(conteudo: String): List<LinhaCsvFluxo> {
    val linhasBrutas = conteudo
        .replace("\uFEFF", "") // remove o BOM, se veio de um CSV exportado por Excel
        .split("\n")
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() }

    val resultado = mutableListOf<LinhaCsvFluxo>()
    var numero = 0
    linhasBrutas.forEachIndexed { indice, linhaTexto ->
        val campos = dividirLinhaCsv(linhaTexto)
        if (indice == 0 && campos.getOrNull(0)?.trim()?.equals("data", ignoreCase = true) == true) {
            return@forEachIndexed // é o cabeçalho, pula
        }
        numero++
        val dataTexto = campos.getOrNull(0)?.trim().orEmpty()
        val tipoTexto = campos.getOrNull(1)?.trim().orEmpty()
        val descricao = campos.getOrNull(2)?.trim().orEmpty()
        val valorTexto = campos.getOrNull(3)?.trim().orEmpty()

        val data = parsearDataCsv(dataTexto)
        val tipo = parsearTipoCsv(tipoTexto)
        val valor = parsearValorCsv(valorTexto)

        val erro = when {
            campos.size < 4 -> "Faltam colunas (esperado: Data;Tipo;Descrição;Valor)"
            dataTexto.isBlank() -> "Data em branco"
            data == null -> "Data inválida (use dd/MM/aaaa)"
            tipoTexto.isBlank() -> "Tipo em branco"
            tipo == null -> "Tipo deve ser Recebido ou Pago"
            descricao.isBlank() -> "Descrição em branco"
            valor == null -> "Valor inválido"
            valor <= 0 -> "Valor deve ser maior que zero"
            else -> null
        }
        resultado.add(LinhaCsvFluxo(numero, dataTexto, tipoTexto, descricao, valorTexto, data, tipo, valor, erro))
    }
    return resultado
}

private fun dividirLinhaCsv(linha: String): List<String> {
    val campos = mutableListOf<String>()
    val atual = StringBuilder()
    var dentroAspas = false
    var i = 0
    while (i < linha.length) {
        val c = linha[i]
        when {
            c == '"' -> {
                if (dentroAspas && i + 1 < linha.length && linha[i + 1] == '"') {
                    atual.append('"'); i++
                } else {
                    dentroAspas = !dentroAspas
                }
            }
            c == ';' && !dentroAspas -> { campos.add(atual.toString()); atual.clear() }
            else -> atual.append(c)
        }
        i++
    }
    campos.add(atual.toString())
    return campos
}

private fun parsearDataCsv(texto: String): Long? {
    if (texto.isBlank()) return null
    // O java.util.Calendar/SimpleDateFormat, mesmo com isLenient=false, aceita um ano
    // com MENOS de 4 dígitos e interpreta literalmente (ex: "12/08/26" vira o ano 26
    // d.C., não 2026!). Exige o formato exato — dia e mês com 2 dígitos, ano com 4 —
    // antes mesmo de tentar interpretar, pra não deixar passar uma data absurda calada.
    if (!texto.matches(Regex("^\\d{1,2}/\\d{1,2}/\\d{4}( \\d{2}:\\d{2})?$"))) return null
    for (padrao in listOf("dd/MM/yyyy HH:mm", "dd/MM/yyyy")) {
        try {
            val fmt = SimpleDateFormat(padrao, Locale("pt", "BR"))
            fmt.isLenient = false
            return fmt.parse(texto)?.time
        } catch (e: Exception) {
            // tenta o próximo formato
        }
    }
    return null
}

private fun parsearTipoCsv(texto: String): String? {
    val t = texto.trim().lowercase(Locale("pt", "BR"))
    return when {
        t.startsWith("receb") || t == "r" || t.startsWith("entrada") -> TipoMovimentacao.RECEBER
        t.startsWith("pag") || t == "p" || t.startsWith("saida") || t.startsWith("saída") -> TipoMovimentacao.PAGAR
        else -> null
    }
}

private fun parsearValorCsv(texto: String): Double? = parsearValorMonetario(texto)

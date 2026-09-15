package com.controlefinanceiro.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * IMPORTANTE (configuração no projeto, uma vez só): o <provider> do FileProvider que
 * já existe (usado pra instalar a atualização) precisa também liberar a pasta de cache
 * onde o CSV é salvo. No arquivo res/xml/file_paths.xml (apontado pelo
 * android:resource do <provider> no AndroidManifest.xml), garanta que tem uma entrada
 * assim, junto das que já existem:
 *
 *   <cache-path name="exportados" path="exportados/" />
 *
 * Sem isso, o compartilhamento do CSV vai falhar com um erro de permissão do
 * FileProvider (mas o download de atualização continua funcionando normal).
 */

/**
 * Monta um CSV com o extrato completo de um cliente: todas as categorias e, dentro de
 * cada uma, todas as movimentações com valor total, em aberto e status. Abre certinho
 * no Excel/Google Planilhas (usa ; como separador, que é o padrão em pt-BR, e BOM UTF-8
 * pra acentuação não vir corrompida).
 */
fun gerarCsvCliente(
    cliente: Cliente,
    categorias: List<Categoria>,
    movimentacoesPorCategoria: Map<String, List<Movimentacao>>
): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    val sb = StringBuilder()
    sb.append('\uFEFF') // BOM: sem isso o Excel mostra os acentos errados
    sb.append("Cliente;${escaparCsv(cliente.nome)}\n")
    sb.append("Saldo;R$ %.2f\n".format(Locale("pt", "BR"), cliente.saldo))
    sb.append("A receber;R$ %.2f\n".format(Locale("pt", "BR"), cliente.totalAReceber))
    sb.append("A pagar;R$ %.2f\n".format(Locale("pt", "BR"), cliente.totalAPagar))
    sb.append("\n")
    sb.append("Categoria;Tipo;Descrição;Data;Valor total;Valor em aberto;Status;Parcelas\n")

    for (categoria in categorias) {
        val tipoCategoria = when (categoria.tipo) {
            TipoMovimentacao.RECEBER -> "A receber"
            TipoMovimentacao.PAGAR -> "A pagar"
            else -> "Fluxo de caixa"
        }
        val movimentacoes = movimentacoesPorCategoria[categoria.id].orEmpty()
        if (movimentacoes.isEmpty()) {
            sb.append("${escaparCsv(categoria.nome)};$tipoCategoria;;;;;;\n")
            continue
        }
        for (mov in movimentacoes) {
            // Numa categoria de fluxo o tipo é por lançamento (Recebido/Pago), não fixo
            // pela categoria como em A receber/A pagar — cada linha mostra o seu próprio.
            val tipo = if (categoria.tipo == TipoMovimentacao.FLUXO) {
                if (mov.tipo == TipoMovimentacao.RECEBER) "Recebido" else "Pago"
            } else {
                tipoCategoria
            }
            val status = when {
                mov.valorEmAberto <= 0.01 -> "Quitada"
                mov.valorEmAberto < mov.valorTotal -> "Parcial"
                else -> "Em aberto"
            }
            sb.append(
                "${escaparCsv(categoria.nome)};$tipo;${escaparCsv(mov.descricao)};" +
                    "${fmt.format(java.util.Date(mov.dataCriacao))};" +
                    "R$ %.2f;R$ %.2f;$status;${mov.numParcelas}\n".format(Locale("pt", "BR"), mov.valorTotal, mov.valorEmAberto)
            )
        }
    }
    return sb.toString()
}

private fun escaparCsv(texto: String): String {
    // Proteção contra "CSV Injection" (injeção de fórmula): se um campo começa com
    // =, +, -, @ (ou tab/CR), o Excel e outros programas de planilha podem interpretar
    // o conteúdo como uma FÓRMULA ao abrir o arquivo, não como texto — um nome de
    // cliente ou descrição com esse formato (mesmo sem intenção maliciosa, tipo
    // "-50 desconto" ou "@joão") vira um link clicável ou, em versões antigas do
    // Excel, chega a executar comando externo via DDE. Prefixamos com aspas simples
    // pra neutralizar: o Excel esconde essa aspas e trata a célula como texto puro.
    var seguro = texto
    if (seguro.isNotEmpty() && seguro[0] in "=+-@\t\r") {
        seguro = "'$seguro"
    }
    // Se o texto (já neutralizado acima) tiver ; ou " ou quebra de linha, precisa
    // envolver em aspas (regra padrão de CSV), dobrando aspas internas.
    return if (seguro.contains(";") || seguro.contains("\"") || seguro.contains("\n")) {
        "\"${seguro.replace("\"", "\"\"")}\""
    } else {
        seguro
    }
}

/** Grava o CSV num arquivo temporário (pasta de cache do app) pronto pra compartilhar. */
fun salvarCsvTemporario(context: Context, conteudoCsv: String, nomeArquivo: String): File {
    val pasta = File(context.cacheDir, "exportados").apply { mkdirs() }
    val arquivo = File(pasta, nomeArquivo)
    arquivo.writeText(conteudoCsv)
    return arquivo
}

/** Legenda que acompanha o arquivo ao compartilhar — evita a pessoa ter que digitar
 *  uma mensagem toda vez que manda o extrato pra alguém. */
fun gerarLegendaCompartilhamento(cliente: Cliente, dataMillis: Long = System.currentTimeMillis()): String {
    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    return "Extrato de ${cliente.nome} — ${fmt.format(java.util.Date(dataMillis))}"
}

/** Abre o menu "Compartilhar" do Android (WhatsApp, e-mail, Drive, etc) pro arquivo
 *  gerado. Usa o mesmo FileProvider já configurado pro instalador de atualização.
 *  Funciona offline: abrir o app de destino e anexar o arquivo não precisa de rede,
 *  só o envio em si (isso já é o WhatsApp/e-mail/etc cuidando, não depende da gente). */
fun compartilharArquivo(context: Context, arquivo: File, titulo: String = "Compartilhar", legenda: String? = null) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        if (!legenda.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, legenda)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, titulo))
}

/** Atalho pra mandar direto pro WhatsApp, sem passar pela lista geral de apps —
 *  tenta o WhatsApp normal primeiro, depois o WhatsApp Business, e só cai pro menu
 *  "Compartilhar" genérico se nenhum dos dois estiver instalado no aparelho.
 *  Continua funcionando offline pelo mesmo motivo do compartilharArquivo: só abre o
 *  app e anexa o arquivo, quem decide quando manda de verdade é o WhatsApp. */
fun compartilharNoWhatsApp(context: Context, arquivo: File, legenda: String? = null) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
    fun intentPara(pacote: String) = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        if (!legenda.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, legenda)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(pacote)
    }
    for (pacote in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try {
            context.startActivity(intentPara(pacote))
            return
        } catch (e: android.content.ActivityNotFoundException) {
            // não tem esse (tenta o próximo, ou cai no fallback abaixo)
        }
    }
    // Nenhum dos dois instalado — cai pro menu geral de compartilhar em vez de travar.
    compartilharArquivo(context, arquivo, "Compartilhar", legenda)
}

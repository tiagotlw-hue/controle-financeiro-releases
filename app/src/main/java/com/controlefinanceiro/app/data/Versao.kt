package com.controlefinanceiro.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** VersÃƒÆ’Ã‚Â£o atual deste app. Muda esses dois valores a cada nova versÃƒÆ’Ã‚Â£o publicada:
 *  `CODIGO` sempre sobe 1 (ÃƒÆ’Ã‚Â© o que decide se tem atualizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o ou nÃƒÆ’Ã‚Â£o), `NOME` ÃƒÆ’Ã‚Â© sÃƒÆ’Ã‚Â³ o
 *  texto bonito mostrado pra pessoa (ex: "1.3.0"). */
object Versao {
    const val CODIGO = 4
    const val NOME = "0.0.4"
}

/** InformaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o da versÃƒÆ’Ã‚Â£o mais nova disponÃƒÆ’Ã‚Â­vel, lida do arquivo de versÃƒÆ’Ã‚Â£o no GitHub. */
data class InfoAtualizacao(
    val codigo: Int,
    val nome: String,
    val urlDownload: String
)

/**
 * Verifica se tem uma versÃƒÆ’Ã‚Â£o mais nova do app disponÃƒÆ’Ã‚Â­vel, lendo um arquivinho de
 * texto (JSON) hospedado num repositÃƒÆ’Ã‚Â³rio do GitHub.
 *
 * Como configurar:
 * 1. Cria (ou jÃƒÆ’Ã‚Â¡ tem) um repositÃƒÆ’Ã‚Â³rio PÃƒÆ’Ã…Â¡BLICO no GitHub, ex: github.com/SEU_USUARIO/SEU_REPO
 * 2. Sobe/edita nele um arquivo `versao.json` na raiz, com este conteÃƒÆ’Ã‚Âºdo (ajustando os
 *    valores a cada nova versÃƒÆ’Ã‚Â£o publicada):
 *    {"codigo": 2, "nome": "1.1.0", "url": "https://raw.githubusercontent.com/tiagotlw-hue/controle-financeiro-releases/main/app-debug.apk"}
 *    - "codigo": o novo CODIGO (tem que ser maior que o Versao.CODIGO atual do app
 *      pra ele avisar que tem atualizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o).
 *    - "nome": o texto da versÃƒÆ’Ã‚Â£o nova, sÃƒÆ’Ã‚Â³ pra mostrar pra pessoa.
 *    - "url": o link direto de download do APK (de uma GitHub Release, por exemplo)
 *      que abre quando a pessoa toca em "Atualizar".
 * 3. Substitui [URL_ARQUIVO_VERSAO] logo abaixo com a URL "raw" desse arquivo (troca
 *    SEU_USUARIO, SEU_REPO e o branch se nÃƒÆ’Ã‚Â£o for "main").
 */
private const val URL_ARQUIVO_VERSAO =
    "https://raw.githubusercontent.com/tiagotlw-hue/controle-financeiro-releases/main/versao.json"

/**
 * Faz a checagem de verdade (rede), tem que ser chamada fora da thread principal
 * (ex: dentro de um `viewModelScope.launch(Dispatchers.IO)` ou `withContext(Dispatchers.IO)`).
 *
 * IMPORTANTE: se nÃƒÆ’Ã‚Â£o tiver internet (ou a conexÃƒÆ’Ã‚Â£o cair no meio, der timeout, o link
 * estiver errado, o arquivo nÃƒÆ’Ã‚Â£o existir mais, etc.), essa funÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o simplesmente retorna
 * `null` ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â o app nÃƒÆ’Ã‚Â£o trava, nÃƒÆ’Ã‚Â£o mostra nenhum erro pra pessoa, e continua funcionando
 * normalmente, exatamente como se nÃƒÆ’Ã‚Â£o tivesse nenhuma atualizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o disponÃƒÆ’Ã‚Â­vel. A
 * checagem ÃƒÆ’Ã‚Â© sÃƒÆ’Ã‚Â³ "tentativa e melhor esforÃƒÆ’Ã‚Â§o": qualquer coisa que dÃƒÆ’Ã‚Âª errado ÃƒÆ’Ã‚Â©
 * silenciosamente ignorada.
 */
fun verificarNovaVersao(): InfoAtualizacao? {
    if (URL_ARQUIVO_VERSAO.contains("SEU_USUARIO")) return null
    var conexao: HttpURLConnection? = null
    return try {
        // O "?t=" no final muda a cada checagem ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â sem isso, o CDN do GitHub
        // (raw.githubusercontent.com) pode servir uma cÃƒÆ’Ã‚Â³pia em cache antiga do
        // versao.json por vÃƒÆ’Ã‚Â¡rios minutos mesmo depois de vocÃƒÆ’Ã‚Âª jÃƒÆ’Ã‚Â¡ ter editado o
        // arquivo lÃƒÆ’Ã‚Â¡, fazendo o app "nÃƒÆ’Ã‚Â£o enxergar" a atualizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o nova.
        val urlComCacheBuster = "$URL_ARQUIVO_VERSAO?t=${System.currentTimeMillis()}"
        conexao = (URL(urlComCacheBuster).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000 // sem rede, falha rÃƒÆ’Ã‚Â¡pido em vez de segurar o app tentando
            readTimeout = 5000
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
        if (conexao.responseCode != HttpURLConnection.HTTP_OK) return null

        val texto = conexao.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(texto)
        val info = InfoAtualizacao(
            codigo = json.getInt("codigo"),
            nome = json.optString("nome", ""),
            urlDownload = json.optString("url", "")
        )
        if (info.codigo > Versao.CODIGO) info else null
    } catch (e: Exception) {
        // Sem internet, DNS falhou, timeout, JSON invÃƒÆ’Ã‚Â¡lido, o que for ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â ignora e segue
        // o app normalmente, como se nÃƒÆ’Ã‚Â£o tivesse atualizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o nenhuma.
        null
    } finally {
        conexao?.disconnect()
    }
}

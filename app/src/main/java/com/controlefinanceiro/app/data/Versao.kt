package com.controlefinanceiro.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** VersÃƒÂ£o atual deste app. Muda esses dois valores a cada nova versÃƒÂ£o publicada:
 *  `CODIGO` sempre sobe 1 (ÃƒÂ© o que decide se tem atualizaÃƒÂ§ÃƒÂ£o ou nÃƒÂ£o), `NOME` ÃƒÂ© sÃƒÂ³ o
 *  texto bonito mostrado pra pessoa (ex: "1.3.0"). */
object Versao {
    const val CODIGO = 6
    const val NOME = "0.0.6"
}

/** InformaÃƒÂ§ÃƒÂ£o da versÃƒÂ£o mais nova disponÃƒÂ­vel, lida do arquivo de versÃƒÂ£o no GitHub. */
data class InfoAtualizacao(
    val codigo: Int,
    val nome: String,
    val urlDownload: String
)

/**
 * Verifica se tem uma versÃƒÂ£o mais nova do app disponÃƒÂ­vel, lendo um arquivinho de
 * texto (JSON) hospedado num repositÃƒÂ³rio do GitHub.
 *
 * Como configurar:
 * 1. Cria (ou jÃƒÂ¡ tem) um repositÃƒÂ³rio PÃƒÅ¡BLICO no GitHub, ex: github.com/SEU_USUARIO/SEU_REPO
 * 2. Sobe/edita nele um arquivo `versao.json` na raiz, com este conteÃƒÂºdo (ajustando os
 *    valores a cada nova versÃƒÂ£o publicada):
 *    {"codigo": 2, "nome": "1.1.0", "url": "https://raw.githubusercontent.com/tiagotlw-hue/controle-financeiro-releases/main/app-debug.apk"}
 *    - "codigo": o novo CODIGO (tem que ser maior que o Versao.CODIGO atual do app
 *      pra ele avisar que tem atualizaÃƒÂ§ÃƒÂ£o).
 *    - "nome": o texto da versÃƒÂ£o nova, sÃƒÂ³ pra mostrar pra pessoa.
 *    - "url": o link direto de download do APK (de uma GitHub Release, por exemplo)
 *      que abre quando a pessoa toca em "Atualizar".
 * 3. Substitui [URL_ARQUIVO_VERSAO] logo abaixo com a URL "raw" desse arquivo (troca
 *    SEU_USUARIO, SEU_REPO e o branch se nÃƒÂ£o for "main").
 */
private const val URL_ARQUIVO_VERSAO =
    "https://raw.githubusercontent.com/tiagotlw-hue/controle-financeiro-releases/main/versao.json"

/**
 * Faz a checagem de verdade (rede), tem que ser chamada fora da thread principal
 * (ex: dentro de um `viewModelScope.launch(Dispatchers.IO)` ou `withContext(Dispatchers.IO)`).
 *
 * IMPORTANTE: se nÃƒÂ£o tiver internet (ou a conexÃƒÂ£o cair no meio, der timeout, o link
 * estiver errado, o arquivo nÃƒÂ£o existir mais, etc.), essa funÃƒÂ§ÃƒÂ£o simplesmente retorna
 * `null` Ã¢â‚¬â€ o app nÃƒÂ£o trava, nÃƒÂ£o mostra nenhum erro pra pessoa, e continua funcionando
 * normalmente, exatamente como se nÃƒÂ£o tivesse nenhuma atualizaÃƒÂ§ÃƒÂ£o disponÃƒÂ­vel. A
 * checagem ÃƒÂ© sÃƒÂ³ "tentativa e melhor esforÃƒÂ§o": qualquer coisa que dÃƒÂª errado ÃƒÂ©
 * silenciosamente ignorada.
 */
fun verificarNovaVersao(): InfoAtualizacao? {
    if (URL_ARQUIVO_VERSAO.contains("SEU_USUARIO")) return null
    var conexao: HttpURLConnection? = null
    return try {
        // O "?t=" no final muda a cada checagem Ã¢â‚¬â€ sem isso, o CDN do GitHub
        // (raw.githubusercontent.com) pode servir uma cÃƒÂ³pia em cache antiga do
        // versao.json por vÃƒÂ¡rios minutos mesmo depois de vocÃƒÂª jÃƒÂ¡ ter editado o
        // arquivo lÃƒÂ¡, fazendo o app "nÃƒÂ£o enxergar" a atualizaÃƒÂ§ÃƒÂ£o nova.
        val urlComCacheBuster = "$URL_ARQUIVO_VERSAO?t=${System.currentTimeMillis()}"
        conexao = (URL(urlComCacheBuster).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000 // sem rede, falha rÃƒÂ¡pido em vez de segurar o app tentando
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
        // Sem internet, DNS falhou, timeout, JSON invÃƒÂ¡lido, o que for Ã¢â‚¬â€ ignora e segue
        // o app normalmente, como se nÃƒÂ£o tivesse atualizaÃƒÂ§ÃƒÂ£o nenhuma.
        null
    } finally {
        conexao?.disconnect()
    }
}

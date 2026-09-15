package com.controlefinanceiro.app.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Baixa o APK da atualização em segundo plano (usando o DownloadManager do próprio
 * Android, que já cuida de mostrar notificação de progresso, continuar mesmo se a
 * pessoa sair do app, retomar se a rede cair, etc) e, quando terminar, abre a tela
 * de instalação automaticamente.
 *
 * IMPORTANTE (limite do Android, não dá pra contornar): a pessoa ainda vai precisar
 * dar UM toque em "Instalar" na tela que abre no final — o Android não deixa nenhum
 * app comum instalar outro APK sem essa confirmação manual, é uma proteção de
 * segurança do sistema. O que essa função automatiza é só o download: a pessoa não
 * precisa mais abrir navegador, achar o arquivo, nada disso.
 *
 * Pré-requisitos no projeto (ver instruções completas que acompanham este arquivo):
 * 1. Permissão REQUEST_INSTALL_PACKAGES no AndroidManifest.xml
 * 2. Um <provider> do FileProvider configurado no AndroidManifest.xml
 * 3. O "url" dentro do versao.json precisa ser o link de download DIRETO do arquivo
 *    .apk (não de uma pasta do Drive) — troca lá se ainda estiver apontando pra pasta.
 */
fun baixarEInstalarAtualizacao(context: Context, urlApk: String, nomeVersao: String) {
    val gerenciador = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val nomeArquivo = "atualizacao-$nomeVersao.apk"

    // O "?t=" no final muda a cada tentativa de download — sem isso, o CDN do
    // GitHub (raw.githubusercontent.com) pode continuar servindo o .apk ANTIGO em
    // cache por um bom tempo, mesmo depois de você já ter substituído o arquivo lá,
    // fazendo a pessoa sempre instalar uma versão desatualizada.
    val urlComCacheBuster = "$urlApk?t=${System.currentTimeMillis()}"

    val requisicao = DownloadManager.Request(Uri.parse(urlComCacheBuster))
        .setTitle("Baixando atualização")
        .setDescription("Versão $nomeVersao")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, nomeArquivo)
        .addRequestHeader("Cache-Control", "no-cache")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    // Se já existir um arquivo baixado antes com esse mesmo nome (de uma tentativa
    // anterior), apaga primeiro — assim garante que o que for validado/instalado
    // depois é sempre o que acabou de chegar agora, nunca uma sobra antiga no disco.
    val arquivoAntigo = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), nomeArquivo)
    if (arquivoAntigo.exists()) arquivoAntigo.delete()

    val idDownload = gerenciador.enqueue(requisicao)

    // Escuta o momento em que esse download específico termina, pra abrir a
    // instalação sozinho (só falta o toque final da pessoa em "Instalar").
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val idRecebido = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (idRecebido != idDownload) return

            try {
                context.unregisterReceiver(this)
            } catch (e: Exception) {
                // já desregistrado, ignora
            }

            val arquivo = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                nomeArquivo
            )
            if (!arquivo.exists()) return

            // Confere se o que foi baixado é mesmo um APK (todo arquivo .apk/.zip começa
            // com a "assinatura" de bytes 'PK'). Se não for, o Drive provavelmente mandou
            // a página de aviso de vírus (HTML) em vez do arquivo binário — nesse caso,
            // NÃO tenta instalar (é isso que causa "problema ao analisar o pacote"),
            // avisa a pessoa e apaga o arquivo inválido.
            if (!pareceApkValido(arquivo)) {
                arquivo.delete()
                Toast.makeText(
                    context,
                    "Não foi possível baixar a atualização corretamente. Tente novamente mais tarde.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val uriArquivo = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                arquivo
            )

            val intentInstalar = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriArquivo, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intentInstalar)
        }
    }

    val filtro = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filtro, Context.RECEIVER_EXPORTED)
    } else {
        context.registerReceiver(receiver, filtro)
    }
}

/** Um APK válido é um arquivo ZIP, e todo ZIP começa com os bytes 0x50 0x4B ('P' 'K').
 *  Se o arquivo baixado não começar com isso (por exemplo, começar com '<' de uma
 *  página HTML), não é um APK de verdade. */
private fun pareceApkValido(arquivo: File): Boolean {
    if (arquivo.length() < 1024) return false // apk de verdade nunca é tão pequeno
    return try {
        arquivo.inputStream().use { stream ->
            val cabecalho = ByteArray(2)
            val lidos = stream.read(cabecalho)
            lidos == 2 && cabecalho[0] == 0x50.toByte() && cabecalho[1] == 0x4B.toByte()
        }
    } catch (e: Exception) {
        false
    }
}

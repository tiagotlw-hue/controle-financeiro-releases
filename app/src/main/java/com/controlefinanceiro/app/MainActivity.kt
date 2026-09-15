package com.controlefinanceiro.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.controlefinanceiro.app.data.Categoria
import com.controlefinanceiro.app.data.Cliente
import com.controlefinanceiro.app.data.InfoAtualizacao
import com.controlefinanceiro.app.data.Lista
import com.controlefinanceiro.app.data.Movimentacao
import com.controlefinanceiro.app.data.Pagamento
import com.controlefinanceiro.app.data.TipoMovimentacao
import com.controlefinanceiro.app.data.Versao
import com.controlefinanceiro.app.data.baixarEInstalarAtualizacao
import com.controlefinanceiro.app.data.verificarNovaVersao
import com.controlefinanceiro.app.ui.components.MiniCalculadora
import com.controlefinanceiro.app.ui.screens.*
import com.controlefinanceiro.app.ui.theme.ControleFinanceiroTheme
import com.controlefinanceiro.app.viewmodel.AppViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Cole aqui o "Web client ID" (tipo 3 / Web) que aparece em:
// Firebase Console > Authentication > Sign-in method > Google > detalhes do SDK Web
const val WEB_CLIENT_ID = "530471929124-7cntd4equh7obf6dul2dcocm10lpe5tk.apps.googleusercontent.com"

// Hierarquia de navegação: Cliente -> Categoria (ex: "Construção a pagar") ->
// Movimentações (cobranças parceladas dentro da categoria) -> Duplicatas/Baixas.
private sealed class Tela {
    data object Login : Tela()
    data object Clientes : Tela()
    data object VisaoGeral : Tela()
    data object Configuracoes : Tela()
    data object ConfiguracoesPreferencias : Tela()
    data object ConfiguracoesCartoes : Tela()
    data object ConfiguracoesConta : Tela()
    data object Listas : Tela()
    data class ListaDetalhe(val lista: Lista) : Tela()
    data class ClienteForm(val cliente: Cliente?) : Tela()
    data class Categorias(val cliente: Cliente) : Tela()
    data class Movimentacoes(val cliente: Cliente, val categoria: Categoria) : Tela()
    data class Entrada(
        val cliente: Cliente,
        val categoria: Categoria,
        val movimentacaoParaEditar: Movimentacao? = null,
        val tipoFluxo: String? = null
    ) : Tela()
    data class Saida(val cliente: Cliente, val categoria: Categoria, val movimentacaoIdFiltro: String? = null) : Tela()
    data class MovimentacaoDetalhe(val cliente: Cliente, val categoria: Categoria, val movimentacao: Movimentacao) : Tela()
    data class BaixaDetalhe(val cliente: Cliente, val categoria: Categoria, val itens: List<Pair<Movimentacao, Pagamento>>) : Tela()
}

/** Espelha o destino de cada `aoVoltar` já usado nas telas, pra saber pra onde ir quando
 *  a pessoa aperta o botão/gesto de voltar do próprio celular (não só o botão "<" da
 *  barra de cima). Retorna null nas telas "raiz" (Login e Clientes) — nesses casos não
 *  tem pra onde voltar dentro do app, então o botão do celular volta a fazer o padrão
 *  do Android (minimizar/fechar). */
private fun telaAnterior(t: Tela): Tela? = when (t) {
    is Tela.Login -> null
    is Tela.Clientes -> null
    is Tela.VisaoGeral -> Tela.Clientes
    is Tela.Configuracoes -> Tela.Clientes
    is Tela.ConfiguracoesPreferencias -> Tela.Configuracoes
    is Tela.ConfiguracoesCartoes -> Tela.Configuracoes
    is Tela.ConfiguracoesConta -> Tela.Configuracoes
    is Tela.Listas -> Tela.Clientes
    is Tela.ListaDetalhe -> Tela.Listas
    is Tela.ClienteForm -> Tela.Clientes
    is Tela.Categorias -> Tela.Clientes
    is Tela.Movimentacoes -> Tela.Categorias(t.cliente)
    is Tela.Entrada -> if (t.movimentacaoParaEditar != null)
        Tela.MovimentacaoDetalhe(t.cliente, t.categoria, t.movimentacaoParaEditar)
    else
        Tela.Movimentacoes(t.cliente, t.categoria)
    is Tela.Saida -> Tela.Movimentacoes(t.cliente, t.categoria)
    is Tela.MovimentacaoDetalhe -> Tela.Movimentacoes(t.cliente, t.categoria)
    is Tela.BaixaDetalhe -> Tela.Movimentacoes(t.cliente, t.categoria)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ControleFinanceiroTheme {
                val viewModel: AppViewModel = viewModel()
                val context = LocalContext.current
                var tela by remember {
                    mutableStateOf<Tela>(
                        if (FirebaseAuth.getInstance().currentUser != null) Tela.Clientes else Tela.Login
                    )
                }

                LaunchedEffect(Unit) {
                    if (FirebaseAuth.getInstance().currentUser != null) {
                        viewModel.iniciarEscutaClientes()
                        viewModel.iniciarEscutaCartoes()
                        viewModel.iniciarEscutaConfiguracoes()
                    }
                }

                // Checa uma vez, ao abrir o app, se tem uma versão mais nova disponível
                // (lendo um arquivinho de versão hospedado numa pasta do Drive — ver
                // Versao.kt pra configurar o link). Só faz essa checagem automática se a
                // pessoa não tiver desligado em Configurações (vem ligada por padrão). Se
                // tiver uma versão nova, pergunta se quer atualizar; se não tiver (ou der
                // erro de rede), não aparece nada.
                var infoAtualizacao by remember { mutableStateOf<InfoAtualizacao?>(null) }
                var mostrarDialogoAtualizacao by remember { mutableStateOf(false) }
                val atualizacaoAutomatica = viewModel.configuracoes.value.atualizacaoAutomatica
                LaunchedEffect(atualizacaoAutomatica) {
                    if (!atualizacaoAutomatica) return@LaunchedEffect
                    val resultado = withContext(Dispatchers.IO) { verificarNovaVersao() }
                    if (resultado != null) {
                        infoAtualizacao = resultado
                        mostrarDialogoAtualizacao = true
                    }
                }

                if (mostrarDialogoAtualizacao && infoAtualizacao != null) {
                    val context = LocalContext.current
                    AlertDialog(
                        onDismissRequest = { mostrarDialogoAtualizacao = false },
                        title = { Text("Nova versão disponível") },
                        text = {
                            Text(
                                "Tem uma versão nova do app (${infoAtualizacao?.nome}). " +
                                    "Você está usando a ${Versao.NOME}. Quer atualizar agora?"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                mostrarDialogoAtualizacao = false
                                val url = infoAtualizacao?.urlDownload
                                val nome = infoAtualizacao?.nome ?: ""
                                if (!url.isNullOrBlank()) {
                                    baixarEInstalarAtualizacao(context, url, nome)
                                }
                            }) { Text("Atualizar") }
                        },
                        dismissButton = {
                            TextButton(onClick = { mostrarDialogoAtualizacao = false }) { Text("Agora não") }
                        }
                    )
                }

                // Faz o botão/gesto de voltar do celular navegar pra tela anterior dentro
                // do app (mesmo destino do botão "<" da barra de cima), em vez de minimizar
                // o app. Nas telas raiz (Login/Clientes), onde não tem mais pra onde voltar,
                // pede confirmação antes de sair — a não ser que a pessoa tenha desligado
                // essa opção em Configurações, caso em que o celular volta a fazer o padrão
                // dele direto (minimizar/fechar).
                val destinoVoltar = telaAnterior(tela)
                val confirmarSaida = viewModel.configuracoes.value.confirmarSaida
                var mostrarConfirmacaoSaida by remember { mutableStateOf(false) }
                BackHandler(enabled = destinoVoltar != null || confirmarSaida) {
                    if (destinoVoltar != null) {
                        tela = destinoVoltar
                    } else {
                        mostrarConfirmacaoSaida = true
                    }
                }

                if (mostrarConfirmacaoSaida) {
                    val activity = LocalContext.current as? Activity
                    AlertDialog(
                        onDismissRequest = { mostrarConfirmacaoSaida = false },
                        title = { Text("Sair do aplicativo") },
                        text = { Text("Tem certeza que deseja sair?") },
                        confirmButton = {
                            TextButton(onClick = {
                                mostrarConfirmacaoSaida = false
                                activity?.finish()
                            }) { Text("Sair") }
                        },
                        dismissButton = {
                            TextButton(onClick = { mostrarConfirmacaoSaida = false }) { Text("Cancelar") }
                        }
                    )
                }

                Box(Modifier.fillMaxSize()) {
                when (val t = tela) {
                    is Tela.Login -> LoginScreen(webClientId = WEB_CLIENT_ID) {
                        viewModel.iniciarEscutaClientes()
                        viewModel.iniciarEscutaCartoes()
                        viewModel.iniciarEscutaConfiguracoes()
                        tela = Tela.Clientes
                    }
                    is Tela.Clientes -> ClientesScreen(
                        viewModel = viewModel,
                        aoAbrirCliente = { tela = Tela.Categorias(it) },
                        aoNovoCliente = { tela = Tela.ClienteForm(null) },
                        aoEditarCliente = { tela = Tela.ClienteForm(it) },
                        aoAbrirVisaoGeral = { tela = Tela.VisaoGeral },
                        aoAbrirConfiguracoes = { tela = Tela.Configuracoes },
                        aoAbrirLista = { tela = Tela.Listas }
                    )
                    is Tela.VisaoGeral -> VisaoGeralScreen(
                        viewModel = viewModel,
                        aoAbrirCliente = { tela = Tela.Categorias(it) },
                        aoVoltar = { tela = Tela.Clientes }
                    )
                    is Tela.Listas -> ListasScreen(
                        viewModel = viewModel,
                        aoAbrirLista = { tela = Tela.ListaDetalhe(it) },
                        aoVoltar = { tela = Tela.Clientes }
                    )
                    is Tela.ListaDetalhe -> ListaDetalheScreen(
                        viewModel = viewModel,
                        lista = t.lista,
                        aoVoltar = { tela = Tela.Listas }
                    )
                    is Tela.Configuracoes -> ConfiguracoesScreen(
                        aoVoltar = { tela = Tela.Clientes },
                        aoAbrirPreferencias = { tela = Tela.ConfiguracoesPreferencias },
                        aoAbrirCartoes = { tela = Tela.ConfiguracoesCartoes },
                        aoAbrirConta = { tela = Tela.ConfiguracoesConta }
                    )
                    is Tela.ConfiguracoesPreferencias -> ConfiguracoesPreferenciasScreen(
                        viewModel = viewModel,
                        aoVoltar = { tela = Tela.Configuracoes }
                    )
                    is Tela.ConfiguracoesCartoes -> ConfiguracoesCartoesScreen(
                        viewModel = viewModel,
                        aoVoltar = { tela = Tela.Configuracoes }
                    )
                    is Tela.ConfiguracoesConta -> ConfiguracoesContaScreen(
                        aoVoltar = { tela = Tela.Configuracoes },
                        aoSairDaConta = {
                            FirebaseAuth.getInstance().signOut()
                            viewModel.aoSairDaConta()
                            // Apaga o e-mail/senha salvos localmente (se a pessoa tinha
                            // marcado "lembrar") — por segurança, ninguém que pegar o
                            // aparelho depois entra automaticamente com a conta anterior.
                            context.getSharedPreferences("login_salvo", android.content.Context.MODE_PRIVATE)
                                .edit().clear().apply()
                            tela = Tela.Login
                        }
                    )
                    is Tela.ClienteForm -> ClienteFormScreen(
                        viewModel = viewModel,
                        clienteExistente = t.cliente,
                        aoVoltar = { tela = Tela.Clientes }
                    )
                    is Tela.Categorias -> CategoriasScreen(
                        viewModel = viewModel,
                        cliente = t.cliente,
                        aoAbrirCategoria = { tela = Tela.Movimentacoes(t.cliente, it) },
                        aoVoltar = { tela = Tela.Clientes }
                    )
                    is Tela.Movimentacoes -> MovimentacoesScreen(
                        viewModel = viewModel,
                        cliente = t.cliente,
                        categoria = t.categoria,
                        aoAbrirMovimentacao = { tela = Tela.MovimentacaoDetalhe(t.cliente, t.categoria, it) },
                        aoAbrirBaixa = { itens -> tela = Tela.BaixaDetalhe(t.cliente, t.categoria, itens) },
                        aoNovaMovimentacao = { tipoFluxo -> tela = Tela.Entrada(t.cliente, t.categoria, tipoFluxo = tipoFluxo) },
                        aoDarBaixa = { tela = Tela.Saida(t.cliente, t.categoria) },
                        aoVoltar = { tela = Tela.Categorias(t.cliente) }
                    )
                    is Tela.Entrada -> EntradaScreen(
                        viewModel = viewModel,
                        clienteId = t.cliente.id,
                        categoriaId = t.categoria.id,
                        tipo = t.categoria.tipo,
                        movimentacaoParaEditar = t.movimentacaoParaEditar,
                        tipoFluxo = t.tipoFluxo,
                        aoVoltar = {
                            tela = if (t.movimentacaoParaEditar != null)
                                Tela.MovimentacaoDetalhe(t.cliente, t.categoria, t.movimentacaoParaEditar)
                            else
                                Tela.Movimentacoes(t.cliente, t.categoria)
                        }
                    )
                    is Tela.Saida -> SaidaScreen(
                        viewModel = viewModel,
                        clienteId = t.cliente.id,
                        categoriaId = t.categoria.id,
                        tipo = t.categoria.tipo,
                        movimentacaoIdFiltro = t.movimentacaoIdFiltro,
                        aoVoltar = { tela = Tela.Movimentacoes(t.cliente, t.categoria) }
                    )
                    is Tela.MovimentacaoDetalhe -> MovimentacaoDetalheScreen(
                        viewModel = viewModel,
                        clienteId = t.cliente.id,
                        categoriaId = t.categoria.id,
                        movimentacaoId = t.movimentacao.id,
                        aoBaixar = {
                            tela = Tela.Saida(t.cliente, t.categoria, t.movimentacao.id)
                        },
                        aoNovaMovimentacaoMesmoTipo = { tipoAtual ->
                            val tipoFluxo = if (t.categoria.tipo == TipoMovimentacao.FLUXO) tipoAtual else null
                            tela = Tela.Entrada(t.cliente, t.categoria, tipoFluxo = tipoFluxo)
                        },
                        aoEditar = { atual ->
                            tela = Tela.Entrada(t.cliente, t.categoria, movimentacaoParaEditar = atual)
                        },
                        aoExcluida = { tela = Tela.Movimentacoes(t.cliente, t.categoria) },
                        aoVoltar = { tela = Tela.Movimentacoes(t.cliente, t.categoria) }
                    )
                    is Tela.BaixaDetalhe -> BaixaDetalheScreen(
                        viewModel = viewModel,
                        clienteId = t.cliente.id,
                        categoriaId = t.categoria.id,
                        itens = t.itens,
                        aoExcluida = { tela = Tela.Movimentacoes(t.cliente, t.categoria) },
                        aoVoltar = { tela = Tela.Movimentacoes(t.cliente, t.categoria) }
                    )
                }

                // Mini calculadora flutuante, disponível em todas as telas acima.
                MiniCalculadora(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 64.dp, end = 8.dp)
                )
                }
            }
        }
    }
}

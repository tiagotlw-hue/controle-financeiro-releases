package com.controlefinanceiro.app.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.controlefinanceiro.app.data.Cartao
import com.controlefinanceiro.app.data.Categoria
import com.controlefinanceiro.app.data.Cliente
import com.controlefinanceiro.app.data.Configuracoes
import com.controlefinanceiro.app.data.FirebaseRepository
import com.controlefinanceiro.app.data.FormaPagamento
import com.controlefinanceiro.app.data.ItemLista
import com.controlefinanceiro.app.data.LinhaCsvFluxo
import com.controlefinanceiro.app.data.Lista
import com.controlefinanceiro.app.data.Movimentacao
import com.controlefinanceiro.app.data.Pagamento
import com.controlefinanceiro.app.data.ParcelaRef
import com.controlefinanceiro.app.data.ParcelaUtils
import com.controlefinanceiro.app.data.gerarCsvCliente
import com.controlefinanceiro.app.data.salvarCsvTemporario
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AppViewModel : ViewModel() {

    private val repo = FirebaseRepository()

    val clientes = mutableStateOf<List<Cliente>>(emptyList())
    val categorias = mutableStateOf<List<Categoria>>(emptyList())
    val movimentacoes = mutableStateOf<List<Movimentacao>>(emptyList())
    val cartoes = mutableStateOf<List<Cartao>>(emptyList())
    val configuracoes = mutableStateOf(Configuracoes())
    val listas = mutableStateOf<List<Lista>>(emptyList())
    val listaCompras = mutableStateOf<List<ItemLista>>(emptyList())
    val carregando = mutableStateOf(false)
    val erro = mutableStateOf<String?>(null)

    private var jobClientes: Job? = null
    private var jobCategorias: Job? = null
    private var jobMovimentacoes: Job? = null
    private var jobCartoes: Job? = null
    private var jobConfiguracoes: Job? = null
    private var jobListas: Job? = null
    private var jobItensLista: Job? = null

    fun usuarioLogado() = repo.usuarioLogado()

    fun limparErro() {
        erro.value = null
    }

    /** Chamado ao fazer logoff: cancela todas as escutas em andamento e limpa o estado
     *  em memória, pra não vazar dados de uma conta pra outra se a pessoa entrar com
     *  um usuário diferente em seguida (sem isso, os jobs ficam "presos" achando que
     *  já estão rodando e não reiniciam, e as listas antigas continuam visíveis por
     *  um instante). Não mexe no Firebase Auth em si — isso é feito por quem chamar
     *  essa função, antes ou depois, junto com a navegação de volta pro Login. */
    fun aoSairDaConta() {
        jobClientes?.cancel(); jobClientes = null
        jobCategorias?.cancel(); jobCategorias = null
        jobMovimentacoes?.cancel(); jobMovimentacoes = null
        jobCartoes?.cancel(); jobCartoes = null
        jobConfiguracoes?.cancel(); jobConfiguracoes = null
        jobListas?.cancel(); jobListas = null
        jobItensLista?.cancel(); jobItensLista = null

        clientes.value = emptyList()
        categorias.value = emptyList()
        movimentacoes.value = emptyList()
        cartoes.value = emptyList()
        configuracoes.value = Configuracoes()
        listas.value = emptyList()
        listaCompras.value = emptyList()
        erro.value = null
    }

    /** Começa a escuta contínua dos clientes. Só deve ser chamada quando já existe um
     *  usuário autenticado (por isso não fica no init: instanciar o ViewModel cedo
     *  demais, antes do login, fazia a escuta falhar e nunca mais religar).
     *  Continua funcionando offline normalmente: entrega o cache local na hora e
     *  atualiza sozinho quando sincroniza com o servidor. */
    fun iniciarEscutaClientes() {
        if (jobClientes != null) return
        jobClientes = viewModelScope.launch {
            try {
                repo.escutarClientes()
                    .catch { e -> erro.value = e.message }
                    .collect { clientes.value = it }
            } finally {
                // Se a escuta caiu (erro tratado acima, ou até cancelamento), libera o
                // "trava" pra próxima chamada poder reconectar — sem isso, um erro único
                // (ex: falha passageira de rede) deixava essa lista travada pro resto da
                // sessão do app, já que o "if (jobClientes != null) return" no início
                // bloquearia qualquer nova tentativa pra sempre.
                jobClientes = null
            }
        }
    }

    /** Escuta contínua dos cartões cadastrados (configuração global do app). */
    fun iniciarEscutaCartoes() {
        if (jobCartoes != null) return
        jobCartoes = viewModelScope.launch {
            try {
                repo.escutarCartoes()
                    .catch { e -> erro.value = e.message }
                    .collect { cartoes.value = it }
            } finally {
                jobCartoes = null // mesmo motivo de iniciarEscutaClientes: permite reconectar depois de um erro
            }
        }
    }

    /** Escuta contínua das preferências gerais (forma de pagamento padrão etc). */
    fun iniciarEscutaConfiguracoes() {
        if (jobConfiguracoes != null) return
        jobConfiguracoes = viewModelScope.launch {
            try {
                repo.escutarConfiguracoes()
                    .catch { e -> erro.value = e.message }
                    .collect { configuracoes.value = it }
            } finally {
                jobConfiguracoes = null // mesmo motivo de iniciarEscutaClientes: permite reconectar depois de um erro
            }
        }
    }

    fun salvarCartao(cartao: Cartao, aoConcluir: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.salvarCartao(cartao)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun excluirCartao(cartaoId: String) {
        viewModelScope.launch {
            try {
                repo.excluirCartao(cartaoId)
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun salvarConfiguracoes(config: Configuracoes) {
        viewModelScope.launch {
            try {
                repo.salvarConfiguracoes(config)
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Começa (ou reinicia, se o cliente mudou) a escuta das categorias desse cliente. */
    fun observarCategorias(clienteId: String) {
        jobCategorias?.cancel()
        jobCategorias = viewModelScope.launch {
            repo.escutarCategorias(clienteId)
                .catch { e -> erro.value = e.message }
                .collect { categorias.value = it }
        }
    }

    fun criarCategoria(clienteId: String, nome: String, tipo: String, aoConcluir: (Categoria) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val categoria = Categoria(clienteId = clienteId, nome = nome, tipo = tipo)
                val id = repo.salvarCategoria(clienteId, categoria)
                aoConcluir(categoria.copy(id = id))
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Edita uma categoria já existente (por enquanto só o nome é alterado pela tela;
     *  o tipo não é reeditável para não deixar movimentações antigas inconsistentes). */
    fun editarCategoria(clienteId: String, categoria: Categoria, aoConcluir: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.salvarCategoria(clienteId, categoria)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun excluirCategoria(clienteId: String, categoriaId: String, aoConcluir: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.excluirCategoria(clienteId, categoriaId)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Começa (ou reinicia, se mudou) a escuta das movimentações de uma categoria. */
    fun observarMovimentacoes(clienteId: String, categoriaId: String) {
        jobMovimentacoes?.cancel()
        jobMovimentacoes = viewModelScope.launch {
            carregando.value = true
            repo.escutarMovimentacoes(clienteId, categoriaId)
                .catch { e ->
                    erro.value = e.message
                    carregando.value = false
                }
                .collect { lista ->
                    movimentacoes.value = lista
                    carregando.value = false
                }
        }
    }

    fun salvarCliente(cliente: Cliente, aoConcluir: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.salvarCliente(cliente)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun excluirCliente(clienteId: String) {
        viewModelScope.launch {
            try {
                repo.excluirCliente(clienteId)
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Busca todas as categorias e movimentações do cliente e monta um CSV com o
     *  extrato completo (funciona offline: usa cache local igual o resto do app). */
    fun exportarClienteCsv(context: Context, cliente: Cliente, aoConcluir: (java.io.File) -> Unit) {
        viewModelScope.launch {
            try {
                val categorias = repo.listarCategoriasUmaVez(cliente.id)
                val movimentacoesPorCategoria = categorias.associate { categoria ->
                    categoria.id to repo.listarMovimentacoesUmaVez(cliente.id, categoria.id)
                }
                val csv = gerarCsvCliente(cliente, categorias, movimentacoesPorCategoria)
                val nomeArquivo = "extrato-${cliente.nome.replace(Regex("[^A-Za-z0-9]"), "_")}.csv"
                val arquivo = salvarCsvTemporario(context, csv, nomeArquivo)
                aoConcluir(arquivo)
            } catch (e: Exception) {
                erro.value = "Não foi possível gerar o extrato: ${e.message}"
            }
        }
    }

    private fun gerarParcelasConforme(
        formaPagamento: String,
        cartao: Cartao?,
        valorTotal: Double,
        numParcelas: Int,
        diaVencimento: Int,
        dataCriacao: Long
    ) = if (formaPagamento == FormaPagamento.CARTAO && cartao != null) {
        ParcelaUtils.gerarParcelasCartao(valorTotal, numParcelas, dataCriacao, cartao.melhorDia, cartao.diaVencimento)
    } else if (formaPagamento == FormaPagamento.DINHEIRO) {
        // Dinheiro é sempre à vista: uma única duplicata, vencendo no mesmo dia da cobrança.
        ParcelaUtils.gerarParcelaAvista(valorTotal, dataCriacao)
    } else {
        ParcelaUtils.gerarParcelas(valorTotal, numParcelas, dataCriacao, diaVencimento)
    }

    fun criarMovimentacao(
        clienteId: String,
        categoriaId: String,
        tipo: String,
        descricao: String,
        valorTotal: Double,
        numParcelas: Int,
        diaVencimento: Int,
        formaPagamento: String = FormaPagamento.DINHEIRO,
        cartao: Cartao? = null,
        dataCriacao: Long = System.currentTimeMillis(),
        valoresManuaisParcelas: Map<Int, Double> = emptyMap(),
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val parcelasBase = gerarParcelasConforme(formaPagamento, cartao, valorTotal, numParcelas, diaVencimento, dataCriacao)
                val parcelas = ParcelaUtils.aplicarValoresManuais(parcelasBase, valoresManuaisParcelas, valorTotal)
                val movimentacao = Movimentacao(
                    clienteId = clienteId,
                    categoriaId = categoriaId,
                    tipo = tipo,
                    descricao = descricao,
                    valorTotal = valorTotal,
                    numParcelas = numParcelas,
                    diaVencimento = diaVencimento,
                    dataCriacao = dataCriacao,
                    formaPagamento = formaPagamento,
                    cartaoId = cartao?.id ?: "",
                    parcelas = parcelas
                )
                repo.salvarMovimentacao(clienteId, categoriaId, movimentacao)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Edita uma movimentação já existente. Só deve ser chamada se ela ainda não tiver
     *  nenhum pagamento (a tela já checa `podeEditarOuExcluir` antes de permitir isso). */
    fun editarMovimentacao(
        movimentacaoExistente: Movimentacao,
        descricao: String,
        valorTotal: Double,
        numParcelas: Int,
        diaVencimento: Int,
        formaPagamento: String,
        cartao: Cartao?,
        dataCriacao: Long,
        valoresManuaisParcelas: Map<Int, Double> = emptyMap(),
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val parcelasBase = gerarParcelasConforme(formaPagamento, cartao, valorTotal, numParcelas, diaVencimento, dataCriacao)
                val parcelas = ParcelaUtils.aplicarValoresManuais(parcelasBase, valoresManuaisParcelas, valorTotal)
                val atualizada = movimentacaoExistente.copy(
                    descricao = descricao,
                    valorTotal = valorTotal,
                    numParcelas = numParcelas,
                    diaVencimento = diaVencimento,
                    dataCriacao = dataCriacao,
                    formaPagamento = formaPagamento,
                    cartaoId = cartao?.id ?: "",
                    parcelas = parcelas
                )
                repo.salvarMovimentacao(movimentacaoExistente.clienteId, movimentacaoExistente.categoriaId, atualizada)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Cria um lançamento de FLUXO DE CAIXA: já nasce quitado (recebido ou pago na
     *  hora), sem parcelamento e sem duplicata em aberto — bem mais simples que
     *  criarMovimentacao, que lida com parcelamento/cartão/baixa. */
    fun criarMovimentacaoFluxo(
        clienteId: String,
        categoriaId: String,
        tipo: String,
        descricao: String,
        valorTotal: Double,
        dataCriacao: Long = System.currentTimeMillis(),
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val movimentacao = Movimentacao(
                    clienteId = clienteId,
                    categoriaId = categoriaId,
                    tipo = tipo,
                    descricao = descricao,
                    valorTotal = valorTotal,
                    numParcelas = 1,
                    dataCriacao = dataCriacao,
                    formaPagamento = FormaPagamento.DINHEIRO,
                    parcelas = ParcelaUtils.gerarParcelaQuitada(valorTotal, dataCriacao)
                )
                repo.salvarMovimentacao(clienteId, categoriaId, movimentacao)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Importa em lote as linhas já validadas de um CSV de fluxo de caixa (a tela de
     *  importação mostra a prévia e filtra as linhas com erro antes de chamar isso —
     *  aqui só recebe o que já está pronto pra virar movimentação). */
    fun importarLancamentosFluxo(
        clienteId: String,
        categoriaId: String,
        linhas: List<LinhaCsvFluxo>,
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val movimentacoes = linhas.filter { it.valida }.map { linha ->
                    Movimentacao(
                        clienteId = clienteId,
                        categoriaId = categoriaId,
                        tipo = linha.tipo!!,
                        descricao = linha.descricao,
                        valorTotal = linha.valor!!,
                        numParcelas = 1,
                        dataCriacao = linha.data!!,
                        formaPagamento = FormaPagamento.DINHEIRO,
                        parcelas = ParcelaUtils.gerarParcelaQuitada(linha.valor, linha.data)
                    )
                }
                repo.importarMovimentacoesFluxo(clienteId, categoriaId, movimentacoes)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = "Não foi possível importar: ${e.message}"
            }
        }
    }

    /** Edita um lançamento de fluxo de caixa já existente (sempre já está quitado,
     *  então só precisa recalcular a duplicata única com os novos valores). */
    fun editarMovimentacaoFluxo(
        movimentacaoExistente: Movimentacao,
        tipo: String,
        descricao: String,
        valorTotal: Double,
        dataCriacao: Long,
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val atualizada = movimentacaoExistente.copy(
                    tipo = tipo,
                    descricao = descricao,
                    valorTotal = valorTotal,
                    dataCriacao = dataCriacao,
                    parcelas = ParcelaUtils.gerarParcelaQuitada(valorTotal, dataCriacao)
                )
                repo.salvarMovimentacao(movimentacaoExistente.clienteId, movimentacaoExistente.categoriaId, atualizada)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Edita só a data/hora (e a descrição) de uma movimentação que já tem baixa registrada
     *  — nesse caso não dá pra mexer em valor, número de parcelas, forma de pagamento etc.,
     *  só recalcula os vencimentos das duplicatas a partir da nova data, preservando o que
     *  já foi pago em cada uma. */
    fun editarDataMovimentacao(
        movimentacaoExistente: Movimentacao,
        novaDescricao: String,
        novaDataCriacao: Long,
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val cartao = cartoes.value.firstOrNull { it.id == movimentacaoExistente.cartaoId }
                // Se a movimentação foi paga no cartão mas esse cartão já foi excluído
                // (excluirCartao não impede isso, mesmo com movimentações antigas usando
                // ele), não tem como recalcular os vencimentos direito — as regras de
                // fechamento/vencimento daquele cartão específico não existem mais. Cair
                // pro algoritmo genérico de dinheiro parcelado mudaria os vencimentos
                // originais da fatura sem nenhum aviso. Melhor manter os vencimentos como
                // estavam e só atualizar descrição/data de criação.
                val cartaoOrfao = movimentacaoExistente.formaPagamento == FormaPagamento.CARTAO && cartao == null
                val novasParcelas = if (cartaoOrfao) {
                    movimentacaoExistente.parcelas
                } else {
                    val parcelasModelo = gerarParcelasConforme(
                        movimentacaoExistente.formaPagamento,
                        cartao,
                        movimentacaoExistente.valorTotal,
                        movimentacaoExistente.numParcelas,
                        movimentacaoExistente.diaVencimento,
                        novaDataCriacao
                    )
                    ParcelaUtils.reaplicarVencimentos(movimentacaoExistente.parcelas, parcelasModelo)
                }
                val atualizada = movimentacaoExistente.copy(
                    descricao = novaDescricao,
                    dataCriacao = novaDataCriacao,
                    parcelas = novasParcelas
                )
                repo.salvarMovimentacao(movimentacaoExistente.clienteId, movimentacaoExistente.categoriaId, atualizada)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun excluirMovimentacao(clienteId: String, categoriaId: String, movimentacaoId: String, aoConcluir: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.excluirMovimentacao(clienteId, categoriaId, movimentacaoId)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Dá baixa distribuindo `valorPago` pelas parcelas selecionadas (de uma ou mais
     *  movimentações da mesma categoria), sempre respeitando a ordem em que foram
     *  passadas (a tela já manda as mais antigas primeiro). */
    fun darBaixa(
        clienteId: String,
        categoriaId: String,
        selecionadas: List<ParcelaRef>,
        valorPago: Double,
        descricaoManual: String,
        dataBaixa: Long = System.currentTimeMillis(),
        proporcional: Boolean = false,
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val movimentacoesPorId = movimentacoes.value.associateBy { it.id }
                val atualizadas = ParcelaUtils.aplicarBaixaConsolidada(
                    movimentacoesPorId = movimentacoesPorId,
                    selecionadas = selecionadas,
                    valorPago = valorPago,
                    descricaoManual = descricaoManual,
                    dataBaixa = dataBaixa,
                    proporcional = proporcional
                )
                repo.aplicarBaixa(clienteId, categoriaId, atualizadas)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Exclui uma baixa específica, devolvendo as duplicatas afetadas ao estado de antes. */
    fun excluirBaixa(
        clienteId: String,
        categoriaId: String,
        movimentacaoId: String,
        pagamento: Pagamento,
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repo.excluirBaixa(clienteId, categoriaId, movimentacaoId, pagamento)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Exclui de uma vez todas as partes de uma baixa que foi dividida entre duplicatas
     *  de mais de uma movimentação (mesmo baixaId). Devolve todas as duplicatas afetadas,
     *  em todas as movimentações envolvidas, ao estado de antes. */
    fun excluirBaixaGrupo(
        clienteId: String,
        categoriaId: String,
        itens: List<Pair<String, Pagamento>>,
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repo.excluirBaixaGrupo(clienteId, categoriaId, itens)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    /** Edita a data/hora de uma baixa, em todas as movimentações que ela afeta (quando
     *  ela foi dividida entre mais de uma de uma vez). Valores e parcelas continuam
     *  os mesmos, só a data muda. */
    fun editarDataBaixaGrupo(
        clienteId: String,
        categoriaId: String,
        itens: List<Pair<String, Pagamento>>,
        novaData: Long,
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                repo.editarDataBaixaGrupo(clienteId, categoriaId, itens, novaData)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    // ---------- LISTAS DE COMPRAS ----------

    /** Escuta contínua de todas as listas que a pessoa tem acesso. */
    fun iniciarEscutaListas() {
        if (jobListas != null) return
        jobListas = viewModelScope.launch {
            try {
                repo.escutarListas()
                    .catch { e -> erro.value = e.message }
                    .collect { listas.value = it }
            } finally {
                jobListas = null // mesmo motivo de iniciarEscutaClientes: permite reconectar depois de um erro
            }
        }
    }

    fun criarLista(nome: String, aoConcluir: (String) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val codigo = repo.criarLista(nome)
                aoConcluir(codigo)
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun renomearLista(listaId: String, novoNome: String, aoConcluir: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.renomearLista(listaId, novoNome)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun excluirLista(listaId: String, aoConcluir: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.excluirLista(listaId)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun entrarLista(codigo: String, aoConcluir: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.entrarLista(codigo.trim().uppercase())
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun sairLista(listaId: String, aoConcluir: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                repo.sairLista(listaId)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    // ---------- ITENS DE UMA LISTA ----------

    /** Começa (ou reinicia, se a lista aberta mudou) a escuta dos itens de uma lista. */
    fun observarItensLista(listaId: String) {
        jobItensLista?.cancel()
        jobItensLista = viewModelScope.launch {
            repo.escutarItensLista(listaId)
                .catch { e -> erro.value = e.message }
                .collect { listaCompras.value = it }
        }
    }

    fun salvarItemLista(
        listaId: String,
        descricao: String,
        observacao: String,
        quantidade: Double,
        unidade: String,
        valorLimite: Double?,
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val item = ItemLista(
                    descricao = descricao,
                    observacao = observacao,
                    quantidade = quantidade,
                    unidade = unidade,
                    valorLimite = valorLimite
                )
                repo.salvarItemLista(listaId, item)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun editarItemLista(
        listaId: String,
        itemExistente: ItemLista,
        descricao: String,
        observacao: String,
        quantidade: Double,
        unidade: String,
        valorLimite: Double?,
        aoConcluir: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val atualizado = itemExistente.copy(
                    descricao = descricao,
                    observacao = observacao,
                    quantidade = quantidade,
                    unidade = unidade,
                    valorLimite = valorLimite
                )
                repo.salvarItemLista(listaId, atualizado)
                aoConcluir()
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun marcarItemListaComprado(listaId: String, itemId: String, comprado: Boolean) {
        viewModelScope.launch {
            try {
                repo.marcarItemListaComprado(listaId, itemId, comprado)
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }

    fun excluirItemLista(listaId: String, itemId: String) {
        viewModelScope.launch {
            try {
                repo.excluirItemLista(listaId, itemId)
            } catch (e: Exception) {
                erro.value = e.message
            }
        }
    }
}

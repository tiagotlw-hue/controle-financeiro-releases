package com.controlefinanceiro.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun uid(): String =
        auth.currentUser?.uid ?: error("Usuário não autenticado")

    private fun clientesRef() =
        db.collection("usuarios").document(uid()).collection("clientes")

    private fun categoriasRef(clienteId: String) =
        clientesRef().document(clienteId).collection("categorias")

    private fun movimentacoesRef(clienteId: String, categoriaId: String) =
        categoriasRef(clienteId).document(categoriaId).collection("movimentacoes")

    private fun cartoesRef() =
        db.collection("usuarios").document(uid()).collection("cartoes")

    private fun configuracoesRef() =
        db.collection("usuarios").document(uid()).collection("config").document("geral")

    private fun listasRef() =
        db.collection("listas")

    private fun itensRef(listaId: String) =
        listasRef().document(listaId).collection("itens")

    // ---------- CLIENTES ----------
    // Usa addSnapshotListener (em vez de get() único) para funcionar offline:
    // o Firestore mantém um cache local e entrega os dados salvos no aparelho
    // instantaneamente, mesmo sem internet, e sincroniza sozinho quando a
    // conexão volta (tanto leituras quanto escritas pendentes).

    fun escutarClientes(): Flow<List<Cliente>> = callbackFlow {
        val registration = clientesRef().addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val lista = snap?.documents?.map { doc ->
                doc.toObject(Cliente::class.java)?.copy(id = doc.id) ?: Cliente(id = doc.id)
            }?.sortedBy { it.nome } ?: emptyList()
            trySend(lista)
        }
        awaitClose { registration.remove() }
    }

    // Escritas (salvar/excluir) não usam .await(): o Firestore já grava no cache
    // local na hora (refletido de imediato pelos listeners acima) e sincroniza
    // sozinho quando a conexão voltar. Esperar o Task completar travaria a tela
    // offline, porque esse Task só resolve quando o servidor confirma o
    // recebimento — o que não acontece sem internet.

    suspend fun salvarCliente(cliente: Cliente): String {
        val ref = if (cliente.id.isBlank()) clientesRef().document() else clientesRef().document(cliente.id)
        ref.set(cliente)
        return ref.id
    }

    suspend fun excluirCliente(clienteId: String) {
        // Mesmo motivo do excluirCategoria: sem isso, todas as categorias e
        // movimentações desse cliente ficavam órfãs no banco pra sempre. Apaga de
        // dentro pra fora: movimentações de cada categoria, depois as categorias,
        // só então o cliente.
        val categoriasSnap = try {
            categoriasRef(clienteId).get(Source.CACHE).await()
        } catch (e: Exception) {
            categoriasRef(clienteId).get(Source.SERVER).await()
        }
        for (categoriaDoc in categoriasSnap.documents) {
            val movsSnap = try {
                movimentacoesRef(clienteId, categoriaDoc.id).get(Source.CACHE).await()
            } catch (e: Exception) {
                movimentacoesRef(clienteId, categoriaDoc.id).get(Source.SERVER).await()
            }
            movsSnap.documents.chunked(400).forEach { grupo ->
                val batch = db.batch()
                grupo.forEach { batch.delete(it.reference) }
                batch.commit()
            }
        }
        categoriasSnap.documents.chunked(400).forEach { grupo ->
            val batch = db.batch()
            grupo.forEach { batch.delete(it.reference) }
            batch.commit()
        }
        clientesRef().document(clienteId).delete()
    }

    // ---------- CATEGORIAS ----------
    // Uma "conta" dentro do cliente (ex: "Construção a pagar"), que agrupa várias
    // movimentações do mesmo tipo.

    fun escutarCategorias(clienteId: String): Flow<List<Categoria>> = callbackFlow {
        val registration = categoriasRef(clienteId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val lista = snap?.documents?.map { doc ->
                doc.toObject(Categoria::class.java)?.copy(id = doc.id) ?: Categoria(id = doc.id)
            }?.sortedByDescending { it.dataCriacao } ?: emptyList()
            trySend(lista)
        }
        awaitClose { registration.remove() }
    }

    suspend fun salvarCategoria(clienteId: String, categoria: Categoria): String {
        return if (categoria.id.isBlank()) {
            val ref = categoriasRef(clienteId).document()
            ref.set(categoria.copy(clienteId = clienteId))
            ref.id
        } else {
            categoriasRef(clienteId).document(categoria.id).set(categoria)
            categoria.id
        }
    }

    suspend fun excluirCategoria(clienteId: String, categoriaId: String) {
        // Antes de excluir a categoria, apaga as movimentações dela — senão elas ficam
        // órfãs no banco pra sempre (chão perdido, sem categoria/cliente que aponte pra
        // elas, mas continuam ocupando espaço e nunca são limpas).
        val movsSnap = try {
            movimentacoesRef(clienteId, categoriaId).get(Source.CACHE).await()
        } catch (e: Exception) {
            movimentacoesRef(clienteId, categoriaId).get(Source.SERVER).await()
        }
        movsSnap.documents.chunked(400).forEach { grupo ->
            val batch = db.batch()
            grupo.forEach { batch.delete(it.reference) }
            batch.commit()
        }
        categoriasRef(clienteId).document(categoriaId).delete()
        recalcularSaldoCliente(clienteId)
    }

    // ---------- CARTÕES ----------
    // Configuração global do app (não é por cliente): cartões cadastrados, cada um com
    // seu próprio dia de fechamento ("melhor dia") e dia de vencimento da fatura.

    fun escutarCartoes(): Flow<List<Cartao>> = callbackFlow {
        val registration = cartoesRef().addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val lista = snap?.documents?.map { doc ->
                doc.toObject(Cartao::class.java)?.copy(id = doc.id) ?: Cartao(id = doc.id)
            }?.sortedBy { it.nome } ?: emptyList()
            trySend(lista)
        }
        awaitClose { registration.remove() }
    }

    suspend fun salvarCartao(cartao: Cartao): String {
        return if (cartao.id.isBlank()) {
            val ref = cartoesRef().document()
            ref.set(cartao)
            ref.id
        } else {
            cartoesRef().document(cartao.id).set(cartao)
            cartao.id
        }
    }

    suspend fun excluirCartao(cartaoId: String) {
        cartoesRef().document(cartaoId).delete()
    }

    // ---------- CONFIGURAÇÕES ----------

    fun escutarConfiguracoes(): Flow<Configuracoes> = callbackFlow {
        val registration = configuracoesRef().addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val config = try {
                snap?.toObject(Configuracoes::class.java) ?: Configuracoes()
            } catch (e: Exception) {
                Configuracoes()
            }
            trySend(config)
        }
        awaitClose { registration.remove() }
    }

    suspend fun salvarConfiguracoes(configuracoes: Configuracoes) {
        configuracoesRef().set(configuracoes)
    }

    // ---------- MOVIMENTAÇÕES ----------

    fun escutarMovimentacoes(clienteId: String, categoriaId: String): Flow<List<Movimentacao>> = callbackFlow {
        val registration = movimentacoesRef(clienteId, categoriaId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val lista = snap?.documents?.map { doc ->
                doc.toObject(Movimentacao::class.java)?.copy(id = doc.id) ?: Movimentacao(id = doc.id)
            }?.sortedByDescending { it.dataCriacao } ?: emptyList()
            trySend(lista)
        }
        awaitClose { registration.remove() }
    }

    /** Busca única (usada internamente para recalcular saldos); tenta cache primeiro para funcionar offline. */
    suspend fun listarMovimentacoesUmaVez(clienteId: String, categoriaId: String): List<Movimentacao> {
        val snap = try {
            movimentacoesRef(clienteId, categoriaId).get(Source.CACHE).await()
        } catch (e: Exception) {
            movimentacoesRef(clienteId, categoriaId).get(Source.SERVER).await()
        }
        return snap.documents.map { doc ->
            doc.toObject(Movimentacao::class.java)?.copy(id = doc.id) ?: Movimentacao(id = doc.id)
        }
    }

    suspend fun listarCategoriasUmaVez(clienteId: String): List<Categoria> {
        val snap = try {
            categoriasRef(clienteId).get(Source.CACHE).await()
        } catch (e: Exception) {
            categoriasRef(clienteId).get(Source.SERVER).await()
        }
        return snap.documents.map { doc ->
            doc.toObject(Categoria::class.java)?.copy(id = doc.id) ?: Categoria(id = doc.id)
        }
    }

    /** Cria uma movimentação nova, ou sobrescreve uma existente (edição) se movimentacao.id
     *  já vier preenchido. */
    suspend fun salvarMovimentacao(clienteId: String, categoriaId: String, movimentacao: Movimentacao): String {
        val ref = movimentacoesRef(clienteId, categoriaId)
        val id = if (movimentacao.id.isBlank()) {
            val novaRef = ref.document()
            novaRef.set(movimentacao.copy(clienteId = clienteId, categoriaId = categoriaId))
            novaRef.id
        } else {
            ref.document(movimentacao.id).set(movimentacao)
            movimentacao.id
        }
        recalcularSaldoCategoria(clienteId, categoriaId)
        recalcularSaldoCliente(clienteId)
        return id
    }

    suspend fun excluirMovimentacao(clienteId: String, categoriaId: String, movimentacaoId: String) {
        movimentacoesRef(clienteId, categoriaId).document(movimentacaoId).delete()
        recalcularSaldoCategoria(clienteId, categoriaId)
        recalcularSaldoCliente(clienteId)
    }

    /** Grava vários lançamentos de fluxo de caixa de uma vez, vindos da importação de
     *  CSV. Usa batch (até 400 por vez — o limite do Firestore é 500, deixamos folga)
     *  em vez de uma escrita por vez, bem mais rápido pra listas grandes e funciona
     *  offline igual as outras escritas (fica na fila local e sincroniza depois). */
    suspend fun importarMovimentacoesFluxo(clienteId: String, categoriaId: String, movimentacoes: List<Movimentacao>) {
        val ref = movimentacoesRef(clienteId, categoriaId)
        movimentacoes.chunked(400).forEach { grupo ->
            val batch = db.batch()
            grupo.forEach { mov ->
                batch.set(ref.document(), mov.copy(id = "", clienteId = clienteId, categoriaId = categoriaId))
            }
            batch.commit()
        }
        recalcularSaldoCategoria(clienteId, categoriaId)
        recalcularSaldoCliente(clienteId)
    }

    /** Grava a baixa em todas as movimentações afetadas de uma vez (atômico via batch;
     *  o batch funciona offline também — fica na fila local e sincroniza depois).
     *  Todas as movimentações de uma baixa pertencem à mesma categoria (a tela de baixa
     *  só junta duplicatas dentro de uma categoria). */
    suspend fun aplicarBaixa(clienteId: String, categoriaId: String, movimentacoesAtualizadas: Map<String, Movimentacao>) {
        val batch = db.batch()
        val ref = movimentacoesRef(clienteId, categoriaId)
        movimentacoesAtualizadas.forEach { (id, mov) ->
            batch.set(ref.document(id), mov)
        }
        batch.commit()
        recalcularSaldoCategoria(clienteId, categoriaId)
        recalcularSaldoCliente(clienteId)
    }

    /** Busca uma movimentação específica; tenta cache primeiro para funcionar offline,
     *  igual listarMovimentacoesUmaVez. */
    private suspend fun buscarMovimentacaoUmaVez(clienteId: String, categoriaId: String, movimentacaoId: String): Movimentacao? {
        val ref = movimentacoesRef(clienteId, categoriaId).document(movimentacaoId)
        val snap = try {
            ref.get(Source.CACHE).await()
        } catch (e: Exception) {
            ref.get(Source.SERVER).await()
        }
        return snap.toObject(Movimentacao::class.java)?.copy(id = snap.id)
    }

    /** Exclui uma baixa específica: busca a movimentação, devolve exatamente o valor que
     *  aquela baixa tinha aplicado em cada parcela afetada, e regrava. */
    suspend fun excluirBaixa(clienteId: String, categoriaId: String, movimentacaoId: String, pagamento: Pagamento) {
        val ref = movimentacoesRef(clienteId, categoriaId).document(movimentacaoId)
        val mov = buscarMovimentacaoUmaVez(clienteId, categoriaId, movimentacaoId) ?: return
        val movRevertida = ParcelaUtils.reverterBaixa(mov, pagamento)
        ref.set(movRevertida)
        recalcularSaldoCategoria(clienteId, categoriaId)
        recalcularSaldoCliente(clienteId)
    }

    /** Edita apenas a data/hora de uma baixa já registrada (valores e parcelas afetadas
     *  continuam os mesmos, então não precisa recalcular saldo). */
    suspend fun editarDataBaixa(
        clienteId: String,
        categoriaId: String,
        movimentacaoId: String,
        pagamento: Pagamento,
        novaData: Long
    ) {
        val ref = movimentacoesRef(clienteId, categoriaId).document(movimentacaoId)
        val mov = buscarMovimentacaoUmaVez(clienteId, categoriaId, movimentacaoId) ?: return
        val movEditada = ParcelaUtils.editarDataBaixa(mov, pagamento, novaData)
        ref.set(movEditada)
    }

    /** Igual [excluirBaixa], mas para quando uma baixa foi dividida entre duplicatas de
     *  mais de uma movimentação: reverte todas de uma vez, num único batch atômico —
     *  ou tudo é gravado, ou nada é (se faltar rede/o app fechar no meio, não fica
     *  metade revertida e metade não). */
    suspend fun excluirBaixaGrupo(clienteId: String, categoriaId: String, itens: List<Pair<String, Pagamento>>) {
        val ref = movimentacoesRef(clienteId, categoriaId)
        val batch = db.batch()
        for ((movimentacaoId, pagamento) in itens) {
            val mov = buscarMovimentacaoUmaVez(clienteId, categoriaId, movimentacaoId) ?: continue
            batch.set(ref.document(movimentacaoId), ParcelaUtils.reverterBaixa(mov, pagamento))
        }
        batch.commit()
        recalcularSaldoCategoria(clienteId, categoriaId)
        recalcularSaldoCliente(clienteId)
    }

    /** Igual [editarDataBaixa], mas para quando a baixa foi dividida entre mais de uma
     *  movimentação: edita a data em todas de uma vez, no mesmo batch atômico. */
    suspend fun editarDataBaixaGrupo(clienteId: String, categoriaId: String, itens: List<Pair<String, Pagamento>>, novaData: Long) {
        val ref = movimentacoesRef(clienteId, categoriaId)
        val batch = db.batch()
        for ((movimentacaoId, pagamento) in itens) {
            val mov = buscarMovimentacaoUmaVez(clienteId, categoriaId, movimentacaoId) ?: continue
            batch.set(ref.document(movimentacaoId), ParcelaUtils.editarDataBaixa(mov, pagamento, novaData))
        }
        batch.commit()
    }

    /** Recalcula e grava o total em aberto denormalizado da categoria, somando
     *  todas as movimentações dela. */
    suspend fun recalcularSaldoCategoria(clienteId: String, categoriaId: String) {
        val movimentacoes = listarMovimentacoesUmaVez(clienteId, categoriaId)
        val totalAberto = movimentacoes.sumOf { it.valorEmAberto }
        categoriasRef(clienteId).document(categoriaId).update("totalAberto", totalAberto)
    }

    /** Recalcula e grava o saldo denormalizado do cliente = total a receber em aberto
     *  menos total a pagar em aberto, somando as movimentações de TODAS as categorias
     *  dele. Também grava os dois totais separados, usados na tela de visão geral. */
    suspend fun recalcularSaldoCliente(clienteId: String) {
        val categorias = listarCategoriasUmaVez(clienteId)
        var aReceber = 0.0
        var aPagar = 0.0
        for (categoria in categorias) {
            val movimentacoes = listarMovimentacoesUmaVez(clienteId, categoria.id)
            val totalAberto = movimentacoes.sumOf { it.valorEmAberto }
            if (categoria.tipo == TipoMovimentacao.RECEBER) aReceber += totalAberto else aPagar += totalAberto
        }
        val saldo = aReceber - aPagar
        clientesRef().document(clienteId).update(
            mapOf(
                "saldo" to saldo,
                "totalAReceber" to aReceber,
                "totalAPagar" to aPagar
            )
        )
    }

    // ---------- LISTAS DE COMPRAS ----------
    // Cada pessoa pode ter várias listas. Cada lista já nasce com um código curto (o
    // próprio id do documento) e uma lista de membros (uids) com acesso — o dono
    // sempre está nela; se mais alguém entrar com o código, a lista passa a ser
    // compartilhada entre os dois automaticamente, sem precisar de nada especial.

    /** Todas as listas que o usuário atual tem acesso (criou ou entrou via código). */
    fun escutarListas(): Flow<List<Lista>> = callbackFlow {
        val registration = listasRef()
            .whereArrayContains("membros", uid())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val lista = snap?.documents?.map { doc ->
                    doc.toObject(Lista::class.java)?.copy(id = doc.id) ?: Lista(id = doc.id)
                }?.sortedByDescending { it.criadoEm } ?: emptyList()
                trySend(lista)
            }
        awaitClose { registration.remove() }
    }

    /** Cria uma lista nova (pessoal, até alguém entrar nela via código) e devolve o
     *  código gerado. */
    suspend fun criarLista(nome: String): String {
        val caracteres = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sem 0/O/1/I pra não confundir
        val codigo = (1..6).map { caracteres.random() }.joinToString("")
        val lista = Lista(id = codigo, nome = nome, membros = listOf(uid()), criadoPor = uid())
        // Não usa .await() aqui: o set() já grava no cache local na hora (e a tela
        // atualiza na mesma hora via escutarListas, que também lê do cache local), e o
        // Firestore sincroniza sozinho quando a conexão voltar. Se esperássemos o Task
        // completar, ficaríamos travados offline — esse Task só resolve quando o
        // servidor confirma o recebimento, o que não acontece sem internet.
        listasRef().document(codigo).set(lista)
        return codigo
    }

    suspend fun renomearLista(listaId: String, novoNome: String) {
        // Mesmo motivo do criarLista: não espera confirmação do servidor.
        listasRef().document(listaId).update("nome", novoNome)
    }

    /** Exclui a lista inteira e todos os itens dela. Qualquer membro pode excluir
     *  (é uma lista simples de compras, não precisa de dono "dono absoluto"). */
    suspend fun excluirLista(listaId: String) {
        val itensSnap = try {
            itensRef(listaId).get(Source.CACHE).await()
        } catch (e: Exception) {
            itensRef(listaId).get(Source.SERVER).await()
        }
        val batch = db.batch()
        itensSnap.documents.forEach { batch.delete(it.reference) }
        batch.commit()
        listasRef().document(listaId).delete()
    }

    /** Entra numa lista existente a partir de um código recebido de outra pessoa.
     *  Isso exige conexão de verdade (não dá pra confirmar que um código existe usando
     *  só o cache local, já que a lista de outra pessoa nunca esteve nesse aparelho).
     *  Lança erro se o código não existir. */
    suspend fun entrarLista(codigo: String) {
        val ref = listasRef().document(codigo)
        val snap = ref.get(Source.SERVER).await()
        if (!snap.exists()) error("Código não encontrado. Confira se digitou certinho.")
        ref.update("membros", com.google.firebase.firestore.FieldValue.arrayUnion(uid()))
    }

    /** Sai de uma lista (deixa de ver/editar) sem apagar ela pros outros membros. */
    suspend fun sairLista(listaId: String) {
        listasRef().document(listaId)
            .update("membros", com.google.firebase.firestore.FieldValue.arrayRemove(uid()))
    }

    // ---------- ITENS DE UMA LISTA ----------

    fun escutarItensLista(listaId: String): Flow<List<ItemLista>> = callbackFlow {
        val registration = itensRef(listaId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val lista = snap?.documents?.map { doc ->
                doc.toObject(ItemLista::class.java)?.copy(id = doc.id) ?: ItemLista(id = doc.id)
            }?.sortedByDescending { it.dataCriacao } ?: emptyList()
            trySend(lista)
        }
        awaitClose { registration.remove() }
    }

    suspend fun salvarItemLista(listaId: String, item: ItemLista): String {
        // O id do documento é gerado localmente (document() já gera um id novo sem
        // precisar de rede) e o set() grava no cache local na hora — por isso não
        // esperamos o Task com .await(): ele só resolveria depois do servidor
        // confirmar, e offline isso nunca chega a acontecer, travando a tela.
        val ref = if (item.id.isBlank()) itensRef(listaId).document() else itensRef(listaId).document(item.id)
        ref.set(item)
        return ref.id
    }

    suspend fun excluirItemLista(listaId: String, itemId: String) {
        itensRef(listaId).document(itemId).delete()
    }

    /** Marca/desmarca um item como comprado, preenchendo (ou limpando) a data automaticamente. */
    suspend fun marcarItemListaComprado(listaId: String, itemId: String, comprado: Boolean) {
        itensRef(listaId).document(itemId).update(
            mapOf(
                "comprado" to comprado,
                "dataComprado" to if (comprado) System.currentTimeMillis() else null
            )
        )
    }

    fun usuarioLogado() = auth.currentUser
}

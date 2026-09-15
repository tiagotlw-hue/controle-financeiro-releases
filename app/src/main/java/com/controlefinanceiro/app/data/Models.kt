package com.controlefinanceiro.app.data

/**
 * Modelos de dados salvos no Firestore em:
 * usuarios/{uid}/clientes/{clienteId}
 * usuarios/{uid}/clientes/{clienteId}/movimentacoes/{movimentacaoId}
 */

object TipoMovimentacao {
    const val RECEBER = "receber" // cliente me deve (eu vou receber)
    const val PAGAR = "pagar"     // eu devo ao cliente (eu vou pagar)
    // Categoria "fluxo de caixa": cada movimentação já entra quitada na hora de criar
    // (recebida ou paga, sem duplicata em aberto e sem precisar dar baixa depois) —
    // um registro simples de entrada/saída, tipo um livro caixa. O tipo de cada
    // movimentação dentro dela é escolhido individualmente (RECEBER ou PAGAR), já que
    // uma categoria de fluxo mistura os dois.
    const val FLUXO = "fluxo"
}

object FormaPagamento {
    const val DINHEIRO = "dinheiro"
    const val CARTAO = "cartao"
}

data class Cliente(
    val id: String = "",
    val nome: String = "",
    val telefone: String = "",
    val email: String = "",
    val endereco: String = "",
    // saldo denormalizado (a receber em aberto - a pagar em aberto), atualizado
    // sempre que uma movimentação é criada, editada, excluída ou tem baixa registrada.
    // Guardado no próprio cliente para a lista de clientes e a visão geral carregarem
    // rápido e funcionarem offline, sem precisar buscar as movimentações de cada cliente.
    val saldo: Double = 0.0,
    val totalAReceber: Double = 0.0,
    val totalAPagar: Double = 0.0
)

/** Uma "conta" dentro do cliente, tipo "Construção a pagar" ou "Empréstimo a receber".
 *  Agrupa várias movimentações (cobranças parceladas) do mesmo tipo. O tipo é fixado
 *  na criação da categoria e vale para todas as movimentações criadas dentro dela. */
data class Categoria(
    val id: String = "",
    val clienteId: String = "",
    val nome: String = "",
    val tipo: String = TipoMovimentacao.RECEBER, // "receber" | "pagar"
    val dataCriacao: Long = System.currentTimeMillis(),
    // total em aberto somando todas as movimentações desta categoria; denormalizado
    // pelo mesmo motivo do saldo do cliente (lista rápida, funciona offline).
    val totalAberto: Double = 0.0
)

/** Um cartão de crédito cadastrado (configuração global do app, não por cliente).
 *  "melhorDia" é o dia de fechamento da fatura; "diaVencimento" é o dia em que a
 *  fatura vence. Usado para calcular automaticamente o vencimento das duplicatas
 *  quando a movimentação é paga no cartão. */
data class Cartao(
    val id: String = "",
    val nome: String = "",
    val melhorDia: Int = 1,
    val diaVencimento: Int = 10
)

/** Preferências gerais do app: forma de pagamento padrão (dinheiro/cartão) que já vem
 *  pré-selecionada ao criar uma nova movimentação, e qual cartão vem pré-selecionado
 *  quando o padrão é cartão. Documento único por usuário. */
data class Configuracoes(
    val formaPagamentoPadrao: String = FormaPagamento.DINHEIRO,
    val cartaoPadraoId: String = "",
    val confirmarSaida: Boolean = true,
    val atualizacaoAutomatica: Boolean = true,
    val unidadePadraoLista: String = "UN",              // já vem selecionada ao adicionar um item novo
    val unidadesPersonalizadas: List<String> = emptyList() // unidades extras que a pessoa cadastrou
)

/** Unidades comuns já disponíveis pra escolher direto, sem precisar digitar. A pessoa
 *  ainda pode adicionar outras próprias (guardadas em Configuracoes.unidadesPersonalizadas). */
object UnidadesLista {
    val PADRAO = listOf("UN", "KG", "G", "L", "ML", "CX", "PCT", "DZ")
}

data class Parcela(
    val numero: Int = 0,
    val valorOriginal: Double = 0.0,
    val valorPago: Double = 0.0,
    val dataVencimento: Long = 0L,
    val status: String = "aberta", // aberta | parcial | paga
    val dataUltimoPagamento: Long? = null
)

data class Pagamento(
    val id: String = "",
    val data: Long = System.currentTimeMillis(),
    val valorPago: Double = 0.0,
    val parcelasNumeros: List<Int> = emptyList(),
    // quanto dessa baixa foi aplicado em cada parcela, chave = número da parcela em texto
    // (Firestore não lida bem com Map de chave inteira). Necessário para poder excluir
    // uma baixa depois e devolver exatamente o valor certo pra cada parcela afetada.
    val parcelasAplicadas: Map<String, Double> = emptyMap(),
    val descricao: String = "",
    // Identifica a "transação" de baixa que originou este registro. Quando uma baixa
    // é dividida entre duplicatas de mais de uma movimentação (ex: R$ 200 cobrindo uma
    // duplicata de R$ 120 de uma movimentação e uma de R$ 80 de outra), cada movimentação
    // afetada recebe seu próprio Pagamento, mas todos compartilham o mesmo baixaId — isso
    // permite juntar tudo numa única linha no extrato em vez de mostrar separado.
    val baixaId: String = ""
)

data class Movimentacao(
    val id: String = "",
    val clienteId: String = "",
    val categoriaId: String = "",
    val tipo: String = TipoMovimentacao.RECEBER, // "receber" | "pagar"
    val descricao: String = "",
    val valorTotal: Double = 0.0,
    val dataCriacao: Long = System.currentTimeMillis(),
    val numParcelas: Int = 1,
    val diaVencimento: Int = 15,
    val formaPagamento: String = FormaPagamento.DINHEIRO, // "dinheiro" | "cartao"
    val cartaoId: String = "", // preenchido quando formaPagamento == cartao
    val parcelas: List<Parcela> = emptyList(),
    val historicoPagamentos: List<Pagamento> = emptyList()
) {
    val valorPagoTotal: Double get() = parcelas.sumOf { it.valorPago }
    val valorEmAberto: Double get() = valorTotal - valorPagoTotal
    val quitada: Boolean get() = parcelas.isNotEmpty() && parcelas.all { it.status == "paga" }
    // só permite editar/excluir a movimentação inteira enquanto nenhuma baixa foi feita
    val podeEditarOuExcluir: Boolean get() = valorPagoTotal <= 0.0
}

/** Referência a uma parcela específica dentro de uma movimentação, usada na tela de baixa (Saída),
 *  que junta parcelas em aberto de várias movimentações do mesmo tipo para o mesmo cliente. */
data class ParcelaRef(
    val movimentacaoId: String,
    val movimentacaoDescricao: String,
    val movimentacaoNumParcelas: Int = 1,
    val parcela: Parcela
)

/** Um item da lista de compras (independente de clientes/pagar/receber — é uma lista
 *  pessoal do usuário, tipo "coisas que preciso comprar"). */
data class ItemLista(
    val id: String = "",
    val descricao: String = "",           // obrigatório: o que precisa comprar
    val observacao: String = "",          // opcional
    val quantidade: Double = 1.0,         // ex: 2 (unidades), 1.5 (kg), etc.
    val unidade: String = "",             // opcional: "kg", "un", "cx", "L"...
    val valorLimite: Double? = null,      // opcional: até quanto vale a pena pagar
    val comprado: Boolean = false,
    val dataCriacao: Long = System.currentTimeMillis(),
    val dataComprado: Long? = null        // preenchido quando marca como comprado
)

/** Uma lista de compras. Toda lista já nasce com um código curto (o próprio id) —
 *  se `membros` tiver só o dono, é uma lista "pessoal" normal; se tiver mais gente,
 *  é uma lista compartilhada. Uma pessoa pode ter várias listas (ex: "Mercado do mês",
 *  "Material de construção"), compartilhando algumas e mantendo outras só pra ela. */
data class Lista(
    val id: String = "",                  // código curto (ex: "AB3K9F")
    val nome: String = "",
    val membros: List<String> = emptyList(), // uids de quem tem acesso (dono incluso)
    val criadoPor: String = "",
    val criadoEm: Long = System.currentTimeMillis()
)

package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.ItemLista
import com.controlefinanceiro.app.data.Lista
import com.controlefinanceiro.app.data.UnidadesLista
import com.controlefinanceiro.app.data.formatarQuantidadeLista
import com.controlefinanceiro.app.viewmodel.AppViewModel
import java.util.Locale
import com.controlefinanceiro.app.data.formatarData
import com.controlefinanceiro.app.data.parsearValorMonetario



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaDetalheScreen(
    viewModel: AppViewModel,
    lista: Lista,
    aoVoltar: () -> Unit
) {
    val itens by viewModel.listaCompras
    val listasAtualizadas by viewModel.listas
    val configuracoes by viewModel.configuracoes
    // Pega a versão mais atual da lista (nome/membros podem ter mudado desde que a
    // tela foi aberta, ex: alguém entrou com o código enquanto você estava aqui).
    val listaAtual = listasAtualizadas.firstOrNull { it.id == lista.id } ?: lista
    val unidadesDisponiveis = remember(configuracoes.unidadesPersonalizadas) {
        (UnidadesLista.PADRAO + configuracoes.unidadesPersonalizadas).distinct()
    }

    var mostrarComprados by remember { mutableStateOf(false) }
    var itemParaEditar by remember { mutableStateOf<ItemLista?>(null) }
    var mostrarFormNovo by remember { mutableStateOf(false) }
    var itemParaExcluir by remember { mutableStateOf<ItemLista?>(null) }
    var mostrarDialogoCompartilhar by remember { mutableStateOf(false) }
    var mostrarDialogoRenomear by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val erro = viewModel.erro.value

    LaunchedEffect(lista.id) { viewModel.observarItensLista(lista.id) }
    LaunchedEffect(Unit) { viewModel.iniciarEscutaConfiguracoes() }

    LaunchedEffect(erro) {
        if (erro != null) {
            snackbarHostState.showSnackbar(erro)
            viewModel.limparErro()
        }
    }

    val itensVisiveis = if (mostrarComprados) itens else itens.filterNot { it.comprado }
    val qtdComprados = itens.count { it.comprado }
    val compartilhada = listaAtual.membros.size > 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(listaAtual.nome)
                        if (compartilhada) {
                            Text(
                                "Compartilhada · ${listaAtual.id}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { mostrarDialogoRenomear = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar nome da lista")
                    }
                    IconButton(onClick = { mostrarDialogoCompartilhar = true }) {
                        Icon(
                            Icons.Default.GroupAdd,
                            contentDescription = if (compartilhada) "Gerenciar compartilhamento" else "Compartilhar lista"
                        )
                    }
                    IconButton(onClick = { mostrarComprados = !mostrarComprados }) {
                        Icon(
                            if (mostrarComprados) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (mostrarComprados)
                                "Ocultar já comprados"
                            else
                                "Mostrar já comprados"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { mostrarFormNovo = true }) {
                Icon(Icons.Default.Add, contentDescription = "Novo item")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!mostrarComprados && qtdComprados > 0) {
                Text(
                    "$qtdComprados já comprado${if (qtdComprados > 1) "s" else ""} oculto${if (qtdComprados > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            if (itensVisiveis.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (itens.isEmpty())
                            "Nenhum item na lista ainda. Toque em + para adicionar."
                        else
                            "Tudo comprado! 🎉",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(itensVisiveis, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { itemParaEditar = item }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.comprado,
                                onCheckedChange = { marcado ->
                                    viewModel.marcarItemListaComprado(lista.id, item.id, marcado)
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (item.quantidade != 1.0 || item.unidade.isNotBlank())
                                        "${formatarQuantidadeLista(item.quantidade)}${if (item.unidade.isNotBlank()) " ${item.unidade}" else "x"} ${item.descricao}"
                                    else
                                        item.descricao,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (item.comprado) TextDecoration.LineThrough else null,
                                    color = if (item.comprado)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                if (item.observacao.isNotBlank()) {
                                    Text(
                                        item.observacao,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    if (item.comprado && item.dataComprado != null)
                                        "Registrado ${formatarData(item.dataCriacao)} · comprado ${formatarData(item.dataComprado)}"
                                    else
                                        "Registrado ${formatarData(item.dataCriacao)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.valorLimite != null) {
                                Text(
                                    "até R$ %.2f".format(Locale("pt", "BR"), item.valorLimite),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            IconButton(onClick = { itemParaExcluir = item }) {
                                Icon(Icons.Default.Delete, contentDescription = "Excluir item")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (mostrarFormNovo) {
        FormItemListaDialog(
            item = null,
            unidadesDisponiveis = unidadesDisponiveis,
            unidadePadrao = configuracoes.unidadePadraoLista,
            aoAdicionarUnidade = { nova ->
                viewModel.salvarConfiguracoes(
                    configuracoes.copy(unidadesPersonalizadas = (configuracoes.unidadesPersonalizadas + nova).distinct())
                )
            },
            onDismiss = { mostrarFormNovo = false },
            onSalvar = { descricao, observacao, quantidade, unidade, valorLimite ->
                viewModel.salvarItemLista(lista.id, descricao, observacao, quantidade, unidade, valorLimite) {
                    mostrarFormNovo = false
                }
            }
        )
    }

    itemParaEditar?.let { item ->
        FormItemListaDialog(
            item = item,
            unidadesDisponiveis = unidadesDisponiveis,
            unidadePadrao = configuracoes.unidadePadraoLista,
            aoAdicionarUnidade = { nova ->
                viewModel.salvarConfiguracoes(
                    configuracoes.copy(unidadesPersonalizadas = (configuracoes.unidadesPersonalizadas + nova).distinct())
                )
            },
            onDismiss = { itemParaEditar = null },
            onSalvar = { descricao, observacao, quantidade, unidade, valorLimite ->
                viewModel.editarItemLista(lista.id, item, descricao, observacao, quantidade, unidade, valorLimite) {
                    itemParaEditar = null
                }
            }
        )
    }

    itemParaExcluir?.let { item ->
        AlertDialog(
            onDismissRequest = { itemParaExcluir = null },
            title = { Text("Excluir item") },
            text = { Text("Tem certeza que deseja excluir \"${item.descricao}\" da lista?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.excluirItemLista(lista.id, item.id)
                    itemParaExcluir = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { itemParaExcluir = null }) { Text("Cancelar") }
            }
        )
    }

    if (mostrarDialogoCompartilhar) {
        DialogoCompartilharLista(
            codigo = listaAtual.id,
            onDismiss = { mostrarDialogoCompartilhar = false }
        )
    }

    if (mostrarDialogoRenomear) {
        var nome by remember { mutableStateOf(listaAtual.nome) }
        var tentouSalvar by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { mostrarDialogoRenomear = false },
            title = { Text("Editar nome da lista") },
            text = {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome da lista *") },
                    isError = tentouSalvar && nome.isBlank(),
                    supportingText = { if (tentouSalvar && nome.isBlank()) Text("Campo obrigatório") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    tentouSalvar = true
                    if (nome.isNotBlank()) {
                        viewModel.renomearLista(lista.id, nome.trim()) { mostrarDialogoRenomear = false }
                    }
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoRenomear = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun DialogoCompartilharLista(
    codigo: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartilhar lista") },
        text = {
            Column {
                Text(
                    "Passe esse código pra outras pessoas — quem digitar ele em " +
                        "\"Entrar numa lista\" passa a ver e editar os mesmos itens:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        codigo,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(codigo)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar código")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
private fun FormItemListaDialog(
    item: ItemLista?,
    unidadesDisponiveis: List<String>,
    unidadePadrao: String,
    aoAdicionarUnidade: (String) -> Unit,
    onDismiss: () -> Unit,
    onSalvar: (descricao: String, observacao: String, quantidade: Double, unidade: String, valorLimite: Double?) -> Unit
) {
    var descricao by remember { mutableStateOf(item?.descricao ?: "") }
    var observacao by remember { mutableStateOf(item?.observacao ?: "") }
    var quantidadeTexto by remember {
        mutableStateOf(formatarQuantidadeLista(item?.quantidade ?: 1.0))
    }
    // Item novo já vem com a unidade padrão selecionada; ao editar um item já
    // existente, mantém a unidade que ele já tinha (com fallback pra padrão se por
    // acaso estiver vazia, ex: itens criados antes dessa mudança).
    var unidade by remember { mutableStateOf(item?.unidade?.takeIf { it.isNotBlank() } ?: unidadePadrao) }
    var expandirUnidade by remember { mutableStateOf(false) }
    var mostrarNovaUnidade by remember { mutableStateOf(false) }
    var valorLimiteTexto by remember {
        mutableStateOf(item?.valorLimite?.let { "%.2f".format(Locale("pt", "BR"), it).replace(".", ",") } ?: "")
    }
    var tentouSalvar by remember { mutableStateOf(false) }
    val descricaoInvalida = tentouSalvar && descricao.isBlank()
    val quantidadeInvalida = tentouSalvar &&
        (parsearValorMonetario(quantidadeTexto) ?: 0.0) <= 0.0
    // Pra avisar antes de descartar algo preenchido/alterado sem querer: compara com
    // os valores de quando o formulário abriu (branco pra item novo, ou os do item
    // já existente no caso de edição).
    val observacaoInicial = item?.observacao ?: ""
    val quantidadeInicial = formatarQuantidadeLista(item?.quantidade ?: 1.0)
    val unidadeInicial = item?.unidade?.takeIf { it.isNotBlank() } ?: unidadePadrao
    val valorLimiteInicial = item?.valorLimite?.let { "%.2f".format(Locale("pt", "BR"), it).replace(".", ",") } ?: ""
    var mostrarConfirmarDescarte by remember { mutableStateOf(false) }
    val houveAlteracao = descricao != (item?.descricao ?: "") || observacao != observacaoInicial ||
        quantidadeTexto != quantidadeInicial || unidade != unidadeInicial || valorLimiteTexto != valorLimiteInicial
    val tentarFechar: () -> Unit = { if (houveAlteracao) mostrarConfirmarDescarte = true else onDismiss() }

    AlertDialog(
        onDismissRequest = tentarFechar,
        title = { Text(if (item == null) "Novo item" else "Editar item") },
        text = {
            Column {
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("O que precisa comprar *") },
                    isError = descricaoInvalida,
                    supportingText = { if (descricaoInvalida) Text("Campo obrigatório") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = quantidadeTexto,
                        onValueChange = { quantidadeTexto = it },
                        label = { Text("Quantidade *") },
                        isError = quantidadeInvalida,
                        supportingText = { if (quantidadeInvalida) Text("Deve ser maior que zero") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandirUnidade,
                        onExpandedChange = { expandirUnidade = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = unidade,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unidade") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirUnidade) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandirUnidade,
                            onDismissRequest = { expandirUnidade = false }
                        ) {
                            unidadesDisponiveis.forEach { opcao ->
                                DropdownMenuItem(
                                    text = { Text(opcao) },
                                    onClick = {
                                        unidade = opcao
                                        expandirUnidade = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ Adicionar unidade") },
                                onClick = {
                                    expandirUnidade = false
                                    mostrarNovaUnidade = true
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = observacao,
                    onValueChange = { observacao = it },
                    label = { Text("Observação") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = valorLimiteTexto,
                    onValueChange = { valorLimiteTexto = it },
                    label = { Text("Valor limite") },
                    prefix = { Text("R$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tentouSalvar = true
                val quantidade = parsearValorMonetario(quantidadeTexto) ?: 0.0
                if (descricao.isNotBlank() && quantidade > 0.0) {
                    val valorLimite = parsearValorMonetario(valorLimiteTexto)
                    onSalvar(descricao.trim(), observacao.trim(), quantidade, unidade.trim(), valorLimite)
                }
            }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = tentarFechar) { Text("Cancelar") }
        }
    )

    if (mostrarConfirmarDescarte) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmarDescarte = false },
            title = { Text("Descartar alterações?") },
            text = { Text("Você preencheu ou mudou algo que ainda não foi salvo. Se sair agora, vai perder o que digitou.") },
            confirmButton = {
                TextButton(onClick = { mostrarConfirmarDescarte = false; onDismiss() }) { Text("Descartar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmarDescarte = false }) { Text("Continuar editando") }
            }
        )
    }

    if (mostrarNovaUnidade) {
        var novaUnidade by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mostrarNovaUnidade = false },
            title = { Text("Adicionar unidade") },
            text = {
                OutlinedTextField(
                    value = novaUnidade,
                    onValueChange = { novaUnidade = it.uppercase() },
                    label = { Text("Ex: SC, FD, ROLO") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nova = novaUnidade.trim()
                        if (nova.isNotBlank()) {
                            aoAdicionarUnidade(nova)
                            unidade = nova
                        }
                        mostrarNovaUnidade = false
                    },
                    enabled = novaUnidade.isNotBlank()
                ) { Text("Adicionar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarNovaUnidade = false }) { Text("Cancelar") }
            }
        )
    }
}

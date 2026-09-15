package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.FormaPagamento
import com.controlefinanceiro.app.data.InfoAtualizacao
import com.controlefinanceiro.app.data.UnidadesLista
import com.controlefinanceiro.app.data.Versao
import com.controlefinanceiro.app.data.baixarEInstalarAtualizacao
import com.controlefinanceiro.app.data.verificarNovaVersao
import com.controlefinanceiro.app.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesPreferenciasScreen(
    viewModel: AppViewModel,
    aoVoltar: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.iniciarEscutaCartoes()
        viewModel.iniciarEscutaConfiguracoes()
    }
    val cartoes by viewModel.cartoes
    val configuracoes by viewModel.configuracoes
    val snackbarHostState = remember { SnackbarHostState() }
    val escopo = rememberCoroutineScope()

    var expandirDropdownCartaoPadrao by remember { mutableStateOf(false) }
    var expandirDropdownUnidadePadrao by remember { mutableStateOf(false) }
    var verificandoAtualizacao by remember { mutableStateOf(false) }
    var infoAtualizacaoManual by remember { mutableStateOf<InfoAtualizacao?>(null) }
    val cartaoPadraoAtual = cartoes.firstOrNull { it.id == configuracoes.cartaoPadraoId }
    val unidadesDisponiveis = remember(configuracoes.unidadesPersonalizadas) {
        (UnidadesLista.PADRAO + configuracoes.unidadesPersonalizadas).distinct()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preferências") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Forma de pagamento padrão", style = MaterialTheme.typography.labelLarge)
            Text(
                "Já vem selecionada ao criar uma nova cobrança (pode trocar na hora).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = configuracoes.formaPagamentoPadrao == FormaPagamento.DINHEIRO,
                    onClick = {
                        viewModel.salvarConfiguracoes(configuracoes.copy(formaPagamentoPadrao = FormaPagamento.DINHEIRO))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Dinheiro") }
                SegmentedButton(
                    selected = configuracoes.formaPagamentoPadrao == FormaPagamento.CARTAO,
                    onClick = {
                        viewModel.salvarConfiguracoes(configuracoes.copy(formaPagamentoPadrao = FormaPagamento.CARTAO))
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Cartão") }
            }

            if (configuracoes.formaPagamentoPadrao == FormaPagamento.CARTAO) {
                Spacer(Modifier.height(16.dp))
                Text("Cartão padrão", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                if (cartoes.isEmpty()) {
                    Text(
                        "Nenhum cartão cadastrado ainda. Cadastre em Configurações > Cartões.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expandirDropdownCartaoPadrao,
                        onExpandedChange = { expandirDropdownCartaoPadrao = it }
                    ) {
                        OutlinedTextField(
                            value = cartaoPadraoAtual?.nome ?: "Selecione um cartão",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirDropdownCartaoPadrao) }
                        )
                        ExposedDropdownMenu(
                            expanded = expandirDropdownCartaoPadrao,
                            onDismissRequest = { expandirDropdownCartaoPadrao = false }
                        ) {
                            cartoes.forEach { cartao ->
                                DropdownMenuItem(
                                    text = { Text(cartao.nome) },
                                    onClick = {
                                        viewModel.salvarConfiguracoes(configuracoes.copy(cartaoPadraoId = cartao.id))
                                        expandirDropdownCartaoPadrao = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Unidade padrão nas listas de compras", style = MaterialTheme.typography.labelLarge)
            Text(
                "Já vem selecionada ao adicionar um item novo numa lista (pode trocar na hora).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expandirDropdownUnidadePadrao,
                onExpandedChange = { expandirDropdownUnidadePadrao = it }
            ) {
                OutlinedTextField(
                    value = configuracoes.unidadePadraoLista,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandirDropdownUnidadePadrao) }
                )
                ExposedDropdownMenu(
                    expanded = expandirDropdownUnidadePadrao,
                    onDismissRequest = { expandirDropdownUnidadePadrao = false }
                ) {
                    unidadesDisponiveis.forEach { opcao ->
                        DropdownMenuItem(
                            text = { Text(opcao) },
                            onClick = {
                                viewModel.salvarConfiguracoes(configuracoes.copy(unidadePadraoLista = opcao))
                                expandirDropdownUnidadePadrao = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.salvarConfiguracoes(configuracoes.copy(confirmarSaida = !configuracoes.confirmarSaida))
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Confirmar saída", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Pede confirmação antes de fechar o app pelo botão/gesto de voltar do celular.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = configuracoes.confirmarSaida,
                    onCheckedChange = { viewModel.salvarConfiguracoes(configuracoes.copy(confirmarSaida = it)) }
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.salvarConfiguracoes(configuracoes.copy(atualizacaoAutomatica = !configuracoes.atualizacaoAutomatica))
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Atualização automática", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Checa sozinho, ao abrir o app, se tem uma versão nova disponível.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = configuracoes.atualizacaoAutomatica,
                    onCheckedChange = { viewModel.salvarConfiguracoes(configuracoes.copy(atualizacaoAutomatica = it)) }
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    verificandoAtualizacao = true
                    escopo.launch {
                        val resultado = withContext(Dispatchers.IO) { verificarNovaVersao() }
                        verificandoAtualizacao = false
                        if (resultado != null) {
                            infoAtualizacaoManual = resultado
                        } else {
                            snackbarHostState.showSnackbar("Você já está usando a versão mais recente (${Versao.NOME}).")
                        }
                    }
                },
                enabled = !verificandoAtualizacao,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (verificandoAtualizacao) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Verificar atualização agora")
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Versão do app: ${Versao.NOME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    infoAtualizacaoManual?.let { info ->
        val context = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { infoAtualizacaoManual = null },
            title = { Text("Nova versão disponível") },
            text = {
                Text("Tem uma versão nova do app (${info.nome}). Você está usando a ${Versao.NOME}. Quer atualizar agora?")
            },
            confirmButton = {
                TextButton(onClick = {
                    infoAtualizacaoManual = null
                    if (info.urlDownload.isNotBlank()) {
                        baixarEInstalarAtualizacao(context, info.urlDownload, info.nome)
                    }
                }) { Text("Atualizar") }
            },
            dismissButton = {
                TextButton(onClick = { infoAtualizacaoManual = null }) { Text("Agora não") }
            }
        )
    }
}

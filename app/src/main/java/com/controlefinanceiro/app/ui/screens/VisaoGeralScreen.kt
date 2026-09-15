package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Cliente
import com.controlefinanceiro.app.ui.components.IconeDirecaoSaldo
import com.controlefinanceiro.app.viewmodel.AppViewModel
import java.util.Locale
import com.controlefinanceiro.app.ui.theme.VerdeSaldo
import com.controlefinanceiro.app.ui.theme.VermelhoSaldo
import com.controlefinanceiro.app.data.formatarMoeda

/** Visão geral: junta o saldo já denormalizado em cada cliente (não faz busca nova,
 *  então funciona instantâneo e offline também). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisaoGeralScreen(
    viewModel: AppViewModel,
    aoAbrirCliente: (Cliente) -> Unit,
    aoVoltar: () -> Unit
) {
    val clientes by viewModel.clientes
    val totalReceber = clientes.sumOf { it.totalAReceber }
    val totalPagar = clientes.sumOf { it.totalAPagar }
    val saldoGeral = totalReceber - totalPagar

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Visão geral") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Saldo geral (todos os clientes)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        formatarMoeda(saldoGeral),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (saldoGeral >= 0) VerdeSaldo else VermelhoSaldo
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("A receber: R$ %.2f".format(Locale("pt", "BR"), totalReceber), color = VerdeSaldo, style = MaterialTheme.typography.bodyMedium)
                        Text("A pagar: R$ %.2f".format(Locale("pt", "BR"), totalPagar), color = VermelhoSaldo, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text(
                "Por cliente",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(4.dp))

            if (clientes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Nenhum cliente cadastrado ainda.")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(clientes, key = { it.id }) { cliente ->
                        ListItem(
                            headlineContent = { Text(cliente.nome) },
                            supportingContent = {
                                Text(
                                    "A receber: R$ %.2f  •  A pagar: R$ %.2f".format(Locale("pt", "BR"), cliente.totalAReceber, cliente.totalAPagar)
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    IconeDirecaoSaldo(positivo = cliente.saldo >= 0)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        formatarMoeda(cliente.saldo),
                                        color = if (cliente.saldo >= 0) VerdeSaldo else VermelhoSaldo,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            },
                            modifier = Modifier.clickable { aoAbrirCliente(cliente) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

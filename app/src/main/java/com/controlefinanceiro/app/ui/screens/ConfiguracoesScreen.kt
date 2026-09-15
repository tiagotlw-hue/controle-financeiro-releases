package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.Versao

/**
 * Tela de "Configurações" reorganizada como um menu: cada opção abre sua
 * própria tela (Preferências, Cartões, Conta). Ver ConfiguracoesPreferenciasScreen.kt,
 * ConfiguracoesCartoesScreen.kt e ConfiguracoesContaScreen.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesScreen(
    aoVoltar: () -> Unit,
    aoAbrirPreferencias: () -> Unit,
    aoAbrirCartoes: () -> Unit,
    aoAbrirConta: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ItemMenuConfiguracoes(
                icone = Icons.Default.Tune,
                titulo = "Preferências",
                subtitulo = "Forma de pagamento padrão, confirmação de saída, atualizações",
                onClick = aoAbrirPreferencias
            )
            HorizontalDivider()
            ItemMenuConfiguracoes(
                icone = Icons.Default.CreditCard,
                titulo = "Cartões",
                subtitulo = "Cadastrar, editar e excluir cartões",
                onClick = aoAbrirCartoes
            )
            HorizontalDivider()
            ItemMenuConfiguracoes(
                icone = Icons.Default.Person,
                titulo = "Conta",
                subtitulo = "Seu perfil, senha de acesso e sair da conta",
                onClick = aoAbrirConta
            )
            HorizontalDivider()

            Spacer(Modifier.weight(1f))
            Text(
                "Versão do app: ${Versao.NOME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun ItemMenuConfiguracoes(
    icone: ImageVector,
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

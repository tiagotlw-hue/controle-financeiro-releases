package com.controlefinanceiro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Verde como cor de marca do app (remete a "no verde"/dinheiro em caixa), com o
// resto do esquema pensado junto em vez de deixar o Material3 preencher sozinho
// com o roxo padrão dele. "secondary" usa o azul do fluxo de caixa, já que é o
// segundo conceito mais importante do app depois do saldo receber/pagar.
private val LightColors = lightColorScheme(
    primary = VerdeSaldo,
    onPrimary = Color.White,
    primaryContainer = VerdeContainerClaro,
    onPrimaryContainer = Color(0xFF0A2E0C),
    secondary = AzulFluxo,
    onSecondary = Color.White,
    secondaryContainer = AzulContainerClaro,
    onSecondaryContainer = Color(0xFF0A1F35),
    error = VermelhoSaldo,
    onError = Color.White,
    errorContainer = VermelhoContainerClaro,
    onErrorContainer = Color(0xFF3A0A08)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BC98D),           // verde mais claro, pra manter contraste no escuro
    onPrimary = Color(0xFF0A2E0C),
    primaryContainer = VerdeContainerEscuro,
    onPrimaryContainer = VerdeContainerClaro,
    secondary = Color(0xFF9BC5F5),         // azul mais claro, mesmo motivo
    onSecondary = Color(0xFF0A1F35),
    secondaryContainer = AzulContainerEscuro,
    onSecondaryContainer = AzulContainerClaro,
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF3A0A08),
    errorContainer = VermelhoContainerEscuro,
    onErrorContainer = VermelhoContainerClaro
)

@Composable
fun ControleFinanceiroTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}

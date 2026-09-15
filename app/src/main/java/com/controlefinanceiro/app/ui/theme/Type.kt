package com.controlefinanceiro.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BaseTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

// Liga "algarismos tabulares" (todo dígito ocupa a mesma largura) em cada estilo
// de texto do app. Sem isso, valores em R$ empilhados numa lista (extrato,
// lista de clientes) não alinham verticalmente, porque "1" é mais estreito que
// "8" numa fonte proporcional normal — R$ 85,50 embaixo de R$ 1.240,00 fica
// torto. Aplicado uma vez aqui, no tema inteiro, em vez de em cada Text
// individualmente (são dezenas de lugares que mostram dinheiro no app).
val Typography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontFeatureSettings = "tnum"),
    displayMedium = BaseTypography.displayMedium.copy(fontFeatureSettings = "tnum"),
    displaySmall = BaseTypography.displaySmall.copy(fontFeatureSettings = "tnum"),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFeatureSettings = "tnum"),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFeatureSettings = "tnum"),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFeatureSettings = "tnum"),
    titleLarge = BaseTypography.titleLarge.copy(fontFeatureSettings = "tnum"),
    titleMedium = BaseTypography.titleMedium.copy(fontFeatureSettings = "tnum"),
    titleSmall = BaseTypography.titleSmall.copy(fontFeatureSettings = "tnum"),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFeatureSettings = "tnum"),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFeatureSettings = "tnum"),
    bodySmall = BaseTypography.bodySmall.copy(fontFeatureSettings = "tnum"),
    labelLarge = BaseTypography.labelLarge.copy(fontFeatureSettings = "tnum"),
    labelMedium = BaseTypography.labelMedium.copy(fontFeatureSettings = "tnum"),
    labelSmall = BaseTypography.labelSmall.copy(fontFeatureSettings = "tnum")
)

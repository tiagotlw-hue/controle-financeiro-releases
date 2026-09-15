package com.controlefinanceiro.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.ui.theme.VerdeSaldo
import com.controlefinanceiro.app.ui.theme.VermelhoSaldo

/**
 * Setinha pra cima (verde) ou pra baixo (vermelho) ao lado de um valor em R$ —
 * reforça positivo/negativo sem depender só da cor pra quem tem dificuldade de
 * distinguir vermelho de verde (a forma mais comum de daltonismo).
 */
@Composable
fun IconeDirecaoSaldo(positivo: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (positivo) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
        contentDescription = if (positivo) "Saldo positivo" else "Saldo negativo",
        tint = if (positivo) VerdeSaldo else VermelhoSaldo,
        modifier = modifier.size(16.dp)
    )
}

package com.controlefinanceiro.app.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta base do app — verde/vermelho/azul usados em toda tela pra indicar
// saldo/direção de dinheiro. Ficam aqui, um lugar só, em vez de cada tela
// declarar sua própria cópia com o mesmo valor hexadecimal.
val VerdeSaldo = Color(0xFF2E7D32)      // saldo positivo, "a receber", "recebido"
val VermelhoSaldo = Color(0xFFC62828)   // saldo negativo, "a pagar", "pago"
val AzulFluxo = Color(0xFF1565C0)       // categorias de fluxo de caixa

// Tons de apoio derivados da paleta base, usados pra preencher o resto do
// esquema de cores do Material3 (fundo de superfície, containers etc.) — ver
// Theme.kt. Sem isso, tudo que não é "primary" cai no roxo padrão do Compose.
val VerdeContainerClaro = Color(0xFFDCEDC8)     // fundo suave atrás de valores positivos
val VermelhoContainerClaro = Color(0xFFFFDAD6)  // fundo suave atrás de valores negativos
val AzulContainerClaro = Color(0xFFD3E4FD)      // fundo suave atrás de fluxo de caixa

val VerdeContainerEscuro = Color(0xFF1B3A1D)
val VermelhoContainerEscuro = Color(0xFF4A1515)
val AzulContainerEscuro = Color(0xFF16324F)

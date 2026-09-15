package com.controlefinanceiro.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.controlefinanceiro.app.data.avaliarExpressao
import com.controlefinanceiro.app.data.formatarResultadoCalculadora
import kotlin.math.roundToInt

/** Um cálculo já feito, guardado no histórico (o que foi digitado + o resultado). */
private data class CalculoHistorico(val expressao: String, val resultado: String)

/** Mini calculadora flutuante, pensada para ficar disponível em todas as telas do
 *  app (é colocada uma única vez, no nível do MainActivity, por cima da tela atual).
 *
 *  Sem grade de botões: o usuário digita a conta pelo próprio teclado do celular
 *  (ex: "120+35*2") e aperta o "concluído"/enter do teclado para calcular. Suporta
 *  apenas soma, subtração, multiplicação e divisão — sem parênteses, sem outras
 *  funções.
 *
 *  Guarda as últimas 10 contas feitas (mais recente primeiro). Como o estado
 *  (expressão atual, histórico, expandida ou não) fica lembrado aqui, ele persiste
 *  enquanto o usuário navega de uma tela para outra dentro do app. */
@Composable
fun MiniCalculadora(modifier: Modifier = Modifier) {
    var expandida by remember { mutableStateOf(false) }
    var expressao by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf(false) }
    var historico by remember { mutableStateOf(listOf<CalculoHistorico>()) }

    // Deslocamento acumulado do arrasto, em pixels, a partir da posição inicial
    // (definida por quem chama, via [modifier]). Fica guardado aqui, então o
    // ícone continua onde a pessoa o deixou enquanto ela navega pelo app.
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val arrastarModifier = Modifier.pointerInput(Unit) {
        detectDragGestures { change, arrasto ->
            change.consume()
            offsetX += arrasto.x
            offsetY += arrasto.y
        }
    }

    fun calcular() {
        val texto = expressao.trim()
        if (texto.isEmpty()) return
        val resultado = avaliarExpressao(texto)
        if (resultado == null) {
            erro = true
            return
        }
        erro = false
        val resultadoTexto = formatarResultadoCalculadora(resultado)
        historico = (listOf(CalculoHistorico(texto, resultadoTexto)) + historico).take(10)
        expressao = resultadoTexto
    }

    Surface(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (!expandida) {
            // Estado recolhido: só um "carimbo" pequeno pra não atrapalhar a tela.
            // Pode ser arrastado pra qualquer lugar; um toque simples (sem arrastar) abre.
            Box(
                Modifier
                    .size(44.dp)
                    .then(arrastarModifier)
                    .clickable { expandida = true },
                contentAlignment = Alignment.Center
            ) {
                Text("🧮", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Column(Modifier.padding(12.dp).width(210.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(arrastarModifier)
                        .clickable { expandida = false },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Calculadora",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text("✕", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = expressao,
                    onValueChange = {
                        expressao = it
                        erro = false
                    },
                    singleLine = true,
                    placeholder = { Text("ex: 120+35*2") },
                    isError = erro,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { calcular() }),
                    modifier = Modifier.fillMaxWidth()
                )
                if (erro) {
                    Text(
                        "Conta inválida",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (historico.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Últimos 10 cálculos", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(Modifier.heightIn(max = 180.dp)) {
                        items(historico) { calc ->
                            Text(
                                "${calc.expressao} = ${calc.resultado}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expressao = calc.resultado }
                                    .padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Avalia uma expressão simples com +, -, * e / (sem parênteses), respeitando a
 *  precedência: multiplicação e divisão primeiro, depois soma e subtração, sempre
 *  da esquerda para a direita. Retorna null se a expressão for inválida (sintaxe
 *  errada, vazia, ou divisão por zero). */


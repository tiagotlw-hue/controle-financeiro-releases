package com.controlefinanceiro.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.controlefinanceiro.app.data.formatarData
import com.controlefinanceiro.app.data.paraMillisUtcDoDia
import com.controlefinanceiro.app.data.aplicarDataUtcNoFusoLocal

private val fmtHora = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

/** Botões lado a lado pra escolher data e hora, cada um abrindo seu próprio diálogo.
 *  Já chega preenchido com o valor atual de [dataHoraMillis] (padrão: agora). Usado
 *  tanto na criação de uma movimentação (compra) quanto no registro de uma baixa,
 *  pra manter o mesmo padrão de data+hora em todo o app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorDataHora(
    dataHoraMillis: Long,
    onDataHoraChange: (Long) -> Unit
) {
    var mostrarDatePicker by remember { mutableStateOf(false) }
    var mostrarTimePicker by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { mostrarDatePicker = true }, modifier = Modifier.weight(1f)) {
            Text(formatarData(dataHoraMillis))
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = { mostrarTimePicker = true }, modifier = Modifier.weight(1f)) {
            Text(fmtHora.format(Date(dataHoraMillis)))
        }
    }

    if (mostrarDatePicker) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = paraMillisUtcDoDia(dataHoraMillis))
        DatePickerDialog(
            onDismissRequest = { mostrarDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { novaDataUtc ->
                        onDataHoraChange(aplicarDataUtcNoFusoLocal(novaDataUtc, dataHoraMillis))
                    }
                    mostrarDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = estado)
        }
    }

    if (mostrarTimePicker) {
        val calAtual = remember { Calendar.getInstance().apply { timeInMillis = dataHoraMillis } }
        val estado = rememberTimePickerState(
            initialHour = calAtual.get(Calendar.HOUR_OF_DAY),
            initialMinute = calAtual.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { mostrarTimePicker = false },
            title = { Text("Escolha o horário") },
            text = { TimePicker(state = estado) },
            confirmButton = {
                TextButton(onClick = {
                    val calNovo = Calendar.getInstance().apply { timeInMillis = dataHoraMillis }
                    calNovo.set(Calendar.HOUR_OF_DAY, estado.hour)
                    calNovo.set(Calendar.MINUTE, estado.minute)
                    onDataHoraChange(calNovo.timeInMillis)
                    mostrarTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarTimePicker = false }) { Text("Cancelar") }
            }
        )
    }
}

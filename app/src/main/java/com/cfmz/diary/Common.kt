package com.cfmz.diary

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle as JTextStyle

val SubjectPalette = listOf(
    Color(0xFF7C6CFF), Color(0xFF2EC4B6), Color(0xFFFF7A59), Color(0xFFFFC043), Color(0xFF4DA3FF),
    Color(0xFFEF5DA8), Color(0xFF8BC34A), Color(0xFFB388FF), Color(0xFF26C6DA), Color(0xFFFF8A65)
)

fun subjectColor(s: String): Color =
    SubjectPalette[(s.trim().lowercase().hashCode() and 0x7fffffff) % SubjectPalette.size]

fun markColor(m: Int): Color = when {
    m >= 5 -> Color(0xFF2ECC71)
    m == 4 -> Color(0xFF8BC34A)
    m == 3 -> Color(0xFFFFB020)
    else -> Color(0xFFFF5C5C)
}

fun onAccent(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF14102E) else Color.White

@Composable
fun Card2(
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier, shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface, border = border
    ) { Column(content = content) }
}

@Composable
fun EmptyState(emoji: String, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 52.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun NumberBadge(n: Int, color: Color) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) { Text("$n", color = color, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun MarkBadge(m: Int) {
    val c = markColor(m)
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(c),
        contentAlignment = Alignment.Center
    ) { Text("$m", color = onAccent(c), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge) }
}

@Composable
fun DiagCard(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val clip = LocalClipboardManager.current
    var open by remember { mutableStateOf(false) }
    val text = DiaryModel.diag()
    Card2(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.BugReport, null, tint = cs.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Диагностика", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Ответ сервера и ID", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = cs.onSurfaceVariant)
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Text(
                    text, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    color = cs.onSurfaceVariant, maxLines = 40
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { clip.setText(AnnotatedString(text)) }) {
                        Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Копировать")
                    }
                    OutlinedButton(onClick = {
                        Session.studentId = 0; Session.yearId = 0; Session.ready = false
                        DiaryModel.reload()
                    }) { Text("Сбросить ID") }
                }
            }
        }
    }
}

@Composable
fun HwDialog(l: Lesson, existing: Hw?, defDue: LocalDate, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var due by remember { mutableStateOf(parseDate(existing?.due) ?: defDue) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = cs.surface,
        title = {
            Column {
                Text(l.subject.ifBlank { "Урок" }, fontWeight = FontWeight.Bold)
                Text(
                    fmtDayFull(l.date) + " · урок ${l.number}",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    label = { Text("Что задали?") }, minLines = 3, maxLines = 6,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text("Срок сдачи", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                val opts = listOf(
                    "След. урок" to defDue,
                    "Завтра" to nextSchoolDay(l.date),
                    "Через неделю" to l.date.plusWeeks(1)
                ).distinctBy { it.second }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    opts.forEach { (label, d) ->
                        FilterChip(selected = due == d, onClick = { due = d }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { due = due.minusDays(1) }) { Icon(Icons.Rounded.ChevronLeft, null) }
                    Text(
                        fmtShort(due), Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { due = due.plusDays(1) }) { Icon(Icons.Rounded.ChevronRight, null) }
                }
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = {
                HwStore.upsert(Hw(l.key(), l.date.toString(), l.subject.ifBlank { "Урок" }, text.trim(), due.toString(), existing?.done ?: false))
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { HwStore.remove(existing.key); onDismiss() }) {
                    Text("Удалить", color = cs.error)
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

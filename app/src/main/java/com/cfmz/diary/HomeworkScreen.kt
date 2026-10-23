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

private fun groupLabel(h: Hw, today: LocalDate): String {
    if (h.done) return "Выполнено"
    val d = parseDate(h.due) ?: return "Без срока"
    return when {
        d.isBefore(today) -> "Просрочено"
        d == today -> "Сегодня"
        d == today.plusDays(1) -> "Завтра"
        else -> fmtShort(d).replaceFirstChar { it.uppercase() }
    }
}

@Composable
fun HomeworkTab() {
    val cs = MaterialTheme.colorScheme
    val hw by HwStore.items.collectAsState()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val active = hw.count { !it.done }
    val shown = when (filter) { 0 -> hw.filter { !it.done }; 1 -> hw.filter { it.done }; else -> hw }
    val sorted = if (filter == 1) shown.sortedByDescending { it.due }
    else shown.sortedWith(compareBy({ it.done }, { it.due }))
    val groups = sorted.groupBy { groupLabel(it, today) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "hdr") {
                Column(Modifier.padding(start = 4.dp)) {
                    Text("Домашка", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (active == 0) "Всё сделано 🎉" else "Осталось: $active",
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                    )
                }
            }
            item(key = "filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Активные", "Выполненные", "Все").forEachIndexed { i, t ->
                        FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(t) })
                    }
                }
            }
            if (sorted.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        if (filter == 1) "📭" else "✨",
                        if (filter == 1) "Выполненного пока нет" else "Заданий нет",
                        "Записывай ДЗ после урока во вкладке «Дневник» или кнопкой «Добавить»"
                    )
                }
            }
            groups.forEach { (label, list) ->
                item(key = "g$label") {
                    Text(
                        label.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = if (label == "Просрочено") cs.error else cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                    )
                }
                items(list, key = { it.key }) { h -> HwRow(h) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = cs.primary, contentColor = cs.onPrimary,
            icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Добавить") }
        )
    }
    if (adding) ManualHwDialog { adding = false }
}

@Composable
private fun HwRow(h: Hw) {
    val cs = MaterialTheme.colorScheme
    val accent = subjectColor(h.subject)
    val due = parseDate(h.due)
    val overdue = !h.done && due != null && due.isBefore(LocalDate.now())
    Card2(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { HwStore.toggle(h.key) }
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bg by animateColorAsState(if (h.done) accent else Color.Transparent, label = "chk")
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(bg).border(2.dp, accent, CircleShape),
                contentAlignment = Alignment.Center
            ) { if (h.done) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = onAccent(accent)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(h.subject, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Text(
                    h.text, style = MaterialTheme.typography.bodyLarge,
                    color = if (h.done) cs.onSurfaceVariant else cs.onSurface,
                    textDecoration = if (h.done) TextDecoration.LineThrough else null
                )
                if (due != null) Text(
                    "до ${fmtShort(due)}", style = MaterialTheme.typography.labelSmall,
                    color = if (overdue) cs.error else cs.onSurfaceVariant
                )
            }
            IconButton(onClick = { HwStore.remove(h.key) }) {
                Icon(Icons.Rounded.DeleteOutline, "Удалить", tint = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ManualHwDialog(onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var subject by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(nextSchoolDay(LocalDate.now())) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp), containerColor = cs.surface,
        title = { Text("Новое задание", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = subject, onValueChange = { subject = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Предмет") }, shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, minLines = 3, maxLines = 6,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Что задали?") }, shape = RoundedCornerShape(16.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { due = due.minusDays(1) }) { Icon(Icons.Rounded.ChevronLeft, null) }
                    Text(
                        "до ${fmtShort(due)}", Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { due = due.plusDays(1) }) { Icon(Icons.Rounded.ChevronRight, null) }
                }
            }
        },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = {
                HwStore.upsert(
                    Hw("m${System.currentTimeMillis()}", LocalDate.now().toString(),
                        subject.trim().ifBlank { "Другое" }, text.trim(), due.toString(), false)
                )
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

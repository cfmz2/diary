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
import kotlinx.coroutines.delay

@Composable
fun DiaryTab() {
    val m = DiaryModel
    val hw by HwStore.items.collectAsState()
    var editing by remember { mutableStateOf<Lesson?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        m.ensureLoaded()
        while (true) { delay(30_000); now = LocalDateTime.now() }
    }
    val today = now.toLocalDate()

    Column(Modifier.fillMaxSize()) {
        DiaryHeader()
        DayStrip(m.weekStart, m.selected, today, m.lessons, hw) { m.pick(it) }
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (m.loading) LinearProgressIndicator(
                Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
        Crossfade(targetState = m.selected, modifier = Modifier.weight(1f), label = "day") { day ->
            DayList(day, now, hw) { editing = it }
        }
    }

    editing?.let { l ->
        val def = m.lessons.filter { it.subject == l.subject && it.date > l.date }
            .minOfOrNull { it.date } ?: nextSchoolDay(l.date)
        HwDialog(l, hw.firstOrNull { it.key == l.key() }, def) { editing = null }
    }
}

@Composable
private fun DiaryHeader() {
    val m = DiaryModel
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(fmtMonth(m.selected), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(
                fmtRange(m.weekStart, m.weekStart.plusDays(6)),
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        IconButton({ m.shiftWeek(-1) }, Modifier.size(42.dp)) { Icon(Icons.Rounded.ChevronLeft, "Назад") }
        IconButton({ m.goToday() }, Modifier.size(42.dp)) { Icon(Icons.Rounded.Today, "Сегодня", tint = cs.primary) }
        IconButton({ m.shiftWeek(1) }, Modifier.size(42.dp)) { Icon(Icons.Rounded.ChevronRight, "Вперёд") }
        IconButton({ m.reload() }, Modifier.size(42.dp)) { Icon(Icons.Rounded.Refresh, "Обновить") }
    }
}

@Composable
private fun DayStrip(
    week: LocalDate, selected: LocalDate, today: LocalDate,
    lessons: List<Lesson>, hw: List<Hw>, onSelect: (LocalDate) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 0..6) {
            val d = week.plusDays(i.toLong())
            DayChip(
                d, selected == d, today == d,
                lessons.any { it.date == d },
                hw.any { !it.done && it.due == d.toString() },
                Modifier.weight(1f)
            ) { onSelect(d) }
        }
    }
}

@Composable
private fun DayChip(
    d: LocalDate, selected: Boolean, isToday: Boolean, hasLessons: Boolean, hasHw: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val textColor by animateColorAsState(if (selected) cs.onPrimary else cs.onSurface, label = "t")
    val bg = if (selected)
        Modifier.background(Brush.verticalGradient(listOf(cs.primary, cs.primary.copy(alpha = 0.7f))), shape)
    else Modifier.background(cs.surface, shape)
    val ring = if (isToday && !selected) Modifier.border(1.5.dp, cs.primary, shape) else Modifier
    Column(
        modifier.clip(shape).then(bg).then(ring).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            d.dayOfWeek.getDisplayName(JTextStyle.SHORT, RU).uppercase(),
            style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f)
        )
        Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = textColor)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier.size(6.dp).clip(CircleShape).background(
                when {
                    hasHw -> cs.tertiary
                    hasLessons -> if (selected) cs.onPrimary.copy(alpha = 0.8f) else cs.primary
                    else -> Color.Transparent
                }
            )
        )
    }
}

@Composable
private fun DayList(day: LocalDate, now: LocalDateTime, hw: List<Hw>, onEdit: (Lesson) -> Unit) {
    val m = DiaryModel
    val ls = m.lessons.filter { it.date == day }.sortedBy { it.number }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        m.note?.let { n -> item(key = "note") { NoteBanner(n) } }
        m.error?.let { e -> item(key = "err") { ErrorCard(e) { m.reload() } } }
        if (day == now.toLocalDate()) {
            item(key = "hero") { HeroCard(ls, now, hw.count { !it.done }) }
        } else {
            item(key = "dayhdr") {
                Text(fmtDayFull(day), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (ls.isEmpty() && !m.loading && m.error == null) {
            item(key = "empty") {
                EmptyState("🌴", "Уроков нет", "В этот день занятий не найдено")
            }
            if (m.lessons.isEmpty()) item(key = "diag") { DiagCard() }
        }
        itemsIndexed(ls, key = { i, l -> "${l.key()}#$i" }) { _, l ->
            LessonCard(l, hw.firstOrNull { it.key == l.key() }, l.status(now)) { onEdit(l) }
        }
    }
}

@Composable
private fun NoteBanner(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.tertiary.copy(alpha = 0.15f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Info, null, tint = cs.tertiary)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorCard(text: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.error.copy(alpha = 0.14f)).padding(16.dp)
    ) {
        Text("Не удалось загрузить", fontWeight = FontWeight.Bold, color = cs.error)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onRetry) { Text("Повторить") }
    }
}

@Composable
private fun HeroCard(ls: List<Lesson>, now: LocalDateTime, pending: Int) {
    val cur = ls.firstOrNull { it.status(now) == LS.Now }
    val next = ls.firstOrNull { it.status(now) == LS.Next }
    val grad = Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3D8BFD)))
    val cap: String; val title: String; val sub: String
    when {
        cur != null -> { cap = "СЕЙЧАС ИДЁТ"; title = cur.subject; sub = "${cur.start}–${cur.end}" }
        next != null -> { cap = "ДАЛЬШЕ"; title = next.subject; sub = "в ${next.start}" }
        ls.isNotEmpty() -> { cap = "СЕГОДНЯ"; title = "Уроки закончились"; sub = "Не забудь записать ДЗ" }
        else -> { cap = "СЕГОДНЯ"; title = "Уроков нет"; sub = "Отдыхай 🌴" }
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(grad).padding(20.dp)) {
        Column {
            Text(
                cap, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                title.ifBlank { "Урок" }, color = Color.White, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(sub, color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyMedium)
            if (cur != null) {
                val s = parseT(cur.start)?.let { cur.date.atTime(it) }
                val e = parseT(cur.end)?.let { cur.date.atTime(it) }
                if (s != null && e != null) {
                    val total = java.time.Duration.between(s, e).seconds.coerceAtLeast(1)
                    val gone = java.time.Duration.between(s, now).seconds.coerceIn(0, total)
                    val left = java.time.Duration.between(now, e).toMinutes().coerceAtLeast(0)
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { gone.toFloat() / total.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color.White, trackColor = Color.White.copy(alpha = 0.25f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("осталось $left мин", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    if (pending > 0) "ДЗ к выполнению: $pending" else "ДЗ: всё сделано ✓",
                    color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LessonCard(l: Lesson, hw: Hw?, st: LS, onHw: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = subjectColor(l.subject)
    val border = if (st == LS.Now) BorderStroke(1.5.dp, accent) else null
    Card2(Modifier.fillMaxWidth(), border) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberBadge(l.number, accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            l.subject.ifBlank { "Урок" }, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Schedule, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text("${l.start}–${l.end}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            if (l.room.isNotBlank()) {
                                Spacer(Modifier.width(10.dp))
                                Icon(Icons.Rounded.MeetingRoom, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(l.room, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            if (st == LS.Now) {
                                Text("  · идёт", style = MaterialTheme.typography.bodySmall, color = accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    l.mark?.let { Spacer(Modifier.width(8.dp)); MarkBadge(it) }
                }
                if (l.tasks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    l.tasks.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                HwBlock(hw, accent, st, onHw)
            }
        }
    }
}

@Composable
private fun HwBlock(hw: Hw?, accent: Color, st: LS, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    if (hw != null) {
        val due = parseDate(hw.due)
        Row(
            Modifier.fillMaxWidth().clip(shape).background(accent.copy(alpha = 0.14f))
                .clickable(onClick = onClick).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (hw.done) Icons.Rounded.CheckCircle else Icons.Rounded.MenuBook, null,
                Modifier.size(20.dp), tint = accent
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    hw.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (hw.done) TextDecoration.LineThrough else null
                )
                if (due != null) Text("до ${fmtShort(due)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
        }
    } else if (st == LS.Past) {
        Button(
            onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent(accent))
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
            Text("Записать ДЗ", fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape,
            border = BorderStroke(1.dp, cs.outlineVariant)
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Добавить ДЗ")
        }
    }
}

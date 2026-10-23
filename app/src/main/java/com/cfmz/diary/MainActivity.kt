package com.cfmz.diary

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HwStore.init(this)
        Reminder.ensureChannel(this)
        Reminder.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private val RU = Locale("ru")
private fun fmtDay(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU))
private fun fmtShort(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("EEE, d MMM", RU))
private fun Lesson.key() = if (id > 0) id.toString() else "$date#$number#$subject"
private fun Lesson.ended(): Boolean {
    val t = runCatching { LocalTime.parse(end) }.getOrNull() ?: return date.isBefore(LocalDate.now())
    return LocalDateTime.now().isAfter(date.atTime(t))
}
private fun nextSchoolDay(d: LocalDate): LocalDate {
    val x = d.plusDays(1)
    return when (x.dayOfWeek) { DayOfWeek.SATURDAY -> x.plusDays(2); DayOfWeek.SUNDAY -> x.plusDays(1); else -> x }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var authed by remember { mutableStateOf(Session.load(ctx)) }
    if (!authed) LoginScreen { Session.save(ctx); authed = true }
    else MainScreen {
        Session.clear(ctx)
        CookieManager.getInstance().removeAllCookies(null)
        authed = false
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit) {
    val done = remember { AtomicBoolean(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler { web?.takeIf { it.canGoBack() }?.goBack() }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
        WebView(c).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                    val u = r.url
                    if (u.host?.endsWith("asurso.ru") == true && u.path?.startsWith("/webapi/") == true) {
                        val at = r.requestHeaders.entries
                            .firstOrNull { it.key.equals("at", true) }?.value
                        if (!at.isNullOrBlank() && done.compareAndSet(false, true)) {
                            Session.at = at
                            Session.cookie = CookieManager.getInstance().getCookie(BASE) ?: ""
                            v.post { onDone() }
                        }
                    }
                    return null
                }
            }
            web = this
            loadUrl(BASE)
        }
    })
}

@Composable
fun MainScreen(onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                icon = { Text("📖") }, label = { Text("Дневник") })
            NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                icon = { Text("📝") }, label = { Text("ДЗ") })
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) DiaryTab(onLogout) else HomeworkTab(onLogout)
        }
    }
}

@Composable
fun DiaryTab(onLogout: () -> Unit) {
    var weekStart by remember {
        mutableStateOf(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    var lessons by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Lesson?>(null) }
    val hw by HwStore.items.collectAsState()

    LaunchedEffect(weekStart, reload) {
        loading = true; error = null
        try {
            lessons = withContext(Dispatchers.IO) {
                if (Session.studentId == 0L || Session.yearId == 0L) Api.init()
                Api.week(weekStart)
            }
        } catch (e: AuthException) { onLogout()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { error = e.message ?: "Ошибка загрузки" }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ weekStart = weekStart.minusWeeks(1) }) { Text("‹") }
            Text("${fmtShort(weekStart)} – ${fmtShort(weekStart.plusDays(6))}",
                Modifier.weight(1f), fontWeight = FontWeight.Bold)
            TextButton({ weekStart = weekStart.plusWeeks(1) }) { Text("›") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Column(Modifier.padding(16.dp)) {
                Text(it, color = MaterialTheme.colorScheme.error)
                Button({ reload++ }) { Text("Повторить") }
            }
        }
        if (!loading && error == null && lessons.isEmpty())
            Text("На этой неделе уроков нет", Modifier.padding(16.dp))
        val grouped = lessons.groupBy { it.date }.toSortedMap()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            grouped.forEach { (date, ls) ->
                item(key = "h$date") {
                    Text(fmtDay(date).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp))
                }
                items(ls, key = { it.key() }) { l ->
                    LessonCard(l, hw.firstOrNull { it.key == l.key() }) { editing = l }
                }
            }
        }
    }

    editing?.let { l ->
        val def = lessons.filter { it.subject == l.subject && it.date > l.date }
            .minOfOrNull { it.date } ?: nextSchoolDay(l.date)
        HwDialog(l, hw.firstOrNull { it.key == l.key() }, def) { editing = null }
    }
}

@Composable
fun LessonCard(l: Lesson, hw: Hw?, onHw: () -> Unit) {
    val ended = l.ended()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${l.number}. ${l.subject}", Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold)
                l.mark?.let { Text("  $it", style = MaterialTheme.typography.titleLarge) }
            }
            Text(listOf("${l.start}–${l.end}", l.room.takeIf { it.isNotBlank() }?.let { "каб. $it" })
                .filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall)
            l.tasks.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(8.dp))
            when {
                hw != null -> OutlinedButton(onHw, Modifier.fillMaxWidth()) {
                    Text((if (hw.done) "✓ " else "ДЗ: ") + hw.text, maxLines = 2)
                }
                ended -> FilledTonalButton(onHw, Modifier.fillMaxWidth()) { Text("＋ Записать ДЗ") }
                else -> OutlinedButton(onHw, Modifier.fillMaxWidth()) { Text("ДЗ") }
            }
        }
    }
}

@Composable
fun HwDialog(l: Lesson, existing: Hw?, defDue: LocalDate, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var due by remember {
        mutableStateOf(existing?.due?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: defDue)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(l.subject) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, label = { Text("Домашнее задание") },
                    minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Срок:")
                    TextButton({ due = due.minusDays(1) }) { Text("‹") }
                    Text(fmtShort(due))
                    TextButton({ due = due.plusDays(1) }) { Text("›") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                HwStore.upsert(Hw(l.key(), l.date.toString(), l.subject, text.trim(),
                    due.toString(), existing?.done ?: false))
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton({ HwStore.remove(existing.key); onDismiss() }) { Text("Удалить") }
                TextButton(onDismiss) { Text("Отмена") }
            }
        }
    )
}

@Composable
fun HomeworkTab(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val hw by HwStore.items.collectAsState()
    var hour by remember { mutableIntStateOf(HwStore.hour) }
    val today = LocalDate.now()
    val sorted = hw.sortedWith(compareBy({ it.done }, { it.due }))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Напоминание о ДЗ", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Время:")
                        TextButton({ hour = (hour + 23) % 24; HwStore.hour = hour }) { Text("‹") }
                        Text("%02d:00".format(hour))
                        TextButton({ hour = (hour + 1) % 24; HwStore.hour = hour }) { Text("›") }
                        Spacer(Modifier.weight(1f))
                        TextButton({
                            Reminder.notifyNow(ctx, hw.filter { !it.done }.ifEmpty { return@TextButton })
                        }) { Text("Тест") }
                    }
                    TextButton(onLogout) { Text("Выйти из аккаунта") }
                }
            }
        }
        if (sorted.isEmpty()) item { Text("Пока нет записанного ДЗ", Modifier.padding(8.dp)) }
        items(sorted, key = { it.key }) { h ->
            val due = runCatching { LocalDate.parse(h.due) }.getOrDefault(today)
            val overdue = !h.done && due.isBefore(today)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(h.done, { HwStore.toggle(h.key) })
                    Column(Modifier.weight(1f)) {
                        Text(h.subject, fontWeight = FontWeight.SemiBold)
                        Text(h.text)
                        Text("до ${fmtShort(due)}", style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ HwStore.remove(h.key) }) { Text("✕") }
                }
            }
        }
    }
}

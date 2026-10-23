package com.cfmz.diary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

val RU: Locale = Locale("ru")
private val DM = DateTimeFormatter.ofPattern("d MMM", RU)

fun parseDate(s: String?): LocalDate? =
    if (s != null && s.length >= 10) runCatching { LocalDate.parse(s.substring(0, 10)) }.getOrNull() else null

fun parseT(s: String): LocalTime? = runCatching {
    val t = s.trim().take(5)
    LocalTime.parse(if (t.length == 4) "0$t" else t)
}.getOrNull()

fun fmtShort(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE, d MMM", RU))
fun fmtMonth(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("LLLL yyyy", RU)).replaceFirstChar { it.uppercase() }
fun fmtDayFull(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)).replaceFirstChar { it.uppercase() }
fun fmtRange(a: LocalDate, b: LocalDate): String = "${a.format(DM)} – ${b.format(DM)}"

fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun nextSchoolDay(d: LocalDate): LocalDate {
    val x = d.plusDays(1)
    return when (x.dayOfWeek) {
        DayOfWeek.SATURDAY -> x.plusDays(2)
        DayOfWeek.SUNDAY -> x.plusDays(1)
        else -> x
    }
}

fun Lesson.key(): String = if (id > 0) id.toString() else "$date#$number#$subject"

enum class LS { Past, Now, Next }

fun Lesson.status(now: LocalDateTime): LS {
    val s = parseT(start)?.let { date.atTime(it) }
    val e = parseT(end)?.let { date.atTime(it) }
    return when {
        e != null && now.isAfter(e) -> LS.Past
        s != null && e != null && !now.isBefore(s) -> LS.Now
        s == null && e == null -> if (date.isBefore(now.toLocalDate())) LS.Past else LS.Next
        else -> LS.Next
    }
}

object DiaryModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cache = HashMap<LocalDate, List<Lesson>>()
    private var job: Job? = null
    private var gen = 0
    private var searched = false
    private var started = false

    var weekStart by mutableStateOf(mondayOf(LocalDate.now()))
    var selected by mutableStateOf(LocalDate.now())
    var lessons by mutableStateOf<List<Lesson>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var note by mutableStateOf<String?>(null)
    var authLost by mutableStateOf(false)

    fun ensureLoaded() { if (!started) { started = true; load() } }

    fun reset() {
        job?.cancel(); cache.clear(); gen++
        searched = false; started = false
        lessons = emptyList(); loading = false; error = null; note = null; authLost = false
        weekStart = mondayOf(LocalDate.now()); selected = LocalDate.now()
    }

    fun reload() { cache.remove(weekStart); note = null; load(true) }

    fun shiftWeek(delta: Int) {
        note = null
        val dow = (selected.dayOfWeek.value - 1).toLong()
        weekStart = weekStart.plusWeeks(delta.toLong())
        selected = weekStart.plusDays(dow)
        load()
    }

    fun goToday() {
        note = null
        weekStart = mondayOf(LocalDate.now()); selected = LocalDate.now()
        load()
    }

    fun pick(d: LocalDate) { selected = d }

    private suspend fun fetch(ws: LocalDate): List<Lesson> {
        val res = withContext(Dispatchers.IO) {
            if (!Session.ready) Api.init()
            Api.week(ws)
        }
        cache[ws] = res
        return res
    }

    fun load(force: Boolean = false) {
        val ws = weekStart
        val cached = cache[ws]
        lessons = cached ?: emptyList()
        if (cached != null && !force) { loading = false; error = null; return }
        val my = ++gen
        job?.cancel()
        job = scope.launch {
            loading = true; error = null
            try {
                var start = ws
                val first = !searched
                var res = fetch(ws)
                searched = true
                var jumped = false
                if (first && res.isEmpty()) {
                    for (i in 1..8) {
                        val s = ws.minusWeeks(i.toLong())
                        val r = try { fetch(s) }
                        catch (e: CancellationException) { throw e }
                        catch (e: AuthException) { throw e }
                        catch (e: Exception) { emptyList<Lesson>() }
                        if (r.isNotEmpty()) { start = s; res = r; jumped = true; break }
                    }
                }
                if (my != gen) return@launch
                if (jumped) {
                    val today = LocalDate.now()
                    weekStart = start
                    selected = res.map { it.date }.filter { !it.isAfter(today) }.maxOrNull() ?: res.first().date
                    note = "На этой неделе уроков нет (каникулы?). Показана последняя неделя с уроками."
                }
                lessons = res
            } catch (e: AuthException) {
                if (my == gen) authLost = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (my == gen) error = e.message ?: "Ошибка загрузки"
            } finally {
                if (my == gen) loading = false
            }
        }
    }

    fun diag(): String = buildString {
        appendLine("studentId=${Session.studentId} yearId=${Session.yearId}")
        appendLine("week=$weekStart lessons=${lessons.size} seenUrls=${Session.seen.size}")
        appendLine("GET ${Api.lastUrl}")
        append(Api.lastBody.take(3000))
    }
}

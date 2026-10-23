package com.cfmz.diary

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

const val BASE = "https://asurso.ru"

class AuthException : Exception("Сессия истекла")

object Session {
    @Volatile var at = ""
    @Volatile var cookie = ""
    @Volatile var studentId = 0L
    @Volatile var yearId = 0L
    @Volatile var ready = false
    val seen = CopyOnWriteArrayList<String>()
    @Volatile private var appCtx: Context? = null

    private fun p(c: Context) = c.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun load(c: Context): Boolean {
        appCtx = c.applicationContext
        val s = p(c)
        at = s.getString("at", "") ?: ""
        cookie = s.getString("cookie", "") ?: ""
        if (s.getInt("ver", 0) == 2) {
            studentId = s.getLong("sid", 0)
            yearId = s.getLong("yid", 0)
        } else { studentId = 0; yearId = 0 }
        return at.isNotEmpty()
    }

    fun idsFromSeen(key: String): List<Long> {
        val re = Regex("[?&]$key=(\\d+)")
        val diary = seen.filter { it.contains("/student/diary") }
        return (diary + seen).mapNotNull { re.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            .filter { it > 0 }.distinct()
    }

    fun persistIds() {
        val c = appCtx ?: return
        p(c).edit().putLong("sid", studentId).putLong("yid", yearId).putInt("ver", 2).apply()
    }

    fun scanSeen() {
        if (studentId == 0L) idsFromSeen("studentId").firstOrNull()?.let { studentId = it }
        if (yearId == 0L) idsFromSeen("yearId").firstOrNull()?.let { yearId = it }
    }

    fun save(c: Context) {
        scanSeen()
        p(c).edit().putString("at", at).putString("cookie", cookie)
            .putLong("sid", studentId).putLong("yid", yearId).putInt("ver", 2).apply()
    }

    fun clear(c: Context) {
        at = ""; cookie = ""; studentId = 0; yearId = 0; ready = false; seen.clear()
        p(c).edit().clear().apply()
    }
}

data class Lesson(
    val id: Long, val date: LocalDate, val number: Int,
    val start: String, val end: String, val subject: String,
    val room: String, val tasks: List<String>, val mark: Int?
)

object Api {
    var lastUrl by mutableStateOf("")
    var lastBody by mutableStateOf("")
    val log = CopyOnWriteArrayList<String>()
    private fun logLine(x: String) { log.add(x); while (log.size > 60) log.removeAt(0) }

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}")

    private fun get(path: String): String {
        lastUrl = path
        val req = Request.Builder().url(BASE + path)
            .header("at", Session.at).header("Cookie", Session.cookie)
            .header("Accept", "application/json, text/plain, */*")
            .header("X-Requested-With", "XMLHttpRequest").build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            lastBody = "HTTP ${r.code}\n$body"
            if (r.code == 401 || r.code == 403) throw AuthException()
            if (!r.isSuccessful) throw IOException("Ошибка сервера: ${r.code}")
            if (body.trimStart().startsWith("<")) throw AuthException()
            return body
        }
    }

    private fun parse(s: String): Any? = try { JSONTokener(s).nextValue() } catch (_: Exception) { null }

    private fun tryGet(path: String): Any? = try {
        parse(get(path))
    } catch (e: AuthException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun findLong(o: Any?, vararg keys: String): Long {
        when (o) {
            is JSONObject -> {
                for (k in keys) { val v = o.optLong(k, 0); if (v > 0) return v }
                val it = o.keys()
                while (it.hasNext()) { val r = findLong(o.opt(it.next()), *keys); if (r > 0) return r }
            }
            is JSONArray -> for (i in 0 until o.length()) { val r = findLong(o.opt(i), *keys); if (r > 0) return r }
        }
        return 0
    }

    private fun firstPos(vararg v: Long): Long = v.firstOrNull { it > 0 } ?: 0L

    fun init() {
        Session.scanSeen()
        if (Session.studentId == 0L || Session.yearId == 0L) {
            val init = tryGet("/webapi/student/diary/init")
            val years = tryGet("/webapi/years/current")
            val ctx = tryGet("/webapi/context")
            if (Session.studentId == 0L)
                Session.studentId = firstPos(
                    findLong(init, "currentStudentId", "studentId"),
                    findLong(ctx, "currentStudentId", "studentId"))
            if (Session.yearId == 0L)
                Session.yearId = firstPos(
                    findLong(init, "yearId", "currentYearId"),
                    findLong(years, "id", "yearId"),
                    findLong(ctx, "schoolYearId", "yearId", "currentYearId"))
        }
        if (Session.studentId == 0L || Session.yearId == 0L)
            throw IOException("Не удалось определить studentId/yearId. Открой «Ещё» → Диагностика.")
        Session.ready = true
    }

    private fun parseDate(s: String?): LocalDate? =
        if (s != null && s.length >= 10 && DATE.containsMatchIn(s))
            runCatching { LocalDate.parse(s.substring(0, 10)) }.getOrNull() else null

    private fun isLesson(o: JSONObject) =
        o.has("subjectName") || (o.has("subject") && (o.has("startTime") || o.has("number")))

    private fun collect(o: Any?, date: LocalDate?, out: MutableList<Lesson>) {
        when (o) {
            is JSONArray -> for (i in 0 until o.length()) collect(o.opt(i), date, out)
            is JSONObject -> {
                var d = date
                parseDate(o.optString("date"))?.let { d = it }
                if (isLesson(o)) {
                    val own = parseDate(o.optString("day")) ?: d
                    if (own != null) out += toLesson(o, own)
                } else {
                    val it = o.keys()
                    while (it.hasNext()) collect(o.opt(it.next()), d, out)
                }
            }
        }
    }

    private fun toLesson(o: JSONObject, date: LocalDate): Lesson {
        val tasks = mutableListOf<String>()
        var mark: Int? = null
        o.optJSONArray("assignments")?.let { a ->
            for (q in 0 until a.length()) {
                val t = a.optJSONObject(q) ?: continue
                val n = t.optString("assignmentName").ifBlank { t.optString("name") }
                if (n.isNotBlank()) tasks += n
                val mk = t.opt("mark")
                val m = when (mk) {
                    is JSONObject -> mk.optInt("mark", 0)
                    is Number -> mk.toInt()
                    else -> 0
                }
                if (m > 0) mark = m
            }
        }
        val sv = o.opt("subject")
        val subj = o.optString("subjectName").ifBlank {
            when (sv) { is JSONObject -> sv.optString("name"); is String -> sv; else -> "" }
        }
        return Lesson(
            o.optLong("classmeetingId"), date,
            o.optInt("number", o.optInt("lessonNumber", 0)),
            o.optString("startTime").ifBlank { o.optString("start") }.take(5),
            o.optString("endTime").ifBlank { o.optString("end") }.take(5),
            subj, o.optString("room"), tasks, mark
        )
    }

    private fun diaryUrl(s: Long, y: Long, start: LocalDate) =
        "/webapi/student/diary?studentId=$s&weekEnd=${start.plusDays(6)}&weekStart=$start" +
            "&withLaAssigns=true&withPastMandatory=true&yearId=$y"

    private fun lessonsOf(body: String): Pair<Int, List<Lesson>> {
        val parsed = parse(body)
        val out = mutableListOf<Lesson>()
        collect(parsed, null, out)
        val days = (parsed as? JSONObject)?.optJSONArray("weekDays")?.length() ?: 0
        val ls = out.distinctBy { "${it.id}|${it.date}|${it.number}|${it.subject}" }
            .sortedWith(compareBy({ it.date }, { it.number }))
        return days to ls
    }

    fun week(start: LocalDate): List<Lesson> =
        lessonsOf(get(diaryUrl(Session.studentId, Session.yearId, start))).second

    /** Перебирает возможные studentId/yearId и недели, пока не найдутся уроки. */
    fun probe(ws: LocalDate): Pair<LocalDate, List<Lesson>>? {
        val init = tryGet("/webapi/student/diary/init")
        logLine("init: " + (init?.toString() ?: "null").take(400))
        val years = tryGet("/webapi/years/current")
        logLine("years/current: " + (years?.toString() ?: "null").take(300))
        val ctx = tryGet("/webapi/context")
        logLine("context: " + (ctx?.toString() ?: "null").take(400))

        val sids = linkedSetOf<Long>()
        sids += Session.studentId
        sids += Session.idsFromSeen("studentId")
        sids += findLong(init, "currentStudentId", "studentId")
        sids += findLong(ctx, "currentStudentId", "studentId")
        val yids = linkedSetOf<Long>()
        yids += Session.yearId
        yids += Session.idsFromSeen("yearId")
        yids += findLong(init, "yearId", "currentYearId")
        yids += findLong(years, "id", "yearId")
        yids += findLong(ctx, "schoolYearId", "yearId", "currentYearId")
        sids.remove(0L); yids.remove(0L)
        logLine("candidates: students=$sids years=$yids")

        var n = 0
        for (s in sids) for (y in yids) {
            if (n++ >= 8) return null
            for (k in 0..3) {
                val w = ws.minusWeeks(k.toLong())
                val r = try { lessonsOf(get(diaryUrl(s, y, w))) }
                catch (e: AuthException) { throw e }
                catch (e: Exception) { logLine("probe s=$s y=$y w=$w error=${e.message}"); continue }
                logLine("probe s=$s y=$y w=$w days=${r.first} lessons=${r.second.size}")
                if (r.second.isNotEmpty()) {
                    Session.studentId = s; Session.yearId = y; Session.ready = true
                    Session.persistIds()
                    return w to r.second
                }
            }
        }
        return null
    }
}

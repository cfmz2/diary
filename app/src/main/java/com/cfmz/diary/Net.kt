package com.cfmz.diary

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

const val BASE = "https://asurso.ru"

class AuthException : Exception("Сессия истекла")

object Session {
    @Volatile var at = ""
    @Volatile var cookie = ""
    @Volatile var studentId = 0L
    @Volatile var yearId = 0L
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)
    fun load(c: Context): Boolean {
        at = p(c).getString("at", "") ?: ""
        cookie = p(c).getString("cookie", "") ?: ""
        return at.isNotEmpty()
    }
    fun save(c: Context) = p(c).edit().putString("at", at).putString("cookie", cookie).apply()
    fun clear(c: Context) { at = ""; cookie = ""; studentId = 0; yearId = 0; p(c).edit().clear().apply() }
}

data class Lesson(
    val id: Long, val date: LocalDate, val number: Int,
    val start: String, val end: String, val subject: String,
    val room: String, val tasks: List<String>, val mark: Int?
)

object Api {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    private fun get(path: String): String {
        val req = Request.Builder().url(BASE + path)
            .header("at", Session.at).header("Cookie", Session.cookie)
            .header("Accept", "application/json").build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403) throw AuthException()
            if (!r.isSuccessful) throw IOException("Ошибка сервера: ${r.code}")
            val body = r.body?.string().orEmpty()
            if (body.trimStart().startsWith("<")) throw AuthException()
            return body
        }
    }

    fun init() {
        runCatching { Session.yearId = JSONObject(get("/webapi/years/current")).optLong("id") }
            .onFailure { if (it is AuthException) throw it }
        val d = JSONObject(get("/webapi/student/diary/init"))
        val first = d.optJSONArray("students")?.optJSONObject(0)?.optLong("studentId") ?: 0L
        Session.studentId = d.optLong("currentStudentId", first).takeIf { it > 0 } ?: first
        if (Session.yearId == 0L) Session.yearId = d.optLong("yearId")
        if (Session.studentId == 0L || Session.yearId == 0L)
            throw IOException("Не удалось получить studentId/yearId")
    }

    fun week(start: LocalDate): List<Lesson> {
        val j = JSONObject(get(
            "/webapi/student/diary?studentId=${Session.studentId}&weekStart=$start" +
                "&weekEnd=${start.plusDays(6)}&yearId=${Session.yearId}"))
        val out = mutableListOf<Lesson>()
        val days = j.optJSONArray("weekDays") ?: return out
        for (i in 0 until days.length()) {
            val d = days.getJSONObject(i)
            val date = LocalDate.parse(d.getString("date").substring(0, 10))
            val ls = d.optJSONArray("lessons") ?: continue
            for (k in 0 until ls.length()) {
                val l = ls.getJSONObject(k)
                val tasks = mutableListOf<String>()
                var mark: Int? = null
                l.optJSONArray("assignments")?.let { a ->
                    for (q in 0 until a.length()) {
                        val t = a.getJSONObject(q)
                        val n = t.optString("assignmentName")
                        if (n.isNotBlank()) tasks += n
                        val m = t.optJSONObject("mark")?.optInt("mark", 0) ?: 0
                        if (m > 0) mark = m
                    }
                }
                out += Lesson(
                    l.optLong("classmeetingId"), date, l.optInt("number"),
                    l.optString("startTime"), l.optString("endTime"),
                    l.optString("subjectName"), l.optString("room"), tasks, mark
                )
            }
        }
        return out.sortedWith(compareBy({ it.date }, { it.number }))
    }
}

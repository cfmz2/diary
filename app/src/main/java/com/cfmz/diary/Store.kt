package com.cfmz.diary

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Hw(
    val key: String, val date: String, val subject: String,
    val text: String, val due: String, val done: Boolean
)

object HwStore {
    private lateinit var p: SharedPreferences
    val items = MutableStateFlow<List<Hw>>(emptyList())

    @Synchronized fun init(c: Context) {
        if (::p.isInitialized) return
        p = c.applicationContext.getSharedPreferences("hw", Context.MODE_PRIVATE)
        items.value = runCatching { read() }.getOrDefault(emptyList())
    }

    private fun read(): List<Hw> {
        val a = JSONArray(p.getString("v", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Hw(o.getString("key"), o.getString("date"), o.getString("subject"),
                o.getString("text"), o.getString("due"), o.optBoolean("done"))
        }
    }

    @Synchronized private fun write(l: List<Hw>) {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("key", it.key).put("date", it.date).put("subject", it.subject)
                .put("text", it.text).put("due", it.due).put("done", it.done))
        }
        p.edit().putString("v", a.toString()).apply()
        items.value = l
    }

    @Synchronized fun upsert(h: Hw) = write(items.value.filter { it.key != h.key } + h)
    @Synchronized fun remove(key: String) = write(items.value.filter { it.key != key })
    @Synchronized fun toggle(key: String) =
        write(items.value.map { if (it.key == key) it.copy(done = !it.done) else it })

    var hour: Int
        get() = p.getInt("hour", 18)
        set(v) = p.edit().putInt("hour", v.coerceIn(0, 23)).apply()
    var lastNotified: String
        get() = p.getString("last", "") ?: ""
        set(v) = p.edit().putString("last", v).apply()
}

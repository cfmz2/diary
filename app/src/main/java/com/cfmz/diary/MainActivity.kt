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
import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        HwStore.init(this)
        Reminder.ensureChannel(this)
        Reminder.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            DiaryTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var authed by remember { mutableStateOf(Session.load(ctx)) }
    fun logout() {
        Session.clear(ctx)
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        DiaryModel.reset()
        authed = false
    }
    LaunchedEffect(DiaryModel.authLost) { if (DiaryModel.authLost) logout() }
    Crossfade(targetState = authed, label = "auth") { a ->
        if (!a) LoginScreen { Session.save(ctx); authed = true }
        else MainScreen { logout() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val act = LocalContext.current as? ComponentActivity
    val done = remember { AtomicBoolean(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    BackHandler {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else act?.finish()
    }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }
    Column(Modifier.fillMaxSize().background(cs.background).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF6C5CE7), Color(0xFF3D8BFD)))),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.MenuBook, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Вход в дневник", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Войди через Госуслуги — дальше всё автоматически",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxSize(), color = cs.primary, trackColor = Color.Transparent)
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) { loading = true }
                        override fun onPageFinished(v: WebView, url: String?) { loading = false }
                        override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                            val u = r.url
                            if (u.host?.endsWith("asurso.ru") == true && u.path?.startsWith("/webapi/") == true) {
                                if (Session.seen.size < 300) Session.seen.add(u.toString())
                                val at = r.requestHeaders.entries
                                    .firstOrNull { it.key.equals("at", true) }?.value
                                if (!at.isNullOrBlank() && done.compareAndSet(false, true)) {
                                    Session.at = at
                                    v.postDelayed({
                                        Session.cookie = CookieManager.getInstance().getCookie(BASE) ?: Session.cookie
                                        onDone()
                                    }, 1500)
                                }
                            }
                            return null
                        }
                    }
                    web = this
                    loadUrl(BASE)
                }
            }
        )
    }
}

@Composable
fun MainScreen(onLogout: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val hw by HwStore.items.collectAsState()
    val pending = hw.count { !it.done }
    Scaffold(
        containerColor = cs.background,
        bottomBar = {
            NavigationBar(containerColor = cs.surface, tonalElevation = 0.dp) {
                NavItem(tab == 0, { tab = 0 }, Icons.Rounded.MenuBook, "Дневник")
                NavItem(tab == 1, { tab = 1 }, Icons.Rounded.Checklist, "ДЗ", pending)
                NavItem(tab == 2, { tab = 2 }, Icons.Rounded.Settings, "Ещё")
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            Crossfade(targetState = tab, label = "tab") { t ->
                when (t) {
                    0 -> DiaryTab()
                    1 -> HomeworkTab()
                    else -> MoreTab(onLogout)
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(selected: Boolean, onClick: () -> Unit, icon: ImageVector, label: String, badge: Int = 0) {
    val cs = MaterialTheme.colorScheme
    NavigationBarItem(
        selected = selected, onClick = onClick,
        icon = { BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) { Icon(icon, null) } },
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        colors = NavigationBarItemDefaults.colors(
            indicatorColor = cs.primaryContainer, selectedIconColor = cs.onPrimaryContainer,
            selectedTextColor = cs.primary, unselectedIconColor = cs.onSurfaceVariant,
            unselectedTextColor = cs.onSurfaceVariant
        )
    )
}

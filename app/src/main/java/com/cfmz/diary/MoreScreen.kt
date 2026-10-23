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
import android.widget.Toast
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.core.app.NotificationManagerCompat

@Composable
fun MoreTab(onLogout: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val hw by HwStore.items.collectAsState()
    var hour by remember { mutableIntStateOf(HwStore.hour) }
    var on by remember { mutableStateOf(HwStore.remindOn) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Ещё", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
        Card2(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = cs.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Напоминание о ДЗ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Каждый день, если есть невыполненное ДЗ на сегодня или завтра",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                        )
                    }
                    Switch(checked = on, onCheckedChange = { on = it; HwStore.remindOn = it })
                }
                AnimatedVisibility(visible = on) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            IconButton(onClick = { hour = (hour + 23) % 24; HwStore.hour = hour }) { Icon(Icons.Rounded.ChevronLeft, null) }
                            Text(
                                "%02d:00".format(hour), style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold, color = cs.primary,
                                modifier = Modifier.padding(horizontal = 18.dp)
                            )
                            IconButton(onClick = { hour = (hour + 1) % 24; HwStore.hour = hour }) { Icon(Icons.Rounded.ChevronRight, null) }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                                    Toast.makeText(ctx, "Разреши уведомления для приложения в настройках телефона", Toast.LENGTH_LONG).show()
                                } else {
                                    val list = hw.filter { !it.done }.ifEmpty {
                                        listOf(Hw("demo", "", "Математика", "Так будет выглядеть напоминание", "", false))
                                    }
                                    Reminder.notifyNow(ctx, list)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                        ) { Text("Отправить тестовое уведомление") }
                    }
                }
            }
        }
        Card2(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onLogout).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = cs.error)
                Spacer(Modifier.width(12.dp))
                Text("Выйти из аккаунта", color = cs.error, fontWeight = FontWeight.SemiBold)
            }
        }
        DiagCard()
        Text(
            "Дневник · версия 1.0", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center
        )
    }
}

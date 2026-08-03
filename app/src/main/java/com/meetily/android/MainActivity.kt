package com.meetily.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Bundle
import android.os.IBinder
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.log10
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object S {
    val bg = Color(0xFF100E13); val panel = Color(0xFF1A1720); val panel2 = Color(0xFF211D29)
    val panel3 = Color(0xFF282334); val line = Color(0xFF2A2633); val line2 = Color(0xFF3A3545)
    val text = Color(0xFFECE8F2); val muted = Color(0xFF8F8A99); val dim = Color(0xFF5C5766)
    val amber = Color(0xFFFFB454); val purple = Color(0xFFD0BCFF); val purpleDeep = Color(0xFF4F378B)
    val green = Color(0xFF7EE8A2); val red = Color(0xFFFF5C5C); val blue = Color(0xFF8AB4FF)
    val mono = FontFamily.Monospace
}

class AppState(val store: Store, val scope: CoroutineScope) {
    var service: RecorderService? = null
    var screen by mutableStateOf("home")
    var selectedId by mutableStateOf<Long?>(null)
    var processing by mutableStateOf(false)
    var procStep by mutableStateOf("")
    var procProgress by mutableStateOf(0f)
    var toast by mutableStateOf<String?>(null)
    var aiTest by mutableStateOf<String?>(null)
    val chat = mutableStateListOf<Pair<Boolean, String>>()

    fun fmt(ms: Long): String {
        val s = ms / 1000; val m = s / 60; val h = m / 60; val d = (ms % 1000) / 100
        val base = if (h > 0) String.format("%02d:%02d:%02d", h, m % 60, s % 60) else String.format("%02d:%02d", m, s % 60)
        return "$base.$d"
    }

    fun startRecording(ctx: Context) {
        val svc = service ?: run { toast = "Kayit servisi baglaniyor, tekrar dene."; return }
        val dir = File(ctx.filesDir, "recordings").apply { mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val path = File(dir, "meeting_$ts.wav").absolutePath
        val id = store.nextId()
        val title = "Toplanti " + SimpleDateFormat("dd MMM HH:mm", Locale("tr")).format(Date())
        store.add(Meeting(id = id, title = title, audioPath = path, createdAt = System.currentTimeMillis(), status = Meeting.STATUS_RECORDING))
        selectedId = id; screen = "rec"
        try { svc.startRecording(id, path, 16000) }
        catch (e: Exception) { toast = "Kayit baslatilamadi: ${e.message}"; store.update(id) { it.status = Meeting.STATUS_FAILED } }
    }

    fun stopRecording() {
        val svc = service ?: return; val id = selectedId ?: return
        val elapsed = svc.elapsedMillis.value; val file = svc.stopRecording()
        store.update(id) { it.durationMs = elapsed; it.status = Meeting.STATUS_TRANSCRIBING }
        processing = true; procStep = "WAV yaziliyor"; procProgress = 0.1f
        scope.launch {
            withContext(Dispatchers.IO) {
                val key = store.groqKey()
                procStep = if (key.isBlank()) "Groq anahtari yok - transkript atlanıyor" else "Whisper ile transkript"
                procProgress = 0.3f
                val tr = if (file != null && key.isNotBlank()) Api.transcribe(file, key).getOrNull().orEmpty() else ""
                store.update(id) { it.transcript = tr; it.status = Meeting.STATUS_SUMMARIZING }
                procStep = "NVIDIA ile ozet"; procProgress = 0.6f
                if (tr.isNotBlank()) {
                    Api.summarize(tr, store.nvidiaModel()).getOrNull()?.let { r ->
                        store.update(id) {
                            it.summary = r.summary; it.topicsJson = Api.toJsonArray(r.topics)
                            it.actionsJson = Api.toJsonArrayActions(r.actions); it.decisionsJson = Api.toJsonArray(r.decisions)
                            it.status = Meeting.STATUS_DONE
                        }
                    } ?: store.update(id) { it.status = Meeting.STATUS_DONE }
                } else store.update(id) { it.status = Meeting.STATUS_DONE }
            }
            procProgress = 1f; procStep = "Tamamlandi"; delay(500)
            processing = false; procStep = ""; procProgress = 0f; screen = "detail"
        }
    }

    fun pauseResume() {
        val svc = service ?: return
        if (svc.recordingState.value == RecordingState.PAUSED) svc.resumeRecording() else svc.pauseRecording()
    }

    fun testAi() {
        aiTest = "test ediliyor..."
        scope.launch {
            val r = withContext(Dispatchers.IO) { Api.ask("Tek kelimeyle yanit ver: calisiyor", "test", store.nvidiaModel()) }
            aiTest = r.fold(onSuccess = { "NVIDIA yanit verdi" }, onFailure = { "Hata: ${it.message}" })
        }
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, b: IBinder?) { state.service = (b as RecorderService.LocalBinder).getService() }
        override fun onServiceDisconnected(name: ComponentName?) { state.service = null }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        state = AppState(Store(this), MainScope())
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
            if (map.values.all { it }) bindSvc() else state.toast = "Mikrofon izni gerekli"
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = S.purple, onPrimary = S.purpleDeep, background = S.bg, surface = S.panel, onSurface = S.text, onSurfaceVariant = S.muted)) {
                Surface(color = S.bg, modifier = Modifier.fillMaxSize()) { AppRoot(state) }
            }
        }
        val need = mutableListOf(Manifest.permission.RECORD_AUDIO).apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS) }
        if (need.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) bindSvc() else launcher.launch(need.toTypedArray())
    }
    private fun bindSvc() { val i = Intent(this, RecorderService::class.java); startService(i); bindService(i, conn, Context.BIND_AUTO_CREATE) }
    override fun onDestroy() { runCatching { unbindService(conn) }; super.onDestroy() }
}

@Composable
fun AppRoot(state: AppState) {
    Box(modifier = Modifier.fillMaxSize().background(S.bg).statusBarsPadding().navigationBarsPadding()) {
        when (state.screen) {
            "home" -> HomeScreenV3(state); "rec" -> RecordingScreenV2(state)
            "detail" -> DetailScreenV2(state); "settings" -> SettingsScreen(state)
        }
        state.toast?.let { msg ->
            LaunchedEffect(msg) { delay(2200); state.toast = null }
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp).clip(RoundedCornerShape(99.dp)).background(S.text).padding(horizontal = 20.dp, vertical = 11.dp)) {
                Text(msg, color = S.bg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun TopBar(title: String, sub: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { onBack() }, contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = S.muted, modifier = Modifier.size(20.dp))
        } else Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = S.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, fontSize = 11.sp, color = S.muted)
        }
        trailing()
    }
}

@Composable
fun Led(color: Color, on: Boolean = true) { Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) color else S.panel3)) }

@Composable
fun StudioButton(label: String, color: Color, textColor: Color = S.bg, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(color).clickable(onClick = onClick).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(label, color = textColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun MetaChip(text: String, hot: Boolean = false) {
    Text(text, fontFamily = S.mono, fontSize = 8.5.sp, color = if (hot) S.amber else S.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.dp, if (hot) S.amber.copy(alpha = 0.35f) else S.line2, RoundedCornerShape(4.dp)).padding(horizontal = 9.dp, vertical = 4.dp))
}

fun fmtDur(ms: Long): String { val s = ms / 1000; return String.format("%02d:%02d", s / 60, s % 60) }

@Composable
fun HomeScreen(state: AppState) { val ctx = LocalContext.current
    val meetings = remember(state.processing) { state.store.list() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Meetily", "Gizlilik odakli toplanti asistani", trailing = {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { state.screen = "settings" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, null, tint = S.muted, modifier = Modifier.size(20.dp))
                }
            })
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (meetings.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Mic, null, tint = S.dim, modifier = Modifier.size(64.dp)); Spacer(Modifier.height(14.dp))
                        Text("Henuz toplanti yok", color = S.muted, fontSize = 14.sp)
                        Text("Baslatmak icin sag alttaki mikrofona dokun", color = S.dim, fontSize = 11.sp)
                    }
                }
                items(meetings, key = { it.id }) { m ->
                    MeetingCard(m, onClick = { state.selectedId = m.id; state.screen = "detail" }, onDelete = { state.store.delete(m.id) })
                    Spacer(Modifier.height(11.dp))
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp).clip(RoundedCornerShape(20.dp)).background(S.purple).clickable { vibrate(ctx, 60); state.startRecording(ctx) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, null, tint = S.purpleDeep, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun MeetingCard(m: Meeting, onClick: () -> Unit, onDelete: () -> Unit) {
    val accent = when (m.status) { Meeting.STATUS_DONE -> S.green; Meeting.STATUS_RECORDING -> S.red; Meeting.STATUS_FAILED -> S.red; else -> S.amber }
    val badge = when (m.status) { Meeting.STATUS_DONE -> "TAMAM" to S.green; Meeting.STATUS_RECORDING -> "KAYIT" to S.red; Meeting.STATUS_FAILED -> "HATA" to S.red; Meeting.STATUS_TRANSCRIBING -> "TRANSKRIP" to S.amber; else -> "OZET" to S.amber }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(S.panel).border(1.dp, S.line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(accent)); Spacer(Modifier.width(12.dp))
        WaveThumb(accent); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = S.text, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(4.dp))
            Text(SimpleDateFormat("dd MMM HH:mm", Locale("tr")).format(Date(m.createdAt)) + " - " + fmtDur(m.durationMs), fontFamily = S.mono, fontSize = 9.5.sp, color = S.muted)
        }
        Text(badge.first, fontFamily = S.mono, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = badge.second, modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(badge.second.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp))
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) { Icon(Icons.Default.Delete, null, tint = S.dim, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun WaveThumb(color: Color) {
    val heights = remember { listOf(0.3f, 0.6f, 0.9f, 0.5f, 0.75f, 0.4f) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(34.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        heights.forEach { h -> Box(Modifier.width(3.dp).height((h * 34).dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.7f))) }
    }
}

@Composable
fun RecordingScreen(state: AppState) {
    val svc = state.service
    val recState = svc?.recordingState?.collectAsState()?.value ?: RecordingState.IDLE
    val elapsed = svc?.elapsedMillis?.collectAsState()?.value ?: 0L
    val amp = svc?.amplitude?.collectAsState()?.value ?: 0f
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(svc) { svc?.let { s -> s.amplitude.collect { a -> history.add(a); while (history.size > 48) history.removeAt(0) } } }
    Column(Modifier.fillMaxSize()) {
        TopBar("Aktif Kayit", "Foreground Service - mikrofon", onBack = { if (!state.processing) state.screen = "home" })
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Led(S.red, recState == RecordingState.RECORDING); Spacer(Modifier.width(8.dp))
            Text(when (recState) { RecordingState.RECORDING -> "KAYIT"; RecordingState.PAUSED -> "DURAKLATILDI"; else -> "HAZIR" }, fontFamily = S.mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = S.red)
            Spacer(Modifier.weight(1f)); Text("CH-01 MIC", fontFamily = S.mono, fontSize = 9.sp, color = S.dim)
        }
        Text(state.fmt(elapsed), fontFamily = S.mono, fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = S.text, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center)
        WaveCanvas(history, amp, recState == RecordingState.RECORDING, Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip(state.store.get(state.selectedId ?: -1)?.audioPath?.substringAfterLast("/") ?: "meeting.wav", hot = true); MetaChip("PCM 16kHz"); MetaChip("16-BIT"); MetaChip("MONO")
        }
        LevelBar(amp); Pipeline(state)
        if (state.processing) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp).height(4.dp).clip(RoundedCornerShape(99.dp)).background(S.panel3)) {
                Box(Modifier.fillMaxWidth(state.procProgress).height(4.dp).clip(RoundedCornerShape(99.dp)).background(S.purple))
            }
            Text(state.procStep, fontFamily = S.mono, fontSize = 9.5.sp, color = S.purple, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        if (!state.processing) {
            Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(S.panel3).border(1.dp, S.line2, CircleShape).clickable { state.pauseResume() }, contentAlignment = Alignment.Center) {
                    Icon(if (recState == RecordingState.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = S.text, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(30.dp))
                Box(Modifier.size(78.dp).clip(CircleShape).background(S.red).clickable { state.stopRecording() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            }
        } else Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun LevelBar(amp: Float) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("SEVIYE", fontFamily = S.mono, fontSize = 8.5.sp, color = S.dim, modifier = Modifier.width(48.dp))
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(99.dp)).background(S.panel3)) { Box(Modifier.fillMaxWidth(amp.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(99.dp)).background(S.purple)) }
    }
}

@Composable
fun WaveCanvas(history: List<Float>, amp: Float, active: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val n = 48; val bw = size.width / (n * 1.8f); val cy = size.height / 2
        for (i in 0 until n) {
            val a = if (i < history.size) history[(history.size - n + i).coerceAtLeast(0)].coerceIn(0f, 1f) else if (i == n - 1) amp else 0.02f
            val h = (a * size.height * 0.86f).coerceAtLeast(3f); val x = i * (bw * 1.8f) + bw * 0.4f
            drawRoundRect(color = if (active) S.purple.copy(alpha = 0.3f + a * 0.7f) else S.line2, topLeft = Offset(x, cy - h / 2), size = Size(bw, h), cornerRadius = CornerRadius(bw / 2))
        }
        drawRect(color = S.purple.copy(alpha = 0.25f), topLeft = Offset(0f, cy - 0.5f), size = Size(size.width, 1f))
    }
}

@Composable
fun Pipeline(state: AppState) {
    val rec = state.service?.recordingState?.collectAsState()?.value
    val step = when { !state.processing && rec == RecordingState.RECORDING -> 0; state.procProgress < 0.5f -> 1; state.procProgress < 0.9f -> 2; state.processing -> 3; else -> 0 }
    val labels = listOf("KAYIT", "TRANSKRIP", "AI OZET")
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, lab ->
            val done = state.processing && i < step; val on = if (state.processing) i == step else i == 0
            val col = if (done) S.green else if (on) S.purple else S.dim
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (done || on) col else S.panel3).border(1.dp, if (done || on) col else S.line2, CircleShape))
                Spacer(Modifier.height(6.dp)); Text(lab, fontFamily = S.mono, fontSize = 8.sp, color = col)
            }
            if (i < labels.size - 1) Box(Modifier.width(36.dp).height(1.dp).padding(bottom = 16.dp).background(if (state.processing && i < step) S.purple else S.line2))
        }
    }
}

@Composable
fun DetailScreen(state: AppState) {
    val m = remember(state.selectedId, state.processing) { state.store.get(state.selectedId ?: -1) }
    val clipboard = LocalClipboardManager.current; val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }; var input by remember { mutableStateOf("") }
    if (m == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Toplanti bulunamadi", color = S.muted) }; return }
    Column(Modifier.fillMaxSize()) {
        TopBar(m.title, onBack = { state.screen = "home" }, trailing = {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { clipboard.setText(AnnotatedString(m.summary.ifBlank { m.transcript })); state.toast = "Panoya kopyalandi" }, contentAlignment = Alignment.Center) { Icon(Icons.Default.ContentCopy, null, tint = S.muted, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(2.dp))
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.summary.ifBlank { m.transcript }) }; ctx.startActivity(Intent.createChooser(i, "Paylas")) }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Share, null, tint = S.muted, modifier = Modifier.size(18.dp)) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetaChip("S " + fmtDur(m.durationMs)); MetaChip("16000Hz"); MetaChip("nvidia") }
            if (m.summary.isNotBlank()) Section("AI OZET", S.purple, S.purpleDeep) {
                Text(m.summary, fontSize = 12.sp, lineHeight = 20.sp, color = Color(0xFFC9C3D4))
                if (m.topics().isNotEmpty()) { Spacer(Modifier.height(12.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { m.topics().forEach { Text(it, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = S.purple, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(S.purple.copy(alpha = 0.12f)).border(1.dp, S.purple.copy(alpha = 0.2f), RoundedCornerShape(99.dp)).padding(horizontal = 11.dp, vertical = 5.dp)) } } }
                m.decisions().forEach { d -> Row(Modifier.padding(top = 7.dp)) { Box(Modifier.width(2.dp).height(14.dp).background(S.purple)); Spacer(Modifier.width(9.dp)); Text(d, fontSize = 10.5.sp, color = Color(0xFFC9C3D4)) } }
            }
            if (m.actions().isNotEmpty()) Section("AKSIYON MADDELERI", S.amber, Color(0xFF3A2A12)) {
                m.actions().forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(String.format("%02d", i + 1), fontFamily = S.mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = S.amber, modifier = Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.task, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = S.text)
                            Text(a.assignee + if (a.deadline.isNotBlank()) " - " + a.deadline else "", fontFamily = S.mono, fontSize = 8.5.sp, color = S.muted)
                        }
                        val pc = when (a.priority) { "KRITIK" -> S.red; "YUKSEK" -> S.amber; else -> S.green }
                        Text(a.priority, fontFamily = S.mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = pc, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(pc.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).clip(RoundedCornerShape(13.dp)).background(S.panel).border(1.dp, S.line, RoundedCornerShape(13.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = S.dim, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(10.dp))
                BasicTextField(value = query, onValueChange = { query = it }, singleLine = true, textStyle = TextStyle(color = S.text, fontSize = 12.sp), modifier = Modifier.weight(1f), decorationBox = { inner -> if (query.isEmpty()) Text("Transkriptte ara...", color = S.dim, fontSize = 12.sp); inner() })
            }
            Text("TRANSKRIPT", fontFamily = S.mono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = S.purple, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            if (m.transcript.isBlank()) Text("Transkript yok. Ayarlardan Groq anahtari ekleyip tekrar kaydet.", color = S.dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp))
            else m.transcript.split(Regex("(?<=\\.)\\s+")).filter { it.isNotBlank() }.forEachIndexed { i, sent ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
                    Text(String.format("%02d", i + 1), fontFamily = S.mono, fontSize = 8.5.sp, color = S.dim, modifier = Modifier.width(26.dp).padding(top = 3.dp))
                    Text(highlight(sent, query), fontSize = 11.sp, lineHeight = 18.sp, color = Color(0xFFB7B1C2), modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("AI'YA SOR", fontFamily = S.mono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = S.purple, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            state.chat.forEach { (u, t) ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), contentAlignment = if (u) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(if (u) S.purpleDeep else S.panel2).border(if (u) 0.dp else 1.dp, S.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Text(if (u) "SEN" else "MEETILY AI", fontFamily = S.mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (u) S.purple else S.blue); Spacer(Modifier.height(4.dp)); Text(t, fontSize = 11.sp, lineHeight = 17.sp, color = S.text)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true, placeholder = { Text("Soru sorun...", color = S.dim, fontSize = 12.sp) }, colors = tfColors(), modifier = Modifier.weight(1f), textStyle = TextStyle(color = S.text, fontSize = 12.sp))
                Spacer(Modifier.width(9.dp))
                Box(Modifier.size(44.dp).clip(CircleShape).background(S.purple).clickable {
                    val q = input.trim(); if (q.isBlank()) return@clickable; input = ""; state.chat.add(true to q)
                    state.scope.launch { val r = withContext(Dispatchers.IO) { Api.ask(q, m.transcript, state.store.nvidiaModel()) }; state.chat.add(false to r.fold(onSuccess = { it }, onFailure = { "Hata: ${it.message}" })) }
                }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = S.purpleDeep, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
fun Section(title: String, accent: Color, bg: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp).clip(RoundedCornerShape(16.dp)).background(bg.copy(alpha = 0.5f)).border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(16.dp)).padding(15.dp)) {
        Text(title, fontFamily = S.mono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = accent); Spacer(Modifier.height(10.dp)); content()
    }
}

fun highlight(text: String, q: String): AnnotatedString {
    if (q.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var idx = 0; val low = text.lowercase(); val ql = q.lowercase()
        while (idx < text.length) {
            val p = low.indexOf(ql, idx)
            if (p < 0) { append(text.substring(idx)); break }
            append(text.substring(idx, p)); withStyle(SpanStyle(background = S.amber.copy(alpha = 0.3f), color = S.amber)) { append(text.substring(p, p + ql.length)) }; idx = p + ql.length
        }
    }
}

@Composable
fun SettingsScreen(state: AppState) {
    var groq by remember { mutableStateOf(state.store.groqKey()) }; var model by remember { mutableStateOf(state.store.nvidiaModel()) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Ayarlar", "Yapilandirma - motor - veri", onBack = { state.screen = "home" })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp)) {
            SetCard("NVIDIA (OZET + SOHBET)") {
                Text("Anahtar uygulamaya gomulu - model asagidan degisir", fontFamily = S.mono, fontSize = 9.sp, color = S.green); Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("NVIDIA MODEL", fontFamily = S.mono, fontSize = 8.sp, color = S.dim) }, singleLine = true, colors = tfColors(), modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = S.text, fontFamily = S.mono, fontSize = 11.sp)); Spacer(Modifier.height(8.dp))
                StudioButton("Modeli Kaydet", S.purple, modifier = Modifier.fillMaxWidth(), onClick = { state.store.setNvidiaModel(model); state.toast = "Model kaydedildi" }); Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { StudioButton("NVIDIA Test", S.panel3, S.text, onClick = { state.testAi() }); Spacer(Modifier.width(10.dp)); state.aiTest?.let { Text(it, fontFamily = S.mono, fontSize = 9.5.sp, color = if (it.startsWith("NVIDIA")) S.green else S.red) } }
            }
            SetCard("TRANSKRIPSIYON (GROQ WHISPER)") {
                Text("Ucretsiz anahtar: console.groq.com", fontFamily = S.mono, fontSize = 9.sp, color = S.muted); Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = groq, onValueChange = { groq = it }, label = { Text("GROQ API KEY", fontFamily = S.mono, fontSize = 8.sp, color = S.dim) }, singleLine = true, colors = tfColors(), modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = S.text, fontFamily = S.mono, fontSize = 11.sp)); Spacer(Modifier.height(8.dp))
                StudioButton("Groq Anahtarini Kaydet", S.purple, modifier = Modifier.fillMaxWidth(), onClick = { state.store.setGroqKey(groq.trim()); state.toast = "Groq anahtari kaydedildi" })
            }
            SetCard("SES KAYDI") { Text("16000 Hz - WAV PCM - 16-bit mono", fontFamily = S.mono, fontSize = 9.sp, color = S.muted) }
            SetCard("VERI") {
                Text(state.store.list().size.toString() + " toplanti - yerel depolama", fontFamily = S.mono, fontSize = 9.sp, color = S.muted); Spacer(Modifier.height(10.dp))
                StudioButton("Tum Verileri Sil", S.red.copy(alpha = 0.15f), S.red, modifier = Modifier.fillMaxWidth(), onClick = { state.store.list().forEach { state.store.delete(it.id) }; state.toast = "Tum veriler silindi" })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SetCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp).clip(RoundedCornerShape(16.dp)).background(S.panel).border(1.dp, S.line, RoundedCornerShape(16.dp)).padding(15.dp)) {
        Text(title, fontFamily = S.mono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = S.purple); Spacer(Modifier.height(12.dp)); content()
    }
}

@Composable
fun tfColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = S.purpleDeep, unfocusedBorderColor = S.line, cursorColor = S.purple, focusedContainerColor = S.panel2, unfocusedContainerColor = S.panel2)

fun fmtBytes(b: Long): String = if (b < 1048576) (b / 1024).toString() + " KB" else String.format("%.1f MB", b / 1048576.0)
fun fmtTotal(ms: Long): String { val m = ms / 60000; return if (m < 60) m.toString() + " dk" else (m / 60).toString() + " s " + (m % 60) + " dk" }

@Composable
fun StatTile(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(S.panel).border(1.dp, S.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(value, fontFamily = S.mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Spacer(Modifier.height(3.dp)); Text(label, fontSize = 9.sp, color = S.muted)
    }
}

@Composable
fun HomeScreenV2(state: AppState) {
    val meetings = remember(state.processing) { state.store.list() }
    var q by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val shown = if (q.isBlank()) meetings else meetings.filter { it.title.contains(q, true) }
    val totalMs = meetings.sumOf { it.durationMs }
    val totalBytes = meetings.sumOf { File(it.audioPath).length() }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Meetily", "Gizlilik odakli toplanti asistani", trailing = {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { state.screen = "settings" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, null, tint = S.muted, modifier = Modifier.size(20.dp))
                }
            })
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(meetings.size.toString(), "TOPLANTI", S.purple, Modifier.weight(1f))
                StatTile(fmtTotal(totalMs), "SURE", S.amber, Modifier.weight(1f))
                StatTile(fmtBytes(totalBytes), "DEPO", S.green, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(13.dp)).background(S.panel).border(1.dp, S.line, RoundedCornerShape(13.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = S.dim, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(10.dp))
                BasicTextField(value = q, onValueChange = { q = it }, singleLine = true, textStyle = TextStyle(color = S.text, fontSize = 12.sp), modifier = Modifier.weight(1f), decorationBox = { inner -> if (q.isEmpty()) Text("Toplantilarda ara...", color = S.dim, fontSize = 12.sp); inner() })
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 10.dp)) {
                if (shown.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Mic, null, tint = S.dim, modifier = Modifier.size(64.dp)); Spacer(Modifier.height(14.dp))
                        Text(if (q.isBlank()) "Henuz toplanti yok" else "Sonuc bulunamadi", color = S.muted, fontSize = 14.sp)
                    }
                }
                items(shown, key = { it.id }) { m ->
                    MeetingCard(m, onClick = { state.selectedId = m.id; state.screen = "detail" }, onDelete = { state.store.delete(m.id) })
                    Spacer(Modifier.height(11.dp))
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp).clip(RoundedCornerShape(20.dp)).background(S.purple).clickable { vibrate(ctx, 60); state.startRecording(ctx) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, null, tint = S.purpleDeep, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun HomeScreenV3(state: AppState) {
    val meetings = remember(state.processing) { state.store.list() }
    var q by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Meeting?>(null) }
    val ctx = LocalContext.current
    val shown = if (q.isBlank()) meetings else meetings.filter { it.title.contains(q, true) }
    val totalMs = meetings.sumOf { it.durationMs }
    val totalBytes = meetings.sumOf { File(it.audioPath).length() }

    val m = deleteTarget
    if (m != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Toplantıyı sil", fontWeight = FontWeight.Bold) },
            text = { Text(m.title + " kalici olarak silinsin mi? Ses dosyasi ve ozet silinecek.") },
            confirmButton = { TextButton(onClick = { state.store.delete(m.id); deleteTarget = null }) { Text("Sil", color = S.red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Vazgeç", color = S.muted) } }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Meetily", "Gizlilik odakli toplanti asistani", trailing = {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { state.screen = "settings" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Settings, null, tint = S.muted, modifier = Modifier.size(20.dp))
                }
            })
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(meetings.size.toString(), "TOPLANTI", S.purple, Modifier.weight(1f))
                StatTile(fmtTotal(totalMs), "SURE", S.amber, Modifier.weight(1f))
                StatTile(fmtBytes(totalBytes), "DEPO", S.green, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(13.dp)).background(S.panel).border(1.dp, S.line, RoundedCornerShape(13.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = S.dim, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(10.dp))
                BasicTextField(value = q, onValueChange = { q = it }, singleLine = true, textStyle = TextStyle(color = S.text, fontSize = 12.sp), modifier = Modifier.weight(1f), decorationBox = { inner -> if (q.isEmpty()) Text("Toplantilarda ara...", color = S.dim, fontSize = 12.sp); inner() })
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 10.dp)) {
                if (shown.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Mic, null, tint = S.dim, modifier = Modifier.size(64.dp)); Spacer(Modifier.height(14.dp))
                        Text(if (q.isBlank()) "Henuz toplanti yok" else "Sonuc bulunamadi", color = S.muted, fontSize = 14.sp)
                    }
                }
                items(shown, key = { it.id }) { m ->
                    MeetingCard(m, onClick = { state.selectedId = m.id; state.screen = "detail" }, onDelete = { deleteTarget = m })
                    Spacer(Modifier.height(11.dp))
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp).clip(RoundedCornerShape(20.dp)).background(S.purple).clickable { vibrate(ctx, 60); state.startRecording(ctx) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Mic, null, tint = S.purpleDeep, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun VuMeter(amp: Float) {
    val seg = 12
    val lit = (amp.coerceIn(0f, 1f) * seg).toInt()
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until seg) {
            val col = if (i < 8) S.green else if (i < 10) S.amber else S.red
            Box(Modifier.width(6.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(if (i < lit) col else S.panel3))
        }
    }
}

@Composable
fun RecordingScreenV2(state: AppState) {
    val svc = state.service
    val recState = svc?.recordingState?.collectAsState()?.value ?: RecordingState.IDLE
    val elapsed = svc?.elapsedMillis?.collectAsState()?.value ?: 0L
    val amp = svc?.amplitude?.collectAsState()?.value ?: 0f
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(svc) { svc?.let { s -> s.amplitude.collect { a -> history.add(a); while (history.size > 48) history.removeAt(0) } } }
    Column(Modifier.fillMaxSize()) {
        TopBar("Aktif Kayit", "Foreground Service - mikrofon", onBack = { if (!state.processing) state.screen = "home" })
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Led(S.red, recState == RecordingState.RECORDING); Spacer(Modifier.width(8.dp))
            Text(when (recState) { RecordingState.RECORDING -> "KAYIT"; RecordingState.PAUSED -> "DURAKLATILDI"; else -> "HAZIR" }, fontFamily = S.mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = S.red)
            Spacer(Modifier.weight(1f)); Text("CH-01 MIC", fontFamily = S.mono, fontSize = 9.sp, color = S.dim)
        }
        Text(state.fmt(elapsed), fontFamily = S.mono, fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = S.text, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center)
        WaveCanvas(history, amp, recState == RecordingState.RECORDING, Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { LevelBar(amp) }
            Spacer(Modifier.width(16.dp)); VuMeter(amp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip(state.store.get(state.selectedId ?: -1)?.audioPath?.substringAfterLast("/") ?: "meeting.wav", hot = true); MetaChip("PCM 16kHz"); MetaChip("16-BIT"); MetaChip("MONO")
        }
        Pipeline(state)
        if (state.processing) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp).height(4.dp).clip(RoundedCornerShape(99.dp)).background(S.panel3)) {
                Box(Modifier.fillMaxWidth(state.procProgress).height(4.dp).clip(RoundedCornerShape(99.dp)).background(S.purple))
            }
            Text(state.procStep, fontFamily = S.mono, fontSize = 9.5.sp, color = S.purple, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        if (!state.processing) {
            Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(S.panel3).border(1.dp, S.line2, CircleShape).clickable { state.pauseResume() }, contentAlignment = Alignment.Center) {
                    Icon(if (recState == RecordingState.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = S.text, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(30.dp))
                Box(Modifier.size(78.dp).clip(CircleShape).background(S.red).clickable { state.stopRecording() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            }
        } else Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun RecordingScreenV4(state: AppState) {
    val svc = state.service
    val recState = svc?.recordingState?.collectAsState()?.value ?: RecordingState.IDLE
    val elapsed = svc?.elapsedMillis?.collectAsState()?.value ?: 0L
    val amp = svc?.amplitude?.collectAsState()?.value ?: 0f
    var peak by remember { mutableStateOf(0.05f) }
    val disp = (amp / peak).coerceIn(0f, 1f)
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(svc) { svc?.let { s -> s.amplitude.collect { a -> peak = maxOf(peak * 0.995f, a, 0.01f); history.add((a / peak).coerceIn(0f, 1f)); while (history.size > 48) history.removeAt(0) } } }
    Column(Modifier.fillMaxSize()) {
        TopBar("Aktif Kayit", "Foreground Service - mikrofon", onBack = { if (!state.processing) state.screen = "home" })
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Led(S.red, recState == RecordingState.RECORDING); Spacer(Modifier.width(8.dp))
            Text(when (recState) { RecordingState.RECORDING -> "KAYIT"; RecordingState.PAUSED -> "DURAKLATILDI"; else -> "HAZIR" }, fontFamily = S.mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = S.red)
            Spacer(Modifier.weight(1f)); Text("CH-01 MIC", fontFamily = S.mono, fontSize = 9.sp, color = S.dim)
        }
        Text(state.fmt(elapsed), fontFamily = S.mono, fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = S.text, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center)
        WaveCanvas(history, disp, recState == RecordingState.RECORDING, Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { LevelBar(disp) }
            Spacer(Modifier.width(16.dp)); VuMeter(disp)
            Spacer(Modifier.width(10.dp)); Text("IN " + String.format("%.2f", amp), fontFamily = S.mono, fontSize = 8.sp, color = S.dim)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip(state.store.get(state.selectedId ?: -1)?.audioPath?.substringAfterLast("/") ?: "meeting.wav", hot = true); MetaChip("PCM 16kHz"); MetaChip("16-BIT"); MetaChip("MONO")
        }
        Pipeline(state)
        if (state.processing) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp).height(4.dp).clip(RoundedCornerShape(99.dp)).background(S.panel3)) {
                Box(Modifier.fillMaxWidth(state.procProgress).height(4.dp).clip(RoundedCornerShape(99.dp)).background(S.purple))
            }
            Text(state.procStep, fontFamily = S.mono, fontSize = 9.5.sp, color = S.purple, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        if (!state.processing) {
            Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(S.panel3).border(1.dp, S.line2, CircleShape).clickable { state.pauseResume() }, contentAlignment = Alignment.Center) {
                    Icon(if (recState == RecordingState.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = S.text, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(30.dp))
                Box(Modifier.size(78.dp).clip(CircleShape).background(S.red).clickable { state.stopRecording() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            }
        } else Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun DetailScreenV2(state: AppState) {
    val m = remember(state.selectedId, state.processing) { state.store.get(state.selectedId ?: -1) }
    val clipboard = LocalClipboardManager.current; val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }; var input by remember { mutableStateOf("") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0f) }
    DisposableEffect(Unit) { onDispose { runCatching { player?.release() }; player = null } }
    LaunchedEffect(playing) { while (playing) { player?.let { pos = it.currentPosition.toFloat() }; delay(200) } }
    if (m == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Toplanti bulunamadi", color = S.muted) }; return }
    val mm = m; val dur = mm.durationMs.coerceAtLeast(1).toFloat()
    Column(Modifier.fillMaxSize()) {
        TopBar(mm.title, onBack = { runCatching { player?.release() }; state.screen = "home" }, trailing = {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable { clipboard.setText(AnnotatedString(mm.summary.ifBlank { mm.transcript })); state.toast = "Panoya kopyalandi" }, contentAlignment = Alignment.Center) { Icon(Icons.Default.ContentCopy, null, tint = S.muted, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(2.dp))
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).clickable {
                try {
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(mm.audioPath))
                    val i = Intent(Intent.ACTION_SEND).apply { type = "audio/wav"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    ctx.startActivity(Intent.createChooser(i, "Sesi paylas"))
                } catch (e: Exception) { state.toast = "Paylasim hatasi: ${e.message}" }
            }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Share, null, tint = S.muted, modifier = Modifier.size(18.dp)) }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetaChip("S " + fmtDur(mm.durationMs)); MetaChip(fmtBytes(File(mm.audioPath).length())); MetaChip("16000Hz"); MetaChip("nvidia") }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(S.panel).border(1.dp, S.line, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(S.purple).clickable {
                    try {
                        if (player == null) player = MediaPlayer().apply { setDataSource(mm.audioPath); prepare() }
                        if (playing) { player?.pause(); playing = false } else { player?.start(); playing = true }
                    } catch (e: Exception) { state.toast = "Oynatma hatasi: ${e.message}" }
                }, contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = S.purpleDeep, modifier = Modifier.size(20.dp)) }
                Spacer(Modifier.width(10.dp))
                Slider(value = pos.coerceIn(0f, dur), onValueChange = { pos = it; player?.seekTo(it.toInt()) }, valueRange = 0f..dur, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp)); Text(fmtDur(pos.toLong()) + "/" + fmtDur(mm.durationMs), fontFamily = S.mono, fontSize = 8.5.sp, color = S.muted)
            }
            if (mm.summary.isNotBlank()) Section("AI OZET", S.purple, S.purpleDeep) {
                Text(mm.summary, fontSize = 12.sp, lineHeight = 20.sp, color = Color(0xFFC9C3D4))
                if (mm.topics().isNotEmpty()) { Spacer(Modifier.height(12.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { mm.topics().forEach { Text(it, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = S.purple, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(S.purple.copy(alpha = 0.12f)).border(1.dp, S.purple.copy(alpha = 0.2f), RoundedCornerShape(99.dp)).padding(horizontal = 11.dp, vertical = 5.dp)) } } }
                mm.decisions().forEach { d -> Row(Modifier.padding(top = 7.dp)) { Box(Modifier.width(2.dp).height(14.dp).background(S.purple)); Spacer(Modifier.width(9.dp)); Text(d, fontSize = 10.5.sp, color = Color(0xFFC9C3D4)) } }
            }
            if (mm.actions().isNotEmpty()) Section("AKSIYON MADDELERI", S.amber, Color(0xFF3A2A12)) {
                mm.actions().forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(String.format("%02d", i + 1), fontFamily = S.mono, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = S.amber, modifier = Modifier.width(22.dp))
                        Column(Modifier.weight(1f)) { Text(a.task, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = S.text); Text(a.assignee + if (a.deadline.isNotBlank()) " - " + a.deadline else "", fontFamily = S.mono, fontSize = 8.5.sp, color = S.muted) }
                        val pc = when (a.priority) { "KRITIK" -> S.red; "YUKSEK" -> S.amber; else -> S.green }
                        Text(a.priority, fontFamily = S.mono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = pc, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(pc.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 3.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp).clip(RoundedCornerShape(13.dp)).background(S.panel).border(1.dp, S.line, RoundedCornerShape(13.dp)).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = S.dim, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(10.dp))
                BasicTextField(value = query, onValueChange = { query = it }, singleLine = true, textStyle = TextStyle(color = S.text, fontSize = 12.sp), modifier = Modifier.weight(1f), decorationBox = { inner -> if (query.isEmpty()) Text("Transkriptte ara...", color = S.dim, fontSize = 12.sp); inner() })
            }
            Text("TRANSKRIPT", fontFamily = S.mono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = S.purple, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            if (mm.transcript.isBlank()) Text("Transkript yok. Ayarlardan Groq anahtari ekleyip tekrar kaydet.", color = S.dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp))
            else mm.transcript.split(Regex("(?<=\\.)\\s+")).filter { it.isNotBlank() }.forEachIndexed { i, sent ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
                    Text(String.format("%02d", i + 1), fontFamily = S.mono, fontSize = 8.5.sp, color = S.dim, modifier = Modifier.width(26.dp).padding(top = 3.dp))
                    Text(highlight(sent, query), fontSize = 11.sp, lineHeight = 18.sp, color = Color(0xFFB7B1C2), modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("AI'YA SOR", fontFamily = S.mono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = S.purple, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            state.chat.forEach { (u, t) ->
                Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), contentAlignment = if (u) Alignment.CenterEnd else Alignment.CenterStart) {
                    Column(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(14.dp)).background(if (u) S.purpleDeep else S.panel2).border(if (u) 0.dp else 1.dp, S.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Text(if (u) "SEN" else "MEETILY AI", fontFamily = S.mono, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (u) S.purple else S.blue); Spacer(Modifier.height(4.dp)); Text(t, fontSize = 11.sp, lineHeight = 17.sp, color = S.text)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true, placeholder = { Text("Soru sorun...", color = S.dim, fontSize = 12.sp) }, colors = tfColors(), modifier = Modifier.weight(1f), textStyle = TextStyle(color = S.text, fontSize = 12.sp))
                Spacer(Modifier.width(9.dp))
                Box(Modifier.size(44.dp).clip(CircleShape).background(S.purple).clickable {
                    val q = input.trim(); if (q.isBlank()) return@clickable; input = ""; state.chat.add(true to q)
                    state.scope.launch { val r = withContext(Dispatchers.IO) { Api.ask(q, mm.transcript, state.store.nvidiaModel()) }; state.chat.add(false to r.fold(onSuccess = { it }, onFailure = { "Hata: ${it.message}" })) }
                }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = S.purpleDeep, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

fun vibrate(ctx: Context, ms: Long) {
    try {
        val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (e: Exception) {}
}

fun dbStr(a: Float): String {
    val d = (20 * log10(maxOf(a, 0.0001f))).toInt()
    return d.toString() + " dB"
}

@Composable
fun RecordingScreenV5(state: AppState) {
    val svc = state.service
    val ctx = LocalContext.current
    val recState = svc?.recordingState?.collectAsState()?.value ?: RecordingState.IDLE
    val elapsed = svc?.elapsedMillis?.collectAsState()?.value ?: 0L
    val amp = svc?.amplitude?.collectAsState()?.value ?: 0f
    var peak by remember { mutableStateOf(0.05f) }
    val disp = (amp / peak).coerceIn(0f, 1f)
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(svc) { svc?.let { s -> s.amplitude.collect { a -> peak = maxOf(peak * 0.995f, a, 0.01f); history.add((a / peak).coerceIn(0f, 1f)); while (history.size > 48) history.removeAt(0) } } }
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(initialValue = 1f, targetValue = 0.25f, animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse")
    Column(Modifier.fillMaxSize()) {
        TopBar("Aktif Kayit", "Foreground Service - mikrofon", onBack = { if (!state.processing) state.screen = "home" })
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(S.red.copy(alpha = if (recState == RecordingState.RECORDING) pulse else 0.3f)))
            Spacer(Modifier.width(8.dp))
            Text(when (recState) { RecordingState.RECORDING -> "KAYIT"; RecordingState.PAUSED -> "DURAKLATILDI"; else -> "HAZIR" }, fontFamily = S.mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = S.red)
            Spacer(Modifier.weight(1f)); Text(dbStr(amp), fontFamily = S.mono, fontSize = 9.sp, color = S.dim)
        }
        Text(state.fmt(elapsed), fontFamily = S.mono, fontSize = 46.sp, fontWeight = FontWeight.SemiBold, color = S.text, modifier = Modifier.fillMaxWidth().padding(top = 18.dp), textAlign = TextAlign.Center)
        WaveCanvas(history, disp, recState == RecordingState.RECORDING, Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 12.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { LevelBar(disp) }
            Spacer(Modifier.width(16.dp)); VuMeter(disp)
            Spacer(Modifier.width(10.dp)); Text("IN " + String.format("%.2f", amp), fontFamily = S.mono, fontSize = 8.sp, color = S.dim)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip(state.store.get(state.selectedId ?: -1)?.audioPath?.substringAfterLast("/") ?: "meeting.wav", hot = true); MetaChip("PCM 16kHz"); MetaChip("16-BIT"); MetaChip("MONO")
        }
        Pipeline(state)
        if (state.processing) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 26.dp, vertical = 6.dp).height(4.dp).clip(RoundedCornerShape(99.dp)).background(S.panel3)) {
                Box(Modifier.fillMaxWidth(state.procProgress).height(4.dp).clip(RoundedCornerShape(99.dp)).background(S.purple))
            }
            Text(state.procStep, fontFamily = S.mono, fontSize = 9.5.sp, color = S.purple, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        if (!state.processing) {
            Row(Modifier.fillMaxWidth().padding(bottom = 30.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(S.panel3).border(1.dp, S.line2, CircleShape).clickable { vibrate(ctx, 40); state.pauseResume() }, contentAlignment = Alignment.Center) {
                    Icon(if (recState == RecordingState.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = S.text, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(30.dp))
                Box(Modifier.size(78.dp).clip(CircleShape).background(S.red).clickable { vibrate(ctx, 120); state.stopRecording() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
            }
        } else Spacer(Modifier.height(30.dp))
    }
}

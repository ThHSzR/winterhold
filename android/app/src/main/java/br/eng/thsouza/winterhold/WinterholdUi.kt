package br.eng.thsouza.winterhold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF111820)
private val Panel = Color(0xFF1D2830)
private val PanelLight = Color(0xFF29404A)
private val Ice = Color(0xFF7EDBD2)
private val Muted = Color(0xFFA9C2C9)
private val Amber = Color(0xFFFFBC73)
private val Red = Color(0xFFFF8297)

private fun environmentIcon(code: String) = when (code) {
    "classroom" -> "🏫"; "lab" -> "🧪"; "office" -> "🏢"; "server" -> "🖥️"; else -> "❄️"
}

private val WinterholdColors = darkColorScheme(
    primary = Ice,
    onPrimary = Ink,
    secondary = Amber,
    background = Ink,
    onBackground = Color(0xFFF0F8F9),
    surface = Panel,
    onSurface = Color(0xFFF0F8F9),
    surfaceVariant = PanelLight,
    onSurfaceVariant = Muted,
    outline = Color(0xFF44606A),
    error = Red
)

private enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Outlined.Home),
    ENVIRONMENTS("Ambientes", Icons.Outlined.Place),
    HISTORY("Histórico", Icons.Outlined.History),
    ALERTS("Alertas", Icons.Outlined.Notifications),
    PEOPLE("Equipe", Icons.Outlined.Groups),
    SETTINGS("Ajustes", Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinterholdApp(store: DemoStore = remember { DemoStore() }) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    MaterialTheme(colorScheme = WinterholdColors) {
        Scaffold(
            containerColor = Ink,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(Ice),
                                contentAlignment = Alignment.Center) {
                                Text("W", color = Ink, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("WINTERHOLD", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                letterSpacing = 1.5.sp)
                        }
                    },
                    actions = {
                        if (screen == Screen.ALERTS) { TextButton(onClick = { screen = Screen.PEOPLE }) { Text("Equipe") } }
                        if (screen != Screen.SETTINGS) {
                            IconButton(onClick = { screen = Screen.SETTINGS }) {
                                Icon(Icons.Outlined.Settings, contentDescription = "Abrir ajustes")
                            }
                        } else {
                            TextButton(onClick = { screen = Screen.HOME }) { Text("Fechar") }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    listOf(Screen.HOME, Screen.ENVIRONMENTS, Screen.ALERTS, Screen.HISTORY).forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = { screen = item },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { padding ->
            val modifier = Modifier.fillMaxSize().padding(padding)
            when (screen) {
                Screen.HOME -> HomeScreen(store, modifier,
                    onAlerts = { screen = Screen.ALERTS }, onHistory = { screen = Screen.HISTORY }, onEnvironments = { screen = Screen.ENVIRONMENTS })
                Screen.ENVIRONMENTS -> EnvironmentsScreen(store, modifier)
                Screen.HISTORY -> HistoryScreen(store, modifier)
                Screen.ALERTS -> AlertsScreen(store, modifier)
                Screen.PEOPLE -> PeopleScreen(store, modifier)
                Screen.SETTINGS -> SettingsScreen(store, modifier)
            }
        }
    }
}

@Composable
private fun DemoBanner(store: DemoStore) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF20404A)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (store.liveMode) "●  ESP32 / MQTT" else "●  MODO DEMO", color = Ice, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text(if (store.liveMode) store.selectedEnvironment?.let { "${environmentIcon(it.icon)} ${it.name}" } ?: "Aguardando ESP32" else "Dados fictícios", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (trailing != null && onClick != null) {
            Text(trailing, color = Ice, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onClick))
        }
    }
}

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)) {
        Box(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.13f))
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetricIcon(metric: Metric, tint: Color = Ice) {
    val icon = when (metric) {
        Metric.TEMPERATURE -> Icons.Outlined.Thermostat
        Metric.HUMIDITY -> Icons.Outlined.WaterDrop
        Metric.VOLTAGE -> Icons.Outlined.Bolt
    }
    Icon(icon, contentDescription = metric.label, tint = tint)
}

@Composable
private fun MetricTile(metric: Metric, value: Float?, hint: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricIcon(metric)
            Text(metric.label, color = Muted, fontSize = 12.sp)
            Text("${formatValue(value)} ${metric.unit}", fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Text(hint, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HomeScreen(store: DemoStore, modifier: Modifier,
                       onAlerts: () -> Unit, onHistory: () -> Unit, onEnvironments: () -> Unit) {
    val latest = store.latest
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageTitle("Sua central", "ESP32 → celular → acesso web") }
        if (store.currentEnvironments.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(store.currentEnvironments.values.filter { it.favorite }.sortedBy { it.name }, key = { it.deviceId }) { profile ->
                    FilterChip(selected = store.currentDeviceId == profile.deviceId,
                        onClick = { store.selectEnvironment(profile.deviceId) },
                        label = { Text("${environmentIcon(profile.icon)} ${profile.name}") })
                }
            }
        }
        item { SectionTitle("Ambiente em foco", "Gerenciar", onEnvironments) }
        item {
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ESTADO DO AMBIENTE", color = Muted, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(if (store.liveMode) (if (store.deviceOnline) "ONLINE" else "SEM SINAL")
                            else if (store.offlineDemo) "SEM SINAL" else if (store.simulationRunning) "SIMULANDO" else "PAUSADO",
                            if (store.liveMode && !store.deviceOnline || !store.liveMode && store.offlineDemo) Red else Ice)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatValue(latest.temperature), fontSize = 68.sp,
                            fontWeight = FontWeight.Light, lineHeight = 70.sp)
                        Text("°C", fontSize = 26.sp, color = Ice)
                    }
                    Text(if (store.liveMode) "Temperatura medida pelo ESP32" else "Temperatura ambiente simulada", color = Muted, fontSize = 13.sp)
                    TrendChart(store.visibleReadings.take(18).reversed().map { it.temperature }, Ice,
                        modifier = Modifier.fillMaxWidth().height(74.dp))
                    HorizontalDivider(color = Color(0xFF36515A))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (store.liveMode) "Última amostra recebida" else "Última amostra fictícia", color = Muted, fontSize = 12.sp,
                            modifier = Modifier.weight(1f))
                        Text(if (latest.time == 0L) "—" else timeOf(latest.time), fontSize = 12.sp)
                    }
                }
            }
        }
        item { SectionTitle("Leituras", "Histórico", onHistory) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    MetricTile(Metric.HUMIDITY, latest.humidity, store.selectedEnvironment?.let { "${it.humidityMin.toInt()}–${it.humidityMax.toInt()}%" } ?: "Limite ${store.humidityLimit.toInt()}%")
                }
                Box(Modifier.weight(1f)) {
                    MetricTile(Metric.VOLTAGE, latest.voltage, store.selectedEnvironment?.let { "${it.voltageMin.toInt()}–${it.voltageMax.toInt()} V" } ?: "Nominal ${store.nominalVoltage.toInt()} V")
                }
            }
        }
        item {
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Central e dispositivo")
                    LinkRow("ESP32 → celular", if (store.liveMode) (if (store.deviceOnline) "MQTT ativo" else "Aguardando sinal") else "Rede local simulada")
                    LinkRow("Acesso remoto", if (DemoCentral.active) "Web ativa no celular" else "Central parada")
                }
            }
        }
        item { SectionTitle("Eventos recentes", "Ver todos", onAlerts) }
        items(store.events.filter { if (store.liveMode) !it.simulated && it.deviceId == store.selectedDeviceId else it.simulated && it.deviceId == store.selectedDemoId }
            .take(2), key = { it.id }) { event -> EventCard(event, store, compact = true) }
        item { Text("A central monitora e registra; nenhum comando é enviado ao ar-condicionado.",
            color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun LinkRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
private fun TrendChart(values: List<Float?>, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val points = values.filterNotNull()
        if (points.size < 2) return@Canvas
        val min = points.minOrNull() ?: 0f
        val max = points.maxOrNull() ?: 1f
        val range = (max - min).coerceAtLeast(0.5f)
        val margin = 5.dp.toPx()
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / (points.size - 1)
            val y = margin + (size.height - margin * 2) * (1 - (value - min) / range)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(tint.copy(alpha = 0.16f), Offset(0f, size.height - margin),
            Offset(size.width, size.height - margin), strokeWidth = 1.dp.toPx())
        drawPath(path, tint, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun formatValue(value: Float?): String = value?.let { String.format(Locale("pt", "BR"), "%.1f", it) } ?: "—"
private fun timeOf(time: Long): String = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(time))
private fun dateOf(time: Long): String = SimpleDateFormat("dd/MM  HH:mm", Locale("pt", "BR")).format(Date(time))

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun HomePreview() { WinterholdApp() }

@Composable
private fun HistoryScreen(store: DemoStore, modifier: Modifier) {
    var selected by remember { mutableStateOf(Metric.TEMPERATURE) }
    val history = store.visibleReadings.toList()
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DemoBanner(store) }
        item { PageTitle("Histórico", if (store.liveMode) "${store.selectedEnvironment?.name ?: "ESP32"} · amostras persistidas" else "Amostras simuladas persistidas no celular") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric.entries.forEach { metric ->
                    FilterChip(selected = selected == metric, onClick = { selected = metric },
                        label = { Text(metric.label, fontSize = 12.sp) })
                }
            }
        }
        item {
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.label, color = Muted, modifier = Modifier.weight(1f))
                        Text("${formatValue(store.latest.value(selected))} ${selected.unit}",
                            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Ice)
                    }
                    TrendChart(history.take(36).reversed().map { it.value(selected) }, Ice,
                        Modifier.fillMaxWidth().height(150.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text("Mais antigo", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("Mais recente", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        item { SectionTitle("Amostras (${history.size} locais)") }
        items(history.take(30), key = { it.time }) { reading ->
            PanelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MetricIcon(selected)
                    Spacer(Modifier.width(12.dp))
                    Text(dateOf(reading.time), color = Muted, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text("${formatValue(reading.value(selected))} ${selected.unit}",
                        fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Text("O histórico local é mantido após fechar o aplicativo; máximo de 60 amostras por dispositivo.",
            color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun EventCard(event: AlertEvent, store: DemoStore, compact: Boolean = false) {
    var showRoutes by remember { mutableStateOf(false) }
    val tint = when (event.severity) {
        Severity.CRITICAL -> Red
        Severity.WARNING -> Amber
        Severity.INFO -> Ice
    }
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (event.resolved) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(9.dp))
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    maxLines = 2, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Text(timeOf(event.time), color = Muted, fontSize = 11.sp)
            }
            Text(event.detail, color = Muted, fontSize = 12.sp)
            event.deviceId?.let { id -> (if (event.simulated) store.demoEnvironments[id] else store.environments[id])?.let { environment ->
                Text("${environmentIcon(environment.icon)} ${environment.name} · $id", color = Ice, fontSize = 11.sp)
            } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(if (event.resolved) "RESOLVIDO" else "ABERTO",
                    if (event.resolved) Ice else tint)
                Spacer(Modifier.weight(1f))
                if (!compact) {
                    TextButton(onClick = { showRoutes = true }) { Text("Ver entregas") }
                }
            }
            if (!compact && !event.resolved) {
                OutlinedButton(onClick = { store.resolveAlert(event.id) },
                    modifier = Modifier.fillMaxWidth()) { Text("Marcar resolvido") }
            }
        }
    }
    if (showRoutes) {
        val routes = store.routesFor(event)
        AlertDialog(
            onDismissRequest = { showRoutes = false },
            title = { Text("Entregas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (event.simulated && !event.title.startsWith("Teste de ")) "Evento simulado: sem envio."
                        else "Tentativas por destinatário e canal:", color = Muted)
                    if (routes.isEmpty()) Text("Nenhum perfil ativo combina com este evento.")
                    routes.forEach { (contact, channels) ->
                        Text("${contact.name}: ${channels.joinToString { "${it.label} (${DemoCentral.deliveryStatus(event.id, contact.id, it) ?: "aguardando configuração"})" }}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRoutes = false }) { Text("Fechar") } }
        )
    }
}

@Composable
private fun AlertsScreen(store: DemoStore, modifier: Modifier) {
    var filter by remember { mutableStateOf("Todos") }
    val filtered = store.events.filter { it.simulated != store.liveMode }.filter { when (filter) {
        "Abertos" -> !it.resolved
        "Resolvidos" -> it.resolved
        else -> true
    } }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { PageTitle("Alertas", if (store.liveMode) "Ocorrências reais e estado das entregas" else "Eventos fictícios e testes de entrega") }
        if (!store.liveMode) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Teste explícito: envia aos destinatários configurados para a variável. Confira os destinos antes de disparar.", color = Muted, fontSize = 12.sp)
                Metric.entries.forEach { metric -> OutlinedButton(onClick = {
                    DemoCentral.sendTest(store.simulateAlert(metric))
                }) { Text("Testar notificação · ${metric.label}") } }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Todos", "Abertos", "Resolvidos").forEach { value ->
                    FilterChip(selected = filter == value, onClick = { filter = value },
                        label = { Text(value) })
                }
            }
        }
        items(filtered, key = { it.id }) { event -> EventCard(event, store) }
        if (filtered.isEmpty()) item { Text("Nenhum evento neste filtro.", color = Muted) }
    }

}

@Composable
private fun PeopleScreen(store: DemoStore, modifier: Modifier) {
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Destinatários", "Endereços e filtros de entrega") }
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar destinatário")
            }
        }
        items(store.contacts, key = { it.id }) { contact -> ContactCard(contact, store) }

    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Novo destinatário") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("Nome do perfil") }, singleLine = true)
                    OutlinedTextField(value = role, onValueChange = { role = it },
                        label = { Text("Função") }, singleLine = true)
                    Text("O perfil inicia com todas as variáveis e canal SMS. Edite no cartão depois.",
                        color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    store.addContact(name, role)
                    showAdd = false
                }) { Text("Adicionar") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ContactCard(contact: DemoContact, store: DemoStore) {
    var expanded by remember { mutableStateOf(false) }
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(PanelLight),
                    contentAlignment = Alignment.Center) {
                    Text(contact.name.take(1).uppercase(), color = Ice, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(contact.name, fontWeight = FontWeight.Bold)
                    Text(contact.role, color = Muted, fontSize = 12.sp)
                }
                Switch(checked = contact.active,
                    onCheckedChange = { store.updateContact(contact.copy(active = it)) })
            }
            Text("${contact.metrics.joinToString { it.label }} · ${contact.channels.joinToString { it.label }}",
                color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Recolher filtros" else "Editar filtros")
            }
            if (expanded) {
                HorizontalDivider()
                var phone by remember(contact.id, contact.phone) { mutableStateOf(contact.phone) }
                var email by remember(contact.id, contact.email) { mutableStateOf(contact.email) }
                var destinationError by remember { mutableStateOf("") }
                OutlinedTextField(phone, { phone = it }, label = { Text("Celular +55… (SMS / WhatsApp)") }, singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true)
                TextButton(onClick = { runCatching { store.updateContact(contact.copy(phone = phone.trim(), email = email.trim())) }.onSuccess { destinationError = "Salvo" }.onFailure { destinationError = it.message.orEmpty() } }) { Text("Salvar destinos") }
                if (destinationError.isNotEmpty()) Text(destinationError, color = Amber, fontSize = 12.sp)
                Text("VARIÁVEIS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Metric.entries.forEach { metric ->
                    ToggleRow(metric.label, metric in contact.metrics) { enabled ->
                        store.updateContact(contact.copy(metrics = if (enabled) contact.metrics + metric
                            else contact.metrics - metric))
                    }
                }
                Text("CANAIS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Channel.entries.forEach { channel ->
                    ToggleRow(channel.label, channel in contact.channels) { enabled ->
                        store.updateContact(contact.copy(channels = if (enabled) contact.channels + channel
                            else contact.channels - channel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun EnvironmentsScreen(store: DemoStore, modifier: Modifier) {
    var editing by remember { mutableStateOf<EnvironmentProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageTitle("Ambientes", if (store.liveMode) "Um ESP32 por ambiente · favoritos fixados no início" else "Cinco espaços fictícios · ajuste leituras e faixas") }
        if (store.liveMode) item { Button(onClick = { editing = null; showEditor = true }) {
            Icon(Icons.Outlined.Add, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Adicionar ambiente")
        } }
        if (!store.liveMode) item { DemoReadingEditor(store) }
        if (store.currentEnvironments.isEmpty()) item { Text("Nenhum ambiente ainda. Cadastre o DEVICE_ID do firmware ou aguarde a primeira leitura.", color = Muted) }
        items(store.currentEnvironments.values.sortedWith(compareByDescending<EnvironmentProfile> { it.favorite }.thenBy { it.name }), key = { it.deviceId }) { profile ->
            PanelCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(environmentIcon(profile.icon), fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(profile.name, fontWeight = FontWeight.Bold); Text(profile.deviceId, color = Muted, fontSize = 12.sp) }
                    IconButton(onClick = { if (store.liveMode) store.saveEnvironment(profile.copy(favorite = !profile.favorite)) else store.saveDemoEnvironment(profile.copy(favorite = !profile.favorite)) }) {
                        Icon(Icons.Outlined.Star, contentDescription = if (profile.favorite) "Desafixar do início" else "Fixar no início",
                            tint = if (profile.favorite) Amber else Muted)
                    }
                }
                StatusPill(if (!store.liveMode) "DEMO" else if (store.lastSeen[profile.deviceId]?.let { store.now - it in 0..15_000L } == true) "ONLINE" else "SEM SINAL",
                    if (!store.liveMode || store.lastSeen[profile.deviceId]?.let { store.now - it in 0..15_000L } == true) Ice else Red)
                Text("${profile.temperatureMin.toInt()}–${profile.temperatureMax.toInt()} °C  ·  ${profile.humidityMin.toInt()}–${profile.humidityMax.toInt()}%  ·  ${profile.voltageMin.toInt()}–${profile.voltageMax.toInt()} V", color = Muted, fontSize = 12.sp)
                Row { TextButton(onClick = { store.selectEnvironment(profile.deviceId) }) { Text("Ver no início") }
                    TextButton(onClick = { editing = profile; showEditor = true }) { Text("Editar limites") } }
            } }
        }
    }
    if (showEditor) EnvironmentEditor(editing, { showEditor = false }) { profile ->
        if (store.liveMode) store.saveEnvironment(profile) else store.saveDemoEnvironment(profile)
        store.selectEnvironment(profile.deviceId); showEditor = false
    }
}

@Composable
private fun DemoReadingEditor(store: DemoStore) {
    var temperature by remember(store.selectedDemoId) { mutableStateOf(store.latest.temperature?.toString() ?: "22") }
    var humidity by remember(store.selectedDemoId) { mutableStateOf(store.latest.humidity?.toString() ?: "50") }
    var voltage by remember(store.selectedDemoId) { mutableStateOf(store.latest.voltage?.toString() ?: "220") }
    var feedback by remember { mutableStateOf("") }
    PanelCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Definir leitura · ${store.selectedEnvironment?.name}", fontWeight = FontWeight.Bold)
        OutlinedTextField(temperature, { temperature = it }, label = { Text("Temperatura °C") }, singleLine = true)
        OutlinedTextField(humidity, { humidity = it }, label = { Text("Umidade %") }, singleLine = true)
        OutlinedTextField(voltage, { voltage = it }, label = { Text("Tensão V") }, singleLine = true)
        Button(onClick = { runCatching { store.setDemoReading(store.selectedDemoId,
            temperature.replace(',', '.').toFloat(), humidity.replace(',', '.').toFloat(),
            voltage.replace(',', '.').toFloat()) }.onSuccess { feedback = "Leitura fictícia atualizada" }
            .onFailure { feedback = it.message.orEmpty() } }) { Text("Aplicar leitura") }
        if (feedback.isNotEmpty()) Text(feedback, color = Amber)
    } }
}

@Composable
private fun SettingsScreen(store: DemoStore, modifier: Modifier) {
    val context = LocalContext.current
    var smsGranted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsGranted = it }
    val deliverySettings = remember { DeliverySettings(context) }
    var host by remember { mutableStateOf(deliverySettings.smtpHost) }
    var user by remember { mutableStateOf(deliverySettings.smtpUser) }
    var from by remember { mutableStateOf(deliverySettings.smtpFrom) }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var bridgeUrl by remember { mutableStateOf(deliverySettings.bridgeUrl) }
    var bridgeToken by remember { mutableStateOf("") }
    var bridgeMessage by remember { mutableStateOf("") }
    var newWebUser by remember { mutableStateOf("") }
    var webUserMessage by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { DemoCentral.refreshUrls() }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageTitle("Ajustes", "Integrações da central Android") }
        item { PanelCard { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Web no celular")
            Text(DemoCentral.status, color = Ice)
            DemoCentral.urls.forEach { url -> SelectionContainer { Text(url, color = Amber) }
                TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Winterhold web", url)) }) { Text("Copiar endereço") } }
            Text("HTTP na rede local; use VPN ou HTTPS no acesso externo.", color = Muted, fontSize = 12.sp)
            TextButton(onClick = { if (DemoCentral.active) CentralService.stop(context) else CentralService.start(context) }) {
                Text(if (DemoCentral.active) "Parar central" else "Retomar central") }
            TextButton(onClick = { CentralService.requestBatterySetup(context) }) { Text("Configurar bateria") }
        } } }
        item { PanelCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Usuários de acesso web")
            Text("Gerencie as chaves somente no celular. A rotação invalida a chave antiga imediatamente.", color = Muted, fontSize = 12.sp)
            DemoCentral.webUsers?.users?.forEach { access ->
                Text("${access.name} · ${if (access.active) "ativo" else "desativado"}")
                SelectionContainer { Text(access.key, color = Amber, fontSize = 12.sp) }
                Row { TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Chave web", access.key)) }) { Text("Copiar") }
                    TextButton(onClick = { DemoCentral.webUsers?.rotate(access.id) }) { Text("Trocar chave") }
                    TextButton(onClick = { runCatching { DemoCentral.webUsers?.setActive(access.id, !access.active) }
                        .onFailure { webUserMessage = it.message.orEmpty() } }) { Text(if (access.active) "Desativar" else "Ativar") } }
            }
            OutlinedTextField(newWebUser, { newWebUser = it }, label = { Text("Nome do novo usuário") }, singleLine = true)
            TextButton(onClick = { runCatching { DemoCentral.webUsers?.add(newWebUser); newWebUser = "" }
                .onFailure { webUserMessage = it.message.orEmpty() } }) { Text("Adicionar usuário") }
            if (webUserMessage.isNotEmpty()) Text(webUserMessage, color = Amber)
        } } }
        item { PanelCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Modo demonstração")
            ToggleRow("Usar leituras fictícias", !store.liveMode) { enabled -> store.liveMode = !enabled; store.simulationRunning = enabled }
            if (!store.liveMode) {
                ToggleRow("Atualização automática", store.simulationRunning) { store.simulationRunning = it }
                ToggleRow("Simular desconexão", store.offlineDemo) { if (it != store.offlineDemo) store.simulateDisconnection() }
            }
        } } }
        item { PanelCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("ESP32 / MQTT")
            Text(DemoCentral.mqttStatus, color = Ice)
            Text("MQTT_HOST = IP deste celular · porta 1883 · usuário winterhold", color = Muted, fontSize = 12.sp)
            SelectionContainer { Text(DemoCentral.mqttSecret, color = Amber, fontSize = 12.sp) }
            TextButton(onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Chave MQTT", DemoCentral.mqttSecret)) }) { Text("Copiar chave MQTT") }
        } } }
        item { PanelCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Canais de alerta")
            Channel.entries.forEach { channel -> ToggleRow(channel.label, channel in store.enabledChannels) { enabled ->
                if (enabled && channel !in store.enabledChannels) store.enabledChannels.add(channel)
                if (!enabled) store.enabledChannels.remove(channel)
            } }
            if (!smsGranted) OutlinedButton(onClick = { smsPermission.launch(Manifest.permission.SEND_SMS) }) {
                Text("Permitir envio de SMS neste aparelho")
            }
            Text("Configure os destinatários em Equipe. Apenas o comando explícito Testar notificação envia no modo demo.", color = Muted, fontSize = 12.sp)
        } } }
        item { PanelCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("E-mail · SMTP TLS (465)")
            OutlinedTextField(host, { host = it }, label = { Text("Servidor SMTP") }, singleLine = true)
            OutlinedTextField(user, { user = it }, label = { Text("Usuário") }, singleLine = true)
            OutlinedTextField(from, { from = it }, label = { Text("E-mail do remetente") }, singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Senha / senha de aplicativo (deixe vazio para manter)") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Button(onClick = { runCatching {
                deliverySettings.smtpHost = host.trim(); deliverySettings.smtpUser = user.trim()
                deliverySettings.smtpFrom = from.trim(); if (password.isNotBlank()) deliverySettings.smtpPassword = password
            }.onSuccess { password = ""; message = "SMTP salvo no celular" }.onFailure { message = it.message.orEmpty() } }) { Text("Salvar SMTP") }
            if (message.isNotEmpty()) Text(message, color = Amber)
        } } }
        item { PanelCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("WhatsApp · ponte Baileys")
            Text("Node executa Baileys no backend; a central Android envia alertas à ponte por HTTPS (ou HTTP no próprio aparelho).", color = Muted, fontSize = 12.sp)
            OutlinedTextField(bridgeUrl, { bridgeUrl = it }, label = { Text("URL da ponte HTTPS") }, singleLine = true)
            OutlinedTextField(bridgeToken, { bridgeToken = it }, label = { Text("Chave da ponte (vazio mantém a atual)") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            Button(onClick = { runCatching {
                deliverySettings.bridgeUrl = bridgeUrl.trim()
                if (bridgeToken.isNotBlank()) deliverySettings.bridgeToken = bridgeToken.trim()
            }.onSuccess { bridgeToken = ""; bridgeMessage = "Ponte salva no celular" }
                .onFailure { bridgeMessage = it.message.orEmpty() } }) { Text("Salvar WhatsApp") }
            if (bridgeMessage.isNotEmpty()) Text(bridgeMessage, color = Amber)
        } } }
        item { Text("Destinatários: abra Alertas → Equipe.", color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun EnvironmentEditor(profile: EnvironmentProfile?, onDismiss: () -> Unit,
                              onSave: (EnvironmentProfile) -> Unit) {
    var id by remember(profile?.deviceId) { mutableStateOf(profile?.deviceId ?: "") }
    var name by remember(profile?.deviceId) { mutableStateOf(profile?.name ?: "") }
    var icon by remember(profile?.deviceId) { mutableStateOf(profile?.icon ?: "snow") }
    var temperatureMin by remember(profile?.deviceId) { mutableStateOf((profile?.temperatureMin ?: 18f).toString()) }
    var temperatureMax by remember(profile?.deviceId) { mutableStateOf((profile?.temperatureMax ?: 26f).toString()) }
    var humidityMin by remember(profile?.deviceId) { mutableStateOf((profile?.humidityMin ?: 30f).toString()) }
    var humidityMax by remember(profile?.deviceId) { mutableStateOf((profile?.humidityMax ?: 70f).toString()) }
    var voltageMin by remember(profile?.deviceId) { mutableStateOf((profile?.voltageMin ?: 198f).toString()) }
    var voltageMax by remember(profile?.deviceId) { mutableStateOf((profile?.voltageMax ?: 242f).toString()) }
    var error by remember(profile?.deviceId) { mutableStateOf<String?>(null) }
    @Composable fun range(label: String, unit: String, minimum: String, onMinimum: (String) -> Unit,
                          maximum: String, onMaximum: (String) -> Unit) {
        Text("$label ($unit)", color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(minimum, onMinimum, label = { Text("Mínimo") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(maximum, onMaximum, label = { Text("Máximo") }, singleLine = true, modifier = Modifier.weight(1f))
        }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (profile == null) "Novo ambiente" else "Editar ambiente") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(id, { id = it }, label = { Text("DEVICE_ID do firmware") },
                    enabled = profile == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text("Nome do ambiente") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Ícone", color = Muted, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EnvironmentProfile.icons) { choice ->
                        FilterChip(selected = icon == choice, onClick = { icon = choice },
                            label = { Text(environmentIcon(choice)) })
                    }
                }
                range("Temperatura", "°C", temperatureMin, { temperatureMin = it }, temperatureMax, { temperatureMax = it })
                range("Umidade", "%", humidityMin, { humidityMin = it }, humidityMax, { humidityMax = it })
                range("Tensão", "V", voltageMin, { voltageMin = it }, voltageMax, { voltageMax = it })
                error?.let { Text(it, color = Red, fontSize = 12.sp) }
            }
        },
        confirmButton = { TextButton(onClick = {
            fun number(value: String) = value.replace(',', '.').toFloatOrNull() ?: Float.NaN
            val result = runCatching {
                EnvironmentProfile(id, name.trim(), icon,
                    number(temperatureMin), number(temperatureMax), number(humidityMin),
                    number(humidityMax), number(voltageMin), number(voltageMax)).validated()
            }
            result.onSuccess { runCatching { onSave(it) }.onFailure { failure -> error = failure.message } }
                .onFailure { failure -> error = failure.message }
        }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun LimitSlider(label: String, value: Float, unit: String,
                        range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${value.toInt()} $unit", color = Ice, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

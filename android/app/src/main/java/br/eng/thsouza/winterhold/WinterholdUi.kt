package br.eng.thsouza.winterhold

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF071923)
private val Panel = Color(0xFF112A36)
private val PanelLight = Color(0xFF183744)
private val Ice = Color(0xFF78DCE8)
private val Muted = Color(0xFFA9C2C9)
private val Amber = Color(0xFFFFCA79)
private val Red = Color(0xFFFF8C88)

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
                    Screen.entries.filter { it != Screen.SETTINGS }.forEach { item ->
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
                    onAlerts = { screen = Screen.ALERTS }, onHistory = { screen = Screen.HISTORY })
                Screen.HISTORY -> HistoryScreen(store, modifier)
                Screen.ALERTS -> AlertsScreen(store, modifier)
                Screen.PEOPLE -> PeopleScreen(store, modifier)
                Screen.SETTINGS -> SettingsScreen(store, modifier)
            }
        }
    }
}

@Composable
private fun DemoBanner() {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF20404A)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("●  MODO DEMO", color = Ice, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.weight(1f))
        Text("Dados fictícios", color = Muted, fontSize = 12.sp)
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
private fun MetricTile(metric: Metric, value: Float, hint: String) {
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
                       onAlerts: () -> Unit, onHistory: () -> Unit) {
    val latest = store.latest
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { DemoBanner() }
        item { PageTitle("Visão geral", "Sala 01  ·  Ambiente climatizado") }
        item {
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ESTADO DO AMBIENTE", color = Muted, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(if (store.offlineDemo) "SEM SINAL" else if (store.simulationRunning) "SIMULANDO" else "PAUSADO",
                            if (store.offlineDemo) Red else Ice)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatValue(latest.temperature), fontSize = 68.sp,
                            fontWeight = FontWeight.Light, lineHeight = 70.sp)
                        Text("°C", fontSize = 26.sp, color = Ice)
                    }
                    Text("Temperatura ambiente simulada", color = Muted, fontSize = 13.sp)
                    TrendChart(store.readings.take(18).reversed().map { it.temperature }, Ice,
                        modifier = Modifier.fillMaxWidth().height(74.dp))
                    HorizontalDivider(color = Color(0xFF36515A))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Última amostra fictícia", color = Muted, fontSize = 12.sp,
                            modifier = Modifier.weight(1f))
                        Text(timeOf(latest.time), fontSize = 12.sp)
                    }
                }
            }
        }
        item { SectionTitle("Leituras", "Histórico", onHistory) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    MetricTile(Metric.HUMIDITY, latest.humidity, "Limite ${store.humidityLimit.toInt()}%")
                }
                Box(Modifier.weight(1f)) {
                    MetricTile(Metric.VOLTAGE, latest.voltage, "Nominal ${store.nominalVoltage.toInt()} V")
                }
            }
        }
        item {
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Central e dispositivo")
                    LinkRow("ESP32 → tablet", if (store.offlineDemo) "Desconectado (demo)" else "Rede local simulada")
                    LinkRow("Armazenamento", "Histórico temporário no aplicativo")
                    LinkRow("Canal móvel", "Não conectado nesta versão")
                }
            }
        }
        item { SectionTitle("Eventos recentes", "Ver todos", onAlerts) }
        items(store.events.take(2), key = { it.id }) { event -> EventCard(event, store, compact = true) }
        item { Text("Protótipo visual · nenhum comando é enviado ao ar-condicionado.",
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
private fun TrendChart(values: List<Float>, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).coerceAtLeast(0.5f)
        val margin = 5.dp.toPx()
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1)
            val y = margin + (size.height - margin * 2) * (1 - (value - min) / range)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(tint.copy(alpha = 0.16f), Offset(0f, size.height - margin),
            Offset(size.width, size.height - margin), strokeWidth = 1.dp.toPx())
        drawPath(path, tint, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun formatValue(value: Float): String = String.format(Locale("pt", "BR"), "%.1f", value)
private fun timeOf(time: Long): String = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(time))
private fun dateOf(time: Long): String = SimpleDateFormat("dd/MM  HH:mm", Locale("pt", "BR")).format(Date(time))

@Preview(showBackground = true, widthDp = 390, heightDp = 850)
@Composable
private fun HomePreview() { WinterholdApp() }

@Composable
private fun HistoryScreen(store: DemoStore, modifier: Modifier) {
    var selected by remember { mutableStateOf(Metric.TEMPERATURE) }
    val history = store.readings.toList()
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DemoBanner() }
        item { PageTitle("Histórico", "Amostras geradas no celular · sem SQLite") }
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
        item { Text("A linha mostra apenas as amostras em memória. Tudo é reiniciado ao fechar o app.",
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(if (event.resolved) "RESOLVIDO" else "ABERTO",
                    if (event.resolved) Ice else tint)
                Spacer(Modifier.weight(1f))
                if (!compact) {
                    TextButton(onClick = { showRoutes = true }) { Text("Ver roteamento") }
                }
            }
            if (!compact && !event.resolved) {
                OutlinedButton(onClick = { store.resolveAlert(event.id) },
                    modifier = Modifier.fillMaxWidth()) { Text("Marcar resolvido (demo)") }
            }
        }
    }
    if (showRoutes) {
        val routes = store.routesFor(event)
        AlertDialog(
            onDismissRequest = { showRoutes = false },
            title = { Text("Roteamento simulado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Nenhuma mensagem foi enviada.", color = Muted)
                    if (routes.isEmpty()) Text("Nenhum perfil ativo combina com este evento.")
                    routes.forEach { (contact, channels) ->
                        Text("${contact.name}: ${channels.joinToString { it.label }}")
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
    var showSimulator by remember { mutableStateOf(false) }
    val filtered = store.events.filter { when (filter) {
        "Abertos" -> !it.resolved
        "Resolvidos" -> it.resolved
        else -> true
    } }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { DemoBanner() }
        item { PageTitle("Alertas", "Eventos fictícios · nenhum envio externo") }
        item {
            Button(onClick = { showSimulator = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Simular um alerta")
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
    if (showSimulator) {
        AlertDialog(
            onDismissRequest = { showSimulator = false },
            title = { Text("Criar evento de demonstração") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Escolha a variável. O evento ficará apenas na memória do celular.", color = Muted)
                    Metric.entries.forEach { metric ->
                        OutlinedButton(onClick = {
                            store.simulateAlert(metric)
                            showSimulator = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            MetricIcon(metric)
                            Spacer(Modifier.width(8.dp))
                            Text(metric.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSimulator = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PeopleScreen(store: DemoStore, modifier: Modifier) {
    var showAdd by remember { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { DemoBanner() }
        item { PageTitle("Equipe", "Perfis de alerta por variável e canal") }
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar perfil demo")
            }
        }
        items(store.contacts, key = { it.id }) { contact -> ContactCard(contact, store) }
        item { Text("Perfis locais fictícios. Nenhum número ou endereço real é solicitado nesta fase.",
            color = Muted, fontSize = 11.sp) }
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Novo perfil demo") },
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
private fun SettingsScreen(store: DemoStore, modifier: Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DemoCentral.refreshUrls() }
    LazyColumn(modifier, contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DemoBanner() }
        item { PageTitle("Ajustes", "Parâmetros ilustrativos para explorar o visual") }
        item {
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Acesso web pelo aplicativo")
                    Text(DemoCentral.status, color = Ice, fontSize = 13.sp)
                    Text("Abra o endereço diretamente pelo IP do celular em outro dispositivo da mesma rede local. Esta demo acadêmica usa HTTP sem login.",
                        color = Muted, fontSize = 12.sp)
                    DemoCentral.urls.forEach { url ->
                        SelectionContainer { Text(url, color = Ice, fontSize = 12.sp) }
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Winterhold web", url))
                        }) { Text("Copiar endereço") }
                    }
                    Text(DemoCentral.persistenceStatus, color = Muted, fontSize = 12.sp)
                    TextButton(onClick = {
                        if (DemoCentral.active) CentralService.stop(context) else CentralService.start(context)
                    }) { Text(if (DemoCentral.active) "Parar central" else "Retomar central") }
                    TextButton(onClick = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }) { Text("Configurações de bateria") }
                    Text("A central continua com a tela fechada e retoma após reiniciar e desbloquear o celular. Parar a central desativa essa retomada até abri-la novamente. Restrições de bateria do Android podem interromper o acesso.",
                        color = Muted, fontSize = 11.sp)
                }
            }
        }
        item {
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle("Simulação")
                    ToggleRow("Gerar novas leituras", store.simulationRunning) {
                        store.simulationRunning = it
                    }
                    ToggleRow("Simular ESP32 desconectado", store.offlineDemo) {
                        if (it != store.offlineDemo) store.simulateDisconnection()
                    }
                    Text("As leituras são geradas a cada 5 segundos. Pausar e desconectar não altera sensores reais.",
                        color = Muted, fontSize = 12.sp)
                }
            }
        }
        item { SectionTitle("Limiares de exemplo") }
        item {
            PanelCard {
                Column {
                    LimitSlider("Temperatura máxima", store.temperatureLimit, "°C", 20f..35f) {
                        store.temperatureLimit = it
                    }
                    LimitSlider("Umidade máxima", store.humidityLimit, "%", 40f..90f) {
                        store.humidityLimit = it
                    }
                    LimitSlider("Tensão nominal", store.nominalVoltage, "V", 100f..250f) {
                        store.nominalVoltage = it
                    }
                    LimitSlider("Tolerância de tensão", store.voltageTolerance, "%", 2f..20f) {
                        store.voltageTolerance = it
                    }
                    val low = store.nominalVoltage * (1 - store.voltageTolerance / 100)
                    val high = store.nominalVoltage * (1 + store.voltageTolerance / 100)
                    Text("Faixa simulada: ${low.toInt()}–${high.toInt()} V. Ajustar controles não avalia leituras anteriores.",
                        color = Muted, fontSize = 12.sp)
                }
            }
        }
        item { SectionTitle("Canais no roteamento") }
        item {
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Channel.entries.forEach { channel ->
                        ToggleRow(channel.label, channel in store.enabledChannels) { enabled ->
                            if (enabled && channel !in store.enabledChannels) store.enabledChannels.add(channel)
                            if (!enabled) store.enabledChannels.remove(channel)
                        }
                    }
                    Text("Estas chaves mudam apenas a prévia de roteamento. SMS, e-mail e WhatsApp não são enviados.",
                        color = Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            PanelCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Integrações futuras")
                    LinkRow("ESP32", "MQTT recebido pelo celular")
                    LinkRow("Persistência", "SQLite")
                    LinkRow("SMS", "Compatibilidade a validar")
                    LinkRow("E-mail", "Provedor autenticado")
                    LinkRow("WhatsApp", "Link assistido ou API oficial")
                    Text("Nenhuma dessas integrações está ativa no modo demo.",
                        color = Amber, fontSize = 12.sp)
                }
            }
        }
        item { Text("Winterhold · protótipo visual 0.1 · estado descartado ao encerrar o processo.",
            color = Muted, fontSize = 11.sp) }
    }
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

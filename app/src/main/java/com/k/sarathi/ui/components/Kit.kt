package com.k.sarathi.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.k.sarathi.ui.theme.Sar
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val CardShape = RoundedCornerShape(20.dp)

/** The basic HUD surface: soft gradient, hairline border, optional accent edge. */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sar.c
    val edge = accent ?: c.line
    Column(
        modifier
            .clip(CardShape)
            .background(Brush.verticalGradient(listOf(c.surfaceHi, c.surface)))
            .border(BorderStroke(1.dp, if (accent != null) accent.copy(alpha = 0.45f) else c.line), CardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                if (accent != null) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, edge.copy(alpha = 0.9f), Color.Transparent)), size = Size(size.width, 2.dp.toPx()))
            }
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = Sar.t.label, color = Sar.c.textDim, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action.uppercase(), style = Sar.t.label, color = Sar.c.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = Sar.c
    val tint = color ?: c.accent
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) tint.copy(alpha = 0.18f) else c.surfaceHi)
            .border(1.dp, if (selected) tint.copy(alpha = 0.7f) else c.line, RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) Icon(leading, null, tint = if (selected) tint else c.textDim, modifier = Modifier.size(16.dp))
        Text(text, style = Sar.t.small.copy(fontWeight = FontWeight.Medium), color = if (selected) c.text else c.textDim, maxLines = 1)
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Sar.c
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) Brush.horizontalGradient(listOf(c.accent, c.accent2.copy(alpha = 0.85f))) else Brush.horizontalGradient(listOf(c.line, c.line)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text.uppercase(), style = Sar.t.label.copy(fontSize = sp(15f)), color = if (enabled) c.onAccent else c.textFaint)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color? = null, onClick: () -> Unit) {
    val c = Sar.c
    val tint = color ?: c.accent
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text.uppercase(), style = Sar.t.label.copy(fontSize = sp(15f)), color = tint)
    }
}

/** Animated progress ring with a centered value. */
@Composable
fun RingGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 6.dp,
    color: Color = Sar.c.accent,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val c = Sar.c
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 120f), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arc = Size(this.size.width - s, this.size.height - s)
            val tl = Offset(s / 2, s / 2)
            drawArc(c.line, 0f, 360f, false, tl, arc, style = Stroke(s))
            if (p > 0f) {
                drawArc(color.copy(alpha = 0.25f), -90f, 360f * p, false, tl, arc, style = Stroke(s * 2.2f, cap = StrokeCap.Round))
                drawArc(color, -90f, 360f * p, false, tl, arc, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

/** Horizontal meter; [marker] (0..1) shows where you "should" be, e.g. budget pace. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = Sar.c.accent, marker: Float? = null, height: Dp = 8.dp) {
    val c = Sar.c
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "meter")
    Canvas(modifier.fillMaxWidth().height(height + 6.dp)) {
        val h = height.toPx()
        val y = (size.height - h) / 2
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(c.line, Offset(0f, y), Size(size.width, h), r)
        if (f > 0) drawRoundRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)), Offset(0f, y), Size(size.width * f, h), r)
        marker?.let { m ->
            val x = size.width * m.coerceIn(0f, 1f)
            drawLine(c.text.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** GitHub-style heatmap of the last [weeks] weeks; [value] gives 0..1 per day. */
@Composable
fun Heatmap(today: LocalDate, weeks: Int = 12, modifier: Modifier = Modifier, color: Color = Sar.c.accent, value: (LocalDate) -> Float) {
    val c = Sar.c
    val start = today.minusDays((weeks * 7 - 1).toLong() + (6 - (today.dayOfWeek.value % 7)))
    Canvas(modifier.fillMaxWidth().height(((7 * 14)).dp)) {
        val cell = minOf(size.width / weeks, size.height / 7)
        val gap = cell * 0.18f
        for (w in 0 until weeks + 1) for (d in 0 until 7) {
            val day = start.plusDays((w * 7 + d).toLong())
            if (day.isAfter(today)) continue
            val v = value(day)
            val x = w * cell * (size.width / (cell * (weeks + 1)))
            drawRoundRect(
                if (v <= 0f) c.line else color.copy(alpha = 0.25f + 0.75f * v.coerceIn(0f, 1f)),
                Offset(x, d * cell), Size(cell - gap, cell - gap), CornerRadius(cell * 0.22f),
            )
        }
    }
}

enum class OrbMode { IDLE, LISTENING, THINKING, SPEAKING }

/** Sarathi's core: concentric rings that breathe, spin while thinking and pulse while listening. */
@Composable
fun Orb(modifier: Modifier = Modifier, size: Dp = 120.dp, mode: OrbMode = OrbMode.IDLE, level: Float = 0f, animate: Boolean = true) {
    val c = Sar.c
    val inf = rememberInfiniteTransition(label = "orb")
    val spin by if (animate) inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (mode == OrbMode.THINKING) 1400 else 9000, easing = LinearEasing)), label = "spin") else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val breathe by if (animate) inf.animateFloat(0.92f, 1.04f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "breathe") else remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val lvl by animateFloatAsState(level, tween(120), label = "lvl")
    Canvas(modifier.size(size).semantics { contentDescription = "Sarathi" }) {
        val r = this.size.minDimension / 2
        val center = this.center
        val scale = when (mode) { OrbMode.LISTENING -> 1f + lvl * 0.18f; else -> breathe }
        drawCircle(Brush.radialGradient(listOf(c.accent.copy(alpha = 0.55f), c.accent.copy(alpha = 0.12f), Color.Transparent), center, r * 1.0f), r * scale, center)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = if (c.isDark) 0.85f else 0.6f), c.accent, c.accent2.copy(alpha = 0.6f)), center, r * 0.42f), r * 0.36f * scale, center)
        val ring = Stroke(1.5.dp.toPx())
        drawCircle(c.accent.copy(alpha = 0.6f), r * 0.62f, center, style = ring)
        drawCircle(c.accent2.copy(alpha = 0.35f), r * 0.84f, center, style = ring)
        for (i in 0 until 3) {
            val a = Math.toRadians((spin + i * 120).toDouble())
            val rr = r * 0.73f
            drawArc(c.accent, (spin + i * 120f), 38f, false, Offset(center.x - rr, center.y - rr), Size(rr * 2, rr * 2), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(c.accent2, 2.5.dp.toPx(), Offset(center.x + (r * 0.84f * cos(a)).toFloat(), center.y + (r * 0.84f * sin(a)).toFloat()))
        }
    }
}

/** Simple animated equaliser used while listening. */
@Composable
fun Waveform(level: Float, modifier: Modifier = Modifier, bars: Int = 24, color: Color = Sar.c.accent) {
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "phase")
    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        val w = size.width / (bars * 1.6f)
        for (i in 0 until bars) {
            val amp = (0.25f + 0.75f * level.coerceIn(0f, 1f)) * (0.35f + 0.65f * abs(sin(phase + i * 0.55f)))
            val h = size.height * amp
            val x = i * w * 1.6f + w / 2
            drawLine(color.copy(alpha = 0.5f + 0.5f * amp), Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

/** Faint HUD grid with a glow at the top, drawn behind the home screen. */
fun Modifier.hudBackdrop(lineColor: Color, glow: Color): Modifier = drawBehind {
    val step = 28.dp.toPx()
    var x = 0f
    while (x < size.width) { drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 0.6f); x += step }
    var y = 0f
    while (y < size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 0.6f); y += step }
    drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), Offset(size.width * 0.5f, 0f), size.width * 0.9f), size.width * 0.9f, Offset(size.width * 0.5f, 0f))
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Sar.c
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = Sar.t.title, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = Sar.t.small, color = c.textDim, textAlign = TextAlign.Center)
        if (action != null && onAction != null) { Spacer(Modifier.height(16.dp)); GhostButton(action, onClick = onAction) }
    }
}

/** Header for pushed screens: back arrow, title, trailing actions. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Sar.c
    Row(modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.text) }
        else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Sar.t.headline, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Sar.t.small, color = c.textDim, maxLines = 1)
        }
        actions()
    }
}

@Composable
fun SarField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle = Sar.t.body,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    val c = Sar.c
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } }, placeholder = placeholder?.let { { Text(it, color = c.textFaint) } },
        singleLine = singleLine, minLines = minLines, textStyle = textStyle.copy(color = c.text),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, trailingIcon = trailing,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.line, cursorColor = c.accent,
            focusedLabelColor = c.accent, unfocusedLabelColor = c.textDim,
            focusedContainerColor = c.surface, unfocusedContainerColor = c.surface,
        ),
    )
}

@Composable
fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Sar.c
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Switch) { haptic.performHapticFeedback(if (!checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff); onChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Sar.t.bodyStrong, color = c.text)
            if (subtitle != null) Text(subtitle, style = Sar.t.small, color = c.textDim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent, uncheckedTrackColor = c.surfaceHi, uncheckedBorderColor = c.line, uncheckedThumbColor = c.textDim),
        )
    }
}

@Composable
fun NavRow(title: String, subtitle: String? = null, icon: ImageVector? = null, trailing: String? = null, onClick: () -> Unit) {
    val c = Sar.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).heightIn(min = 52.dp).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Sar.t.bodyStrong, color = c.text)
            if (subtitle != null) Text(subtitle, style = Sar.t.small, color = c.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) Text(trailing, style = Sar.t.small, color = c.textDim)
    }
}

/** Swipe left to delete; the caller shows an undo snackbar. */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Sar.c
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var widthPx = 1f
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(c.bad.copy(alpha = (abs(offset.value) / 300f).coerceIn(0f, 0.85f))),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
        ) { Icon(Icons.Rounded.Delete, "Delete", tint = Color.White, modifier = Modifier.padding(end = 20.dp)) }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    widthPx = size.width.toFloat()
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value < -widthPx * 0.35f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    offset.animateTo(-widthPx, tween(160)); onDelete(); offset.snapTo(0f)
                                } else offset.animateTo(0f, spring())
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    ) { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + drag).coerceIn(-widthPx, 0f)) }
                    }
                },
        ) { content() }
    }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = Sar.c.text) {
    Column(modifier) {
        Text(value, style = Sar.t.num.copy(fontSize = sp(22f), fontWeight = FontWeight.Bold), color = color, maxLines = 1)
        Text(label.uppercase(), style = Sar.t.label.copy(fontSize = sp(11f)), color = Sar.c.textDim, maxLines = 1)
    }
}

fun sp(v: Float) = androidx.compose.ui.unit.TextUnit(v, androidx.compose.ui.unit.TextUnitType.Sp)

/** A tappable row that also supports long-press (used by timeline items). */
fun Modifier.tapAndHold(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)

@Composable
fun LaunchedOnce(block: suspend () -> Unit) = LaunchedEffect(Unit) { block() }

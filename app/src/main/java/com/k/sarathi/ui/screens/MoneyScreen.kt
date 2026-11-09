package com.k.sarathi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k.sarathi.data.Category
import com.k.sarathi.data.Expense
import com.k.sarathi.domain.Money
import com.k.sarathi.domain.toDate
import com.k.sarathi.domain.toZoned
import com.k.sarathi.ui.Fmt
import com.k.sarathi.ui.components.EmptyState
import com.k.sarathi.ui.components.HudCard
import com.k.sarathi.ui.components.Meter
import com.k.sarathi.ui.components.ScreenHeader
import com.k.sarathi.ui.components.SectionLabel
import com.k.sarathi.ui.components.SwipeToDelete
import com.k.sarathi.ui.components.sp
import com.k.sarathi.ui.env
import com.k.sarathi.ui.rememberNow
import com.k.sarathi.ui.theme.Sar
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MoneyScreen() {
    val e = env
    val c = Sar.c
    val db by e.repo.store.state.collectAsState()
    val now = rememberNow({ e.repo.clock.now() })
    val cur = db.settings.currency
    val m = Money.month(db, now)
    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Expense?>(null) }
    var budgetFor by remember { mutableStateOf<Category?>(null) }
    var monthlyDialog by remember { mutableStateOf(false) }
    LaunchedEffect(e.bus.addExpenseOnOpen) { if (e.bus.addExpenseOnOpen) { e.bus.addExpenseOnOpen = false; add = true } }

    val daysLeft = m.month.lengthOfMonth() - now.dayOfMonth
    val recent = db.expenses.sortedByDescending { it.at }.take(60)
    val byDay = recent.groupBy { it.at.toDate(now.zone) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
            item {
                ScreenHeader("Money", onBack = null, subtitle = m.month.month.name.lowercase().replaceFirstChar { it.uppercase() } + " ${m.month.year}") {
                    IconButton(onClick = {
                        if (db.expenses.isEmpty()) e.bus.snack("Nothing to export yet")
                        else e.platform.shareFile("sarathi-expenses-${now.toLocalDate()}.csv", "text/csv", Money.csv(db.expenses, now.zone))
                    }) { Icon(Icons.Rounded.Download, "Export CSV", tint = c.textDim) }
                    IconButton(onClick = { monthlyDialog = true }) { Icon(Icons.Rounded.Tune, "Monthly budget", tint = c.textDim) }
                }
            }
            item {
                HudCard(Modifier.padding(horizontal = 16.dp), accent = when { m.budget <= 0 -> null; m.usedFraction > 1f -> c.bad; m.overPace -> c.warn; else -> c.good }, onClick = { if (m.budget <= 0) monthlyDialog = true }) {
                    Text("SPENT THIS MONTH", style = Sar.t.label, color = c.textDim)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Money.fmt(m.spent, cur), style = Sar.t.bigNum, color = c.text)
                        if (m.budget > 0) Text("  of ${Money.fmt(m.budget, cur)}", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    if (m.budget > 0) {
                        Meter(m.usedFraction, color = when { m.usedFraction > 1f -> c.bad; m.overPace -> c.warn; else -> c.good }, marker = m.elapsed)
                        Spacer(Modifier.height(8.dp))
                        val leftText = if (m.left >= 0) "${Money.fmt(m.left, cur)} left · $daysLeft days to go" else "${Money.fmt(-m.left, cur)} over budget"
                        val perDay = if (m.left > 0 && daysLeft > 0) " · ${Money.fmt(m.left / daysLeft, cur)}/day" else ""
                        Text(leftText + perDay, style = Sar.t.small, color = if (m.left < 0) c.bad else c.textDim)
                    } else {
                        Text("Set a monthly budget to see your pace →", style = Sar.t.small, color = c.accent)
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionLabel("Insights")
                    HudCard {
                        Money.insights(m, cur).forEachIndexed { i, line ->
                            Text(line, style = Sar.t.small, color = c.text, modifier = Modifier.padding(top = if (i == 0) 0.dp else 8.dp))
                        }
                    }
                    SectionLabel("Envelopes · tap one to set its budget")
                }
            }
            item {
                HudCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                    val spentBy = m.byCategory.toMap()
                    val cats = Category.entries.filter { (spentBy[it] ?: 0.0) > 0 || Money.categoryBudget(db.budget, it) > 0 }
                    if (cats.isEmpty()) Text("Spending by category appears here.", style = Sar.t.small, color = c.textDim, modifier = Modifier.padding(14.dp))
                    cats.sortedByDescending { spentBy[it] ?: 0.0 }.forEach { k ->
                        val spent = spentBy[k] ?: 0.0
                        val b = Money.categoryBudget(db.budget, k)
                        val frac = if (b > 0) (spent / b).toFloat() else if (m.spent > 0) (spent / m.spent).toFloat() else 0f
                        val color = when { b <= 0 -> c.accent; frac > 1f -> c.bad; frac > m.elapsed + 0.1f -> c.warn; else -> c.good }
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { budgetFor = k }.padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${k.emoji}  ${k.label}", style = Sar.t.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                                Text(Money.fmt(spent, cur), style = Sar.t.num, color = c.text)
                                if (b > 0) Text(" / ${Money.fmt(b, cur)}", style = Sar.t.small, color = c.textDim)
                            }
                            Spacer(Modifier.height(6.dp))
                            Meter(frac, color = color, marker = if (b > 0) m.elapsed else null, height = 6.dp)
                        }
                    }
                }
            }
            item { SectionLabel("Recent", Modifier.padding(horizontal = 16.dp)) }
            if (recent.isEmpty()) item {
                EmptyState(Icons.Rounded.Savings, "No expenses yet", "Tap + or tell Sarathi “spent 120 on coffee”. UPI amounts in any app can be shared here too.")
            }
            byDay.forEach { (day, list) ->
                item(key = "d$day") {
                    val label = when (day) {
                        now.toLocalDate() -> "Today"
                        now.toLocalDate().minusDays(1) -> "Yesterday"
                        else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
                    }
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
                        Text(label.uppercase(), style = Sar.t.label.copy(fontSize = sp(11f)), color = c.textFaint, modifier = Modifier.weight(1f))
                        Text(Money.fmt(list.sumOf { it.amount }, cur), style = Sar.t.num.copy(fontSize = sp(12f)), color = c.textFaint)
                    }
                }
                items(list, key = { it.id }) { x ->
                    SwipeToDelete(onDelete = {
                        e.repo.deleteExpense(x.id)?.let { r -> e.bus.undo("Removed ${Money.fmt(r.amount, cur)}") { e.repo.restoreExpense(r) } }
                    }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).clickable { edit = x }.heightIn(min = 56.dp).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(x.category.emoji, style = Sar.t.title)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(x.note.ifBlank { x.category.label }, style = Sar.t.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${x.category.label} · ${Fmt.time(x.at.toZoned(now.zone), db.settings)}", style = Sar.t.small, color = c.textDim)
                            }
                            Text(Money.fmt(x.amount, cur), style = Sar.t.num.copy(fontSize = sp(16f)), color = c.text)
                        }
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 24.dp)
                .clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(c.accent, c.accent2)))
                .clickable(role = Role.Button) { add = true }.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = c.onAccent)
            Spacer(Modifier.width(6.dp))
            Text("EXPENSE", style = Sar.t.label.copy(fontSize = sp(14f)), color = c.onAccent)
        }
    }

    if (add) ExpenseSheet(null) { add = false }
    edit?.let { x -> ExpenseSheet(x) { edit = null } }
    budgetFor?.let { k ->
        AmountDialog("${k.emoji} ${k.label} budget / month", Money.categoryBudget(db.budget, k), onDismiss = { budgetFor = null }) { v -> e.repo.setCategoryBudget(k, v) }
    }
    if (monthlyDialog) AmountDialog("Monthly budget", db.budget.monthly, onDismiss = { monthlyDialog = false }) { v -> e.repo.setMonthlyBudget(v) }
}

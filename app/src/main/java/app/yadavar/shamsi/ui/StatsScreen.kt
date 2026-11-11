package app.yadavar.shamsi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yadavar.shamsi.jalali.JDate
import app.yadavar.shamsi.jalali.addDays
import app.yadavar.shamsi.jalali.iranWeekday
import app.yadavar.shamsi.jalali.dateEpoch
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Section
import app.yadavar.shamsi.model.dateOf
import app.yadavar.shamsi.model.faNum
import app.yadavar.shamsi.model.sectionOf
import java.util.Date

private val DoneColor = Color(0xFF3E9E6E)
private val LateColor = Color(0xFFD96060)
private val WaitColor = Color(0xFFD0891A)

data class MonthStats(val done: Int, val overdue: Int, val pending: Int)

/** The time ranges of the tab bar on the statistics page. */
enum class StatsRange(val label: String) {
    All("همه"),
    Past("گذشته"),
    Today("امروز"),
    ThisWeek("این هفته"),
    NextWeek("هفته آینده"),
}

/**
 * Numbers for one range: finished (every finished interval of a repeating reminder counts too), overdue right now,
 * and still waiting. A finished reminder belongs to the day it was finished; an unfinished one to its date.
 * Reminders without a date only appear in "همه". Weeks run from Saturday to Friday.
 */
fun computeStats(reminders: List<Reminder>, repeatDone: List<Long>, range: StatsRange, now: Date = Date()): MonthStats {
    val today = toJalali(now)
    val dayStart = dateEpoch(today.jy, today.jm, today.jd, 0, 0)
    val weekFirst = addDays(today, -iranWeekday(today))
    fun startOf(d: JDate) = dateEpoch(d.jy, d.jm, d.jd, 0, 0)
    val from: Long
    val to: Long
    when (range) {
        StatsRange.All -> { from = Long.MIN_VALUE; to = Long.MAX_VALUE }
        StatsRange.Past -> { from = Long.MIN_VALUE; to = dayStart }
        StatsRange.Today -> { from = dayStart; to = startOf(addDays(today, 1)) }
        StatsRange.ThisWeek -> { from = startOf(weekFirst); to = startOf(addDays(weekFirst, 7)) }
        StatsRange.NextWeek -> { from = startOf(addDays(weekFirst, 7)); to = startOf(addDays(weekFirst, 14)) }
    }
    fun inRange(epoch: Long) = epoch >= from && epoch < to
    var done = repeatDone.count { inRange(it) }
    var overdue = 0
    var pending = 0
    for (reminder in reminders) {
        if (reminder.completed) {
            val at = reminder.completedAt
            if (range == StatsRange.All || (at != null && inRange(at))) done++
        } else {
            val date = dateOf(reminder)
            val anchor = if (date != null) startOf(date) else null
            if (range == StatsRange.All || (anchor != null && inRange(anchor))) {
                if (sectionOf(reminder, now) == Section.Overdue) overdue++ else pending++
            }
        }
    }
    return MonthStats(done, overdue, pending)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatsTab(padding: PaddingValues, reminders: List<Reminder>, repeatDone: List<Long>) {
    var range by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(StatsRange.All) }
    val now = remember(reminders, repeatDone, range) { Date() }
    val stats = remember(reminders, repeatDone, range) { computeStats(reminders, repeatDone, range, now) }
    val total = stats.done + stats.overdue + stats.pending
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        androidx.compose.material3.ScrollableTabRow(
            selectedTabIndex = range.ordinal,
            edgePadding = 8.dp,
            containerColor = Color.Transparent,
        ) {
            StatsRange.entries.forEach { item ->
                androidx.compose.material3.Tab(
                    selected = range == item,
                    onClick = { range = item },
                    text = { Text(item.label, maxLines = 1, softWrap = false) },
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "آمار: " + range.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            SectionBox {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Donut(stats, total)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val percent = if (total == 0) 0 else stats.done * 100 / total
                        Text(faNum("$percent٪"), style = MaterialTheme.typography.headlineMedium)
                        Text("انجام شده", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SectionBox {
                BarRow("انجام شده", stats.done, total, DoneColor)
                BarRow("عقب‌افتاده", stats.overdue, total, LateColor)
                BarRow("در انتظار", stats.pending, total, WaitColor)
            }
            if (total == 0) {
                Text(
                    "در این بازه یادآوری‌ای نیست.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Donut(stats: MonthStats, total: Int) {
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Canvas(modifier = Modifier.size(190.dp).padding(8.dp)) {
        val stroke = 26.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        if (total > 0) {
            var start = -90f
            listOf(stats.done to DoneColor, stats.overdue to LateColor, stats.pending to WaitColor).forEach { (value, color) ->
                if (value > 0) {
                    val sweep = 360f * value / total
                    drawArc(color, start, (sweep - 2f).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                    start += sweep
                }
            }
        }
    }
}

@Composable
private fun BarRow(label: String, value: Int, total: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Text(label, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text(faNum(value), style = MaterialTheme.typography.titleMedium)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        ) {
            val fraction = if (total == 0) 0f else value.toFloat() / total
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
            )
        }
    }
}

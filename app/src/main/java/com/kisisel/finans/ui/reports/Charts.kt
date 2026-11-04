package com.kisisel.finans.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kisisel.finans.core.Money

data class BarSeries(val name: String, val color: Color)

/** values: seri sırasıyla kuruş değerleri. */
data class BarGroup(val label: String, val values: List<Long>)

/** Harici kütüphanesiz gruplanmış sütun grafiği. Çubuk yükseklikleri yalnızca görsel ölçeklemedir. */
@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    series: List<BarSeries>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 150.dp
) {
    val realMax = groups.flatMap { it.values }.maxOrNull() ?: 0L
    val scaleMax = realMax.coerceAtLeast(1L)

    Column(
        modifier.semantics { contentDescription = chartDescription(groups, series) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(s.color))
                    Spacer(Modifier.width(4.dp))
                    Text(s.name, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("En yüksek değer: ${Money.format(realMax)}", style = MaterialTheme.typography.labelSmall)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            groups.forEach { g ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier.height(chartHeight),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        g.values.forEachIndexed { i, v ->
                            Bar(v, scaleMax, series.getOrNull(i)?.color ?: Color.Gray)
                        }
                    }
                    Text(g.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Bar(value: Long, max: Long, color: Color) {
    if (value <= 0L) {
        Box(Modifier.width(16.dp).height(1.dp).background(color.copy(alpha = 0.4f)))
    } else {
        val fraction = (value.toDouble() / max.toDouble()).toFloat().coerceIn(0.02f, 1f)
        Box(Modifier.width(16.dp).fillMaxHeight(fraction).background(color))
    }
}

private fun chartDescription(groups: List<BarGroup>, series: List<BarSeries>): String =
    groups.joinToString(". ") { g ->
        g.label + ": " + g.values.mapIndexed { i, v ->
            "${series.getOrNull(i)?.name ?: ""} ${Money.format(v)}"
        }.joinToString(", ")
    }

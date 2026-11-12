package app.yadavar.shamsi.ui

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yadavar.shamsi.model.faNum
import kotlin.math.abs

/**
 * One scrolling column of numbers (0 until [count]). The number in the middle is the chosen one;
 * the list snaps to it when the finger is lifted.
 */
@Composable
fun WheelColumn(
    count: Int,
    selected: Int,
    onSelected: (Int) -> Unit,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 48.dp,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, count - 1))
    val latest by rememberUpdatedState(onSelected)
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
        }.collect { index -> if (index != null) latest(index) }
    }
    val centerIndex by remember {
        androidx.compose.runtime.derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index ?: selected
        }
    }
    Box(modifier = modifier.height(itemHeight * 3), contentAlignment = Alignment.Center) {
        // Soft band behind the chosen row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
        )
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth().height(itemHeight * 3),
            contentPadding = PaddingValues(vertical = itemHeight),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed((0 until count).toList()) { index, value ->
                val distance = abs(index - centerIndex)
                Box(modifier = Modifier.height(itemHeight).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        faNum(value.toString().padStart(2, '0')),
                        fontSize = fontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.alpha(if (distance == 0) 1f else if (distance == 1) 0.45f else 0.2f),
                    )
                }
            }
        }
    }
}

/**
 * Two scrolling columns: hours (large digits) and minutes (smaller digits). Hours are on the left,
 * minutes on the right, as on every clock, even inside the right-to-left screens.
 */
@Composable
fun WheelTimePicker(hour: Int, minute: Int, onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    var h by remember(hour, minute) { androidx.compose.runtime.mutableIntStateOf(hour) }
    var m by remember(hour, minute) { androidx.compose.runtime.mutableIntStateOf(minute) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelColumn(
                count = 24,
                selected = hour,
                onSelected = { h = it; onChange(it, m) },
                fontSize = 40.sp,
                modifier = Modifier.width(96.dp),
            )
            Text(":", fontSize = 32.sp, color = MaterialTheme.colorScheme.onSurface)
            WheelColumn(
                count = 60,
                selected = minute,
                onSelected = { m = it; onChange(h, it) },
                fontSize = 24.sp,
                modifier = Modifier.width(80.dp),
            )
        }
    }
}

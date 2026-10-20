package com.vehicare.ui.service.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vehicare.domain.HistorySort
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Paper
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.sort_date
import vehicare.shared.generated.resources.sort_mileage

@Composable
fun HistorySortControl(
    selected: HistorySort,
    onSelect: (HistorySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = Paper,
        activeContentColor = Ink,
        activeBorderColor = Ink,
        inactiveContainerColor = Mist,
        inactiveContentColor = Slate,
        inactiveBorderColor = Hairline,
        disabledActiveContainerColor = Paper,
        disabledActiveContentColor = Slate,
        disabledActiveBorderColor = Hairline,
        disabledInactiveContainerColor = Mist,
        disabledInactiveContentColor = Slate,
        disabledInactiveBorderColor = Hairline,
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selected == HistorySort.DATE,
            onClick = { onSelect(HistorySort.DATE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            colors = colors,
        ) {
            Text(text = stringResource(Res.string.sort_date))
        }
        SegmentedButton(
            selected = selected == HistorySort.MILEAGE,
            onClick = { onSelect(HistorySort.MILEAGE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            colors = colors,
        ) {
            Text(text = stringResource(Res.string.sort_mileage))
        }
    }
}
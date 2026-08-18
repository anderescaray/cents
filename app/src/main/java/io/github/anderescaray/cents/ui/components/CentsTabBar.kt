package io.github.anderescaray.cents.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme

/**
 * The iOS tab bar: icon above a small label, tinted when selected, a hairline on top and
 * a bar background. No indicator pill and no ripple, both of which read as Material.
 */
@Composable
fun CentsTabBar(
    items: List<CentsTabItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CentsTheme.colors.barBackground),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CentsDimens.separatorThickness)
                    .background(CentsTheme.colors.barSeparator),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CentsDimens.tabBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val tint =
                    if (selected) {
                        CentsTheme.colors.accent
                    } else {
                        CentsTheme.colors.secondaryLabel
                    }

                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(item.key) },
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = item.label,
                        style = CentsTheme.typography.caption2,
                        color = tint,
                    )
                }
            }
        }
        Box(modifier = Modifier.height(navigationBarPadding.calculateBottomPadding()))
    }
}

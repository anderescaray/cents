package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter

/**
 * The money figures, drawn the same way on Wallet and at the top of Home: the total as a card,
 * then cash and bank beside what is invested, then what they leave out. Nothing at all when none
 * is chosen. Home passes the owner's choice (`DECISIONS.md` D34); Wallet passes every figure (D37).
 *
 * [totalStyle] and [onOpenInvestments] are the differences between the two screens. Wallet leads
 * with the total and its Invested tile opens the portfolio; on Home the total stays smaller than
 * the month's *left* or *overspent*, which is the figure there, and nothing is a way somewhere.
 */
@Composable
fun MoneyFiguresBlock(
    netWorth: NetWorth,
    figures: MoneyFigures,
    totalStyle: TextStyle,
    modifier: Modifier = Modifier,
    onOpenInvestments: (() -> Unit)? = null,
) {
    if (!figures.any) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CentsDimens.spacingM)) {
        if (figures.total) TotalCard(netWorth, totalStyle)
        if (figures.normal || figures.invested) {
            Row(horizontalArrangement = Arrangement.spacedBy(CentsDimens.spacingM)) {
                if (figures.normal) {
                    WorthTile(
                        iconRes = R.drawable.ic_wallet,
                        color = CentsTheme.colors.accent,
                        label = stringResource(R.string.money_normal),
                        value = MoneyFormatter.format(netWorth.normal),
                        negative = netWorth.normal.isNegative,
                        modifier = Modifier.weight(1f).testTag(NORMAL_FIGURE_TAG),
                    )
                }
                if (figures.invested) {
                    val invested = netWorth.invested
                    WorthTile(
                        iconRes = R.drawable.ic_trend_up,
                        color = CentsTheme.colors.positive,
                        label = stringResource(R.string.net_worth_invested),
                        value = MoneyFormatter.format(invested),
                        negative = invested.isNegative,
                        onClick = onOpenInvestments,
                        modifier = Modifier.weight(1f).testTag(INVESTED_FIGURE_TAG),
                    )
                }
            }
        }
        figuresNotes(netWorth).forEach { note ->
            Text(
                text = note,
                style = CentsTheme.typography.footnote,
                color = CentsTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = CentsDimens.spacingL),
            )
        }
    }
}

/**
 * Cash and bank plus invested, as one number, on the screen's one dark card: deep ink with a glow
 * of the accent in its top corner, so the total reads as the thing the others are parts of.
 */
@Composable
private fun TotalCard(
    netWorth: NetWorth,
    style: TextStyle,
) {
    val total = netWorth.total
    val colors = CentsTheme.colors
    val shape = RoundedCornerShape(CentsDimens.cardCornerRadius)
    Column(
        modifier =
            Modifier
                .testTag(TOTAL_FIGURE_TAG)
                .fillMaxWidth()
                .then(
                    if (colors.isDark) {
                        Modifier
                    } else {
                        Modifier.dropShadow(
                            shape,
                            Shadow(
                                radius = 24.dp,
                                color = colors.heroBottom.copy(alpha = HERO_SHADOW),
                                offset = DpOffset(0.dp, 10.dp),
                            ),
                        )
                    },
                ).clip(shape)
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(colors.heroGlow, Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * HERO_GLOW_REACH,
                        ),
                    )
                }.padding(
                    horizontal = CentsDimens.spacingL + CentsDimens.spacingXs,
                    vertical =
                        CentsDimens.spacingL + CentsDimens.spacingXs,
                ),
    ) {
        Text(
            text = stringResource(R.string.net_worth_total),
            style = CentsTheme.typography.subhead,
            color = Color.White.copy(alpha = HERO_LABEL_ALPHA),
        )
        Spacer(Modifier.height(CentsDimens.spacingXs))
        AnimatedMoney(
            money = total,
            style = style,
            color = if (total.isNegative) colors.heroNegative else Color.White,
        )
    }
}

private const val HERO_SHADOW = 0.28f
private const val HERO_GLOW_REACH = 0.9f
private const val HERO_LABEL_ALPHA = 0.66f

/** One part of what the owner has, as a small card: cash and bank, or what is invested. */
@Composable
private fun WorthTile(
    iconRes: Int,
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    /** Drawn in the warning colour, as an overdrawn total is everywhere else. */
    negative: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    CentsCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(iconRes = iconRes, color = color)
            Spacer(Modifier.weight(1f))
            if (onClick != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = CentsTheme.colors.tertiaryLabel,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(CentsDimens.spacingM))
        Text(text = label, style = CentsTheme.typography.footnote, color = CentsTheme.colors.secondaryLabel)
        Text(
            text = value,
            style = CentsTheme.typography.headline,
            color = if (negative) CentsTheme.colors.destructiveText else CentsTheme.colors.label,
            maxLines = 1,
        )
    }
}

/**
 * What the figures shown leave out, said when it is left out: money in archived accounts exists
 * and is not in the figures above, and a figure that quietly omits it is the kind of number that
 * gets believed and should not be.
 */
@Composable
private fun figuresNotes(netWorth: NetWorth): List<String> =
    buildList {
        if (netWorth.hasArchivedMoney) {
            add(stringResource(R.string.net_worth_archived, MoneyFormatter.format(netWorth.inArchivedAccounts)))
        }
    }

internal const val NORMAL_FIGURE_TAG = "figureNormal"
internal const val INVESTED_FIGURE_TAG = "figureInvested"
internal const val TOTAL_FIGURE_TAG = "figureTotal"

package io.github.anderescaray.cents.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.anderescaray.cents.R
import io.github.anderescaray.cents.domain.model.MoneyFigures
import io.github.anderescaray.cents.domain.model.NetWorth
import io.github.anderescaray.cents.ui.theme.CentsDimens
import io.github.anderescaray.cents.ui.theme.CentsTheme
import io.github.anderescaray.cents.ui.util.MoneyFormatter
import kotlin.math.roundToInt

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
                // Each tile says what part of the whole it is, when both are shown and the
                // whole is something to be a part of.
                val both = figures.normal && figures.invested
                if (figures.normal) {
                    WorthTile(
                        share = if (both) shareOf(netWorth.normal.minorUnits, netWorth.invested.minorUnits) else null,
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
                        share = if (both) shareOf(invested.minorUnits, netWorth.normal.minorUnits) else null,
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

/**
 * One part of what the owner has, as a small card: a wash of its colour from the top corner, its
 * mark as a solid disc, the figure, and - when [share] is given - a bar of how much of the whole
 * it is, drawn on the first time it is seen.
 */
@Composable
private fun WorthTile(
    iconRes: Int,
    color: Color,
    label: String,
    value: String,
    share: Float?,
    modifier: Modifier = Modifier,
    /** Drawn in the warning colour, as an overdrawn total is everywhere else. */
    negative: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val valueColor = if (negative) CentsTheme.colors.destructiveText else CentsTheme.colors.label
    Column(
        modifier =
            modifier
                .scale(rememberPressScale(pressed))
                .cardSurface()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = TILE_WASH), Color.Transparent),
                            center = Offset.Zero,
                            radius = size.maxDimension,
                        ),
                    )
                }.then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).padding(CentsDimens.spacingL),
    ) {
        TileHeader(iconRes = iconRes, color = color, share = share, opens = onClick != null)
        Spacer(Modifier.height(CentsDimens.spacingM))
        Text(text = label, style = CentsTheme.typography.footnote, color = CentsTheme.colors.secondaryLabel)
        Text(
            text = headlineMoney(value, CentsTheme.typography.title3.fontSize, valueColor),
            style = CentsTheme.typography.title3,
            color = valueColor,
            maxLines = 1,
        )
        if (share != null) {
            Spacer(Modifier.height(CentsDimens.spacingM))
            ShareBar(share = share, color = color)
        }
    }
}

/** The tile's mark, its share of the whole as a small pill, and a chevron when it opens something. */
@Composable
private fun TileHeader(
    iconRes: Int,
    color: Color,
    share: Float?,
    opens: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TileBadge(iconRes = iconRes, color = color)
        Spacer(Modifier.weight(1f))
        if (share != null) {
            Text(
                text = stringResource(R.string.reports_share, (share * PERCENT).roundToInt()),
                style = CentsTheme.typography.caption1,
                color = CentsTheme.colors.secondaryLabel,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(CentsTheme.colors.fill)
                        .padding(horizontal = CentsDimens.spacingS, vertical = 2.dp),
            )
        }
        if (opens) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = CentsTheme.colors.tertiaryLabel,
                modifier = Modifier.padding(start = CentsDimens.spacingXs).size(16.dp),
            )
        }
    }
}

/** The tile's mark: its colour as a disc lit from above, standing on a glow of itself. */
@Composable
private fun TileBadge(
    iconRes: Int,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .size(CentsDimens.badgeSize)
                .dropShadow(
                    CircleShape,
                    Shadow(radius = 10.dp, color = color.copy(alpha = TILE_GLOW), offset = DpOffset(0.dp, 4.dp)),
                ).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(lerp(color, Color.White, TILE_HIGHLIGHT), color))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(CentsDimens.badgeSize * TILE_ICON_SHARE),
        )
    }
}

/** A thin bar, filled as far as [share] of the whole, the first time it is seen. */
@Composable
private fun ShareBar(
    share: Float,
    color: Color,
) {
    val drawn by rememberDrawOn()
    val track = CentsTheme.colors.fill
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(SHARE_BAR_HEIGHT.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(track)
                    val filled = size.width * share * drawn
                    if (filled > 0f) {
                        drawRoundRect(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(color.copy(alpha = SHARE_START), color),
                                    endX = filled,
                                ),
                            size = size.copy(width = filled),
                            cornerRadius = CornerRadius(size.height / 2),
                        )
                    }
                },
    )
}

/**
 * What part of [part] + [other] the first is, from 0 to 1; null when either is below zero or both
 * are zero, where "a share of the whole" means nothing. Through a `Double`, as [between] is.
 */
internal fun shareOf(
    part: Long,
    other: Long,
): Float? {
    val whole = part.toDouble() + other.toDouble()
    return if (part < 0 || other < 0 || whole == 0.0) null else (part / whole).toFloat()
}

private const val PERCENT = 100
private const val TILE_WASH = 0.12f
private const val TILE_GLOW = 0.35f
private const val TILE_HIGHLIGHT = 0.15f
private const val TILE_ICON_SHARE = 0.52f
private const val SHARE_BAR_HEIGHT = 6
private const val SHARE_START = 0.55f

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

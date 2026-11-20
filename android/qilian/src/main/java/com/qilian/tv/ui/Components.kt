package com.qilian.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** 全屏背景：墨绿渐变 + 角落金色光晕 + 淡淡的棋盘格纹。 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Palette.background),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Palette.Gold.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.08f),
                    radius = size.width * 0.45f,
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.86f, size.height * 0.08f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Palette.Cinnabar.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 1.0f),
                    radius = size.width * 0.4f,
                ),
                radius = size.width * 0.4f,
                center = Offset(size.width * 0.05f, size.height * 1.0f),
            )
        }
        content()
    }
}

/** 印章式图标：圆角方块里一个衬线汉字。 */
@Composable
fun Seal(
    char: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    background: Color = Palette.Cinnabar,
    foreground: Color = Palette.Ivory,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f))
            .background(Brush.linearGradient(listOf(background, background.copy(alpha = 0.82f))))
            .border(1.dp, foreground.copy(alpha = 0.28f), RoundedCornerShape(size * 0.24f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(char, style = Type.Title.copy(fontSize = (size.value * 0.56f).sp, color = foreground, letterSpacing = 0.sp))
    }
}

/** 可聚焦卡片（Google TV 风格：聚焦放大 + 金色描边 + 光晕）。 */
@Composable
fun FocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    container: Brush? = null,
    containerColor: Color = Palette.InkCard,
    focusedScale: Float = 1.06f,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.touchClick(enabled, onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (container != null) Color.Transparent else containerColor,
            focusedContainerColor = if (container != null) Color.Transparent else containerColor,
            pressedContainerColor = if (container != null) Color.Transparent else containerColor,
            contentColor = Palette.Ivory,
            focusedContentColor = Palette.Ivory,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Palette.InkLine), shape = shape),
            focusedBorder = Border(BorderStroke(2.5.dp, Palette.Gold), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Palette.Gold.copy(alpha = 0.45f), elevation = 18.dp),
        ),
    ) {
        Column(
            Modifier
                .then(if (container != null) Modifier.fillMaxSize().background(container) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}

/** TV 按钮：图标印章 + 文字，聚焦时金底黑字。 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seal: String? = null,
    primary: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    val base = when {
        primary -> Palette.Cinnabar
        else -> Palette.InkCard
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.touchClick(enabled, onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = base,
            contentColor = if (danger) Palette.Cinnabar.copy(alpha = 0.95f) else Palette.Ivory,
            focusedContainerColor = Palette.Gold,
            focusedContentColor = Palette.Night,
            pressedContainerColor = Palette.GoldDeep,
            pressedContentColor = Palette.Night,
            disabledContainerColor = base.copy(alpha = 0.35f),
            disabledContentColor = Palette.Muted.copy(alpha = 0.6f),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (primary) Color.Transparent else Palette.InkLine), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Palette.GoldSoft), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Palette.Gold.copy(alpha = 0.5f), elevation = 12.dp),
        ),
    ) {
        Row(
            Modifier.padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 9.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (seal != null) {
                Text(seal, style = Type.CardTitle.copy(fontSize = if (compact) 15.sp else 17.sp, color = androidx.compose.ui.graphics.Color.Unspecified))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = Type.BodyStrong.copy(fontSize = if (compact) 14.sp else 16.sp, color = Color.Unspecified),
                maxLines = 1,
            )
        }
    }
}

/** 二选一 / 多选一的分段选择器（每段都是可聚焦按钮）。 */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Night.copy(alpha = 0.55f))
            .border(1.dp, Palette.InkLine, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val shape = RoundedCornerShape(11.dp)
            Surface(
                onClick = { onSelect(value) },
                enabled = enabled,
                modifier = Modifier.touchClick(enabled) { onSelect(value) },
                shape = ClickableSurfaceDefaults.shape(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSelected) Palette.InkCard else Color.Transparent,
                    contentColor = if (isSelected) Palette.GoldSoft else Palette.IvoryMuted,
                    focusedContainerColor = Palette.Gold,
                    focusedContentColor = Palette.Night,
                    disabledContainerColor = if (isSelected) Palette.InkCard.copy(alpha = 0.5f) else Color.Transparent,
                    disabledContentColor = Palette.Muted,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, if (isSelected) Palette.Gold.copy(alpha = 0.6f) else Color.Transparent), shape = shape),
                ),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = Type.BodyStrong.copy(color = Color.Unspecified),
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Gold),
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = Type.Title)
        Spacer(Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = Type.Small)
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Palette.Gold) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 2.dp),
    ) {
        Text(text, style = Type.Small.copy(color = color, fontSize = 11.sp))
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Palette.Gold) {
    Box(
        modifier
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Palette.Night.copy(alpha = 0.6f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
        )
    }
}

/** 面板：半透明深色卡片。 */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.InkRaised.copy(alpha = 0.86f))
            .border(1.dp, Palette.InkLine, RoundedCornerShape(20.dp))
            .padding(padding),
        content = content,
    )
}

/** 遥控器友好的确认弹窗；默认焦点在“取消”。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "取消",
    danger: Boolean = true,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Palette.InkCard, Palette.InkRaised)))
                .border(1.dp, Palette.Gold.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                .padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal("南", size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = Type.Headline.copy(fontSize = 22.sp))
            }
            Spacer(Modifier.height(16.dp))
            Text(message, style = Type.Body.copy(fontSize = 15.sp, lineHeight = 24.sp))
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                val focus = androidx.compose.runtime.remember { FocusRequester() }
                TvButton(dismissText, onDismiss, Modifier.focusRequester(focus))
                TvButton(confirmText, onConfirm, primary = !danger, danger = danger)
                androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            }
        }
    }
}

/** 提示条（棋盘上方浮现的一句话）。 */
@Composable
fun Toast(text: String, modifier: Modifier = Modifier, color: Color = Palette.Gold) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.Night.copy(alpha = 0.88f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(text, style = Type.BodyStrong.copy(color = color), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Avatar(char: String, red: Boolean, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Palette.PieceFace, Palette.PieceEdge)))
            .border(2.dp, if (red) Palette.PieceRed else Palette.PieceBlack, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(char, style = Type.Title.copy(fontSize = (size.value * 0.5f).sp, color = if (red) Palette.PieceRed else Palette.PieceBlack))
    }
}

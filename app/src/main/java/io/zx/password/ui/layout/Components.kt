package io.zx.password.ui.layout

import androidx.compose.foundation.ExperimentalFoundationApi

class Components {
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun SwipeableItem(
        modifier: Modifier = Modifier,
        actionButtonWidth: Dp = 80.dp,
        onDelete: () -> Unit, // 在这里定义你的删除或其他操作
        content: @Composable () -> Unit
    ) {
        val density = LocalDensity.current
        val actionWidthPx = with(density) { actionButtonWidth.toPx() }
        // 定义锚点：中心位置为0，右侧（滑出）位置为正数
        val anchors = DraggableAnchors {
            DragAnchors.Center at 0f
            DragAnchors.End at actionWidthPx
        }

        // 创建并记住拖动状态
        val state = remember {
            AnchoredDraggableState(
                initialValue = DragAnchors.Center,
                anchors = anchors,
                positionalThreshold = { distance -> distance * 0.5f }, // 滑动超过50%时自动吸附
                velocityThreshold = { with(density) { 100.dp.toPx() } },
                animationSpec = tween()
            )
        }

        Box(modifier = modifier.clipToBounds()) {
            // 1. 背景层：放置操作按钮，会停留在右侧
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = actionButtonWidth),
                contentAlignment = Alignment.CenterEnd
            ) {
                // 这里是你的操作按钮，例如“删除”
                DeleteButton(onClick = onDelete)
            }

            // 2. 前景层：你的卡片内容，会随拖动偏移
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = state.offset.roundToInt(), y = 0) }
                    .anchoredDraggable(state, Orientation.Horizontal)
            ) {
                content()
            }
        }
    }

    @Composable
    fun DeleteButton(onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(80.dp)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.error)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text("删除", color = androidx.compose.material3.MaterialTheme.colorScheme.onError)
        }
    }
}
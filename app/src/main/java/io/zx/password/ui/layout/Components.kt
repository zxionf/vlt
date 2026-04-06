package io.zx.password.ui.layout

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults.flingBehavior
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

    enum class DragAnchors { Center, End }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun SwipeableItem(
        modifier: Modifier = Modifier,
        actionButtonWidth: Dp = 80.dp,
        onDelete: () -> Unit,
        content: @Composable () -> Unit
    ) {
        val density = LocalDensity.current
        val actionWidthPx = with(density) { actionButtonWidth.toPx() }

        val anchors = DraggableAnchors {
            DragAnchors.Center at 0f
            DragAnchors.End at -actionWidthPx
        }

        val state = remember {
            AnchoredDraggableState(
                initialValue = DragAnchors.Center,
                anchors = anchors,
            )
        }

//        val flingBehavior = remember {
//            AnchoredDraggableDefaults.flingBehavior(
//                snapAnimationSpec = spring(),
//                decayAnimationSpec = exponentialDecay()
//            )
//        }

        Box(modifier = modifier.clipToBounds()) {
            // 背景层：删除按钮，固定在右侧
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(align = Alignment.CenterEnd)
            ) {
                DeleteButton(
                    modifier = Modifier.width(actionButtonWidth),
                    onClick = onDelete
                )
            }

            // 前景层：卡片内容，向左偏移
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = state.offset.roundToInt(), y = 0) }
                    .anchoredDraggable(
                        state = state,
                        orientation = Orientation.Horizontal,
//                        flingBehavior = flingBehavior
                    )
            ) {
                // 给前景卡片添加一个背景色（例如白色），否则可能会透明看到后面的按钮
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    content()
                }
            }
        }
    }

    @Composable
    fun DeleteButton(onClick: () -> Unit, modifier: Modifier) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(80.dp)
                .background(MaterialTheme.colorScheme.error)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("删除", color = MaterialTheme.colorScheme.onError)
        }
    }

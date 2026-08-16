package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.theme.BwTheme

/**
 * Wraps a card so swiping it left or right reveals a red delete affordance.
 *
 * @param onSwiped invoked once the swipe passes the threshold.
 * @param autoDismiss `true` = let the card animate off (the caller removes it from the list
 *   immediately, e.g. Wishlist); `false` = snap the card back after [onSwiped] so the caller can
 *   confirm first (e.g. Collection shows a dialog, then deletes).
 */
@Composable
fun SwipeToDelete(
    onSwiped: () -> Unit,
    autoDismiss: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target != SwipeToDismissBoxValue.Settled) {
                onSwiped()
                autoDismiss
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { DeleteBackground(state) },
        content = { content() },
    )
}

@Composable
private fun DeleteBackground(state: SwipeToDismissBoxState) {
    val colors = BwTheme.colors
    val alignment =
        if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart
        else Alignment.CenterEnd
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.error)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bw_delete),
            contentDescription = "Delete",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

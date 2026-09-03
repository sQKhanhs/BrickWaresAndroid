package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.senniapp.brickwares.ui.theme.BwTheme
import com.senniapp.brickwares.ui.theme.BwType
import kotlinx.coroutines.launch

/**
 * Full-screen image gallery: a swipeable pager over the [candidates] (e.g. a set's box shot + render)
 * with a thumbnail strip for jumping between them. Any candidate whose image 404s (e.g. a set that has
 * a box but no render, or vice-versa) is dropped, so only real photos are listed. Tap the backdrop (or
 * back / ✕) to dismiss. Shared by the Set Detail hero and the item cards' image tap.
 */
@Composable
fun ImageGalleryDialog(candidates: List<String>, onDismiss: () -> Unit) {
    val colors = BwTheme.colors
    // Images that failed to load — removed from the pager and the thumbnail strip.
    val failed = remember(candidates) { mutableStateListOf<String>() }
    val images = candidates.filterNot { it in failed }
    val onImageError: (String) -> Unit = { url -> if (url !in failed) failed.add(url) }
    val pagerState = rememberPagerState(pageCount = { candidates.size - failed.size })
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xF2000000))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val url = images.getOrNull(page)
                Box(
                    modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 96.dp),
                            onState = { if (it is AsyncImagePainter.State.Error) onImageError(url) },
                        )
                    }
                }
            }
            // Close affordance.
            Text(
                "✕",
                style = BwType.cardTitle.copy(fontSize = 22.sp),
                color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).clickable(onClick = onDismiss),
            )
            // Thumbnail strip — only meaningful with more than one image. The thumbnails render all
            // candidates, so a 404 is detected and dropped even if the user never swipes to it.
            if (images.size > 1) {
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    images.forEachIndexed { i, url ->
                        val selected = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(
                                    BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.brandYellow else Color(0x55FFFFFF)),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { scope.launch { pagerState.animateScrollToPage(i) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                onState = { if (it is AsyncImagePainter.State.Error) onImageError(url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

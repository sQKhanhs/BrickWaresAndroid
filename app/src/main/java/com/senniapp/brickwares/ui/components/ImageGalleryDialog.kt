package com.senniapp.brickwares.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.senniapp.brickwares.data.model.ItemType
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
fun ImageGalleryDialog(candidates: List<String>, initialPage: Int = 0, onDismiss: () -> Unit) {
    val colors = BwTheme.colors
    // Images that failed to load — removed from the pager and the thumbnail strip.
    val failed = remember(candidates) { mutableStateListOf<String>() }
    val images = candidates.filterNot { it in failed }
    val onImageError: (String) -> Unit = { url -> if (url !in failed) failed.add(url) }
    // Open on the image the caller was showing (e.g. the hero pager's current page).
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (candidates.size - 1).coerceAtLeast(0)),
        pageCount = { candidates.size - failed.size },
    )
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

/**
 * Inline hero image gallery for the detail pages: a swipeable [HorizontalPager] over [candidates] with a
 * thumbnail strip below (only shown with more than one image) for tapping between them. Any candidate
 * whose image 404s is dropped from both the pager and the strip. The image floats borderless on its
 * parent (the hero card); tapping it opens the full-screen [ImageGalleryDialog] at the current image.
 * A [NoImagePlaceholder] shows when nothing loads.
 */
@Composable
fun HeroImageGallery(
    candidates: List<String>,
    itemType: ItemType,
    modifier: Modifier = Modifier,
    imageHeight: Dp = 260.dp,
    /** Maps an inline [candidates] url to the full-resolution url shown in the full-screen gallery on tap
     *  — e.g. the small hero thumbnail → the multi-MB render for crisp zoom. Identity by default. */
    fullResOf: (String) -> String = { it },
) {
    val colors = BwTheme.colors
    val platformContext = LocalPlatformContext.current
    var showFullscreen by remember { mutableStateOf(false) }
    // Probe the candidates before drawing the strip: keep only the urls that actually load, so a set whose
    // box shot 404s (e.g. a promo with no box) never flashes a 2nd thumbnail that then vanishes. Coil
    // caches the probe, so the on-screen load is a cache hit. Null while still probing.
    var resolved by remember(candidates) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(candidates) {
        val loader = SingletonImageLoader.get(platformContext)
        resolved = candidates.filter { url ->
            loader.execute(ImageRequest.Builder(platformContext).data(url).build()) is SuccessResult
        }
    }
    // While probing, show just the first candidate as the single main image (no strip yet) so the page
    // isn't blank; once probed, show the confirmed images — and the strip only when 2+ actually loaded.
    val images = resolved ?: candidates.take(1)
    val pagerState = rememberPagerState(pageCount = { images.size })
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Rounded white backing: on the pure-white light card it's invisible (the image floats,
        // borderless); on the dark card it frames the opaque render JPG as an intentional rounded tile
        // instead of a hard-edged white rectangle — matching the thumbnail strip below.
        val panel = Modifier.fillMaxWidth().height(imageHeight).clip(RoundedCornerShape(14.dp)).background(Color.White)
        if (images.isEmpty()) {
            Box(panel, contentAlignment = Alignment.Center) {
                NoImagePlaceholder(itemType, iconSize = 72.dp)
            }
        } else {
            HorizontalPager(state = pagerState, modifier = panel) { page ->
                val url = images.getOrNull(page)
                Box(
                    modifier = Modifier.fillMaxSize().clickable { showFullscreen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            // Thumbnail strip — only once probing has SETTLED (so it never appears then collapses) and
            // there are 2+ real images to switch between.
            if (resolved != null && images.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    images.forEachIndexed { i, url ->
                        val selected = i == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(
                                    BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.brandYellow else colors.borderSoft),
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { scope.launch { pagerState.animateScrollToPage(i) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    if (showFullscreen && images.isNotEmpty()) {
        ImageGalleryDialog(
            candidates = images.map(fullResOf),
            initialPage = pagerState.currentPage,
            onDismiss = { showFullscreen = false },
        )
    }
}

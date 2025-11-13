package com.feed.thechick.ui.main.gamescreen

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.feed.thechick.R
import com.feed.thechick.audio.rememberAudioController
import com.feed.thechick.ui.main.component.GradientOutlinedTextShort
import com.feed.thechick.ui.main.component.SecondaryIconButton
import com.feed.thechick.ui.main.gamescreen.engine.ChickState
import com.feed.thechick.ui.main.gamescreen.engine.GameEvent
import com.feed.thechick.ui.main.gamescreen.engine.ItemType
import com.feed.thechick.ui.main.gamescreen.engine.SpawnedItem
import com.feed.thechick.ui.main.gamescreen.engine.Viewport
import com.feed.thechick.ui.main.gamescreen.overlay.GameSettingsOverlay
import com.feed.thechick.ui.main.gamescreen.overlay.IntroOverlay
import com.feed.thechick.ui.main.gamescreen.overlay.WinOverlay
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ----------------------- Composable -----------------------
@Composable
fun GameScreen(
    onExitToMenu: (Int) -> Unit,
    viewModel: GameViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {

    // ----------------------- Enter Intro Every Time -----------------------
    LaunchedEffect(Unit) {
        viewModel.showIntroOnEnter()
    }

    // ----------------------- Context -----------------------
    val context = LocalContext.current

    // ----------------------- Audio -----------------------
    val audio = rememberAudioController()
    

    // ----------------------- State -----------------------
    val state by viewModel.state.collectAsState()

    // ----------------------- FX State -----------------------
    data class LostFx(val id: Long, val at: Offset)
    val lostFx = remember { mutableStateListOf<LostFx>() }
    var fxCounter by remember { mutableLongStateOf(0L) }

    // ----------------------- Audio + FX events -----------------------
    LaunchedEffect(audio) {
        viewModel.events.collect { event ->
            when (event) {
                GameEvent.FeedSuccess -> audio.playGameFeed()
                GameEvent.Mistake -> audio.playGameLose()
                GameEvent.GameWon -> audio.playGameWin()
                is GameEvent.LostSeedOverflow -> {
                    lostFx += LostFx(id = fxCounter++, at = event.at)
                }
            }
        }
    }

    // ----------------------- Pause on App Background -----------------------
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val s = viewModel.state.value
                if (!s.showWin && !s.showSettings) {
                    viewModel.pauseAndOpenSettings()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ----------------------- Back button -----------------------
    BackHandler {
        if (!state.showSettings && !state.showWin) {
            viewModel.pauseAndOpenSettings()
        } else {
        }
    }

    // ----------------------- State -----------------------
    LaunchedEffect(state.chickState) {
        if (state.chickState != ChickState.Idle) {
            delay(1000)
            viewModel.acknowledgeChickIdle()
        }
    }

    // ----------------------- Layout -----------------------
    Surface(color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

            val density = LocalDensity.current
            val fieldWidth = maxWidth
            val fieldHeight = maxHeight
            val fieldWidthPx = with(density) { fieldWidth.toPx() }
            val fieldHeightPx = with(density) { fieldHeight.toPx() }

            var mouthBounds by remember { mutableStateOf<Rect?>(null) }

            LaunchedEffect(fieldWidthPx, fieldHeightPx) {
                viewModel.bindSpawner(
                    spawnTick = {
                        val vp = Viewport(
                            widthPx = fieldWidthPx,
                            heightPx = fieldHeightPx,
                            horizontalPaddingPx = with(density) { 24.dp.toPx() },
                            verticalPaddingPx = with(density) { 24.dp.toPx() },
                            verticalLimitRatio = 0.7f
                        )
                        viewModel.spawnTick(vp, density)
                    }
                )
            }

            Image(
                painter = painterResource(id = R.drawable.bg_game),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                SecondaryIconButton(
                    onClick = { viewModel.pauseAndOpenSettings() },
                    modifier = Modifier.size(62.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.fillMaxSize(0.8f)
                    )
                }
                Scoreboard(
                    score = state.score,
                    lives = state.lives,
                    modifier = Modifier.wrapContentWidth(Alignment.End)
                )
            }

            Chick(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .size(fieldWidth * 0.7f),
                state = state.chickState,
                onMouthMeasured = { mouthBounds = it }
            )

            state.items.forEach { item ->
                key(item.id) {
                    DraggableItem(
                        item = item,
                        onReleased = { released, center ->
                            viewModel.removeItem(released.id)
                            val mouth = mouthBounds
                            if (mouth != null && mouth.contains(center)) {
                                if (released.type == ItemType.Seed) viewModel.registerSuccess()
                                else viewModel.registerMistake()
                            } else {
                                if (released.type == ItemType.Seed) viewModel.registerMistake()
                            }
                        }
                    )
                }
            }
            // ----------------------- Lost Seed FX -----------------------
            lostFx.forEach { fx ->
                key(fx.id) {
                    LostLifeBurst(at = fx.at, onFinished = {
                        lostFx.removeAll { it.id == fx.id }
                    })
                }
            }
            AnimatedVisibility(
                visible = state.showIntro,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(10f)
            ) {
                IntroOverlay(onStart = { viewModel.reset() })
            }

            AnimatedVisibility(
                visible = state.showSettings,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(15f)
            ) {
                GameSettingsOverlay(
                    onResume = { viewModel.resumeFromSettings() },
                    onRetry = { viewModel.reset() },
                    onHome = { viewModel.closeSettingsToHome(onExitToMenu) }
                )
            }

            AnimatedVisibility(
                visible = state.showWin,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.zIndex(20f)
            ) {
                WinOverlay(
                    score = state.score,
                    onShare = { shareScore(context, state.score) },
                    onHome = { onExitToMenu(state.score) },
                    onRetry = { viewModel.reset() }
                )
            }
        }
    }
}


@Composable
fun Scoreboard(
    score: Int,
    lives: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        GradientOutlinedTextShort(
            text = "Corn fed: $score",
            fontSize = 28.sp,
            gradientColors = listOf(Color(0xFFFFF4C2), Color(0xFFFFC66E)),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { index ->
                val filled = index < lives
                Image(
                    painter = painterResource(id = R.drawable.item_egg),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer(alpha = if (filled) 1f else 0.35f),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

// ----------------------- Chick -----------------------
@Composable
fun Chick(
    modifier: Modifier,
    state: ChickState,
    onMouthMeasured: (Rect) -> Unit,
) {
    val density = LocalDensity.current
    val imageRes = when (state) {
        ChickState.Idle -> R.drawable.chicken_idle
        ChickState.Happy -> R.drawable.chicken_happy
        ChickState.Cry -> R.drawable.chicken_cry
    }
    Box(
        modifier = modifier.onGloballyPositioned { layout ->
            val bounds = layout.boundsInRoot()
            val extra = with(density) { 16.dp.toPx() }
            val expanded = Rect(
                left = bounds.left - extra,
                top = bounds.top - extra,
                right = bounds.right + extra,
                bottom = bounds.bottom + extra
            )
            onMouthMeasured(expanded)
        }
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

// ----------------------- DraggableItem -----------------------
@Composable
fun DraggableItem(
    item: SpawnedItem,
    onReleased: (SpawnedItem, Offset) -> Unit,
) {
    var offset by remember(item.id) { mutableStateOf(item.startOffset) }
    var isDragging by remember(item.id) { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isDragging) 1.12f else 1f, label = "drag-scale")

    val dragModifier = Modifier
        .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .size(item.size)
        .zIndex(if (isDragging) 2f else 1f)
        .pointerInput(item.id) {
            detectDragGestures(
                onDragStart = { isDragging = true },
                onDrag = { change, dragAmount ->
                    change.consume()
                    offset += dragAmount
                },
                onDragEnd = {
                    isDragging = false
                    onReleased(item, offset + Offset(item.sizePx / 2f, item.sizePx / 2f))
                },
                onDragCancel = {
                    isDragging = false
                    offset = item.startOffset
                }
            )
        }

    Image(
        painter = painterResource(id = item.type.drawableRes),
        contentDescription = item.type.contentDescription,
        modifier = dragModifier.graphicsLayer { alpha = if (isDragging) 0.85f else 1f },
        contentScale = ContentScale.Fit
    )
}

fun shareScore(context: Context, score: Int) {
    val text = "I fed $score seeds in Egg Magnet! 🐣🌽"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share result"))
}

// ----------------------- FX: LostLifeBurst -----------------------
@Composable
fun LostLifeBurst(
    at: Offset,
    onFinished: () -> Unit
) {
    val density = LocalDensity.current
    val sizeDp = 44.dp
    val sizePx = with(density) { sizeDp.toPx() }
    val anim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        anim.animateTo(1f, animationSpec = tween(durationMillis = 600))
        onFinished()
    }

    val scale = 0.7f + 0.6f * anim.value
    val alpha = 1f - anim.value
    val dy = with(density) { 16.dp.toPx() } * (-anim.value)

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (at.x - sizePx / 2f).roundToInt(),
                    y = (at.y - sizePx / 2f + dy).roundToInt()
                )
            }
            .size(sizeDp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .zIndex(9f)
    ) {
        Image(
            painter = painterResource(id = R.drawable.item_egg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(Color(0xFFE74C3C), blendMode = BlendMode.Modulate)
        )
    }
}
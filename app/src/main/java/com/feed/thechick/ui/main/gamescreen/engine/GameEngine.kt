package com.feed.thechick.ui.main.gamescreen.engine

import com.feed.thechick.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

// ----------------------- Consts -----------------------
private const val INITIAL_SPAWN_DELAY = 1600L
private const val MIN_SPAWN_DELAY = 520L

// ----------------------- Models -----------------------
data class Viewport(
    val widthPx: Float,
    val heightPx: Float,
    val horizontalPaddingPx: Float,
    val verticalPaddingPx: Float,
    val verticalLimitRatio: Float = 0.7f
)

data class SpawnedItem(
    val id: Int,
    val type: ItemType,
    val size: Dp,
    val sizePx: Float,
    val startOffset: Offset,
    val spawnedAt: Long,
)

fun SpawnedItem.bounds(extra: Float = 0f): Rect {
    return Rect(
        left = startOffset.x - extra,
        top = startOffset.y - extra,
        right = startOffset.x + sizePx + extra,
        bottom = startOffset.y + sizePx + extra
    )
}

enum class ItemType(
    val size: Dp,
    val drawableRes: Int,
    val contentDescription: String,
) {
    Seed(72.dp, R.drawable.item_gold_corn, "Golden corn"),
    Rock(72.dp, R.drawable.item_stone, "Stone"),
    Frog(86.dp, R.drawable.item_frog, "Frog");

    companion object {
        fun random(): ItemType {
            val roll = Random.nextFloat()
            return when {
                roll < 0.6f -> Seed
                roll < 0.8f -> Rock
                else -> Frog
            }
        }
    }
}

enum class ChickState { Idle, Happy, Cry }

data class GameState(
    val score: Int = 0,
    val lives: Int = 3,
    val running: Boolean = false,
    val showIntro: Boolean = true,
    val showSettings: Boolean = false,
    val showWin: Boolean = false,
    val spawnDelay: Long = INITIAL_SPAWN_DELAY,
    val items: List<SpawnedItem> = emptyList(),
    val chickState: ChickState = ChickState.Idle,
    val nextItemId: Int = 0
)

sealed interface GameEvent {
    data object FeedSuccess : GameEvent
    data object Mistake : GameEvent
    data object GameWon : GameEvent
    data class LostSeedOverflow(val at: Offset) : GameEvent
}

// ----------------------- Engine -----------------------
class GameEngine {

    private val _state = MutableStateFlow(GameState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private val maxItems = 8

    fun reset() {
        _state.value = GameState(
            score = 0,
            lives = 3,
            running = true,
            showIntro = false,
            showSettings = false,
            showWin = false,
            spawnDelay = INITIAL_SPAWN_DELAY,
            items = emptyList(),
            chickState = ChickState.Idle,
            nextItemId = 0
        )
    }

    fun showIntroOnEnter() {
        val s = _state.value
        _state.value = s.copy(
            running = false,
            showIntro = true,
            showSettings = false,
            showWin = false,
            items = emptyList(),
            chickState = ChickState.Idle
        )
    }


    fun pauseAndOpenSettings() {
        _state.value = _state.value.copy(running = false, showSettings = true)
    }

    fun resumeFromSettings() {
        val s = _state.value
        if (!s.showIntro && !s.showWin) {
            _state.value = s.copy(running = true, showSettings = false)
        } else {
            _state.value = s.copy(showSettings = false)
        }
    }

    fun closeSettingsToHome(onExit: (Int) -> Unit) {
        val s = _state.value
        _state.value = s.copy(running = false, showSettings = false)
        onExit(s.score)
    }

    fun onBackPressed(onExit: (Int) -> Unit) {
        val s = _state.value
        when {
            s.showSettings -> resumeFromSettings()
            s.showWin -> {}
            else -> onExit(s.score)
        }
    }

    fun registerSuccess() {
        val s = _state.value
        val newDelay = maxOf(MIN_SPAWN_DELAY, (s.spawnDelay * 0.92f).toLong())
        _state.value = s.copy(
            score = s.score + 1,
            spawnDelay = newDelay,
            chickState = ChickState.Happy
        )
        _events.tryEmit(GameEvent.FeedSuccess)
    }

    fun registerMistake() {
        val s = _state.value
        val newLives = (s.lives - 1).coerceAtLeast(0)
        val endState = if (newLives <= 0) {
            s.copy(
                lives = 0,
                running = false,
                items = emptyList(),
                showSettings = false,
                showWin = true,
                chickState = ChickState.Cry
            )
        } else {
            s.copy(lives = newLives, chickState = ChickState.Cry)
        }
        _state.value = endState
        _events.tryEmit(GameEvent.Mistake)
        if (endState.showWin) {
            _events.tryEmit(GameEvent.GameWon)
        }
    }

    fun acknowledgeChickIdle() {
        val s = _state.value
        if (s.chickState != ChickState.Idle) {
            _state.value = s.copy(chickState = ChickState.Idle)
        }
    }

    fun removeItem(id: Int) {
        val s = _state.value
        _state.value = s.copy(items = s.items.filterNot { it.id == id })
    }

    fun spawnTick(viewport: Viewport, density: Density) {
        val s = _state.value
        if (!s.running) return

        val type = ItemType.random()
        val size = type.size
        val sizePx = with(density) { size.toPx() }
        val xRange = (viewport.widthPx - viewport.horizontalPaddingPx * 2 - sizePx).coerceAtLeast(0f)
        val verticalLimit = viewport.heightPx * viewport.verticalLimitRatio
        val yRange = (verticalLimit - viewport.verticalPaddingPx - sizePx).coerceAtLeast(0f)
        if (xRange <= 0f || yRange <= 0f) return

        val spacing = with(density) { 12.dp.toPx() }
        var placed: SpawnedItem? = null

        repeat(24) {
            val startX = viewport.horizontalPaddingPx + Random.nextFloat() * xRange
            val startY = viewport.verticalPaddingPx + Random.nextFloat() * yRange
            val candidate = Rect(
                left = startX - spacing,
                top = startY - spacing,
                right = startX + sizePx + spacing,
                bottom = startY + sizePx + spacing
            )
            val overlaps = s.items.any { existing -> candidate.overlaps(existing.bounds(spacing)) }
            if (!overlaps) {
                placed = SpawnedItem(
                    id = s.nextItemId,
                    type = type,
                    size = size,
                    sizePx = sizePx,
                    startOffset = Offset(startX, startY),
                    spawnedAt = System.currentTimeMillis()
                )
                return@repeat
            }
        }

        val newItem = placed ?: return
        var newItems = s.items + newItem
        var newLives = s.lives
        var mistake = false

        if (newItems.size > maxItems) {
            val removed = newItems.first()
            newItems = newItems.drop(1)
            if (removed.type == ItemType.Seed) {
                newLives = (newLives - 1).coerceAtLeast(0)
                mistake = true
                val center = removed.startOffset + Offset(removed.sizePx / 2f, removed.sizePx / 2f)
                _events.tryEmit(GameEvent.LostSeedOverflow(center))
            }
        }

        val endState = if (newLives <= 0) {
            s.copy(
                items = emptyList(),
                nextItemId = s.nextItemId + 1,
                lives = 0,
                running = false,
                showWin = true,
                showSettings = false,
                chickState = ChickState.Cry
            )
        } else {
            s.copy(
                items = newItems,
                nextItemId = s.nextItemId + 1,
                lives = newLives
            )
        }

        _state.value = endState
        if (mistake) {
            _events.tryEmit(GameEvent.Mistake)
            if (endState.showWin) {
                _events.tryEmit(GameEvent.GameWon)
            }
        }
    }
}

package com.pico.junglechess.game

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow

data class GameUiState(
    val position: Position = Position.initial(),
    val difficulty: Difficulty = Difficulty.EASY,
    val selected: Square? = null,
    val message: String = "捏合抓起红方棋子，放到发光格子；也可点选棋子和落点",
    val thinking: Boolean = false,
    val lastMove: Move? = null,
    val searchDepth: Int = 0,
    val nodes: Int = 0,
    val revision: Int = 0
) {
    val legalDestinations get() = selected?.let { from ->
        Jungle.legalMoves(position).filter { it.from == from }.map { it.to }
    }.orEmpty()
}

class GameViewModel : ViewModel() {
    private val mutable = MutableStateFlow(GameUiState())
    val state = mutable.asStateFlow()
    private val selections = MutableSharedFlow<Animal>(extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val selectedAnimals = selections.asSharedFlow()
    private var aiJob: Job? = null
    private var generation = 0

    fun select(square: Square): Boolean {
        val s = mutable.value
        if (s.thinking || s.position.outcome != null || s.position.turn != Side.RED) return false
        val piece = s.position[square]
        if (piece?.side != Side.RED) return false
        val moves = Jungle.legalMoves(s.position).filter { it.from == square }
        mutable.value = s.copy(selected = square, message =
            "${piece.animal.label} · 等级${piece.animal.rank} · ${square.label}　" +
                if (moves.isEmpty()) "当前无法移动" else "${moves.size}个合法落点")
        selections.tryEmit(piece.animal)
        return true
    }

    fun tap(square: Square): Boolean {
        if (mutable.value.position[square]?.side == Side.RED) return select(square)
        return drop(square)
    }

    fun drop(square: Square?): Boolean {
        val s = mutable.value
        val from = s.selected ?: return false
        if (s.thinking || s.position.turn != Side.RED || s.position.outcome != null) return false
        val move = square?.let { Move(from, it) }
        if (move == null || move !in Jungle.legalMoves(s.position)) {
            mutable.value = s.copy(selected = null, message = "落点不合法，棋子已回到原位", revision=s.revision+1)
            return false
        }
        commit(move)
        startAI()
        return true
    }

    fun cancelSelection() {
        mutable.value = mutable.value.copy(selected=null,revision=mutable.value.revision+1)
    }

    fun newGame() {
        generation++
        aiJob?.cancel()
        mutable.value = GameUiState(difficulty=mutable.value.difficulty, revision=mutable.value.revision+1)
    }

    fun setDifficulty(difficulty: Difficulty) {
        if (mutable.value.difficulty == difficulty) return
        generation++
        aiJob?.cancel()
        val old = mutable.value
        mutable.value = old.copy(difficulty=difficulty, selected=null, thinking=false,
            message="已切换为${difficulty.label}难度",revision=old.revision+1)
        startAI()
    }

    private fun commit(move: Move, search: SearchResult? = null) {
        val old = mutable.value
        val moving = requireNotNull(old.position[move.from])
        val captured = old.position[move.to]
        val next = Jungle.play(old.position,move)
        val actor = if (moving.side==Side.RED) "你" else "AI"
        val text = "$actor：${moving.animal.label} ${move.from.label} → ${move.to.label}" +
            (captured?.let { "，吃掉${it.animal.label}" } ?: "")
        mutable.value = old.copy(position=next,selected=null,thinking=false,lastMove=move,
            message=text,searchDepth=search?.depth?:old.searchDepth,nodes=search?.nodes?:old.nodes,
            revision=old.revision+1)
        Log.i("JungleGame","ply=${next.ply} $text outcome=${next.outcome}")
    }

    private fun startAI() {
        val s = mutable.value
        if (s.position.turn != Side.BLUE || s.position.outcome != null) return
        val token = generation
        mutable.value = s.copy(thinking=true)
        aiJob = viewModelScope.launch {
            delay(500) // Let the preceding move finish its animation.
            val snapshot = mutable.value
            val job = currentCoroutineContext().job
            val result = withContext(Dispatchers.Default) {
                JungleAI().choose(snapshot.position,snapshot.difficulty,cancelled={ !job.isActive })
            }
            ensureActive()
            if (generation==token && mutable.value.position==snapshot.position) {
                result.move?.let { commit(it,result) }
            }
        }
    }
}

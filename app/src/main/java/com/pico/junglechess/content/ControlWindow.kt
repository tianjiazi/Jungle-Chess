package com.pico.junglechess.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcherOwner
import com.pico.junglechess.R
import com.pico.junglechess.game.*
import com.pico.spatial.ui.design.*
import com.pico.spatial.ui.design.windows.AlertDialog
import com.pico.spatial.ui.design.windows.Sheet
import com.pico.spatial.ui.platform.containers.WindowContainerScope

@Composable
fun WindowContainerScope.ControlWindow(session: PanelSession, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val windowOwner = remember { Any() }
    DisposableEffect(session, lifecycle) {
        session.attachWindow(windowOwner)
        session.setWindowVisible(windowOwner, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> session.setWindowVisible(windowOwner, true)
                Lifecycle.Event.ON_STOP -> session.setWindowVisible(windowOwner, false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            session.detachWindow(windowOwner)
        }
    }
    val binding by session.binding.collectAsStateWithLifecycle()
    val game = binding
    if (game == null) {
        Box(modifier.windowConstraints(640.dp, 480.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("正在连接林间棋局…", style = PicoTheme.typography.titleLarge)
        }
        return
    }
    val state by game.vm.state.collectAsStateWithLifecycle()
    var rules by rememberSaveable { mutableStateOf(false) }
    var rulesPage by rememberSaveable { mutableStateOf(0) }
    var restart by rememberSaveable { mutableStateOf(false) }
    var boardSettings by rememberSaveable { mutableStateOf(false) }
    val backDispatcher = (LocalLifecycleOwner.current as? OnBackPressedDispatcherOwner)?.onBackPressedDispatcher
    DisposableEffect(backDispatcher, boardSettings, rules, restart) {
        val callback = object : OnBackPressedCallback(boardSettings && !rules && !restart) {
            override fun handleOnBackPressed() { boardSettings = false }
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
    Column(modifier.windowConstraints(640.dp, if (boardSettings) 368.dp else 480.dp).fillMaxSize()) {
        TitleBar(
            leadingActions = {
                if (boardSettings) {
                    Button(onClick = { boardSettings = false }, size = ButtonDefaults.Min) { Text("关闭") }
                } else {
                    Image(painterResource(R.mipmap.ic_spatial_launcher), contentDescription = "林间斗兽棋图标",
                        modifier = Modifier.size(56.dp).clip(CircleShape)
                            .border(2.dp, PicoTheme.colorScheme.fillTertiary, CircleShape))
                }
            },
            title = { Text(if (boardSettings) "棋盘设置" else "林间斗兽棋", style = PicoTheme.typography.headlineLarge) },
        )
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (game.loading || game.error != null) {
                Text(game.error ?: "正在布置林间棋桌…", style = PicoTheme.typography.bodyLarge)
                return@Column
            }
            if (boardSettings) {
                BoardSettings(game.scene)
                return@Column
            }
            val outcome = state.position.outcome
            val status = when {
                outcome != null -> if (outcome.winner == null) "本局和棋" else if (outcome.winner == Side.RED) "你赢了！" else "AI获胜"
                state.thinking -> "蓝方思考中…"
                else -> "红方回合 · 轮到你了"
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(status, style = PicoTheme.typography.titleLarge)
                    Text("红 ${state.position.board.count { it?.side == Side.RED }} / 蓝 ${state.position.board.count { it?.side == Side.BLUE }} · 第${state.position.ply / 2 + 1}回合",
                        style = PicoTheme.typography.bodyMedium)
                }
                Text(outcome?.reason ?: state.message, style = PicoTheme.typography.bodyMedium,
                    modifier = Modifier.heightIn(min = 52.dp))
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { game.scene.endDrag(true); restart = true },
                    modifier = Modifier.fillMaxWidth()) { Text("新对局") }
                Button(onClick = { game.scene.endDrag(true); rulesPage = 0; rules = true },
                    modifier = Modifier.fillMaxWidth()) { Text("规则") }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("对弈难度", style = PicoTheme.typography.bodyLarge)
                    SegmentControl(
                        modifier = Modifier.width(320.dp).height(SegmentControlDefaults.Regular.height),
                        cornerRadius = SegmentControlDefaults.Regular.containerCornerRadius(),
                    ) {
                        Difficulty.entries.forEach { difficulty ->
                            SegmentItem(
                                selected = state.difficulty == difficulty,
                                onClick = { game.scene.endDrag(true); game.vm.setDifficulty(difficulty) },
                                title = { Text(difficulty.label) },
                                textStyle = SegmentControlDefaults.Regular.textStyle(),
                                contentPadding = SegmentControlDefaults.Regular.itemContentPadding(),
                            )
                        }
                    }
                }
                Button(onClick = { game.scene.endDrag(true); boardSettings = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("棋盘设置")
                }
            }
        }
    }
    // Native modal windows are siblings of the page, with their own system material.
    if (restart && !game.loading && game.error == null) {
        AlertDialog(
            onDismissRequest = { restart = false },
            title = { Text("开始新对局？", style = PicoTheme.typography.headlineLarge) },
            content = { Text("当前棋局将重置，保留已选难度和棋盘位置。", style = PicoTheme.typography.bodyMedium) },
            buttons = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    Button(onClick = { restart = false }) { Text("继续本局") }
                    Button(onClick = { game.scene.endDrag(true); game.vm.newGame(); restart = false }) { Text("确认开始") }
                }
            },
        )
    }
    if (rules && !game.loading && game.error == null) {
        Sheet(
            modifier = Modifier.width(640.dp),
            leadingAction = {
                Button(onClick = { rules = false }, size = ButtonDefaults.Min) { Text("关闭") }
            },
            onDismissRequest = { rules = false },
            title = { Text("游戏规则") },
            contentPadding = PaddingValues(24.dp),
            contentSpace = 12.dp,
            bottom = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${rulesPage + 1} / 2", style = PicoTheme.typography.bodyMedium)
                    Button(onClick = { rulesPage = 1 - rulesPage }) {
                        Text(if (rulesPage == 0) "下一页" else "上一页")
                    }
                }
            },
        ) {
            Column(Modifier.height(192.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (rulesPage == 0) {
                    Text("象8 ＞ 狮7 ＞ 虎6 ＞ 豹5 ＞ 狼4 ＞ 狗3 ＞ 猫2 ＞ 鼠1", style = PicoTheme.typography.titleMedium)
                    Text("每回合横或竖走一格，同级可互吃。鼠能吃象，象不能吃鼠。", style = PicoTheme.typography.bodyMedium)
                    Text("只有鼠能入水，禁止跨水岸吃子。狮虎可横竖跳河，路径上的任何鼠都会阻挡。", style = PicoTheme.typography.bodyMedium)
                } else {
                    Text("进入敌方陷阱等级降至0，可被任意守方棋子吃掉，离开后恢复。己方陷阱不降级。", style = PicoTheme.typography.bodyMedium)
                    Text("占领敌方兽穴、吃光敌子或使其无路可走即获胜，不可进入己方兽穴。", style = PicoTheme.typography.bodyMedium)
                    Text("同一局面（含行动方）三次重复，或连续100步无吃子，判和。红方先行。", style = PicoTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun BoardSettings(scene: JungleScene) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { scene.lift(.08f) }, modifier = Modifier.width(144.dp).height(64.dp)) {
                Text("升高棋盘")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { scene.rotate(-45f) }, modifier = Modifier.width(144.dp).height(64.dp)) {
                    Text("向左旋转")
                }
                Button(onClick = { scene.resetView() }, modifier = Modifier.width(144.dp).height(64.dp)) {
                    Text("棋盘复位")
                }
                Button(onClick = { scene.rotate(45f) }, modifier = Modifier.width(144.dp).height(64.dp)) {
                    Text("向右旋转")
                }
            }
            Button(onClick = { scene.lift(-.08f) }, modifier = Modifier.width(144.dp).height(64.dp)) {
                Text("降低棋盘")
            }
        }
    }
}

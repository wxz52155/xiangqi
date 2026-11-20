package com.qilian.tv.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.qilian.tv.data.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** 应用级的更新状态；首页自动检测与设置页手动检测共用。 */
class UpdateController(private val context: Context) {
    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data object UpToDate : State()
        data class Available(val release: Updater.Release) : State()
        data class Downloading(val release: Updater.Release, val progress: Float) : State()
        data class Ready(val release: Updater.Release, val file: File) : State()
        data class Failed(val message: String, val release: Updater.Release? = null) : State()
    }

    val installed = Updater.installed(context)
    var state by mutableStateOf<State>(State.Idle)
        private set
    var dialogOpen by mutableStateOf(false)
    private var job: Job? = null
    private var autoChecked = false

    val latest: Updater.Release?
        get() = when (val s = state) {
            is State.Available -> s.release
            is State.Downloading -> s.release
            is State.Ready -> s.release
            is State.Failed -> s.release
            else -> null
        }

    /** 启动时静默检测一次，有新版本才弹窗。 */
    fun autoCheck(scope: CoroutineScope) {
        if (autoChecked) return
        autoChecked = true
        check(scope, silent = true)
    }

    fun check(scope: CoroutineScope, silent: Boolean = false) {
        if (state is State.Checking || state is State.Downloading) return
        job?.cancel()
        job = scope.launch {
            state = State.Checking
            state = try {
                val release = Updater.latest()
                if (Updater.isNewer(release, installed)) {
                    dialogOpen = true
                    State.Available(release)
                } else State.UpToDate
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (silent) State.Idle else State.Failed(e.message ?: "检测失败")
            }
        }
    }

    fun download(scope: CoroutineScope) {
        val release = latest ?: return
        job?.cancel()
        job = scope.launch {
            state = State.Downloading(release, 0f)
            state = try {
                val file = Updater.download(context, release) { p -> state = State.Downloading(release, p) }
                State.Ready(release, file).also { install(it) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                State.Failed(e.message ?: "下载失败", release)
            }
        }
    }

    fun install(given: State.Ready? = null) {
        val ready = given ?: (state as? State.Ready) ?: return
        if (!Updater.canInstall(context)) {
            Updater.openInstallPermission(context)
            return
        }
        runCatching { Updater.install(context, ready.file) }
            .onFailure { state = State.Failed("无法打开安装程序：${it.message}", ready.release) }
    }

    /** 截图测试用：直接显示某个新版本。 */
    fun preview(release: Updater.Release) {
        state = State.Available(release)
        dialogOpen = true
    }

    fun cancel() {
        job?.cancel()
        val release = latest
        state = if (release != null) State.Available(release) else State.Idle
        dialogOpen = false
    }
}

/** 新版本弹窗：版本号、更新内容、下载进度、安装。 */
@Composable
fun UpdateDialog(controller: UpdateController, scope: CoroutineScope) {
    if (!controller.dialogOpen) return
    val release = controller.latest ?: return
    val state = controller.state
    Dialog(onDismissRequest = { controller.cancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Palette.InkCard, Palette.InkRaised)))
                .border(1.dp, Palette.Gold.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal("新", size = 40.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("发现新版本 v${release.versionName}", style = Type.Headline.copy(fontSize = 23.sp))
                    Text(
                        "当前 v${controller.installed.versionName}" +
                            (if (release.size > 0) " · 安装包 ${"%.1f".format(release.size / 1048576f)} MB" else "") +
                            (if (release.publishedAt.isNotBlank()) " · ${release.publishedAt}" else ""),
                        style = Type.Small,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("更新内容", style = Type.Label.copy(color = Palette.Gold))
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Night.copy(alpha = 0.55f))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(release.notes.ifBlank { "常规改进与问题修复。" }, style = Type.Body.copy(fontSize = 15.sp, lineHeight = 24.sp, color = Palette.Ivory))
            }
            Spacer(Modifier.height(18.dp))
            when (state) {
                is UpdateController.State.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("正在下载 ${(state.progress * 100).toInt()}%", style = Type.BodyStrong.copy(color = Palette.GoldSoft))
                        Spacer(Modifier.width(14.dp))
                        ProgressBar(state.progress, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                is UpdateController.State.Failed -> {
                    Text(state.message, style = Type.BodyStrong.copy(color = Palette.Cinnabar))
                    Spacer(Modifier.height(12.dp))
                }
                is UpdateController.State.Ready -> {
                    Text("下载完成。若没有出现安装界面，请在系统设置里允许“南坡象棋”安装应用后再点“安装”。", style = Type.Small.copy(color = Palette.GoldSoft))
                    Spacer(Modifier.height(12.dp))
                }
                else -> Unit
            }
            val focus = remember { FocusRequester() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                when (state) {
                    is UpdateController.State.Downloading -> TvButton("取消下载", { controller.cancel() }, Modifier.focusRequester(focus))
                    is UpdateController.State.Ready -> {
                        TvButton("稍后", { controller.dialogOpen = false })
                        TvButton("安装", { controller.install() }, Modifier.focusRequester(focus), seal = "装", primary = true)
                    }
                    else -> {
                        TvButton("稍后", { controller.cancel() })
                        TvButton(
                            if (state is UpdateController.State.Failed) "重新下载" else "下载并安装",
                            { controller.download(scope) },
                            Modifier.focusRequester(focus),
                            seal = "更",
                            primary = true,
                        )
                    }
                }
            }
            LaunchedEffect(state::class) { runCatching { focus.requestFocus() } }
        }
    }
}

/** 设置页 / 关于页里的版本卡片。 */
@Composable
fun VersionPanel(controller: UpdateController, scope: CoroutineScope, modifier: Modifier = Modifier) {
    Panel(modifier.fillMaxWidth()) {
        Text("版本", style = Type.Label.copy(color = Palette.Gold))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("当前 v${controller.installed.versionName}", style = Type.BodyStrong)
            Spacer(Modifier.weight(1f))
            when (val s = controller.state) {
                is UpdateController.State.Checking -> Pill("检测中", color = Palette.Muted)
                is UpdateController.State.UpToDate -> Pill("已是最新", color = Palette.Jade)
                is UpdateController.State.Available, is UpdateController.State.Downloading, is UpdateController.State.Ready ->
                    Pill("新版 v${controller.latest?.versionName}", color = Palette.Cinnabar)
                is UpdateController.State.Failed -> Pill(if (s.release == null) "检测失败" else "下载失败", color = Palette.Cinnabar)
                else -> Unit
            }
        }
        if (controller.state is UpdateController.State.Failed && (controller.state as UpdateController.State.Failed).release == null) {
            Spacer(Modifier.height(6.dp))
            Text((controller.state as UpdateController.State.Failed).message, style = Type.Small)
        }
        Spacer(Modifier.height(12.dp))
        if (controller.latest != null) {
            TvButton("查看新版本", { controller.dialogOpen = true }, seal = "新", primary = true, compact = true)
        } else {
            TvButton(
                if (controller.state is UpdateController.State.Checking) "检测中…" else "检查更新",
                { controller.check(scope) },
                seal = "更",
                compact = true,
                enabled = controller.state !is UpdateController.State.Checking,
            )
        }
    }
}

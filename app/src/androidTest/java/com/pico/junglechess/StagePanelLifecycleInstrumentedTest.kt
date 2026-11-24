package com.pico.junglechess

import android.content.Intent
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage as ActivityStage
import com.pico.junglechess.content.PanelSession
import com.pico.spatial.core.container.SpatialContainer
import com.pico.spatial.core.container.SpatialContainerType
import com.pico.spatial.ui.platform.containers.closeStage
import com.pico.spatial.ui.platform.containers.openWindowContainer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.FutureTask
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import com.pico.junglechess.game.*
import kotlinx.coroutines.flow.MutableStateFlow
import android.app.ActivityManager
import android.media.MediaPlayer
import com.pico.junglechess.content.BackgroundMusic
import com.pico.junglechess.content.SkySettings
import android.view.View
import android.view.ViewGroup
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.ecs.resource.BlendingMode

/** Reads actual SDK container instances; does not inject game or lifecycle state. */
@RunWith(AndroidJUnit4::class)
class StagePanelLifecycleInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task=FutureTask<T> { block() }
        instrumentation.runOnMainSync(task)
        return task.get()
    }
    private fun containers(): List<SpatialContainer> = main {
        // SDK's diagnostic registry is inspected only in this test APK.
        val manager=Class.forName("com.pico.spatial.core.container.SpatialContainerManager")
        val method=manager.getMethod("getContainers\$spatialsdk_core_core")
        @Suppress("UNCHECKED_CAST")
        val registry=method.invoke(manager.getField("INSTANCE").get(null)) as Map<Int,SpatialContainer>
        registry.values.filter { container ->
            SpatialContainer::class.java.getMethod("getAlive\$spatialsdk_core_core").invoke(container) == true
        }
    }
    private fun stageCount() = containers().count { it.type == SpatialContainerType.STAGE }
    private fun panelCount() = containers().count {
        it.type == SpatialContainerType.WINDOW_CONTAINER && it.name.startsWith(PanelSession.WINDOW_ID)
    }
    private suspend fun await(label: String, predicate: () -> Boolean) {
        try { withTimeout(20000) { while(!predicate()) delay(50) } }
        catch (error: TimeoutCancellationException) {
            throw AssertionError("$label; alive=${containers().map { "${it.name}:${it.type}" }}",error)
        }
    }
    private fun launchStage() = main {
        val context=instrumentation.targetContext
        val intent=requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun music(): BackgroundMusic = main {
        val owner=ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(ActivityStage.RESUMED)
            .filterIsInstance<ViewModelStoreOwner>().first {
                it.viewModelStore.keys().any { key -> key.contains("BackgroundMusic") }
            }
        ViewModelProvider(owner)[BackgroundMusic::class.java]
    }
    private fun player(music: BackgroundMusic): MediaPlayer? =
        BackgroundMusic::class.java.getDeclaredField("player").apply { isAccessible=true }
            .get(music) as MediaPlayer?
    private fun field(music: BackgroundMusic, name: String): Any? =
        BackgroundMusic::class.java.getDeclaredField(name).apply { isAccessible=true }.get(music)
    private fun spatialContent(): SpatialViewContent = main {
        fun search(view: View): SpatialViewContent? {
            if (view.javaClass.name=="com.pico.spatial.core.container.SpatialView")
                return view.javaClass.getMethod("getContent").invoke(view) as SpatialViewContent
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                search(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        (monitor.getActivitiesInStage(ActivityStage.RESUMED) +
            monitor.getActivitiesInStage(ActivityStage.PAUSED)).firstNotNullOf {
            search(it.window.decorView)
        }
    }
    private fun skySettings(): SkySettings = main {
        val owner = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(ActivityStage.RESUMED)
            .filterIsInstance<ViewModelStoreOwner>().first {
                it.viewModelStore.keys().any { key -> key.contains("SkySettings") }
            }
        ViewModelProvider(owner)[SkySettings::class.java]
    }
    private fun descendants(entity: Entity): List<Entity> =
        listOf(entity) + entity.getChildren().flatMap(::descendants)

    @Test fun skyImmersionPreservesForegroundAndSurvivesRecreation(): Unit = runBlocking {
        launchStage()
        await("Stage and controls opened") { stageCount()==1 && panelCount()==1 }
        await("Sky and pieces loaded") {
            main { spatialContent().entities.any { it.getName()=="jungle_table" &&
                it.getChildren().count { child -> child.getName().startsWith("piece_") }==16 } }
        }
        val settings = skySettings()
        try {
            main { settings.setLevel(100) }
            val roots = main { spatialContent().entities.toList() }
            val sky = main { roots.single { it.getName()=="jungle_sky" } }
            val skyMaterials = main { descendants(sky).flatMap {
                it.components[ModelComponent::class.java]?.materials?.filterIsInstance<UnlitMaterial>().orEmpty()
            }.distinct() }
            await("Full sky applied") { main { sky.enabled && skyMaterials.all { it.getOpacity()==1f } } }
            val foreground = main { roots.filter { it!==sky }.flatMap(::descendants) }
            val poses = main { foreground.associateWith {
                listOf(it.enabled, it.components[TransformComponent::class.java]?.getLocalMatrix()?.toString())
            } }
            fun materialValues(material: UnlitMaterial): List<Any> = listOf(
                material.getOpacity(), material.getBlendingMode(), material.getDepthWrite(),
                material.getDepthTest(), material.getBaseColor().toString())
            val materials = main { foreground.flatMap {
                it.components[ModelComponent::class.java]?.materials?.filterIsInstance<UnlitMaterial>().orEmpty()
            }.distinct().associateWith(::materialValues) }
            assertTrue("Foreground contains rendered models", materials.isNotEmpty())
            for (level in listOf(0, 50, 100, 0, 100)) {
                main { settings.setLevel(level) }
                await("Sky $level applied") { main {
                    sky.enabled==(level>0) && skyMaterials.all { it.getOpacity()==level/100f }
                } }
                main {
                    val stage=containers().first { it.type==SpatialContainerType.STAGE }
                    val runtime=stage.javaClass.getMethod("getContainerInstance\$spatialsdk_core_core").invoke(stage)
                    assertEquals("Mixed avoids system model fade",1,runtime.javaClass.getMethod("getStyle").invoke(runtime))
                    skyMaterials.forEach {
                        assertEquals(if(level==100) BlendingMode.OPAQUE else BlendingMode.TRANSPARENT,it.getBlendingMode())
                        assertEquals(level==100,it.getDepthWrite())
                    }
                    foreground.forEach {
                        assertEquals("Foreground pose/visibility ${it.getName()}", poses[it],
                            listOf(it.enabled, it.components[TransformComponent::class.java]?.getLocalMatrix()?.toString()))
                    }
                    materials.forEach { (material, values) -> assertEquals(values,materialValues(material)) }
                    assertEquals(roots,spatialContent().entities.toList())
                }
                Log.i("JungleSkyTest","level=$level foregroundEntities=${foreground.size} materials=${materials.size} unchanged PASS")
            }
            main { settings.setLevel(50) }
            withContext(Dispatchers.Main) { closeStage() }
            await("Stage closed") { stageCount()==0 && panelCount()==0 }
            launchStage()
            await("Stage reopened") { stageCount()==1 && panelCount()==1 }
            assertEquals(50,skySettings().level.value)
            await("Saved sky restored") { main {
                val root=spatialContent().entities.firstOrNull { it.getName()=="jungle_sky" }
                root!=null && descendants(root).any {
                    it.components[ModelComponent::class.java]?.materials?.filterIsInstance<UnlitMaterial>()
                        ?.any { material -> material.getOpacity()==.5f }==true
                }
            } }
            Log.i("JungleSkyTest","Stage recreation saved level=50 PASS")
        } finally {
            main {
                settings.setLevel(100)
                runCatching { skySettings().setLevel(100) }
                instrumentation.targetContext.getSharedPreferences("sky_settings",0)
                    .edit().putInt("immersion",100).commit()
            }
        }
    }
    @Test fun audioSettingsControlSeparatePlayersAndSurviveStageRecreation(): Unit = runBlocking {
        launchStage()
        await("Stage and controls opened") { stageCount()==1 && panelCount()==1 }
        val audio=music()
        assertTrue("Music defaults on",audio.settings.value.musicEnabled)
        assertTrue("Calls default on",audio.settings.value.soundEffectsEnabled)
        await("Music and eight recordings ready") {
            main { player(audio)?.isPlaying==true && (field(audio,"loaded") as Set<*>).size==8 }
        }
        try {
            main {
                audio.setMusicEnabled(false)
                assertFalse(requireNotNull(player(audio)).isPlaying)
                audio.playAnimal(Animal.RAT)
                assertTrue((field(audio,"stream") as Int)>0)
                assertTrue(audio.settings.value.soundEffectsEnabled)
                Log.i("JungleAudioTest","musicOff callsOn independent PASS")
                audio.setSoundEffectsEnabled(false)
                assertEquals(0,field(audio,"stream"))
                assertEquals(false,field(audio,"requestedFocus"))
                assertFalse(requireNotNull(player(audio)).isPlaying)
                Log.i("JungleAudioTest","bothOff stopped focusReleased PASS")
                audio.setMusicEnabled(true)
            }
            await("Music resumes independently") { main { player(audio)?.isPlaying==true } }
            main {
                audio.playAnimal(Animal.CAT)
                assertEquals(0,field(audio,"stream"))
                assertFalse(audio.settings.value.soundEffectsEnabled)
                Log.i("JungleAudioTest","musicOn callsOff independent PASS")
                audio.setSoundEffectsEnabled(true)
                audio.playAnimal(Animal.CAT)
                assertTrue((field(audio,"stream") as Int)>0)
                assertTrue(requireNotNull(player(audio)).isPlaying)
                audio.setMusicEnabled(false)
                audio.setSoundEffectsEnabled(false)
            }
            withContext(Dispatchers.Main) { closeStage() }
            await("Disabled Stage closed") { stageCount()==0 && panelCount()==0 }
            assertNull(player(audio))
            assertNull(field(audio,"calls"))
            launchStage()
            await("Stage recreated") { stageCount()==1 && panelCount()==1 }
            val restored=music()
            assertNotSame(audio,restored)
            main {
                assertFalse(restored.settings.value.musicEnabled)
                assertFalse(restored.settings.value.soundEffectsEnabled)
                assertEquals(false,field(restored,"requestedFocus"))
                assertFalse(player(restored)?.isPlaying==true)
                Log.i("JungleAudioTest","recreated preferences remain bothOff PASS")
                restored.setMusicEnabled(true)
                restored.setSoundEffectsEnabled(true)
            }
            await("Music reenabled after recreation") { main { player(restored)?.isPlaying==true } }
        } finally {
            // Leave the user's trial with both switches on, including when an assertion fails.
            main {
                val prefs=instrumentation.targetContext.getSharedPreferences("audio_settings",0)
                prefs.edit().putBoolean("music_enabled",true)
                    .putBoolean("sound_effects_enabled",true).commit()
            }
            withContext(Dispatchers.Main) { closeStage() }
        }
        await("Audio test cleanup") { stageCount()==0 && panelCount()==0 }
    }
    @Test fun shortAnimalCallsFollowSuccessfulSelectionAndRelease(): Unit = runBlocking {
        launchStage()
        await("Stage and controls opened") { stageCount()==1 && panelCount()==1 }
        val music=music()
        await("Eight short calls loaded") { main { (field(music,"loaded") as Set<*>).size==8 } }
        val vm=main {
            val owner=ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(ActivityStage.RESUMED)
                .filterIsInstance<ViewModelStoreOwner>().first {
                    it.viewModelStore.keys().any { key -> key.contains("GameViewModel") }
                }
            ViewModelProvider(owner)[GameViewModel::class.java]
        }
        for (animal in Animal.entries) {
            val square=vm.state.value.position.board.indexOfFirst {
                it?.side==Side.RED && it.animal==animal
            }.let { Square(it%7,it/7) }
            main { assertTrue(vm.tap(square)) }
            await("Selected $animal produces its own sound") {
                main { field(music,"lastAnimal")==animal && (field(music,"stream") as Int)>0 }
            }
            val played=main { field(music,"lastCall") }
            main { assertTrue(vm.select(square)) }
            delay(50)
            assertEquals("Immediate repeat is deduplicated",played,main { field(music,"lastCall") })
            delay(320)
            main { assertTrue(vm.select(square)) }
            await("Same selected animal can sound again") { main { field(music,"lastCall")!=played } }
            Log.i("JungleCallsTest","animal=$animal selected repeatCooldown PASS")
        }
        val before=main { field(music,"lastCall") }
        main { assertFalse(vm.select(Square(0,0))) }
        delay(100)
        assertEquals("Blue selection is silent",before,main { field(music,"lastCall") })
        main {
            @Suppress("UNCHECKED_CAST")
            val state=GameViewModel::class.java.getDeclaredField("mutable")
                .apply { isAccessible=true }.get(vm) as MutableStateFlow<GameUiState>
            val original=state.value
            state.value=original.copy(thinking=true)
            assertFalse(vm.select(Square(0,6)))
            state.value=original.copy(position=original.position.copy(turn=Side.BLUE))
            assertFalse(vm.select(Square(0,6)))
            state.value=original.copy(position=original.position.copy(outcome=Outcome(Side.RED,"test")))
            assertFalse(vm.select(Square(0,6)))
            state.value=original
        }
        delay(100)
        assertEquals("AI, thinking and terminal selections are silent",before,main { field(music,"lastCall") })
        main {
            music.setActive(false)
            music.playAnimal(Animal.RAT)
            assertEquals(0,field(music,"stream"))
            assertEquals(before,field(music,"lastCall"))
            music.setActive(true)
        }
        withContext(Dispatchers.Main) { closeStage() }
        await("Stage closed") { stageCount()==0 && panelCount()==0 }
        assertNull(field(music,"calls"))
        assertTrue((field(music,"loaded") as Set<*>).isEmpty())
        assertNull(player(music))
        Log.i("JungleCallsTest","rejected selections silent hidden stopped released PASS")
    }
    @Test fun closingStageRemovesControlsAndReopeningCreatesOnlyOnePanel(): Unit = runBlocking {
        repeat(2) { cycle ->
            launchStage()
            await("Stage and controls opened") { stageCount()==1 && panelCount()==1 }
            val music=music()
            await("Music starts with Stage") { main { player(music)?.isPlaying==true } }
            Log.i("JungleStageTest","cycle=$cycle beforeClose stage=${stageCount()} panels=${panelCount()}")
            withContext(Dispatchers.Main) { closeStage() }
            await("Stage and controls closed") { stageCount()==0 && panelCount()==0 }
            delay(1800) // Observe delayed effects after Stage shutdown.
            assertEquals(0,stageCount())
            assertEquals(0,panelCount())
            assertNull("Music resources released with Stage",player(music))
            Log.i("JungleStageTest","cycle=$cycle afterClose stage=0 panels=0 PASS")
        }
        // A late request must not leave a standalone panel outside the game scene.
        main {
            instrumentation.targetContext.openWindowContainer(PanelSession.WINDOW_ID,PanelSession.WINDOW_TAG)
        }
        delay(1800)
        assertEquals(0,stageCount())
        assertEquals(0,panelCount())
        Log.i("JungleStageTest","latePanelRequest outsideStage panels=0 PASS")
    }
    @Test fun backgroundingStageClosesPanelAndReturningRestoresOne(): Unit = runBlocking {
        launchStage()
        await("Stage and controls opened") { stageCount()==1 && panelCount()==1 }
        val music=music()
        await("Music starts with Stage") { main { player(music)?.isPlaying==true } }
        val initialPosition=main { requireNotNull(player(music)).currentPosition }
        delay(600)
        main {
            val media=requireNotNull(player(music))
            assertTrue(media.isLooping)
            assertTrue(media.duration in 68000..69000)
            assertTrue(media.currentPosition>initialPosition+200)
        }
        main {
            val activity=ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(ActivityStage.RESUMED)
                .first { it.packageName==instrumentation.targetContext.packageName }
            assertTrue(activity.moveTaskToBack(true))
        }
        await("Background Stage controls closed") { panelCount()==0 }
        delay(1800)
        assertEquals(0,panelCount())
        val pausedPosition=main {
            val media=requireNotNull(player(music))
            assertFalse(media.isPlaying)
            media.currentPosition
        }
        delay(300)
        assertEquals(pausedPosition,main { requireNotNull(player(music)).currentPosition })
        Log.i("JungleStageTest","music background paused position=$pausedPosition PASS")
        Log.i("JungleStageTest","backgroundStage remainingStages=${stageCount()} panels=0 PASS")
        launchStage()
        await("Returning restores one panel") { stageCount()==1 && panelCount()==1 }
        await("Returning resumes music") { main { player(music)?.isPlaying==true } }
        Log.i("JungleStageTest","music restored looping=true PASS")
        Log.i("JungleStageTest","returned stage=1 panels=1 PASS")
        withContext(Dispatchers.Main) { closeStage() }
        await("Returned Stage closed") { stageCount()==0 && panelCount()==0 }
        assertNull("Music released on final exit",player(music))
    }

    /** CLI clicks the real published result button while this test observes shutdown.
     * Near-terminal seeding is restricted to the test APK; the actual move ends the match.
     */
    @Test fun resultExitButtonClosesAllContainersAndTasks(): Unit = runBlocking {
        org.junit.Assume.assumeTrue(
            "Requires an external operator to click the published exit button; enable with -e manual_exit true",
            InstrumentationRegistry.getArguments().getString("manual_exit") == "true"
        )
        launchStage()
        await("Stage and controls opened") { stageCount()==1 && panelCount()==1 }
        val music=music()
        await("Result match music playing") { main { player(music)?.isPlaying==true } }
        main {
            val activities=ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(ActivityStage.RESUMED)
            val owner=activities.filterIsInstance<ViewModelStoreOwner>().first {
                it.viewModelStore.keys().any { key -> key.contains("GameViewModel") }
            }
            val vm=ViewModelProvider(owner)[GameViewModel::class.java]
            val field=GameViewModel::class.java.getDeclaredField("mutable").apply { isAccessible=true }
            @Suppress("UNCHECKED_CAST")
            val flow=field.get(vm) as MutableStateFlow<GameUiState>
            val board=MutableList<Piece?>(63) { null }
            board[Square(3,1).index]=Piece(Side.RED,Animal.RAT)
            board[Square(6,6).index]=Piece(Side.BLUE,Animal.RAT)
            flow.value=GameUiState(position=Position(board),difficulty=Difficulty.EASY)
            assertTrue(vm.select(Square(3,1)))
            assertTrue(vm.drop(Square(3,0)))
            assertEquals(Side.RED,vm.state.value.position.outcome?.winner)
        }
        Log.i("JungleStageTest","resultExit READY: click published 退出游戏 button")
        withTimeout(90000) { while(stageCount()!=0 || panelCount()!=0) delay(100) }
        delay(1800)
        assertEquals(0,stageCount())
        assertEquals(0,panelCount())
        assertNull("Result exit releases music",player(music))
        val tasks=instrumentation.targetContext.getSystemService(ActivityManager::class.java).appTasks
        assertTrue("Application recent tasks removed",tasks.isEmpty())
        Log.i("JungleStageTest","resultExit stage=0 panels=0 tasks=0 PASS")
        launchStage()
        await("A fresh launch after full exit") { stageCount()==1 && panelCount()==1 }
        withContext(Dispatchers.Main) { closeStage() }
        await("Fresh launch cleaned up") { stageCount()==0 && panelCount()==0 }
    }
}

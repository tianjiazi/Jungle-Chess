package com.pico.junglechess.content

import android.util.Log
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.pico.junglechess.game.*
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.content.setAccessibilityActions
import kotlinx.coroutines.CancellationException
import kotlin.math.*

class JungleScene(private val vm: GameViewModel) {
    private lateinit var content: SpatialViewContent
    private val roots = mutableListOf<Entity>()
    private var sky: Entity? = null
    private var skyMaterials: List<UnlitMaterial> = emptyList()
    private var skyImmersion = 100
    private var appliedSkyImmersion = -1
    private val pieces = mutableMapOf<Int,Entity>()
    private val tiles = mutableMapOf<Square,Entity>()
    private val rings = mutableMapOf<Square,Entity>()
    private val ringStyles = mutableMapOf<Square,Int>()
    private val pieceSquares = mutableMapOf<Int,Square?>()
    private var legalPosition: Position? = null
    private var legalSelection: Square? = null
    private var legalTargets: Set<Square> = emptySet()
    private val owned = mutableListOf<AutoCloseable>()
    private lateinit var table: Entity
    private lateinit var validMaterial: UnlitMaterial
    private lateinit var selectedMaterial: UnlitMaterial
    private lateinit var lastMaterial: UnlitMaterial
    private lateinit var invalidMaterial: UnlitMaterial
    private var disposed = false
    var ready = false
        private set
    private var state = vm.state.value
    private var dragId: Int? = null
    private var dragPosition = Vector3.ZERO
    private var hover: Square? = null
    private var yaw = 0f
    private var height = .74f
    private var depth = -1.12f
    private data class Motion(val entity: Entity, val from: Vector3, val to: Vector3, var elapsed: Float=0f, val duration: Float=.28f)
    private val motions = mutableMapOf<Int,Motion>()
    private var celebratedOutcome: Outcome? = null
    private var celebrationSeconds = 0f
    private data class Winner(val id: Int, val entity: Entity, val square: Square)
    private var winners: List<Winner> = emptyList()
    private lateinit var fireworks: Entity
    private data class Spark(val head: Entity, val tail: Entity, val direction: Vector3)
    private data class Burst(val center: Vector3, val sparks: List<Spark>)
    private val bursts = mutableListOf<Burst>()

    private fun position(entity: Entity) = entity.components[TransformComponent::class.java]!!.position
    private fun place(entity: Entity, p: Vector3) { entity.components[TransformComponent::class.java]!!.setPosition(p) }
    private fun checkAlive(entity: Entity) {
        if (disposed) { entity.destroy(); throw CancellationException("Scene disposed") }
    }
    private fun own(entity: Entity): Entity {
        checkAlive(entity); content.addEntity(entity); roots += entity; return entity
    }
    private fun child(parent: Entity, entity: Entity): Entity {
        checkAlive(entity); parent.addChild(entity); return entity
    }
    private fun material(color: Color4): UnlitMaterial = UnlitMaterial.create().also {
        // A highlight can temporarily have no ModelComponent references after a move.
        // Keep it alive until close(), so the next selection can reuse it safely.
        it.toGlobal()
        it.setBaseColor(color)
        owned += it
    }

    suspend fun load(c: SpatialViewContent, attachments: SpatialViewAttachments) {
        if (disposed) return
        content = c
        attachments.entity("control")?.let { panel ->
            own(panel)
            place(panel,Vector3(0f,1.43f,-1.92f))
        }
        val skyRoot = Entity.loadSuspend("asset://sky.glb")
        if (disposed) { skyRoot.destroy(); return }
        skyRoot.setName("jungle_sky")
        sky = own(skyRoot)
        fun collectSkyMaterials(entity: Entity): List<UnlitMaterial> =
            entity.components[ModelComponent::class.java]?.materials
                ?.filterIsInstance<UnlitMaterial>().orEmpty() +
                entity.getChildren().flatMap { collectSkyMaterials(it) }
        skyMaterials = collectSkyMaterials(skyRoot).distinct()
        check(skyMaterials.isNotEmpty()) { "Sky has no adjustable material" }
        setSkyImmersion(skyImmersion)
        val forest = Entity.loadSuspend("asset://forest.glb")
        if (disposed) { forest.destroy(); return }
        own(forest)
        val details = Entity.loadSuspend("asset://forest_details.glb")
        if (disposed) { details.destroy(); return }
        own(details)
        // Opaque unlit GLBs contain baked soft lighting in linear vertex colours.
        // Avoid per-eye PBR, runtime IBL generation and real-time shadow passes.
        table = own(Entity().apply { setName("jungle_table") })
        applyTablePose()
        child(table,Entity.loadSuspend("asset://board.glb"))
        validMaterial = material(Color4(.35f,1f,.72f,1f))
        selectedMaterial = material(Color4(1f,.86f,.40f,1f))
        lastMaterial = material(Color4(.57f,.78f,1f,1f))
        invalidMaterial = material(Color4(1f,.29f,.22f,1f))
        val ringMesh = MeshResource.createTorus(.064f,.003f).also { owned += it }
        val tileShape = ShapeResource.createBox(Vector3(.151f,.025f,.151f)).also { owned += it }
        val physics = PhysicsMaterialResource().also { owned += it }
        for (y in 0..8) for (x in 0..6) {
            val square = Square(x,y)
            val tile = child(table,Entity().apply {
                setName("tile_${square.index}")
                components.set(CollisionComponent(listOf(tileShape),physics))
                components.set(InteractableComponent())
                components.set(AccessibilityComponent("格子 ${square.label}",terrain(square)).apply { isAccessibilityElement=true })
            })
            place(tile,Vector3((x-3)*CELL,.024f,(y-4)*CELL))
            tiles[square]=tile
            content.setAccessibilityActions(tile,"square_${square.label}") {
                onClick("落子到${square.label}") { vm.tap(square) }
            }
            rings[square] = child(table,Entity().apply {
                components.set(ModelComponent(ringMesh,validMaterial))
                components[TransformComponent::class.java]!!.setQuaternion(EulerAngles(90f,0f,0f).toQuat())
                enabled=false
            }).also { place(it,Vector3((x-3)*CELL,.038f,(y-4)*CELL)) }
        }
        val pieceShape = ShapeResource.createBox(Vector3(.122f,.156f,.124f))
            .offsetByTranslation(Vector3(0f,.074f,0f)).also { owned += it }
        for (side in Side.entries) for (animal in Animal.entries) {
            val piece = Piece(side,animal)
            val root = child(table,Entity().apply {
                setName("piece_${piece.id}")
                if (side == Side.RED) {
                    components.set(CollisionComponent(listOf(pieceShape),physics))
                    components.set(InteractableComponent())
                    components.set(HoverEffectComponent())
                }
                components.set(AccessibilityComponent("${if(side==Side.RED) "红方" else "蓝方"}${animal.label}","等级${animal.rank}")
                    .apply { isAccessibilityElement=true })
            })
            pieces[piece.id]=root
            val model=Entity.loadSuspend("asset://${side.name.lowercase()}_${animal.name.lowercase()}.glb")
            if (disposed) { model.destroy(); return }
            child(root,model)
            // Both faces remain readable from the player's starting position.
            if (side == Side.RED) content.setAccessibilityActions(root,"piece_${piece.id}") {
                onClick("选择${animal.label}") {
                    currentSquare(piece.id)?.let { vm.tap(it) } ?: false
                }
                customActions=listOf(CustomAccessibilityAction("抓起${animal.label}") { beginDrag(root) },
                    CustomAccessibilityAction("放下${animal.label}") { endDrag(); true })
                // Same board validator as physical drag; no test-only state injection.
                move { p ->
                    if (dragId==piece.id) { dragPosition=p; hover=squareAt(p); updateRings() }
                    else { currentSquare(piece.id)?.let { place(root,at(it)) } }
                }
            }
        }
        createFireworks()
        ready=true
        sync(vm.state.value,true)
        Log.i("JungleScene","READY FullSpace=true board=7x9 animals=${pieces.size}")
    }

    /** Only the independent sky GLB participates in app-driven immersion changes. */
    fun setSkyImmersion(level: Int) {
        skyImmersion = level.coerceIn(0, 100)
        val root = sky ?: return
        if (disposed || appliedSkyImmersion == skyImmersion) return
        val opacity = skyImmersion / 100f
        skyMaterials.forEach {
            it.setBlendingMode(if (skyImmersion == 100) BlendingMode.OPAQUE else BlendingMode.TRANSPARENT)
            it.setDepthWrite(skyImmersion == 100)
            it.setOpacity(opacity)
        }
        root.enabled = skyImmersion > 0
        appliedSkyImmersion = skyImmersion
        Log.i("JungleSky", "immersion=$skyImmersion skyOpacity=$opacity foreground=unchanged")
    }

    fun terrain(s: Square) = when {
        Jungle.water(s) -> "小河 · 仅鼠可进入"
        Jungle.den(s)!=null -> "${if(Jungle.den(s)==Side.RED) "红方" else "蓝方"}兽穴"
        Jungle.trap(s)!=null -> "${if(Jungle.trap(s)==Side.RED) "红方" else "蓝方"}陷阱"
        else -> "陆地"
    }
    private fun at(s: Square) = Vector3((s.x-3)*CELL,.032f,(s.y-4)*CELL)
    private fun squareAt(p: Vector3): Square? {
        if (p.y < -.06f || p.y > .42f) return null
        return Square((p.x/CELL+3).roundToInt(),(p.z/CELL+4).roundToInt()).takeIf { it.valid }
    }
    private fun currentSquare(id: Int) = state.position.board.indexOfFirst { it?.id==id }
        .takeIf { it>=0 }?.let { Square(it%7,it/7) }
    private fun resolve(entity: Entity?): Entity? {
        var node=entity
        repeat(5) {
            val name = node?.getName()
            if (name?.startsWith("piece_")==true) {
                val id = name.removePrefix("piece_").toIntOrNull()
                return node.takeIf { id != null && id in pieces && state.position.board.any { it?.id == id && it.side == Side.RED } }
            }
            if (name?.startsWith("tile_")==true) return node
            node=node?.getParent()
        }
        return null
    }
    fun isGameEntity(e: Entity) = resolve(e)!=null
    fun tap(entity: Entity?) {
        if (!ready) return
        val name=resolve(entity)?.getName() ?: return
        if(name.startsWith("piece_")) {
            currentSquare(name.removePrefix("piece_").toInt())?.let { vm.tap(it) }
        } else {
            val i=name.removePrefix("tile_").toInt()
            vm.tap(Square(i%7,i/7))
        }
    }
    fun beginDrag(entity: Entity?): Boolean {
        if (!ready || dragId!=null) return false
        val root=resolve(entity) ?: return false
        val id=root.getName().removePrefix("piece_").toIntOrNull() ?: return false
        val square=currentSquare(id) ?: return false
        if (!vm.select(square)) return false
        state=vm.state.value
        motions.remove(id)
        dragId=id
        dragPosition=position(root)+Vector3(0f,.035f,0f)
        hover=square
        updateRings()
        return true
    }
    fun drag(dx: Float,dy: Float,dz: Float) {
        if (!ready || dragId==null) return
        // Convert a delta by subtracting two converted positions: px/left-handed view
        // -> metres/right-handed SpatialView -> rotated table local coordinates.
        val origin=content.convertPosition(Vector3.ZERO,ViewCoordinateSpace.Local,table)
        val end=content.convertPosition(Vector3(dx,dy,dz),ViewCoordinateSpace.Local,table)
        val delta=end-origin
        val next=dragPosition+delta
        dragPosition=Vector3(next.x.coerceIn(-1.2f,1.2f),next.y.coerceIn(.04f,.65f),next.z.coerceIn(-1.4f,1.4f))
        hover=squareAt(dragPosition)
        updateRings()
    }
    fun endDrag(cancel: Boolean=false) {
        val id=dragId ?: return
        val from=currentSquare(id)
        val target=if(cancel) null else squareAt(dragPosition)
        dragId=null; hover=null
        if(cancel) vm.cancelSelection() else vm.drop(target)
        state=vm.state.value
        val dest=currentSquare(id) ?: from
        if(dest!=null) motions[id]=Motion(pieces.getValue(id),position(pieces.getValue(id)),at(dest))
        sync(state)
    }
    fun sync(s: GameUiState, immediate: Boolean=false) {
        if (!ready || disposed) return
        val boardChanged=state.position!=s.position
        val restarting=state.position.outcome!=null && s.position.outcome==null
        val celebrationChanged=celebratedOutcome!=s.position.outcome
        if (dragId!=null && (s.selected==null || s.thinking || boardChanged)) {
            dragId=null; hover=null
        }
        state=s
        if (celebrationChanged) {
            celebratedOutcome=s.position.outcome
            celebrationSeconds=0f
            fireworks.enabled=false
            bursts.forEach { burst -> burst.sparks.forEach { it.head.enabled=false; it.tail.enabled=false } }
            winners=pieces.mapNotNull { (id,entity) ->
                val square=currentSquare(id)
                if (square!=null && state.position[square]?.side==celebratedOutcome?.winner)
                    Winner(id,entity,square) else null
            }
            Log.i("JungleCelebration", "winner=${celebratedOutcome?.winner} jumpingPieces=${winners.size} fireworks=${celebratedOutcome?.winner==Side.RED}")
        }
        for ((id,entity) in pieces) {
            val sq=currentSquare(id)
            if (!pieceSquares.containsKey(id) || pieceSquares[id]!=sq || immediate || celebrationChanged) {
                entity.enabled=sq!=null
                if (sq!=null) entity.components[AccessibilityComponent::class.java]!!.value=
                    "${sq.label} · 等级${state.position[sq]!!.animal.rank}" +
                        if (state.position.outcome?.winner==state.position[sq]?.side) " · 胜方庆祝跳动" else ""
                pieceSquares[id]=sq
            }
            if (sq==null) { motions.remove(id); continue }
            if (id==dragId) continue
            val to=at(sq)
            if (immediate || restarting) {
                motions.remove(id)
                place(entity,to)
            }
            else if (celebratedOutcome!=null && !celebrationChanged) continue
            else if ((position(entity)-to).length()>.001f && motions[id]?.to!=to)
                motions[id]=Motion(entity,position(entity),to,duration=if(boardChanged) .34f else .22f)
        }
        updateRings()
    }
    private fun updateRings() {
        if (!ready) return
        if (legalPosition !== state.position || legalSelection!=state.selected) {
            legalPosition=state.position
            legalSelection=state.selected
            legalTargets=state.legalDestinations.toSet()
        }
        val legal=legalTargets
        for ((sq,ring) in rings) {
            val show=sq in legal || sq==state.selected || sq==state.lastMove?.from || sq==state.lastMove?.to || sq==hover
            val style=when {
                !show -> 0
                sq==hover && sq !in legal && sq!=state.selected -> 4
                sq==state.selected -> 2
                sq in legal -> 1
                else -> 3
            }
            val old=ringStyles[sq] ?: 0
            if (old==style) continue
            if ((old==0)!=(style==0)) ring.enabled=show
            if (style!=0) ring.components[ModelComponent::class.java]!!.materials[0]=when(style) {
                1 -> validMaterial
                2 -> selectedMaterial
                3 -> lastMaterial
                else -> invalidMaterial
            }
            ringStyles[sq]=style
        }
    }
    fun frame(dt: Float) {
        if (!ready || disposed) return
        val iterator=motions.iterator()
        while(iterator.hasNext()) {
            val motion=iterator.next().value
            motion.elapsed+=dt
            val t=(motion.elapsed/motion.duration).coerceIn(0f,1f)
            val smooth=t*t*(3-2*t)
            val pos=motion.from*(1-smooth)+motion.to*smooth+Vector3(0f,sin(t*PI.toFloat())*.045f,0f)
            place(motion.entity,pos)
            if(t>=1) iterator.remove()
        }
        dragId?.let { id ->
            val root=pieces.getValue(id)
            val p=position(root)
            place(root,p+(dragPosition-p)*(1-exp(-28*dt)))
        }
        if (celebratedOutcome!=null) {
            celebrationSeconds+=dt
            val time=celebrationSeconds-VictoryMotion.SETTLE_SECONDS
            if (time>0f) {
                for (winner in winners) {
                    motions.remove(winner.id)
                    place(winner.entity,at(winner.square)+Vector3(0f,VictoryMotion.jump(winner.id,time),0f))
                }
                if (celebratedOutcome?.winner==Side.RED) animateFireworks(time)
            }
        }
    }
    /** A fixed pool of opaque unlit sparks; no lights, physics, or per-frame spawning. */
    private fun createFireworks() {
        fireworks=own(Entity().apply {
            setName("victory_fireworks")
            components.set(AccessibilityComponent("胜利烟花","玩家获胜后天空庆祝").apply { isAccessibilityElement=true })
            enabled=false
        })
        val mesh=MeshResource.createSphere(.027f).also { owned+=it }
        val colors=listOf(Color4(1f,.72f,.18f,1f),Color4(1f,.32f,.65f,1f),Color4(.32f,1f,.84f,1f))
        val centers=listOf(Vector3(-1.8f,3.6f,-4.8f),Vector3(0f,4.1f,-5.4f),Vector3(1.8f,3.7f,-4.8f))
        for (group in 0 until VictoryMotion.BURST_GROUPS) {
            val glow=material(colors[group])
            val sparks=List(VictoryMotion.SPARKS_PER_BURST) { index ->
                // Evenly spread directions over a sphere, with no random flicker.
                val y=1f-2f*(index+.5f)/VictoryMotion.SPARKS_PER_BURST
                val radius=sqrt(1f-y*y)
                val angle=index*2.399963f+group*.7f
                val direction=Vector3(cos(angle)*radius,y,sin(angle)*radius)
                fun spark(name: String)=child(fireworks,Entity().apply {
                    setName(name)
                    components.set(ModelComponent(mesh,glow))
                    enabled=false
                })
                Spark(spark("firework_${group}_${index}"),spark("trail_${group}_${index}"),direction)
            }
            bursts+=Burst(centers[group],sparks)
        }
    }
    private fun animateFireworks(time: Float) {
        val visible=time<VictoryMotion.FIREWORK_SECONDS
        if (fireworks.enabled!=visible) fireworks.enabled=visible
        if (!visible) return
        bursts.forEachIndexed { group,burst ->
            val age=VictoryMotion.fireworkAge(group,time) ?: return@forEachIndexed
            burst.sparks.forEachIndexed sparkLoop@ { index,spark ->
                val launching=age<.6f
                val active=age<2.45f && (!launching || index==0)
                if (spark.head.enabled!=active) spark.head.enabled=active
                val tailActive=active && !launching
                if (spark.tail.enabled!=tailActive) spark.tail.enabled=tailActive
                if (!active) return@sparkLoop
                if (launching) {
                    place(spark.head,burst.center-Vector3(0f,(1f-age/.6f)*2.2f,0f))
                    spark.head.components[TransformComponent::class.java]!!.setScaleVector(Vector3(.65f,1.7f,.65f))
                } else {
                    val elapsed=age-.6f
                    fun point(t: Float)=burst.center+spark.direction*(1.35f*(1-exp(-1.1f*t)))-
                        Vector3(0f,.3f*t*t,0f)
                    place(spark.head,point(elapsed))
                    place(spark.tail,point((elapsed-.085f).coerceAtLeast(0f)))
                    val fade=((1.85f-elapsed)/.6f).coerceIn(0f,1f)
                    val size=(.85f+.15f*sin(elapsed*19f+index))*fade
                    spark.head.components[TransformComponent::class.java]!!.setScaleVector(Vector3(size,size,size))
                    spark.tail.components[TransformComponent::class.java]!!.setScaleVector(Vector3(size*.45f,size*.45f,size*.45f))
                }
            }
        }
    }
    fun rotate(degrees: Float) { if(ready) { endDrag(true); yaw+=degrees; applyTablePose() } }
    fun lift(delta: Float) { if(ready) { endDrag(true); height=(height+delta).coerceIn(.55f,1.10f); applyTablePose() } }
    fun resetView() { if(ready) { endDrag(true); yaw=0f; height=.74f; depth=-1.12f; applyTablePose() } }
    private fun applyTablePose() {
        place(table,Vector3(0f,height,depth))
        table.components[TransformComponent::class.java]!!.setQuaternion(EulerAngles(0f,yaw,0f).toQuat())
    }
    fun close() {
        disposed=true; ready=false; motions.clear(); winners=emptyList(); bursts.clear()
        celebratedOutcome=null; celebrationSeconds=0f
        roots.reversed().forEach { runCatching { it.destroy() } }
        owned.reversed().forEach { runCatching { it.close() } }
        roots.clear(); owned.clear(); ringStyles.clear(); pieceSquares.clear()
        sky = null
        skyMaterials = emptyList()
    }
    companion object { const val CELL=.16f }
}

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
        val sky = Entity.loadSuspend("asset://sky.glb")
        if (disposed) { sky.destroy(); return }
        own(sky)
        val forest = Entity.loadSuspend("asset://forest.glb")
        if (disposed) { forest.destroy(); return }
        own(forest)
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
                components.set(CollisionComponent(listOf(pieceShape),physics))
                components.set(InteractableComponent())
                components.set(HoverEffectComponent())
                components.set(AccessibilityComponent("${if(side==Side.RED) "红方" else "蓝方"}${animal.label}","等级${animal.rank}")
                    .apply { isAccessibilityElement=true })
            })
            pieces[piece.id]=root
            val model=Entity.loadSuspend("asset://${side.name.lowercase()}_${animal.name.lowercase()}.glb")
            if (disposed) { model.destroy(); return }
            child(root,model)
            // Both faces remain readable from the player's starting position.
            content.setAccessibilityActions(root,"piece_${piece.id}") {
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
        ready=true
        sync(vm.state.value,true)
        Log.i("JungleScene","READY FullSpace=true board=7x9 animals=${pieces.size}")
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
            if (node?.getName()?.startsWith("piece_")==true || node?.getName()?.startsWith("tile_")==true) return node
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
        val origin=content.convertPosition(Vector3.ZERO,ViewCoordinateSpace.Local,content.localSpatialCoordinateSpace)
        val end=content.convertPosition(Vector3(dx,dy,dz),ViewCoordinateSpace.Local,content.localSpatialCoordinateSpace)
        val ref=content.localSpatialCoordinateSpace.origin
        val delta=table.convertPositionFrom(end,ref)-table.convertPositionFrom(origin,ref)
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
        if (dragId!=null && (s.selected==null || s.thinking || boardChanged)) {
            dragId=null; hover=null
        }
        state=s
        for ((id,entity) in pieces) {
            val sq=currentSquare(id)
            if (!pieceSquares.containsKey(id) || pieceSquares[id]!=sq || immediate) {
                entity.enabled=sq!=null
                if (sq!=null) entity.components[AccessibilityComponent::class.java]!!.value=
                    "${sq.label} · 等级${state.position[sq]!!.animal.rank}"
                pieceSquares[id]=sq
            }
            if (sq==null) { motions.remove(id); continue }
            if (id==dragId) continue
            val to=at(sq)
            if (immediate) place(entity,to)
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
    }
    fun rotate(degrees: Float) { if(ready) { endDrag(true); yaw+=degrees; applyTablePose() } }
    fun lift(delta: Float) { if(ready) { endDrag(true); height=(height+delta).coerceIn(.55f,1.10f); applyTablePose() } }
    fun resetView() { if(ready) { endDrag(true); yaw=0f; height=.74f; depth=-1.12f; applyTablePose() } }
    private fun applyTablePose() {
        place(table,Vector3(0f,height,depth))
        table.components[TransformComponent::class.java]!!.setQuaternion(EulerAngles(0f,yaw,0f).toQuat())
    }
    fun close() {
        disposed=true; ready=false; motions.clear()
        roots.reversed().forEach { runCatching { it.destroy() } }
        owned.reversed().forEach { runCatching { it.close() } }
        roots.clear(); owned.clear(); ringStyles.clear(); pieceSquares.clear()
    }
    companion object { const val CELL=.16f }
}

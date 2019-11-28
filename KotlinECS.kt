/**
 * 此為使用LibGDX和Artemis ECS Framework所開發的遊戲，其中的Render相關原始碼。
 * 利用了排序功能和自己寫的RendererHelper，減少Render Call次數，將遊戲效能提升了超過三倍。
 */
class RenderBatchSystem : BaseSystem() {
    private lateinit var mRender: ComponentMapper<RenderComponent>
    private lateinit var mTexture: ComponentMapper<TextureComponent>
    private lateinit var mShader: ComponentMapper<ShaderComponent>
    private lateinit var mCrop: ComponentMapper<CropComponent>
    private lateinit var mUI: ComponentMapper<UIComponent>

    val renderer = RendererHelper()

    private val sortedJobs = Bag<RenderJob>(RenderJob::class.java)
    private var needSorting = false

    init {
        Jungle.instance.debugView?.renderer = renderer
    }

    private val jobPool = object : Pool<RenderJob>() {
        override fun newObject(): RenderJob {
            return RenderJob(-1, null)
        }
    }

    private val comparator by lazy {
        object : Comparator<RenderJob> {
            fun compare(p0: Float, p1: Float): Int {
                return p0.compareTo(p1)
            }

            fun compare(p0: Int, p1: Int): Int {
                return p0.compareTo(p1)
            }

            fun compareNull(p0: Any?, p1: Any?): Int {
                return when {
                    p0 != null && p1 == null -> 1
                    p0 == null && p1 != null -> -1
                    else -> 0
                }
            }

            override fun compare(j0: RenderJob, j1: RenderJob): Int {
                val p0 = j0.entityId
                val p1 = j1.entityId

                compareNull(mUI[p0], mUI[p1]).let {
                    if (it != 0) return it
                }

                compare(
                    j0.agent!!.zOverride ?: mRender[p0].z,
                    j1.agent!!.zOverride ?: mRender[p1].z
                ).let {
                    if (it != 0) return it
                }

                compare(j0.agent.hashCode(), j1.agent.hashCode())

                compare(
                    mTexture[p0]?.region?.texture?.hashCode() ?: 0,
                    mTexture[p1]?.region?.texture?.hashCode() ?: 0
                ).let {
                    if (it != 0) return it
                }

                compare(
                    mShader[p0]?.hashCode() ?: 0,
                    mShader[p1]?.hashCode() ?: 0
                ).let {
                    if (it != 0) return it
                }

                compare(
                    mCrop[p0]?.rect?.hashCode() ?: 0,
                    mCrop[p1]?.rect?.hashCode() ?: 0
                ).let {
                    if (it != 0) return it
                }

                return 0
            }
        }
    }

    override fun processSystem() {
        if (needSorting) {
            try {
                sortedJobs.sort(comparator)
            } catch (e: NullPointerException) {
                return
            }
            needSorting = false
        }

        val data = sortedJobs.data
        for (i in 0 until sortedJobs.size()) {
            val job = data[i]
            val agent = job.agent!!

            agent.process(job.entityId)
        }

        renderer.end()
    }

    fun registerAgent(entityId: Int, agent: RenderSystem) {
        if (!mRender.has(entityId)) error("Require RenderComponent!")
        val job = jobPool.obtain()
        job.let {
            it.entityId = entityId
            it.agent = agent
        }
        sortedJobs.add(job)
        needSorting = true
    }

    fun unregisterAgent(entityId: Int, agent: RenderSystem) {
        val data = sortedJobs.data
        for (i in (0 until sortedJobs.size()).reversed()) {
            val job = data[i]
            if (job.entityId == entityId && job.agent == agent) {
                sortedJobs.remove(i)
                jobPool.free(job)
                needSorting = true
                break
            }
        }
    }

    override fun dispose() {
        renderer.dispose()
        Jungle.instance.debugView?.renderer = null
    }

    class RenderJob(var entityId: Int, var agent: RenderSystem?) : Pool.Poolable {
        override fun reset() {
            entityId = -1
            agent = null
        }
    }
}
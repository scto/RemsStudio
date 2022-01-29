package me.anno.engine

import me.anno.config.DefaultConfig
import me.anno.config.DefaultConfig.style
import me.anno.ecs.prefab.Prefab
import me.anno.ecs.prefab.PrefabCache.loadScenePrefab
import me.anno.engine.ui.DefaultLayout
import me.anno.engine.ui.EditorState
import me.anno.engine.ui.render.ECSShaderLib
import me.anno.engine.ui.scenetabs.ECSSceneTabs
import me.anno.gpu.GFX
import me.anno.gpu.shader.ShaderLib
import me.anno.input.ActionManager
import me.anno.io.files.FileReference
import me.anno.language.translation.Dict
import me.anno.studio.StudioBase
import me.anno.studio.rems.StudioActions
import me.anno.ui.Panel
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.debug.ConsoleOutputPanel
import me.anno.ui.debug.FrameTimes
import me.anno.ui.editor.OptionBar
import me.anno.ui.editor.RemsStudioUILayouts.createReloadWindow
import me.anno.ui.editor.config.ConfigPanel
import me.anno.utils.OS
import me.anno.utils.files.Files.findNextFileName
import me.anno.utils.hpc.SyncMaster
import org.apache.logging.log4j.LogManager


// todo runtime-components/hierarchy: must be displayed
// todo must have warning
// todo must be editable -> no CSet/CAdd, just instance changes


// todo color input sometimes janky... why?
// todo bug: text panel cannot be deleted from CanvasComponent
// todo bug: text input panel ml deletes two chars on first delete, why?

// todo panel: console output of multiple lines, with filters

// todo also the main object randomly just shrinks down (pool & truck)

// todo fix: tooltip texts of properties are not being displayed

// todo to reduce the size of the engine, physics engines could be turned into mods
// todo libraries like jpeg2000, pdf and such should become mods as well
// todo spellchecking could then become a mod :)


// todo right click on a path:
// todo - mutate -> create an asset based on that and replace the path, then inspect
// todo - inspect -> show it in the editor

// todo drop in meshes
// todo drop in ui maybe...
// todo key listeners (?)...

// todo reduce animations to a single translation plus rotations only?
// todo animation matrices then can be reduced to rotation + translation

// could not reproduce it lately -> was it fixed?
// to do bug: long text field is broken...

class RemsEngine : StudioBase(true, "Rem's Engine", "RemsEngine", 1) {

    lateinit var currentProject: GameEngineProject

    val syncMaster = SyncMaster()

    override fun loadConfig() {
        DefaultConfig.defineDefaultFileAssociations()
    }

    override fun onGameInit() {

        // CommandLineReader.start()
        ECSRegistry.init()

        Dict.loadDefault()

        // pdf stuff
        LogManager.disableLogger("PDICCBased")
        LogManager.disableLogger("PostScriptTable")
        LogManager.disableLogger("GlyphSubstitutionTable")
        LogManager.disableLogger("GouraudShadingContext")
        LogManager.disableLogger("FontMapperImpl")
        LogManager.disableLogger("FileSystemFontProvider")
        LogManager.disableLogger("ScratchFileBuffer")
        LogManager.disableLogger("FontFileFinder")
        LogManager.disableLogger("PDFObjectStreamParser")

    }

    override fun onGameLoopStart() {
        super.onGameLoopStart()
        GameEngine.scaledDeltaTime = GFX.deltaTime * GameEngine.timeFactor
        GameEngine.scaledNanos += (GameEngine.scaledDeltaTime * 1e9).toLong()
        GameEngine.scaledTime = GameEngine.scaledNanos * 1e-9
    }

    override fun onGameLoop(w: Int, h: Int) {
        DefaultConfig.saveMaybe("main.config")
        super.onGameLoop(w, h)
    }

    override fun save() {
        ECSSceneTabs.currentTab?.save()
    }

    fun loadSafely(file: FileReference): Prefab {
        if (file.exists && !file.isDirectory) {
            return try {
                loadScenePrefab(file)
            } catch (e: Exception) {
                val nextName = findNextFileName(file, 1, '-')
                LOGGER.warn("Could not open $file", e)
                val newFile = file.getSibling(nextName)
                return loadSafely(newFile)
            }
        } else {
            val prefab = Prefab("Entity")
            prefab.source = file
            return prefab
        }
    }

    override fun createUI() {

        // todo select project view, like Rem's Studio
        // todo what do we use as a background?

        val projectFile = OS.documents.getChild("RemsEngine").getChild("SampleProject")
        currentProject = GameEngineProject.readOrCreate(projectFile)!!
        currentProject.init()

        ShaderLib.init()
        ECSShaderLib.init()

        // todo select scene
        // todo show scene, and stuff, like Rem's Studio

        // todo different editing modes like Blender?, e.g. animating stuff, scripting, ...
        // todo and always be capable to change stuff

        // todo create our editor, where we can drag stuff into the scene, view it in 3D, move around, and such
        // todo play the scene

        // todo base shaders, which can be easily made touch-able

        // for testing directly jump in the editor

        val editScene = loadSafely(currentProject.lastScene)

        val style = style

        val list = PanelListY(style)

        val isGaming = false
        EditorState.syncMaster = syncMaster
        EditorState.projectFile = editScene.source

        ECSSceneTabs.open(syncMaster, editScene)
        // ECSSceneTabs.add(syncMaster, projectFile.getChild("2ndScene.json"))


        val options = OptionBar(style)


        val configTitle = Dict["Config", "ui.top.config"]
        options.addAction(configTitle, Dict["Settings", "ui.top.config.settings"]) {
            val panel = ConfigPanel(DefaultConfig, false, style)
            val window = createReloadWindow(panel, true) { createUI() }
            panel.create()
            windowStack.push(window)
        }

        options.addAction(configTitle, Dict["Style", "ui.top.config.style"]) {
            val panel = ConfigPanel(DefaultConfig.style.values, true, style)
            val window = createReloadWindow(panel, true) { createUI() }
            panel.create()
            windowStack.push(window)
        }

        list.add(options)

        list.add(ECSSceneTabs)

        val editUI = DefaultLayout.createDefaultMainUI(projectFile, syncMaster, isGaming, style)
        list.add(editUI)

        list.add(ConsoleOutputPanel.createConsoleWithStats(true, style))
        windowStack.push(list)

        ECSSceneTabs.window = windowStack.firstElement()
        StudioActions.register()
        ActionManager.init()

    }

    class RuntimeInfoPlaceholder : Panel(style) {
        override fun calculateSize(w: Int, h: Int) {
            minW = if (instance?.showFPS == true) FrameTimes.width else 0
            minH = 1
        }
    }

    override fun run() {
        instance2 = this
        super.run()
    }

    companion object {

        private val LOGGER = LogManager.getLogger(RemsEngine::class)
        var instance2: RemsEngine? = null

        @JvmStatic
        fun main(args: Array<String>) {
            RemsEngine().run()
        }

    }

}
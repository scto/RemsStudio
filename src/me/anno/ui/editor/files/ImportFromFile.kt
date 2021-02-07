package me.anno.ui.editor.files

import me.anno.config.DefaultConfig
import me.anno.language.translation.NameDesc
import me.anno.objects.SoftLink
import me.anno.objects.Transform
import me.anno.objects.Transform.Companion.toTransform
import me.anno.objects.Video
import me.anno.objects.meshes.Mesh
import me.anno.objects.modes.UVProjection
import me.anno.objects.text.Text
import me.anno.studio.StudioBase.Companion.addEvent
import me.anno.studio.rems.RemsStudio
import me.anno.studio.rems.Selection.selectTransform
import me.anno.ui.base.menu.Menu.ask
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.utils.types.Strings.getImportType
import org.apache.logging.log4j.LogManager
import org.joml.Vector3f
import java.io.File
import kotlin.concurrent.thread

object ImportFromFile {

    fun addChildFromFile(
        parent: Transform?,
        file: File,
        useSoftLink: Boolean?,
        doSelect: Boolean,
        callback: (Transform) -> Unit
    ) =
        addChildFromFile(parent, file, useSoftLink, doSelect, 0, callback)

    fun addChildFromFile(
        parent: Transform?,
        file: File,
        useSoftLink: Boolean?,
        doSelect: Boolean,
        depth: Int,
        callback: (Transform) -> Unit
    ) {
        if (file.isDirectory) {
            val directory = Transform(parent)
            directory.name = file.name
            if (depth < DefaultConfig["import.depth.max", 3]) {
                thread {
                    file.listFiles()?.filter { !it.name.startsWith(".") }?.forEach {
                        addEvent {
                            addChildFromFile(directory, it, useSoftLink, doSelect, depth + 1, callback)
                        }
                    }
                }
            }
        } else {
            val name = file.name
            when (file.extension.getImportType()) {
                "Transform" -> when (useSoftLink) {
                    null -> openMenu(listOf(
                        MenuOption(NameDesc("Link")) {
                            addChildFromFile(parent, file, true, doSelect, depth, callback)
                        },
                        MenuOption(NameDesc("Copy")) {
                            addChildFromFile(parent, file, false, doSelect, depth, callback)
                        }
                    ))
                    true -> {
                        val transform = SoftLink(file)
                        RemsStudio.largeChange("Added ${transform.name} to ${file.name}") {
                            var name2 = "${file.parentFile?.parentFile?.name}/${file.parentFile?.name}/${file.name}"
                            name2 = name2.replace("/Scenes/Root", "/")
                            name2 = name2.replace("/Scenes/", "/")
                            if (name2.endsWith(".json")) name2 = name2.substring(0, name2.length - 5)
                            if (name2.endsWith("/")) name2 = name2.substring(0, name2.lastIndex)
                            transform.name = name2
                            parent?.addChild(transform)
                            if(doSelect) selectTransform(transform)
                            callback(transform)
                        }
                    }
                    else -> {
                        thread {
                            val text = file.readText()
                            try {
                                val transform = text.toTransform()
                                if (transform == null) {
                                    LOGGER.warn("JSON didn't contain Transform!")
                                    addText(name, parent, text, doSelect, callback)
                                } else {
                                    addEvent {
                                        RemsStudio.largeChange("Added ${transform.name}") {
                                            parent?.addChild(transform)
                                            if (doSelect) selectTransform(transform)
                                            callback(transform)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                LOGGER.warn("Didn't understand JSON! ${e.message}")
                                addText(name, parent, text, doSelect, callback)
                            }
                        }
                    }
                }
                "Cubemap-Equ" -> {
                    RemsStudio.largeChange("Added Cubemap") {
                        val cube = Video(file, parent)
                        cube.scale.set(Vector3f(1000f, 1000f, 1000f))
                        cube.uvProjection.set(UVProjection.Equirectangular)
                        cube.name = name
                        if (doSelect) selectTransform(cube)
                        callback(cube)
                    }
                }
                "Cubemap-Tiles" -> {
                    RemsStudio.largeChange("Added Cubemap") {
                        val cube = Video(file, parent)
                        cube.scale.set(Vector3f(1000f, 1000f, 1000f))
                        cube.uvProjection.set(UVProjection.TiledCubemap)
                        cube.name = name
                        if (doSelect) selectTransform(cube)
                        callback(cube)
                    }
                }
                "Video", "Image", "Audio" -> {// the same, really ;)
                    // rather use a list of keywords?
                    RemsStudio.largeChange("Added Video") {
                        val video = Video(file, parent)
                        val fName = file.name
                        video.name = fName
                        if (DefaultConfig["import.decideCubemap", true]) {
                            if (fName.contains("360", true)) {
                                video.scale.set(Vector3f(1000f, 1000f, 1000f))
                                video.uvProjection.set(UVProjection.Equirectangular)
                            } else if (fName.contains("cubemap", true)) {
                                video.scale.set(Vector3f(1000f, 1000f, 1000f))
                                video.uvProjection.set(UVProjection.TiledCubemap)
                            }
                        }
                        if (doSelect) selectTransform(video)
                        callback(video)
                    }
                }
                "Text" -> {
                    try {
                        addText(name, parent, file.readText(), doSelect, callback)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return
                    }
                }
                "Mesh" -> {
                    RemsStudio.largeChange("Added Mesh") {
                        val mesh = Mesh(file, parent)
                        mesh.file = file
                        mesh.name = name
                        if (doSelect) selectTransform(mesh)
                        callback(mesh)
                    }
                }
                "HTML" -> {
                    // parse html? maybe, but html and css are complicated
                    // rather use screenshots or svg...
                    // integrated browser?
                    LOGGER.warn("todo html")
                }
                "Markdeep", "Markdown" -> {
                    // execute markdeep script or interpret markdown to convert it to html? no
                    // I see few use-cases
                    LOGGER.warn("todo markdeep")
                }
                else -> LOGGER.warn("Unknown file type: ${file.extension}")
            }
        }
    }

    fun addText(name: String, parent: Transform?, text: String, doSelect: Boolean, callback: (Transform) -> Unit) {
        // important ;)
        // should maybe be done sometimes in object as well ;)
        if (text.length > 500) {
            addEvent {
                ask(
                    NameDesc("Text has %1 characters, import?", "", "obj.text.askLargeImport")
                        .with("%1", text.codePoints().count().toString())
                ) {
                    RemsStudio.largeChange("Imported Text") {
                        val textNode = Text(text, parent)
                        textNode.name = name
                        if (doSelect) selectTransform(textNode)
                        callback(textNode)
                    }
                }
            }
            return
        }
        addEvent {
            RemsStudio.largeChange("Imported Text") {
                val textNode = Text(text, parent)
                textNode.name = name
                if (doSelect) selectTransform(textNode)
                callback(textNode)
            }
        }
    }

    private val LOGGER = LogManager.getLogger(ImportFromFile::class)

}
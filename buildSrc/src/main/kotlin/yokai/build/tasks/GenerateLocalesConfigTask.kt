package yokai.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class GenerateLocalesConfigTask : DefaultTask() {

    @get:Inject
    abstract val objectFactory: ObjectFactory

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun action() {
        val languages = objectFactory.fileTree()
            .from("src/commonMain/moko-resources")
            .matching { include("**/strings.xml") }
            .filterNot { it.readText().contains(emptyResourcesElement) }
            .map {
                it.parentFile.name
                    .replace("base", "en")
                    .replace("-r", "-")
                    .replace("+", "-")
                    .takeIf(String::isNotBlank) ?: "en"
            }
            .sorted()
            .joinToString(separator = "\n") {
                "   <locale android:name=\"$it\"/>"
            }

        val content = """
<?xml version="1.0" encoding="utf-8"?>
<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
$languages
</locale-config>
""".trimIndent()

        outputDir.get().file("xml/locales_config.xml").asFile.apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }

    companion object {
        private val emptyResourcesElement = "<resources>\\s*</resources>|<resources\\s*/>".toRegex()
    }
}

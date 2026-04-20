import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File

data class LicenseData(
    val projectName: String,
    val projectUrl: String,
    val version: String,
    val licenseName: String,
    val licenseUrl: String,
)

/**
 * Generates CSV, JSON, TXT, and Markdown files of open source dependencies with their respective license URLs
 *
 * Add `tasks.register<OssDisclaimerGenerator>("generateOssDisclaimer")` to root build.gradle.kts
 */
abstract class OssDisclaimerGenerator : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: org.gradle.api.file.DirectoryProperty

    init {
        outputDirectory.convention(project.layout.buildDirectory.dir("reports/open-source-licenses"))
    }

    @TaskAction
    fun generate() {
        val outputName = "open-source-licenses"
        val outputDir = outputDirectory.get().asFile.apply { mkdirs() }
        val artifactExtRegex = Regex("-(jvm|android|js|wasm-js|wasm-wasi|iosarm64|iosx64|iossimulatorarm64|macosarm64|macosx64|linuxx64|linuxarm64|mingwx64|watchosarm64|watchossimulatorarm64|tvosarm64|tvossimulatorarm64|native)$")

        // 1. Gather dependency information
        val deduplicatedModuleKeys = mutableSetOf<String>()
        val allDeps = buildSet {
            project.subprojects.forEach { subproject ->
                val targetConfigs = subproject.configurations
                    .filter { it.isCanBeResolved && (it.name.endsWith("RuntimeClasspath") || it.name == "metadataCompileClasspath") }
                    .map { it.name }

                targetConfigs.forEach { configName ->
                    val config = subproject.configurations.findByName(configName) ?: return@forEach
                    if (!config.isCanBeResolved) return@forEach

                    val componentIds = config.incoming.resolutionResult.allComponents
                        .map { it.id }
                        .filterIsInstance<ModuleComponentIdentifier>()

                    val resolutionResult = project.dependencies.createArtifactResolutionQuery()
                        .forComponents(componentIds)
                        .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                        .execute()

                    resolutionResult.resolvedComponents.forEach { component ->
                        val id = component.id as ModuleComponentIdentifier
                        val sanitizedName = id.module.replace(artifactExtRegex, "")

                        if (deduplicatedModuleKeys.contains("${id.group}:$sanitizedName")) return@forEach
                        deduplicatedModuleKeys.add("${id.group}:$sanitizedName")

                        val pomFile = component.getArtifacts(MavenPomArtifact::class.java)
                            .filterIsInstance<ResolvedArtifactResult>()
                            .firstOrNull()?.file

                        val xml = try { XmlParser(false, false).parse(pomFile) } catch (e: Exception) { null }
                        val poms = listOf(xml, *resolveParentPomXmls(xml))

                        val projectUrl = poms.firstNotNullOfOrNull {
                            (it?.get("url") as? NodeList)?.firstOrNull().let { (it as? Node)?.text() }
                        }
                        val license = poms.firstNotNullOfOrNull {
                            val licenses = it?.get("licenses") as? NodeList
                            val firstLicense = (licenses?.getAt("license"))?.firstOrNull() as? Node
                                ?: return@firstNotNullOfOrNull null
                            val name = (firstLicense["name"] as? NodeList)?.firstOrNull()
                                ?.let { (it as? Node)?.text() } ?: return@firstNotNullOfOrNull null
                            val licenseUrl = (firstLicense["url"] as? NodeList)?.firstOrNull()
                                ?.let { (it as? Node)?.text() } ?: return@firstNotNullOfOrNull null
                            Pair(name, licenseUrl)
                        }

                        val data = LicenseData(
                            projectName = "${id.group}:$sanitizedName",
                            version = id.version,
                            projectUrl = projectUrl ?: "N/A",
                            licenseName = license?.first ?: "N/A",
                            licenseUrl = license?.second ?: "N/A"
                        )
                        add(data)
                    }
                }
            }
        }.sortedBy { it.projectName }

        File(outputDir, "$outputName.csv").bufferedWriter().use { out ->
            out.append("Project,Project URL,Version,License,License URL\n")
            allDeps.forEach { dep ->
                out.append("\"${dep.projectName}\",\"${dep.projectUrl}\",${dep.version},\"${dep.licenseName}\",\"${dep.licenseUrl}\"\n")
            }
        }

        File(outputDir, "$outputName.json").bufferedWriter().use { out ->
            out.write("[")
            allDeps.forEachIndexed { index, dep ->
                out.write("""{"projectName":"${dep.projectName}","projectUrl":"${dep.projectUrl}","version":"${dep.version}","license":"${dep.licenseName}","licenseUrl":"${dep.licenseUrl}"}""")
                if (index != allDeps.lastIndex) out.write(",")
            }
            out.write("]")
        }

        File(outputDir, "$outputName.txt").bufferedWriter().use { out ->
            allDeps.forEach { dep ->
                out.append("NAME: ${dep.projectName}\n")
                out.append("URL: ${dep.projectUrl}\n")
                out.append("VERSION: ${dep.version}\n")
                out.append("LICENSE: ${dep.licenseName} - (${dep.licenseUrl}) \n")
                out.append("\n")
            }
        }

        File(outputDir, "$outputName.md").bufferedWriter().use { out ->
            allDeps.forEach { dep ->
                out.append("NAME: ${dep.projectName}\n")
                out.append("URL: ${if (dep.projectUrl != "N/A") "<${dep.projectUrl}>" else dep.projectUrl }\n")
                out.append("VERSION: ${dep.version}\n")
                out.append("LICENSE: ${dep.licenseName} - (${if (dep.licenseUrl != "N/A") "<${dep.licenseUrl}>)" else dep.licenseUrl}\n")
                out.append("\n")
            }
        }

        println("Open source disclaimer generated at: ${outputDir.absolutePath}")
    }

    private fun resolveParentPomXmls(node: Node?): Array<out Node> {
        if (node == null) return emptyArray()

        val pomList = mutableListOf<Node>()
        var parent = (node["parent"] as? NodeList)?.firstOrNull() as? Node?

        while (parent != null) {
            val pGroup = (parent["groupId"] as? NodeList)?.firstOrNull().let { (it as? Node)?.text() }
            val pArtifact = (parent["artifactId"] as? NodeList)?.firstOrNull().let { (it as? Node)?.text() }
            val pVersion = (parent["version"] as? NodeList)?.firstOrNull().let { (it as? Node)?.text() }
            val parentComponentId =
                project.configurations.detachedConfiguration(project.dependencies.create("${pGroup}:${pArtifact}:${pVersion}"))
                    .incoming.resolutionResult.root.dependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .map { it.selected.id }
                    .firstOrNull()

            if (parentComponentId == null) break

            val parentQuery = project.dependencies.createArtifactResolutionQuery()
                .forComponents(parentComponentId)
                .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                .execute()

            val parentPomFile = parentQuery.resolvedComponents.firstOrNull()
                ?.getArtifacts(MavenPomArtifact::class.java)
                ?.filterIsInstance<ResolvedArtifactResult>()
                ?.firstOrNull()?.file

            val xml = try { XmlParser(false, false).parse(parentPomFile) } catch (e: Exception) { null } ?: break
            pomList.add(xml)
            parent = (xml["parent"] as? NodeList)?.firstOrNull() as? Node?
        }

        return pomList.toTypedArray()
    }
}

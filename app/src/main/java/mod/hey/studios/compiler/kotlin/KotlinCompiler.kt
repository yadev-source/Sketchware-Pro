package mod.hey.studios.compiler.kotlin

import a.a.a.ProjectBuilder
import mod.hey.studios.build.BuildSettings
import mod.hey.studios.compiler.kotlin.KotlinCompilerUtil.*
import mod.jbk.util.LogUtil
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.File

class KotlinCompiler(
    private val builder: ProjectBuilder
) {
    private val workspace = builder.yq

    @Throws(Throwable::class)
    fun compile() {
        val timeMillis = System.currentTimeMillis()

        val filesToCompile = getFilesToCompile(workspace).apply {
            if (!areAnyKtFilesPresent(workspace)) {
                LogUtil.d(TAG, "No kotlin source files found, skipping kotlinc")
                return
            }
        }

        val mKotlinHome = File(KotlinCompilerBridge.getKotlinHome(workspace)).apply { mkdirs() }
        val mClassOutput = File(workspace.compiledClassesPath).apply { mkdirs() }

        // ── Classpath: log + sanitize trailing ':' ──
        val classpath = sanitizeClasspath(builder.getClasspath())
        val cpEntryCount = if (classpath.isEmpty()) 0 else classpath.split(":").size
        LogUtil.d(TAG, "Classpath: $cpEntryCount entries, ${classpath.length} chars")
        for (entry in classpath.split(":")) {
            LogUtil.d(TAG, "  cp entry: $entry")
        }

        // ── Source files: log ──
        val sourcePaths = filesToCompile.map { it.absolutePath }
        LogUtil.d(TAG, "Source files (${sourcePaths.size}):")
        sourcePaths.forEach { LogUtil.d(TAG, "  src: $it") }

        val compiler = K2JVMCompiler()
        val collector = DiagnosticCollector()
        val plugins = getCompilerPlugins(workspace).map(File::getAbsolutePath).toTypedArray()

        // ── Set arguments langsung (tanpa CLI parsing) ──
        val args = K2JVMCompilerArguments().apply {
            compileJava = false
            includeRuntime = false
            noJdk = true
            noReflect = true
            noStdlib = true

            this.classpath = classpath          // ← pengganti "-cp <value>"
            freeArgs = sourcePaths              // ← pengganti positional args

            kotlinHome = mKotlinHome.absolutePath
            destination = mClassOutput.absolutePath
            pluginClasspaths = plugins
        }

        LogUtil.d(TAG, "K2JVMCompilerArguments: " +
                "classpath=${args.classpath?.length} chars, " +
                "freeArgs=${args.freeArgs.size}, " +
                "destination=${args.destination}, " +
                "kotlinHome=${args.kotlinHome}")

        // Tidak perlu compiler.parseArguments() lagi
        compiler.exec(collector, Services.EMPTY, args)

        LogUtil.d(TAG, "kotlinc diagnostics:\n$collector")

        File(mClassOutput, "META-INF").deleteRecursively()

        if (collector.hasErrors()) {
            LogUtil.e(TAG, "Failed to compile Kotlin files")
            throw Exception(collector.getDiagnostics(areWarningsEnabled()))
        } else {
            LogUtil.d(TAG, "Compiling Kotlin files took ${System.currentTimeMillis() - timeMillis} ms")
        }
    }

    /** Kotlinc menolak classpath dengan trailing ':' di sebagian versi. */
    private fun sanitizeClasspath(cp: String?): String {
        if (cp.isNullOrEmpty()) return ""
        var end = cp.length
        while (end > 0 && cp[end - 1] == ':') end--
        return cp.substring(0, end)
    }

    private fun areWarningsEnabled(): Boolean {
        return builder.build_settings.getValue(
            BuildSettings.SETTING_NO_WARNINGS,
            BuildSettings.SETTING_GENERIC_VALUE_TRUE
        ) != BuildSettings.SETTING_GENERIC_VALUE_TRUE
    }

    companion object {
        const val TAG = "KotlinCompiler"
    }
}
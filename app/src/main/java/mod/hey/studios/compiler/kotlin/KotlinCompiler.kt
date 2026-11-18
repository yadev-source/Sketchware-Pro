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

        // Sanitize trailing ':' agar kotlinc tidak komplain
        val classpath = sanitizeClasspath(builder.getClasspath())
        LogUtil.d(TAG, "Classpath: ${classpath.length} chars, " +
                "${if (classpath.isEmpty()) 0 else classpath.split(":").size} entries")

        val arguments = mutableListOf<String>().apply {
            add("-classpath")              // ← FIX dari "-cp"
            add(classpath)
            addAll(filesToCompile.map { it.absolutePath })
        }

        val compiler = K2JVMCompiler()
        val collector = DiagnosticCollector()
        val plugins = getCompilerPlugins(workspace).map(File::getAbsolutePath).toTypedArray()

        val args = K2JVMCompilerArguments().apply {
            compileJava = false
            includeRuntime = false
            noJdk = true
            noReflect = true
            noStdlib = true

            kotlinHome = mKotlinHome.absolutePath
            destination = mClassOutput.absolutePath
            pluginClasspaths = plugins
        }

        LogUtil.d(TAG, "Running kotlinc with these arguments: $arguments")

        // ── KEMBALIKAN parseArguments (penting!) ──
        compiler.parseArguments(arguments.toTypedArray(), args)
        compiler.exec(collector, Services.EMPTY, args)

        LogUtil.d(TAG, "kotlinc MessageCollector: $collector")

        File(mClassOutput, "META-INF").deleteRecursively()

        if (collector.hasErrors()) {
            LogUtil.e(TAG, "Failed to compile Kotlin files")
            throw Exception(collector.getDiagnostics(areWarningsEnabled()))
        } else {
            LogUtil.d(TAG, "Compiling Kotlin files took ${System.currentTimeMillis() - timeMillis} ms")
        }
    }

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
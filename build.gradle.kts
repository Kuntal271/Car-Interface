import com.android.build.api.dsl.ApplicationExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
}

// ---- Quality gates (no module build file needs to change) ----
//
//   ./gradlew :app:detektDebug :vehicle:detektMain     static analysis with type resolution (the CI gate): new findings fail, existing ones are in
//                                                      config/detekt/baseline-<module>.xml. `detekt` is the same without type resolution (fast, subset).
//   ./gradlew :app:lintDebug :app:lintRelease          Android lint, new errors fail (existing ones are in config/lint/lint-baseline-app.xml)
//   ./gradlew :vehicle:jacocoTestReport :app:createDebugUnitTestCoverageReport -Pcoverage      JVM-test coverage (tools/coverage-summary.py prints it)
//   ./gradlew :app:compileReleaseKotlin -PcomposeReports                                        Compose compiler stability/skippability reports
//   tools/rebaseline.sh                                regenerate the baselines (after a merge, never to hide a regression)
//
// "Baseline mode": the gate is green today without touching existing code, and goes red only for NEW findings.
val wantCoverage = providers.gradleProperty("coverage").isPresent
val wantComposeReports = providers.gradleProperty("composeReports").isPresent

subprojects {
    // ---- detekt: all modules ----
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = rootProject.file("config/detekt/baseline-${project.name}.xml")
        source.setFrom("src/main/java", "src/main/kotlin")     // production code only; tests are exempt from style rules
    }
    val detektBaselineFile = rootProject.file("config/detekt/baseline-${project.name}.xml")      // ONE baseline per module for every variant task
    tasks.withType<DetektCreateBaselineTask>().configureEach { baseline.set(detektBaselineFile); jvmTarget = "17" }
    tasks.withType<Detekt>().configureEach {
        baseline.set(detektBaselineFile)
        jvmTarget = "17"
        reports {
            xml.required.set(true)
            html.required.set(true)
            sarif.required.set(true)
            txt.required.set(false)
            md.required.set(false)
        }
    }
    dependencies.add("detektPlugins", "io.nlopez.compose.rules:detekt:0.4.22")     // Compose-specific rules (modifiers, stability, remember)

    // ---- Android application module: lint + unit-test coverage + Compose compiler reports ----
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            lint {
                baseline = rootProject.file("config/lint/lint-baseline-${project.name}.xml")
                abortOnError = true
                warningsAsErrors = false          // only errors fail the build; warnings are tracked through the baseline
                checkDependencies = true
                checkReleaseBuilds = true
                htmlReport = true
                xmlReport = true
                sarifReport = true
                textReport = false
            }
            testCoverage { jacocoVersion = "0.8.12" }
            buildTypes { getByName("debug") { enableUnitTestCoverage = wantCoverage } }
        }
    }
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        if (wantComposeReports) {
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                reportsDestination.set(layout.buildDirectory.dir("compose_reports"))
                metricsDestination.set(layout.buildDirectory.dir("compose_reports"))
            }
        }
    }

    // ---- JaCoCo for the pure-Kotlin module (the Android module uses AGP's own coverage task) ----
    plugins.withId("org.jetbrains.kotlin.jvm") {
        if (wantCoverage) {
            apply(plugin = "jacoco")
            extensions.configure<JacocoPluginExtension> { toolVersion = "0.8.12" }
            tasks.withType<JacocoReport>().configureEach {
                dependsOn(tasks.named("test"))
                reports { xml.required.set(true); html.required.set(true) }
            }
        }
    }
}

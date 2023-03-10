package com.deploygate.gradle.plugins.internal.gradle

import com.deploygate.gradle.plugins.TestAndroidProject
import com.deploygate.gradle.plugins.TestDeployGatePlugin
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.Nonnull

class GradleCompatAcceptanceSpec extends Specification {

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()

    @Nonnull
    TestAndroidProject testAndroidProject

    @Nonnull
    TestDeployGatePlugin testDeployGatePlugin

    def setup() {
        testAndroidProject = new TestAndroidProject(testProjectDir)
        testDeployGatePlugin = new TestDeployGatePlugin()

        testAndroidProject.useGradleCompatResource()
    }

    @IgnoreIf({ jvm.isJavaVersionCompatible(17) })
    @Unroll
    def "For JDK 11 or lower, check whether or not we can accept the build gradle. Unrolled #gradleVersion"() {
        given:
        def runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(testDeployGatePlugin.loadPluginClasspath())
                .withGradleVersion(gradleVersion)
                .withArguments("tasks" /*, "--stacktrace" */)

        and:
        def buildResult = runner.build()

        expect:
        buildResult.task(":tasks").outcome == TaskOutcome.SUCCESS

        where:
        gradleVersion << [
                "5.4.1",
                "5.6.4",
                "6.1.1",
                "7.0.2"
        ]
    }

    @Requires(value = { jvm.isJavaVersionCompatible(17) }, reason = "jdk 17 or higher cannot evaluate old Gradles that depend on jvm7")
    @Unroll
    def "For JDK 17 or higher, check whether or not we can accept the build gradle. Unrolled #gradleVersion"() {
        given:
        def runner = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withPluginClasspath(testDeployGatePlugin.loadPluginClasspath())
                .withGradleVersion(gradleVersion)
                .withArguments("tasks" /*, "--stacktrace" */)

        and:
        def buildResult = runner.build()

        expect:
        buildResult.task(":tasks").outcome == TaskOutcome.SUCCESS

        where:
        gradleVersion << [
                "8.0"
        ]
    }
}

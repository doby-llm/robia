package com.gusanitolabs.robia

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * The keep rules the precise cutout needs at runtime (Flogger under MediaPipe, MediaPipe's JNI classes, ONNX Runtime's JNI classes).
 * R8 behaviour cannot be unit-tested; this pins the rules in the file so an edit cannot drop one silently.
 */
class ProguardRulesContractTest {
    private val rules: List<String> by lazy {
        val file = listOf(Path.of("app/proguard-rules.pro"), Path.of("../app/proguard-rules.pro")).first { Files.isRegularFile(it) }
        file.toFile().readLines().map { it.trim() }
    }

    @Test
    fun floggerIsKeptAndItsWarningsAreSuppressed() {
        assertTrue(rules.contains("-keep class com.google.common.flogger.** { *; }"))
        assertTrue(rules.contains("-dontwarn com.google.common.flogger.**"))
    }

    @Test
    fun mediaPipeAndOnnxRuntimeNativeClassesAreKept() {
        assertTrue(rules.contains("-keep class com.google.mediapipe.** { *; }"))
        assertTrue(rules.contains("-keep class ai.onnxruntime.** { *; }"))
    }

    @Test
    fun mediaPipeProtoWarningsAreSuppressedOnlyForTheTwoUnpackagedClasses() {
        assertTrue(rules.contains("-dontwarn com.google.mediapipe.proto.CalculatorProfileProto*"))
        assertTrue(rules.contains("-dontwarn com.google.mediapipe.proto.GraphTemplateProto*"))
        assertFalse(rules.contains("-dontwarn com.google.mediapipe.proto.**"))
    }

    @Test
    fun protobufLiteRuntimeAndGeneratedMessageFieldsAreKept() {
        assertTrue(rules.contains("-keep class com.google.protobuf.** { *; }"))
        assertTrue(rules.contains("-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }"))
        assertTrue(rules.contains("-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite\$Builder { *; }"))
        assertTrue(rules.contains("-keepclassmembers enum * implements com.google.protobuf.Internal\$EnumLite { *; }"))
    }

    @Test
    fun nativeMethodNamesAreKeptForEveryJniClass() {
        assertTrue(rules.contains("-keepclasseswithmembernames class * { native <methods>; }"))
    }
}

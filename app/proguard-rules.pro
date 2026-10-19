-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { native <methods>; }
-keep class com.google.gson.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-dontwarn com.google.ai.edge.litertlm.**
-keep class kotlin.Metadata { *; }
-keep class ai.onnxruntime.** { *; }
-keep class com.google.mediapipe.** { *; }
# Flogger comes in through tasks-core 1.1.1 (flogger:0.6 and flogger-system-backend:0.6, compile scope; neither jar has consumer rules).
# CallerFinder.findCallerOf compares the FluentLogger class name with the stack; renamed or inlined Flogger frames make MediaPipe's
# Graph.<clinit> fail with "no caller found on the stack for: ...".
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**
# Compile-time annotations only.
-dontwarn com.google.auto.value.**
# tasks-core 1.1.1 packages 123 com.google.mediapipe.proto classes (kept above), but references two that it does not package:
# CalculatorProfileProto (GraphProfiler.getCalculatorProfiles) and GraphTemplateProto (Graph.loadBinaryGraphTemplate). The app calls neither.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto*
-dontwarn com.google.mediapipe.proto.GraphTemplateProto*
# protobuf-javalite 4.26.1 (transitive via tasks-core 1.1.1) ships no META-INF/proguard rules, so R8 renamed and shrank com.google.protobuf.Any.
# MessageSchema reads every GeneratedMessageLite field by name (Class.getDeclaredField), so the whole lite runtime is kept.
-keep class com.google.protobuf.** { *; }
# Upstream rule, protobuf java/lite/proguard.pgcfg at v26.1; that file is not packaged in the 4.26.1 artifact. Generated lite messages keep their fields.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
# Precaution, not upstream and not confirmed as required: generated builders extend GeneratedMessageLite$Builder.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite$Builder { *; }
# Precaution, not upstream and not confirmed as required: lite enums map numbers through forNumber and internalGetValueMap.
-keepclassmembers enum * implements com.google.protobuf.Internal$EnumLite { *; }
# JNI: native methods are bound by name. MediaPipe, ONNX Runtime, LiteRT-LM and androidx.datastore.core.NativeSharedCounter (datastore-core-android 1.1.1, jni/)
# need their native method names kept. Standard Android JNI rule.
-keepclasseswithmembernames class * { native <methods>; }

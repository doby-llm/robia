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

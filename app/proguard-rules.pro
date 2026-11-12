-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class cn.easyar.** { *; }

# MediaPipe profiler classes that are missing from tasks-vision: their calls are never used.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate

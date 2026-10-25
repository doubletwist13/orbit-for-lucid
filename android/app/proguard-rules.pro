# protobuf-lite generated messages are reflected on by the runtime
-keep class mobilegateway.protos.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

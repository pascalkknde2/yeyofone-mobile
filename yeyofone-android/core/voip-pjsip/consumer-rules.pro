# PJSUA2 Java bindings are generated during the native build and use JNI names.
-keep class org.pjsip.pjsua2.** { *; }

# These override Account/Call callbacks (onRegState, onIncomingCall, onCallState, etc.) that
# native code invokes directly via JNI virtual dispatch - nothing in Kotlin calls them, so R8
# would otherwise be free to treat them as unreachable or safe to rename (BUILD-06).
-keep class com.yeyofone.core.voip.pjsip.Pjsua2EndpointBackend$NativeAccount { *; }
-keep class com.yeyofone.core.voip.pjsip.Pjsua2EndpointBackend$NativeCall { *; }

# BUILD-06: app-level R8 rules. Each dependency with its own JNI/reflection needs (PJSUA2, Room)
# ships its own consumer-rules.pro, applied automatically - this file only covers the app module's
# own code. Activities/services/receivers/providers declared in AndroidManifest.xml are kept
# automatically by AGP's built-in rules and need no entry here.

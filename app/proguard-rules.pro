# No app-specific keep rules yet. Compose and AndroidX ship consumer rules.

# The engine's JNI surface: native method names are the contract with
# mindanchor_llama.cpp and must survive minification untouched.
-keepclasseswithmembernames,includedescriptorclasses class org.mindanchor.narrate.LlamaEngine {
    native <methods>;
}

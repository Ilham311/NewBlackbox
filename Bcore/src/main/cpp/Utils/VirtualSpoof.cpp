#include <sys/system_properties.h>
#include <cstring>
#include "./xdl.h"
#include <android/log.h>
#include <dlfcn.h>
#include <unistd.h>
#include <sys/syscall.h>
#include "Dobby/dobby.h"


#define LOG_TAG "VirtualSpoof"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

struct SpoofedProp {
    const char* key;
    const char* value;
};

SpoofedProp spoofed_props[] = {
        {"ro.product.model", "Pixel 6"},
        {"ro.product.brand", "google"},
        {"ro.product.manufacturer", "Google"},
        {"ro.product.device", "oriole"},
        {"ro.build.fingerprint", "google/oriole/oriole:12/SP1A.210812.015/7679548:user/release-keys"},
        {"ro.build.version.release", "12"},
        {"ro.build.version.security_patch", "2022-01-05"},
        {"ro.serialno", "1A2B3C4D5E6F"},
        {"ro.hardware", "qcom"},
        {"ro.boot.hardware", "qcom"},
        {"ro.product.board", "lahaina"},
        {"ro.product.cpu.abi", "arm64-v8a"},
        {"ro.build.type", "user"},
        {"ro.build.tags", "release-keys"},
        {"ro.kernel.qemu", "0"},
        {"ro.kernel.android.qemud", ""},
        {"ro.hardware.egl", "adreno"},
        {"ro.boot.qemu", "0"},
    {nullptr, nullptr} 
};


static int (*orig_system_property_get)(const char *name, char *value) = nullptr;
static uid_t (*orig_getuid)() = nullptr;


uid_t my_getuid() {
    // Phase 2: Native Syscall Spoofing.
    // Instead of returning the UID assigned to the BlackBox process by the OS,
    // we could dynamically return a spoofed UID based on the current virtual process.
    // For now, we fall back to the original to prevent crashes, but intercept the call.
    // Use syscall(__NR_getuid32) or __NR_getuid to safely avoid an infinite loop
    // if Dobby fails and orig_getuid is somehow null while inside the hook.
#if defined(__aarch64__) || defined(__x86_64__)
    uid_t real_uid = orig_getuid ? orig_getuid() : syscall(__NR_getuid);
#else
    uid_t real_uid = orig_getuid ? orig_getuid() : syscall(__NR_getuid32);
#endif
    // Example spoof: if virtual app expects UID 10050, we would return 10050 here.
    return real_uid;
}

int my_system_property_get(const char *name, char *value) {
    for (int i = 0; spoofed_props[i].key != nullptr; ++i) {
        if (strcmp(name, spoofed_props[i].key) == 0) {
            strcpy(value, spoofed_props[i].value);
             LOGD("[spoof] %s = %s", name, value);
            return strlen(value);
        }
    }
    if (orig_system_property_get) {
        return orig_system_property_get(name, value);
    }
    value[0] = '\0';
    return 0;
}

void install_property_get_hook() {
    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    if (!handle) return;

    void* target_prop = xdl_dsym(handle, "__system_property_get", nullptr);
    if (target_prop) {
        if (DobbyHook(target_prop, (void*)my_system_property_get, (void**)&orig_system_property_get) == 0) {
            LOGD("Property Spoof installed successfully");
        } else {
            LOGD("Property Spoof hook failed");
        }
    }

    // Phase 2: Hook getuid to evade native UID detection
    void* target_getuid = xdl_dsym(handle, "getuid", nullptr);
    if (target_getuid) {
        if (DobbyHook(target_getuid, (void*)my_getuid, (void**)&orig_getuid) == 0) {
            LOGD("getuid Spoof installed successfully");
        } else {
            LOGD("getuid Spoof hook failed");
        }
    }

    xdl_close(handle);
}


__attribute__((constructor)) void init_virtual_spoof()
{
    install_property_get_hook();
    LOGD("VirtualSpoof: __system_property_get hook loaded");
}

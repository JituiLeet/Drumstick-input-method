#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <algorithm>
#include "rime_api.h"

#define TAG "DrumstickRime"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static RimeApi* gApi = nullptr;
static bool gReady = false;

static void rimeNotify(void*, RimeSessionId, const char* type, const char* value) {
    if (type && value) LOGI("Rime event: %s=%s", type, value);
}

static bool apiHas(size_t offset, const void* fn) {
    return gApi && gApi->data_size >= static_cast<int>(offset) && fn;
}

static bool loadApi() {
    if (gApi) return true;
    gApi = rime_get_api();
    if (!gApi) {
        LOGE("rime_get_api() returned null");
        return false;
    }
    return true;
}

static void initStruct(RimeTraits& traits) {
    traits = {};
    traits.data_size = sizeof(RimeTraits) - sizeof(traits.data_size);
}

static std::string takeCommit(RimeSessionId id) {
    if (!gApi || !gApi->get_commit) return {};
    RimeCommit commit = {};
    commit.data_size = sizeof(RimeCommit) - sizeof(commit.data_size);
    if (!gApi->get_commit(id, &commit)) return {};
    std::string text = commit.text ? commit.text : "";
    if (gApi->free_commit) gApi->free_commit(&commit);
    return text;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeAvailable(JNIEnv*, jclass) {
    return loadApi() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeVersion(JNIEnv* env, jclass) {
    if (!loadApi() || !gApi->get_version) return nullptr;
    const char* version = gApi->get_version();
    return version ? env->NewStringUTF(version) : nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeInitialize(JNIEnv* env, jclass, jstring shared, jstring user) {
    if (!loadApi()) return JNI_FALSE;
    const char* sharedDir = env->GetStringUTFChars(shared, nullptr);
    const char* userDir = env->GetStringUTFChars(user, nullptr);

    RimeTraits traits;
    initStruct(traits);
    traits.shared_data_dir = sharedDir;
    traits.user_data_dir = userDir;
    traits.distribution_name = "Drumstick Input Method";
    traits.distribution_code_name = "drumstick";
    traits.distribution_version = "1.0.0";
    traits.app_name = "rime.drumstick";
    traits.min_log_level = 2;
    traits.log_dir = userDir;
    traits.prebuilt_data_dir = sharedDir;
    traits.staging_dir = userDir;

    if (gReady) {
        env->ReleaseStringUTFChars(shared, sharedDir);
        env->ReleaseStringUTFChars(user, userDir);
        return JNI_TRUE;
    }

    gApi->setup(&traits);
    if (gApi->set_notification_handler) gApi->set_notification_handler(&rimeNotify, nullptr);
    gApi->initialize(&traits);

    bool maintenanceOk = true;
    if (gApi->start_maintenance) {
        maintenanceOk = gApi->start_maintenance(True);
        if (maintenanceOk && gApi->join_maintenance_thread) {
            gApi->join_maintenance_thread();
        }
    }

    if (gApi->is_maintenance_mode && gApi->is_maintenance_mode()) {
        LOGE("Rime is still in maintenance mode after join");
        maintenanceOk = false;
    }
    // Build/refresh prebuilt data before creating sessions. A session can be
    // created successfully even when the requested schema was never deployed.
    if (maintenanceOk && gApi->deploy) {
        if (!gApi->deploy()) {
            LOGE("Rime deploy failed");
            maintenanceOk = false;
        }
    }
    gReady = maintenanceOk;
    env->ReleaseStringUTFChars(shared, sharedDir);
    env->ReleaseStringUTFChars(user, userDir);
    LOGI("Rime initialized: %s", gReady ? "yes" : "no");
    return gReady ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeFinalize(JNIEnv*, jclass) {
    if (gReady && gApi && gApi->finalize) gApi->finalize();
    gReady = false;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeCreateSession(JNIEnv*, jclass) {
    if (!gReady || !gApi || !gApi->create_session) return 0;
    return static_cast<jlong>(gApi->create_session());
}

extern "C" JNIEXPORT void JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeDestroySession(JNIEnv*, jclass, jlong id) {
    if (gApi && gApi->destroy_session && id) gApi->destroy_session(static_cast<RimeSessionId>(id));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeInput(JNIEnv* env, jclass, jlong id, jstring seq) {
    if (!gReady || !id || !gApi || !gApi->simulate_key_sequence) return nullptr;
    const char* s = env->GetStringUTFChars(seq, nullptr);
    Bool handled = gApi->simulate_key_sequence(static_cast<RimeSessionId>(id), s);
    env->ReleaseStringUTFChars(seq, s);
    if (!handled) return nullptr;
    std::string commit = takeCommit(static_cast<RimeSessionId>(id));
    return commit.empty() ? nullptr : env->NewStringUTF(commit.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeProcessKey(JNIEnv* env, jclass, jlong id, jint keycode, jint mask) {
    if (!gReady || !id || !gApi || !gApi->process_key) return nullptr;
    Bool handled = gApi->process_key(static_cast<RimeSessionId>(id), static_cast<int>(keycode), static_cast<int>(mask));
    if (!handled) return nullptr;
    std::string commit = takeCommit(static_cast<RimeSessionId>(id));
    return commit.empty() ? nullptr : env->NewStringUTF(commit.c_str());
}



extern "C" JNIEXPORT jstring JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeSimulateKeySequence(JNIEnv* env, jclass, jlong id, jstring seq) {
    if (!gReady || !id || !gApi || !gApi->simulate_key_sequence || !seq) return nullptr;
    const char* s = env->GetStringUTFChars(seq, nullptr);
    Bool handled = gApi->simulate_key_sequence(static_cast<RimeSessionId>(id), s);
    env->ReleaseStringUTFChars(seq, s);
    if (!handled) return nullptr;
    std::string commit = takeCommit(static_cast<RimeSessionId>(id));
    return commit.empty() ? nullptr : env->NewStringUTF(commit.c_str());
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeGetContext(JNIEnv* env, jclass, jlong id) {
    jclass strCls = env->FindClass("java/lang/String");
    if (!strCls) return nullptr;
    if (!gReady || !id || !gApi || !gApi->get_context) return env->NewObjectArray(1, strCls, nullptr);

    RimeContext ctx = {};
    ctx.data_size = sizeof(RimeContext) - sizeof(ctx.data_size);
    if (!gApi->get_context(static_cast<RimeSessionId>(id), &ctx)) {
        return env->NewObjectArray(1, strCls, nullptr);
    }

    const int count = std::max(0, ctx.menu.num_candidates);
    jobjectArray arr = env->NewObjectArray(count + 1, strCls, nullptr);
    env->SetObjectArrayElement(arr, 0, env->NewStringUTF(ctx.composition.preedit ? ctx.composition.preedit : ""));
    for (int i = 0; i < count; ++i) {
        const char* text = ctx.menu.candidates && ctx.menu.candidates[i].text
                ? ctx.menu.candidates[i].text : "";
        env->SetObjectArrayElement(arr, i + 1, env->NewStringUTF(text));
    }
    if (gApi->free_context) gApi->free_context(&ctx);
    return arr;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeSelectCandidate(JNIEnv* env, jclass, jlong id, jint index) {
    if (!gReady || !id || !gApi || !gApi->select_candidate || index < 0) return nullptr;
    if (!gApi->select_candidate(static_cast<RimeSessionId>(id), static_cast<size_t>(index))) return nullptr;
    std::string commit = takeCommit(static_cast<RimeSessionId>(id));
    return commit.empty() ? nullptr : env->NewStringUTF(commit.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeSetInput(JNIEnv* env, jclass, jlong id, jstring input) {
    if (!gReady || !id || !gApi || !gApi->set_input) return JNI_FALSE;
    const char* s = env->GetStringUTFChars(input, nullptr);
    Bool ok = gApi->set_input(static_cast<RimeSessionId>(id), s);
    env->ReleaseStringUTFChars(input, s);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeSetAsciiMode(JNIEnv*, jclass, jlong id, jboolean enabled) {
    if (gApi && gApi->set_option && id) gApi->set_option(static_cast<RimeSessionId>(id), "ascii_mode", enabled ? True : False);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeGetAsciiMode(JNIEnv*, jclass, jlong id) {
    if (gApi && gApi->get_option && id) return gApi->get_option(static_cast<RimeSessionId>(id), "ascii_mode") ? JNI_TRUE : JNI_FALSE;
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeChangePage(JNIEnv*, jclass, jlong id, jboolean backward) {
    if (!gReady || !id || !gApi || !gApi->change_page) return JNI_FALSE;
    return gApi->change_page(static_cast<RimeSessionId>(id), backward ? True : False) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeSelectSchema(JNIEnv* env, jclass, jlong id, jstring schema) {
    if (!gReady || !id || !gApi || !schema || gApi->data_size < (int)(offsetof(RimeApi, select_schema) + sizeof(gApi->select_schema)) || !gApi->select_schema) return JNI_FALSE;
    const char* s = env->GetStringUTFChars(schema, nullptr);
    Bool ok = gApi->select_schema(static_cast<RimeSessionId>(id), s);
    LOGI("select_schema(%s): %s", s, ok ? "success" : "failed");
    env->ReleaseStringUTFChars(schema, s);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_jituileet_inputmethod_RimeNative_nativeDeploy(JNIEnv*, jclass) {
    if (!gApi || !gApi->deploy) return JNI_FALSE;
    return gApi->deploy() ? JNI_TRUE : JNI_FALSE;
}

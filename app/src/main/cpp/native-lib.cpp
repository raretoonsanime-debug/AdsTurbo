#include <jni.h>
#include <android/log.h>
#include "frida-core.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AdTurbo", __VA_ARGS__)

static JavaVM* g_vm = nullptr;
static jobject g_bridge = nullptr;
static jmethodID g_onMsg = nullptr;
static FridaDeviceManager* g_mgr = nullptr;
static FridaDevice* g_dev = nullptr;
static FridaSession* g_sess = nullptr;
static FridaScript* g_script = nullptr;

static void on_message(FridaScript* s, const gchar* message, gpointer a, gpointer b) {
    if (!g_vm || !g_bridge) return;
    JNIEnv* env = nullptr;
    g_vm->AttachCurrentThread(&env, nullptr);
    jstring js = env->NewStringUTF(message ? message : "");
    env->CallVoidMethod(g_bridge, g_onMsg, js);
    env->DeleteLocalRef(js);
}

extern "C" JNIEXPORT void JNICALL
Java_com_adturbo_app_Frida_attach(JNIEnv* env, jobject, jstring pkg, jstring source) {
    const char* cpkg = env->GetStringUTFChars(pkg, nullptr);
    const char* csrc = env->GetStringUTFChars(source, nullptr);
    if (!g_mgr) {
        g_mgr = frida_device_manager_new();
        g_dev = frida_device_manager_get_device_by_type_sync(
                    g_mgr, FRIDA_DEVICE_TYPE_LOCAL, 5000, nullptr, nullptr);
    }
    FridaProcess* proc = frida_device_get_process_by_name_sync(
                            g_dev, cpkg, nullptr, nullptr, nullptr);
    if (!proc) { LOGI("no process %s", cpkg); return; }
    gint pid = frida_process_get_pid(proc);
    g_sess = frida_device_attach_sync(g_dev, pid, nullptr, nullptr, nullptr);
    g_script = frida_session_create_script_sync(g_sess, csrc, nullptr, nullptr, nullptr);
    g_signal_connect(g_script, "message", G_CALLBACK(on_message), nullptr);
    frida_script_load_sync(g_script, nullptr, nullptr);
    LOGI("attached %s pid=%d", cpkg, pid);
    env->ReleaseStringUTFChars(pkg, cpkg);
    env->ReleaseStringUTFChars(source, csrc);
}

extern "C" JNIEXPORT void JNICALL
Java_com_adturbo_app_Frida_post(JNIEnv* env, jobject, jstring json) {
    if (!g_script) return;
    const char* c = env->GetStringUTFChars(json, nullptr);
    frida_script_post(g_script, c, nullptr);
    env->ReleaseStringUTFChars(json, c);
}

extern "C" JNIEXPORT void JNICALL
Java_com_adturbo_app_Frida_detach(JNIEnv*, jobject) {
    if (g_script) { frida_script_unload_sync(g_script, nullptr, nullptr); g_script = nullptr; }
    if (g_sess)   { frida_session_detach_sync(g_sess, nullptr, nullptr);  g_sess = nullptr; }
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    JNIEnv* env = nullptr;
    vm->GetEnv((void**) &env, JNI_VERSION_1_6);
    jclass cls = env->FindClass("com/adturbo/app/Frida");
    jfieldID instF = env->GetStaticFieldID(cls, "INSTANCE", "Lcom/adturbo/app/Frida;");
    g_bridge = env->NewGlobalRef(env->GetStaticObjectField(cls, instF));
    g_onMsg = env->GetStaticMethodID(cls, "onMessage", "(Ljava/lang/String;)V");
    return JNI_VERSION_1_6;
}

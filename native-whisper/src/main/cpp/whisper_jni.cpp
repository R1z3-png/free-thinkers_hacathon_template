#include <jni.h>
#include <whisper.h>
#include <algorithm>
#include <memory>
#include <string>
#include <vector>

namespace {
struct Runtime { whisper_context * context = nullptr; int threads = 2; };
jlong create(JNIEnv * env, jobject, jstring path, jint threads) {
    const char * p = env->GetStringUTFChars(path, nullptr);
    auto * r = new Runtime(); r->threads = std::max(1, static_cast<int>(threads));
    auto params = whisper_context_default_params();
    params.use_gpu = false;
    r->context = whisper_init_from_file_with_params(p, params);
    env->ReleaseStringUTFChars(path, p);
    if (!r->context) { delete r; return 0; }
    return reinterpret_cast<jlong>(r);
}
jobjectArray transcribe(JNIEnv * env, jobject, jlong handle, jfloatArray input, jstring lang) {
    auto * r = reinterpret_cast<Runtime *>(handle);
    if (!r || !r->context) return nullptr;
    const jsize n = env->GetArrayLength(input);
    std::vector<float> samples(static_cast<size_t>(n));
    env->GetFloatArrayRegion(input, 0, n, samples.data());
    const char * language = env->GetStringUTFChars(lang, nullptr);
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = r->threads; params.no_context = true; params.token_timestamps = false;
    params.print_realtime = false; params.print_progress = false; params.print_timestamps = false; params.print_special = false;
    params.language = language; params.translate = false;
    const int rc = whisper_full(r->context, params, samples.data(), static_cast<int>(samples.size()));
    env->ReleaseStringUTFChars(lang, language);
    if (rc != 0) return nullptr;

    jclass wordClass = env->FindClass("org/freethinkers/transcriber/domain/processing/RecognizedWord");
    jmethodID ctor = env->GetMethodID(wordClass, "<init>", "(JJLjava/lang/String;)V");
    std::vector<jobject> words;
    const int segments = whisper_full_n_segments(r->context);
    for (int si = 0; si < segments; ++si) {
        const char * segmentText = whisper_full_get_segment_text(r->context, si);
        if (!segmentText || !*segmentText) continue;
        const int64_t start = whisper_full_get_segment_t0(r->context, si);
        const int64_t end = whisper_full_get_segment_t1(r->context, si);
        if (end <= start) continue;

        jstring text = env->NewStringUTF(segmentText);
        words.push_back(env->NewObject(
            wordClass,
            ctor,
            static_cast<jlong>(start * 10),
            static_cast<jlong>(end * 10),
            text));
        env->DeleteLocalRef(text);
    }
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(words.size()), wordClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(words.size()); ++i) { env->SetObjectArrayElement(result, i, words[i]); env->DeleteLocalRef(words[i]); }
    return result;
}
void release(JNIEnv *, jobject, jlong handle) { auto * r = reinterpret_cast<Runtime *>(handle); if (r) { whisper_free(r->context); delete r; } }
JNINativeMethod methods[] = {
    {const_cast<char *>("nativeCreate"), const_cast<char *>("(Ljava/lang/String;I)J"), reinterpret_cast<void *>(create)},
    {const_cast<char *>("nativeTranscribe"), const_cast<char *>("(J[FLjava/lang/String;)[Lorg/freethinkers/transcriber/domain/processing/RecognizedWord;"), reinterpret_cast<void *>(transcribe)},
    {const_cast<char *>("nativeRelease"), const_cast<char *>("(J)V"), reinterpret_cast<void *>(release)},
};
}
JNIEXPORT jint JNI_OnLoad(JavaVM * vm, void *) {
    JNIEnv * env = nullptr; if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = env->FindClass("org/freethinkers/transcriber/data/processing/JniWhisperCppRuntime");
    if (!cls || env->RegisterNatives(cls, methods, sizeof(methods)/sizeof(methods[0])) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}

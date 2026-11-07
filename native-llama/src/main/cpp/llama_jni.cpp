#include <jni.h>
#include <android/log.h>
#include <llama.h>
#include <algorithm>
#include <cctype>
#include <cstring>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

namespace {
struct Runtime { llama_model * model = nullptr; llama_context * context = nullptr; int threads = 2; };
std::once_flag backendOnce;

bool isWhitespace(uint32_t codePoint) {
    return codePoint == 0x20 || (codePoint >= 0x09 && codePoint <= 0x0D) || codePoint == 0x00A0 ||
        codePoint == 0x1680 || (codePoint >= 0x2000 && codePoint <= 0x200A) ||
        codePoint == 0x2028 || codePoint == 0x2029 || codePoint == 0x202F ||
        codePoint == 0x205F || codePoint == 0x3000;
}

uint32_t nextCodePoint(const std::string & text, size_t & offset) {
    const auto first = static_cast<unsigned char>(text[offset++]);
    if (first < 0x80) return first;
    int continuationCount = 0;
    uint32_t codePoint = 0;
    if ((first & 0xE0) == 0xC0) { continuationCount = 1; codePoint = first & 0x1F; }
    else if ((first & 0xF0) == 0xE0) { continuationCount = 2; codePoint = first & 0x0F; }
    else if ((first & 0xF8) == 0xF0) { continuationCount = 3; codePoint = first & 0x07; }
    else return first;
    for (int i = 0; i < continuationCount; ++i) {
        if (offset >= text.size()) return first;
        const auto next = static_cast<unsigned char>(text[offset]);
        if ((next & 0xC0) != 0x80) return first;
        ++offset;
        codePoint = (codePoint << 6) | (next & 0x3F);
    }
    return codePoint;
}

bool isWordCodePoint(uint32_t codePoint) {
    if (codePoint < 0x80) return std::isalnum(static_cast<unsigned char>(codePoint)) != 0;
    if (isWhitespace(codePoint)) return false;
    if ((codePoint >= 0x2000 && codePoint <= 0x206F) || (codePoint >= 0x2E00 && codePoint <= 0x2E7F) ||
        (codePoint >= 0x3000 && codePoint <= 0x303F) || (codePoint >= 0xFE10 && codePoint <= 0xFE1F) ||
        (codePoint >= 0xFE30 && codePoint <= 0xFE4F) || (codePoint >= 0xFF01 && codePoint <= 0xFF0F) ||
        (codePoint >= 0xFF1A && codePoint <= 0xFF20) || (codePoint >= 0xFF3B && codePoint <= 0xFF40) ||
        (codePoint >= 0xFF5B && codePoint <= 0xFF65)) return false;
    return true;
}

int countWords(const std::string & text) {
    int count = 0;
    bool insideWord = false;
    size_t offset = 0;
    while (offset < text.size()) {
        const bool isWord = isWordCodePoint(nextCodePoint(text, offset));
        if (isWord && !insideWord) ++count;
        insideWord = isWord;
    }
    return count;
}

bool endsWithSentencePunctuation(const std::string & text) {
    std::vector<uint32_t> codePoints;
    size_t offset = 0;
    while (offset < text.size()) codePoints.push_back(nextCodePoint(text, offset));
    while (!codePoints.empty()) {
        const uint32_t last = codePoints.back();
        if (isWhitespace(last) || last == '"' || last == '\'' || last == ')' || last == ']' || last == '}' ||
            last == 0x00BB || last == 0x2019 || last == 0x201D) {
            codePoints.pop_back();
        } else break;
    }
    if (codePoints.empty()) return false;
    const uint32_t last = codePoints.back();
    return last == '.' || last == '?' || last == '!' || last == 0x2026 || last == 0x3002 || last == 0xFF01 || last == 0xFF1F;
}

jlong create(JNIEnv * env, jobject, jstring path, jint contextTokens, jint threads) {
    std::call_once(backendOnce, [] { llama_backend_init(); });
    const char * p = env->GetStringUTFChars(path, nullptr);
    auto * r = new Runtime(); r->threads = std::max(1, static_cast<int>(threads));
    auto mp = llama_model_default_params(); mp.n_gpu_layers = 0;
    r->model = llama_model_load_from_file(p, mp);
    env->ReleaseStringUTFChars(path, p);
    if (!r->model) { delete r; return 0; }
    auto cp = llama_context_default_params();
    cp.n_ctx = static_cast<uint32_t>(std::max(128, static_cast<int>(contextTokens)));
    cp.n_batch = cp.n_ubatch = std::min<uint32_t>(cp.n_ctx, 512);
    cp.n_threads = cp.n_threads_batch = r->threads;
    r->context = llama_init_from_model(r->model, cp);
    if (!r->context) { llama_model_free(r->model); delete r; return 0; }
    return reinterpret_cast<jlong>(r);
}
jstring complete(JNIEnv * env, jobject, jlong handle, jstring prompt, jint maxTokens, jint expectedWordCount) {
    auto * r = reinterpret_cast<Runtime *>(handle);
    if (!r || !r->context) return env->NewStringUTF("");
    const char * p = env->GetStringUTFChars(prompt, nullptr);
    const llama_vocab * vocab = llama_model_get_vocab(r->model);
    const int32_t len = static_cast<int32_t>(strlen(p));
    std::vector<llama_token> tokens(static_cast<size_t>(len) + 8);
    int32_t n = llama_tokenize(vocab, p, len, tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    env->ReleaseStringUTFChars(prompt, p);
    if (n < 0) return env->NewStringUTF("");
    tokens.resize(static_cast<size_t>(n));
    if (tokens.empty()) return env->NewStringUTF("");
    if (static_cast<uint32_t>(n + maxTokens) > llama_n_ctx(r->context)) return env->NewStringUTF("");
    llama_memory_clear(llama_get_memory(r->context), true);
    for (int32_t offset = 0; offset < n; offset += 512) {
        const int32_t count = std::min<int32_t>(512, n - offset);
        auto batch = llama_batch_get_one(tokens.data() + offset, count);
        if (llama_decode(r->context, batch) != 0) return env->NewStringUTF("");
    }
    llama_sampler * sampler = llama_sampler_init_greedy();
    std::string output;
    std::vector<char> piece(256);
    const int limit = std::max(1, static_cast<int>(maxTokens));
    int generatedTokens = 0;
    bool stoppedAfterTranscript = false;
    for (int i = 0; i < limit; ++i) {
        llama_token token = llama_sampler_sample(sampler, r->context, -1);
        generatedTokens = i + 1;
        if (llama_vocab_is_eog(vocab, token)) break;
        int32_t bytes = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, false);
        if (bytes < 0) { piece.resize(static_cast<size_t>(-bytes)); bytes = llama_token_to_piece(vocab, token, piece.data(), static_cast<int32_t>(piece.size()), 0, false); }
        if (bytes > 0) output.append(piece.data(), static_cast<size_t>(bytes));
        if (expectedWordCount > 0 && countWords(output) >= expectedWordCount && endsWithSentencePunctuation(output)) {
            stoppedAfterTranscript = true;
            break;
        }
        llama_token next = token;
        auto nextBatch = llama_batch_get_one(&next, 1);
        if (llama_decode(r->context, nextBatch) != 0) break;
    }
    llama_sampler_free(sampler);
    __android_log_print(ANDROID_LOG_INFO, "QwenBenchmark", "generated_tokens=%d max_tokens=%d expected_words=%d early_stop=%d",
        generatedTokens, limit, expectedWordCount, stoppedAfterTranscript ? 1 : 0);
    return env->NewStringUTF(output.c_str());
}
void release(JNIEnv *, jobject, jlong handle) {
    auto * r = reinterpret_cast<Runtime *>(handle);
    if (r) { llama_free(r->context); llama_model_free(r->model); delete r; }
}
JNINativeMethod methods[] = {
    {const_cast<char *>("nativeCreate"), const_cast<char *>("(Ljava/lang/String;II)J"), reinterpret_cast<void *>(create)},
    {const_cast<char *>("nativeComplete"), const_cast<char *>("(JLjava/lang/String;II)Ljava/lang/String;"), reinterpret_cast<void *>(complete)},
    {const_cast<char *>("nativeRelease"), const_cast<char *>("(J)V"), reinterpret_cast<void *>(release)},
};
}
JNIEXPORT jint JNI_OnLoad(JavaVM * vm, void *) {
    JNIEnv * env = nullptr; if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass cls = env->FindClass("org/freethinkers/transcriber/data/processing/JniLlamaCppRuntime");
    if (!cls || env->RegisterNatives(cls, methods, sizeof(methods)/sizeof(methods[0])) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}

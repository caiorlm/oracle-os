#include <jni.h>
#include <android/log.h>

#include "chat.h"
#include "common.h"
#include "llama.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

constexpr int kBatchSize = 128;
std::mutex g_mutex;
std::once_flag g_backend_init;
std::atomic<uint64_t> g_active_id{0};
std::atomic<uint64_t> g_next_id{1};
std::atomic<bool> g_cancelled{false};

struct Session {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_batch_ext * batch = nullptr;
    llama_sampler * sampler = nullptr;
    common_chat_templates_ptr templates;
    const llama_vocab * vocab = nullptr;

    std::vector<llama_token> prompt_tokens;
    size_t prompt_cursor = 0;
    llama_pos position = 0;
    llama_token pending_token = LLAMA_TOKEN_NULL;
    int generated = 0;
    int max_tokens = 0;
    uint64_t generation_id = 0;
    std::chrono::steady_clock::time_point generation_started;

    ~Session() {
        if (batch) llama_batch_ext_free(batch);
        if (sampler) llama_sampler_free(sampler);
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
};

std::unique_ptr<Session> g_session;

void throw_java(JNIEnv * env, const char * type, const std::string & message) {
    const auto exception = env->FindClass(type);
    if (exception) env->ThrowNew(exception, message.c_str());
}

std::string bytes_from_java(JNIEnv * env, jbyteArray input) {
    if (!input) throw std::invalid_argument("Prompt is null");
    const auto size = env->GetArrayLength(input);
    std::string result(static_cast<size_t>(size), '\0');
    if (size > 0) env->GetByteArrayRegion(input, 0, size, reinterpret_cast<jbyte *>(&result[0]));
    return result;
}

bool abort_requested(void *) {
    return g_cancelled.load(std::memory_order_relaxed);
}

void set_batch(Session & session, const llama_token * tokens, int count, llama_pos first_pos, bool logits) {
    llama_batch_ext_clear(session.batch);
    for (int i = 0; i < count; ++i) {
        const int index = llama_batch_ext_add_token(session.batch, 0, tokens[i]);
        const llama_pos pos = first_pos + i;
        if (index < 0 || !llama_batch_ext_set_pos(session.batch, index, &pos)) {
            throw std::runtime_error("Could not prepare token batch");
        }
        if (logits && i == count - 1 && !llama_batch_ext_set_output_logits(session.batch, index, true)) {
            throw std::runtime_error("Could not request output logits");
        }
    }
}

bool process_batch(Session & session) {
    const int result = llama_process(session.context, LLAMA_PROCESS_TYPE_DECODE, session.batch);
    if (g_cancelled.load(std::memory_order_relaxed)) return false;
    if (result != 0) throw std::runtime_error("llama_process failed: " + std::to_string(result));
    return true;
}

std::vector<llama_token> tokenize_chat(Session & session, const std::string & user_prompt) {
    common_chat_msg message;
    message.role = "user";
    message.content = user_prompt;
    common_chat_templates_inputs inputs;
    inputs.messages.push_back(std::move(message));
    inputs.add_generation_prompt = true;
    inputs.enable_thinking = false;
    inputs.use_jinja = true;
    const auto formatted = common_chat_templates_apply(session.templates.get(), inputs);

    std::vector<llama_token> tokens;
    bool first = true;
    for (const auto & part : formatted.prompt.parts) {
        if (part.text.empty()) continue;
        // User text must not be parsed as control/special tokens.
        const auto segment = common_tokenize(session.vocab, part.text, first, !part.is_input);
        tokens.insert(tokens.end(), segment.begin(), segment.end());
        first = false;
    }
    if (tokens.empty()) throw std::invalid_argument("Chat template produced no tokens");
    return tokens;
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_oracleos_inference_LlamaCppEngine_nativeLoad(
        JNIEnv * env, jobject, jstring path, jint context_tokens, jint threads) {
    try {
        if (!path) throw std::invalid_argument("Model path is null");
        if (context_tokens < 512 || context_tokens > 8192 || threads < 1 || threads > 8) {
            throw std::invalid_argument("Invalid context or thread limit");
        }
        std::lock_guard<std::mutex> lock(g_mutex);
        if (g_session) throw std::logic_error("A model is already loaded");
        std::call_once(g_backend_init, [] { llama_backend_init(); });

        const char * utf_path = env->GetStringUTFChars(path, nullptr);
        if (!utf_path) return;
        std::string model_path(utf_path);
        env->ReleaseStringUTFChars(path, utf_path);

        auto session = std::make_unique<Session>();
        auto model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
        session->model = llama_model_load_from_file(model_path.c_str(), model_params);
        if (!session->model) throw std::runtime_error("Unable to load GGUF model");
        if (llama_model_has_encoder(session->model)) {
            throw std::runtime_error("Encoder-decoder GGUF models are not supported by this adapter");
        }

        auto context_params = llama_context_default_params();
        context_params.n_ctx = static_cast<uint32_t>(context_tokens);
        context_params.n_batch = kBatchSize;
        context_params.n_ubatch = kBatchSize;
        context_params.n_threads = threads;
        context_params.n_threads_batch = threads;
        session->context = llama_init_from_model(session->model, context_params);
        if (!session->context) throw std::runtime_error("Unable to allocate llama context");
        llama_set_abort_callback(session->context, abort_requested, nullptr);

        session->vocab = llama_model_get_vocab(session->model);
        session->templates = common_chat_templates_init(session->model, "");
        session->sampler = llama_sampler_init_greedy();
        session->batch = llama_batch_ext_init(session->context);
        if (!session->vocab || !session->templates || !session->sampler || !session->batch) {
            throw std::runtime_error("Unable to prepare llama resources");
        }
        g_cancelled.store(false, std::memory_order_relaxed);
        g_session = std::move(session);
        __android_log_print(ANDROID_LOG_INFO, "OracleInference", "model ready; context=%d threads=%d", context_tokens, threads);
    } catch (const std::exception & error) {
        __android_log_print(ANDROID_LOG_ERROR, "OracleInference", "load: %s", error.what());
        throw_java(env, "java/lang/IllegalStateException", error.what());
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_oracleos_inference_LlamaCppEngine_nativeBegin(
        JNIEnv * env, jobject, jbyteArray prompt, jint max_tokens) {
    try {
        const auto text = bytes_from_java(env, prompt);
        if (text.empty() || text.size() > 16384 || max_tokens < 1 || max_tokens > 1024) {
            throw std::invalid_argument("Prompt or output limit is invalid");
        }
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!g_session) throw std::logic_error("No model is loaded");
        if (g_active_id.load(std::memory_order_relaxed) != 0) {
            throw std::logic_error("Generation already active");
        }
        auto & session = *g_session;
        auto tokens = tokenize_chat(session, text);
        if (tokens.size() + static_cast<size_t>(max_tokens) > llama_n_ctx(session.context)) {
            throw std::invalid_argument("Prompt exceeds the configured context budget");
        }
        llama_memory_clear(llama_get_memory(session.context), false);
        llama_sampler_reset(session.sampler);
        session.prompt_tokens = std::move(tokens);
        session.prompt_cursor = 0;
        session.position = 0;
        session.pending_token = LLAMA_TOKEN_NULL;
        session.generated = 0;
        session.max_tokens = max_tokens;
        session.generation_id = g_next_id.fetch_add(1, std::memory_order_relaxed);
        session.generation_started = std::chrono::steady_clock::now();
        g_cancelled.store(false, std::memory_order_relaxed);
        g_active_id.store(session.generation_id, std::memory_order_release);
        __android_log_print(ANDROID_LOG_INFO, "OracleInference", "generation started; prompt_tokens=%zu max_tokens=%d", session.prompt_tokens.size(), max_tokens);
        return static_cast<jlong>(session.generation_id);
    } catch (const std::exception & error) {
        throw_java(env, "java/lang/IllegalArgumentException", error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_oracleos_inference_LlamaCppEngine_nativeNext(JNIEnv * env, jobject, jlong id) {
    try {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!g_session || id == 0 || g_session->generation_id != static_cast<uint64_t>(id) ||
            g_active_id.load(std::memory_order_acquire) != static_cast<uint64_t>(id) ||
            g_cancelled.load(std::memory_order_relaxed)) return nullptr;
        auto & session = *g_session;

        while (session.prompt_cursor < session.prompt_tokens.size()) {
            const int count = static_cast<int>(std::min<size_t>(
                kBatchSize, session.prompt_tokens.size() - session.prompt_cursor));
            const bool last = session.prompt_cursor + count == session.prompt_tokens.size();
            set_batch(session, session.prompt_tokens.data() + session.prompt_cursor,
                      count, session.position, last);
            if (!process_batch(session)) return nullptr;
            session.prompt_cursor += count;
            session.position += count;
        }

        if (session.generated >= session.max_tokens) return nullptr;
        if (session.pending_token != LLAMA_TOKEN_NULL) {
            set_batch(session, &session.pending_token, 1, session.position, true);
            if (!process_batch(session)) return nullptr;
            ++session.position;
            session.pending_token = LLAMA_TOKEN_NULL;
        }

        const llama_token token = llama_sampler_sample(session.sampler, session.context, -1);
        if (llama_vocab_is_eog(session.vocab, token)) return nullptr;
        session.pending_token = token;
        ++session.generated;
        if (session.generated == 1 || session.generated % 16 == 0) {
            const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - session.generation_started).count();
            __android_log_print(ANDROID_LOG_INFO, "OracleInference", "generated=%d elapsed_ms=%lld", session.generated, static_cast<long long>(elapsed));
        }
        const auto piece = common_token_to_piece(session.vocab, token, false);
        auto result = env->NewByteArray(static_cast<jsize>(piece.size()));
        if (result && !piece.empty()) {
            env->SetByteArrayRegion(result, 0, static_cast<jsize>(piece.size()),
                                    reinterpret_cast<const jbyte *>(piece.data()));
        }
        return result;
    } catch (const std::exception & error) {
        throw_java(env, "java/lang/IllegalStateException", error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_oracleos_inference_LlamaCppEngine_nativeCancel(JNIEnv *, jobject, jlong id) {
    if (id != 0 && g_active_id.load(std::memory_order_acquire) == static_cast<uint64_t>(id)) {
        g_cancelled.store(true, std::memory_order_release);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_oracleos_inference_LlamaCppEngine_nativeEnd(JNIEnv *, jobject, jlong id) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_session || id == 0 || g_session->generation_id != static_cast<uint64_t>(id)) return;
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - g_session->generation_started).count();
    __android_log_print(ANDROID_LOG_INFO, "OracleInference", "generation ended; tokens=%d elapsed_ms=%lld cancelled=%d",
                        g_session->generated, static_cast<long long>(elapsed), g_cancelled.load());
    g_active_id.store(0, std::memory_order_release);
    g_session->generation_id = 0;
    g_session->prompt_tokens.clear();
    g_session->pending_token = LLAMA_TOKEN_NULL;
    g_cancelled.store(false, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_oracleos_inference_LlamaCppEngine_nativeUnload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_active_id.store(0, std::memory_order_release);
    g_cancelled.store(true, std::memory_order_relaxed);
    g_session.reset();
}

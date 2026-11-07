cmake_minimum_required(VERSION 3.22.1)
project(oracle_inference LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_SOURCE "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_SOURCE}/CMakeLists.txt")
    message(FATAL_ERROR "Pinned llama.cpp source missing. Run scripts/bootstrap-llama.sh first.")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_SOURCE}" "${CMAKE_BINARY_DIR}/llama-build")

add_library(oracle_inference SHARED oracle_jni.cpp)
target_include_directories(oracle_inference PRIVATE
    "${LLAMA_SOURCE}/include"
    "${LLAMA_SOURCE}/common"
    "${LLAMA_SOURCE}/ggml/include")
target_link_libraries(oracle_inference PRIVATE llama llama-common log android)

# The debug APK is used for on-device acceptance tests. NDK's Debug configuration
# otherwise builds ggml at -O0, making a small model unusably slow on 4 GB phones.
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    foreach(native_target ggml-base ggml-cpu llama llama-common oracle_inference)
        if(TARGET ${native_target})
            target_compile_options(${native_target} PRIVATE -O2)
        endif()
    endforeach()
endif()

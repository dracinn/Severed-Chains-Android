cmake_minimum_required(VERSION 3.22.1)
project(scopus C)

# Third-party sources are fetched and SHA-256-verified by the fetchThirdParty
# Gradle task into android/build/third_party/src (not committed).
set(TP ${CMAKE_CURRENT_SOURCE_DIR}/../../../build/third_party/src)

# AGP builds Debug variants without optimization; opus encode/decode must not
# run at -O0 (upstream warns it is "very slow"), so force -O2 for all targets.
add_compile_options(-O2)

if(NOT EXISTS ${TP}/libogg-1.3.5/CMakeLists.txt
   OR NOT EXISTS ${TP}/opus-1.5.2/CMakeLists.txt
   OR NOT EXISTS ${TP}/opusfile-0.12/include/opusfile.h)
  message(FATAL_ERROR "third_party sources missing; run the fetchThirdParty task")
endif()

# libogg 1.3.5 (official CMake build -> target `ogg`)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(INSTALL_DOCS OFF CACHE BOOL "" FORCE)
set(INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(INSTALL_CMAKE_PACKAGE_MODULE OFF CACHE BOOL "" FORCE)
add_subdirectory(${TP}/libogg-1.3.5 ${CMAKE_BINARY_DIR}/third_party/libogg)

# opus 1.5.2 (official CMake build -> target `opus`; keep NEON defaults)
set(OPUS_INSTALL_PKG_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_INSTALL_CMAKE_CONFIG_MODULE OFF CACHE BOOL "" FORCE)
set(OPUS_HARDENING OFF CACHE BOOL "" FORCE)
add_subdirectory(${TP}/opus-1.5.2 ${CMAKE_BINARY_DIR}/third_party/opus)

# opusfile 0.12 ships only autotools upstream; its HTTP support lives in a
# separate libopusurl (src/http.c, OP_ENABLE_HTTP) which is simply not
# compiled here, so HTTP is disabled entirely.
set(OPUSFILE_DIR ${TP}/opusfile-0.12)
add_library(opusfile STATIC
  ${OPUSFILE_DIR}/src/info.c
  ${OPUSFILE_DIR}/src/internal.c
  ${OPUSFILE_DIR}/src/opusfile.c
  ${OPUSFILE_DIR}/src/stream.c)
target_include_directories(opusfile PUBLIC ${OPUSFILE_DIR}/include)
# ogg/opus export their include dirs PUBLIC, so linking propagates them for
# compiling opusfile itself (ogg.h + generated config_types.h, opus_multistream.h)
target_link_libraries(opusfile PUBLIC ogg opus)

add_library(scopus SHARED opus_jni.c yuv2rgb.c debug_gl.c os_jni.c)
target_link_libraries(scopus PRIVATE opusfile opus ogg log EGL GLESv3)

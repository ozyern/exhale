# Automix's native analysis: whole-track tempo, beat grid, key, energy and structure, by
# autocorrelation (no ML model). Ported from BitChord (github.com/kushagrasinghx/BitChord),
# which ported it from Orchard (github.com/SFG5453/Orchard); see the headers of each source
# for its licence.

cmake_minimum_required(VERSION 3.22.1)
project(exhale_analysis LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(exhale_analysis SHARED
    analyzer/audio_analysis.cpp
    analyzer/tempo_analysis.cpp
    analyzer/resampler.cpp
    jni/analysis_jni.cpp
    jni/mp3_jni.c
)

# LAME, for saving songs as MP3 (LGPL-2.0-or-later; see lame/COPYING).
file(GLOB LAME_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/lame/*.c)
add_library(exhale_lame STATIC ${LAME_SOURCES})
target_include_directories(exhale_lame PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/lame)
target_compile_definitions(exhale_lame PRIVATE HAVE_CONFIG_H)
target_compile_options(exhale_lame PRIVATE -O2 -w -fvisibility=hidden)

target_include_directories(exhale_analysis PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# -ffast-math is deliberately NOT enabled: the analyzer's arithmetic is tuned against exact
# IEEE behaviour.
target_compile_options(exhale_analysis PRIVATE -O3 -fvisibility=hidden)

find_library(log-lib log)
target_link_libraries(exhale_analysis exhale_lame ${log-lib})

# 16 KB page-size support (Android 15+): align LOAD segments to 16 KB, a superset of 4 KB.
target_link_options(exhale_analysis PRIVATE "-Wl,-z,max-page-size=16384")

cmake_minimum_required(VERSION 3.22)
project(adturbo)
add_library(adturbo SHARED native-lib.cpp)
target_include_directories(adturbo PRIVATE
    ${CMAKE_SOURCE_DIR}
    /usr/include/glib-2.0
    /usr/lib/x86_64-linux-gnu/glib-2.0/include)
set(FRIDA_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
if(EXISTS ${FRIDA_DIR}/libfrida-core.so)
  add_library(frida-core SHARED IMPORTED)
  set_target_properties(frida-core PROPERTIES IMPORTED_LOCATION ${FRIDA_DIR}/libfrida-core.so)
else()
  add_library(frida-core STATIC IMPORTED)
  set_target_properties(frida-core PROPERTIES IMPORTED_LOCATION ${FRIDA_DIR}/libfrida-core.a)
endif()
target_link_libraries(adturbo frida-core log android)

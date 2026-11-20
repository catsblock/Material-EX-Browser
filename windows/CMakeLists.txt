cmake_minimum_required(VERSION 3.21)
project(materialex CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

option(MEX_BUILD_APP "Build the CEF browser application (Windows only)" ON)
option(MEX_BUILD_TESTS "Build core unit tests" ON)

add_library(materialex_core STATIC
  src/core/url_utils.cc
  src/core/filename.cc
  src/core/text_store.cc
  src/core/permission_policy.cc
  src/core/records.cc)
target_include_directories(materialex_core PUBLIC src)

if(MEX_BUILD_TESTS)
  enable_testing()
  add_executable(materialex_core_tests tests/core_tests.cc)
  target_link_libraries(materialex_core_tests materialex_core)
  add_test(NAME core_tests COMMAND materialex_core_tests)
endif()

if(MEX_BUILD_APP AND WIN32)
  include(cmake/DownloadCEF.cmake)
  set(CEF_ROOT "" CACHE PATH "Path to an extracted CEF binary distribution")
  if(NOT CEF_ROOT)
    download_cef(windows64 stable "${CMAKE_BINARY_DIR}/cef" CEF_ROOT)
  endif()
  option(USE_SANDBOX "Enable the CEF sandbox" ON)
  list(APPEND CMAKE_MODULE_PATH "${CEF_ROOT}/cmake")
  find_package(CEF REQUIRED)
  add_subdirectory("${CEF_LIBCEF_DLL_WRAPPER_PATH}" libcef_dll_wrapper)
  ADD_LOGICAL_TARGET("libcef_lib" "${CEF_LIB_DEBUG}" "${CEF_LIB_RELEASE}")
  set(CEF_TARGET materialex)
  SET_CEF_TARGET_OUT_DIR()

  set(MEX_APP_SOURCES
    src/main.cc
    src/app.cc
    src/client.cc
    src/main_window.cc
    src/panels.cc
    src/downloads.cc
    src/permissions.cc
    src/profile.cc
    src/platform.cc
    src/strings.cc)

  if(USE_SANDBOX)
    add_library(materialex SHARED ${MEX_APP_SOURCES})
    SET_LIBRARY_TARGET_PROPERTIES(materialex)
    COPY_SINGLE_FILE(materialex "${CEF_BINARY_DIR}/bootstrap.exe" "${CEF_TARGET_OUT_DIR}/materialex.exe")
    if(CMAKE_GENERATOR MATCHES "Visual Studio")
      set_target_properties(materialex PROPERTIES
        VS_DEBUGGER_COMMAND "$<TARGET_FILE_DIR:materialex>/materialex.exe")
    endif()
  else()
    add_executable(materialex WIN32 ${MEX_APP_SOURCES})
    SET_EXECUTABLE_TARGET_PROPERTIES(materialex)
    ADD_WINDOWS_MANIFEST("${CMAKE_CURRENT_SOURCE_DIR}/res" materialex "exe")
  endif()

  target_include_directories(materialex PRIVATE "${CEF_ROOT}" src)
  add_dependencies(materialex libcef_dll_wrapper)
  target_link_libraries(materialex PRIVATE materialex_core libcef_lib libcef_dll_wrapper ${CEF_STANDARD_LIBS}
                        comctl32 shell32 ole32 user32 shlwapi)
  target_compile_definitions(materialex PRIVATE UNICODE _UNICODE WIN32_LEAN_AND_MEAN NOMINMAX)

  COPY_FILES(materialex "${CEF_BINARY_FILES}" "${CEF_BINARY_DIR}" "${CEF_TARGET_OUT_DIR}")
  COPY_FILES(materialex "${CEF_RESOURCE_FILES}" "${CEF_RESOURCE_DIR}" "${CEF_TARGET_OUT_DIR}")

  if(USE_SANDBOX)
    SET_LPAC_ACLS(materialex)
  endif()
endif()

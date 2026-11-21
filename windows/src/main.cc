#include <windows.h>

#include "app.h"
#include "downloads.h"
#include "include/cef_command_line.h"
#include "include/cef_sandbox_win.h"
#include "include/cef_version_info.h"
#include "main_window.h"
#include "platform.h"
#include "profile.h"

namespace {

int RunMain(HINSTANCE instance, void* sandbox_info) {
  SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);

  CefMainArgs main_args(instance);
  CefRefPtr<mex::BrowserApp> app(new mex::BrowserApp());

  int exit_code = CefExecuteProcess(main_args, app, sandbox_info);
  if (exit_code >= 0) return exit_code;

  mex::MainWindow::RegisterWindowClass(instance);
  mex::Profile::Get().Load();
  mex::DownloadManager::Get().Load();

  std::wstring data_dir = mex::DataDir();
  CefSettings settings;
  if (!sandbox_info) settings.no_sandbox = true;
  CefString(&settings.root_cache_path) = data_dir + L"\\Profile";
  CefString(&settings.cache_path) = data_dir + L"\\Profile\\Default";
  CefString(&settings.log_file) = data_dir + L"\\debug.log";
  settings.log_severity = LOGSEVERITY_WARNING;
  settings.persist_session_cookies = true;
  settings.multi_threaded_message_loop = false;

  if (!CefInitialize(main_args, settings, app, sandbox_info)) {
    return CefGetExitCode();
  }

  CefRunMessageLoop();
  CefShutdown();
  return 0;
}

}  // namespace

#if defined(CEF_USE_BOOTSTRAP)

CEF_BOOTSTRAP_EXPORT int RunWinMain(HINSTANCE instance, LPWSTR, int, void* sandbox_info, cef_version_info_t*) {
  return RunMain(instance, sandbox_info);
}

#else

int APIENTRY wWinMain(HINSTANCE instance, HINSTANCE, LPWSTR, int) {
  return RunMain(instance, nullptr);
}

#endif

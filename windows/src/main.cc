#include <windows.h>

#include "app.h"
#include "downloads.h"
#include "include/cef_command_line.h"
#include "include/cef_sandbox_win.h"
#include "main_window.h"
#include "platform.h"
#include "profile.h"

int APIENTRY wWinMain(HINSTANCE instance, HINSTANCE, LPWSTR, int) {
  SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);

  void* sandbox_info = nullptr;
#if defined(CEF_USE_SANDBOX)
  CefScopedSandboxInfo scoped_sandbox;
  sandbox_info = scoped_sandbox.sandbox_info();
#endif

  CefMainArgs main_args(instance);
  CefRefPtr<mex::BrowserApp> app(new mex::BrowserApp());

  int exit_code = CefExecuteProcess(main_args, app, sandbox_info);
  if (exit_code >= 0) return exit_code;

  CefRefPtr<CefCommandLine> command_line = CefCommandLine::CreateCommandLine();
  command_line->InitFromString(::GetCommandLineW());

  mex::Profile::Get().Load();
  mex::DownloadManager::Get().Load();

  std::wstring data_dir = mex::DataDir();
  CefSettings settings;
#if !defined(CEF_USE_SANDBOX)
  settings.no_sandbox = true;
#endif
  CefString(&settings.root_cache_path) = data_dir + L"\\Profile";
  CefString(&settings.cache_path) = data_dir + L"\\Profile\\Default";
  CefString(&settings.log_file) = data_dir + L"\\debug.log";
  settings.log_severity = LOGSEVERITY_WARNING;
  settings.persist_session_cookies = true;
  settings.multi_threaded_message_loop = false;

  if (!CefInitialize(main_args, settings, app, sandbox_info)) {
    return CefGetExitCode();
  }

  mex::MainWindow::RegisterWindowClass(instance);
  CefRunMessageLoop();
  CefShutdown();
  return 0;
}

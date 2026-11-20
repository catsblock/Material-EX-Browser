#include "app.h"

#include "core/url_utils.h"
#include "main_window.h"
#include "profile.h"

namespace mex {

namespace {

std::string UrlFromCommandLine(CefRefPtr<CefCommandLine> cl) {
  std::string url = cl->GetSwitchValue("url").ToString();
  if (url.empty()) {
    CefCommandLine::ArgumentList args;
    cl->GetArguments(args);
    if (!args.empty()) url = args.front().ToString();
  }
  return NormalizeInput(url, Profile::Get().SearchTemplate());
}

}  // namespace

void BrowserApp::OnBeforeCommandLineProcessing(const CefString& process_type, CefRefPtr<CefCommandLine> command_line) {
  if (process_type.empty()) {
    command_line->AppendSwitchWithValue("disable-features", "Translate");
  }
}

void BrowserApp::OnContextInitialized() {
  MainWindow::Create(UrlFromCommandLine(CefCommandLine::GetGlobalCommandLine()));
}

bool BrowserApp::OnAlreadyRunningAppRelaunch(CefRefPtr<CefCommandLine> command_line, const CefString&) {
  std::string url = UrlFromCommandLine(command_line);
  MainWindow* w = MainWindow::Last();
  if (w) {
    w->Activate();
    if (!url.empty()) w->AddTab(url, true);
  } else {
    MainWindow::Create(url);
  }
  return true;
}

}  // namespace mex

#include "main_window.h"

#include <commctrl.h>
#include <shellapi.h>

#include <algorithm>
#include <windowsx.h>

#include "core/text_store.h"
#include "core/url_utils.h"
#include "downloads.h"
#include "include/cef_app.h"
#include "include/cef_browser.h"
#include "include/cef_version.h"
#include "include/wrapper/cef_helpers.h"
#include "panels.h"
#include "permissions.h"
#include "platform.h"
#include "profile.h"
#include "strings.h"

namespace mex {

namespace {

constexpr wchar_t kWindowClass[] = L"MaterialEXMainWindow";

enum Command {
  kCmdBack = 101,
  kCmdForward,
  kCmdReload,
  kCmdHome,
  kCmdNewTab,
  kCmdBookmark,
  kCmdMenu,
  kCmdAddress = 120,
  kCmdTabs = 121,
  kCmdSecurity = 122,
  kCmdNewWindow = 200,
  kCmdCloseTab,
  kCmdCloseOthers,
  kCmdBookmarks,
  kCmdHistory,
  kCmdDownloads,
  kCmdSiteEditor,
  kCmdSettings,
  kCmdDevTools,
  kCmdFullscreen,
  kCmdExit,
  kCmdFocusAddress,
  kCmdNextTab,
  kCmdPrevTab,
  kCmdZoomIn,
  kCmdZoomOut,
  kCmdZoomReset,
  kCmdUaGlobal = 300,
  kCmdUaDefault,
  kCmdUaDesktop,
  kCmdUaMobile,
  kCmdUaCustom,
};

constexpr UINT kMsgCommand = WM_APP + 1;

std::vector<MainWindow*>& Windows() {
  static std::vector<MainWindow*> w;
  return w;
}

std::string TruncateTitle(const std::string& t) {
  const size_t kMax = 60;
  if (t.size() <= kMax) return t;
  size_t cut = kMax;
  while (cut > 0 && (static_cast<unsigned char>(t[cut]) & 0xC0) == 0x80) --cut;
  return t.substr(0, cut) + "...";
}

std::string MajorVersion() { return std::to_string(CHROME_VERSION_MAJOR); }

}  // namespace

void MainWindow::RegisterWindowClass(HINSTANCE instance) {
  WNDCLASSEXW wc{};
  wc.cbSize = sizeof(wc);
  wc.style = CS_HREDRAW | CS_VREDRAW;
  wc.lpfnWndProc = WndProc;
  wc.hInstance = instance;
  wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
  wc.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
  wc.lpszClassName = kWindowClass;
  wc.hIcon = LoadIconW(nullptr, IDI_APPLICATION);
  RegisterClassExW(&wc);
}

size_t MainWindow::Count() { return Windows().size(); }

int g_live_windows = 0;

MainWindow::MainWindow() { ++g_live_windows; }

MainWindow::~MainWindow() {
  if (--g_live_windows == 0) {
    DownloadManager::Get().MarkAllInterrupted();
    DownloadManager::Get().Save();
    LogLine("all windows released, quitting loop");
    CefQuitMessageLoop();
  }
}

void MainWindow::MaybeDelete() {
  if (destroyed_ && owned_ids_.empty() && client_->PopupCount() == 0) delete this;
}

MainWindow* MainWindow::Last() { return Windows().empty() ? nullptr : Windows().back(); }

MainWindow* MainWindow::Create(const std::string& url) {
  MainWindow* w = new MainWindow();
  if (!w->Init(url)) {
    if (w->hwnd_) DestroyWindow(w->hwnd_);
    else delete w;
    return nullptr;
  }
  return w;
}

bool MainWindow::Init(const std::string& url) {
  rtl_ = IsRtlUi();
  client_ = new Client(this);
  hwnd_ = CreateWindowExW(0, kWindowClass, Tr(Str::AppName), WS_OVERLAPPEDWINDOW,
                          CW_USEDEFAULT, CW_USEDEFAULT, 1280, 800, nullptr, nullptr, GetModuleHandleW(nullptr), this);
  if (!hwnd_) return false;
  Windows().push_back(this);
  RestorePlacement();
  ShowWindow(hwnd_, saved_placement_.showCmd == SW_SHOWMAXIMIZED ? SW_SHOWMAXIMIZED : SW_SHOW);
  UpdateWindow(hwnd_);
  AddTab(url, true);
  return true;
}

void MainWindow::Activate() {
  if (IsIconic(hwnd_)) ShowWindow(hwnd_, SW_RESTORE);
  SetForegroundWindow(hwnd_);
}

void MainWindow::CreateControls() {
  HINSTANCE inst = GetModuleHandleW(nullptr);
  INITCOMMONCONTROLSEX icc{sizeof(icc), ICC_TAB_CLASSES};
  InitCommonControlsEx(&icc);
  tabs_ = CreateWindowExW(0, WC_TABCONTROLW, L"", WS_CHILD | WS_VISIBLE | TCS_FIXEDWIDTH | TCS_FOCUSNEVER, 0, 0, 10, 10,
                          hwnd_, reinterpret_cast<HMENU>(static_cast<INT_PTR>(kCmdTabs)), inst, nullptr);
  SetWindowSubclass(tabs_, TabsProc, 1, reinterpret_cast<DWORD_PTR>(this));
  struct B {
    int id;
    Str label;
  };
  const B buttons[] = {{kCmdBack, Str::Back},       {kCmdForward, Str::Forward}, {kCmdReload, Str::Reload},
                       {kCmdHome, Str::Home},       {kCmdNewTab, Str::NewTab},   {kCmdBookmark, Str::AddBookmark},
                       {kCmdMenu, Str::Menu}};
  for (const B& b : buttons) {
    CreateWindowExW(0, L"BUTTON", Tr(b.label), WS_CHILD | WS_VISIBLE | WS_TABSTOP | BS_PUSHBUTTON, 0, 0, 10, 10, hwnd_,
                    reinterpret_cast<HMENU>(static_cast<INT_PTR>(b.id)), inst, nullptr);
  }
  address_ = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | WS_TABSTOP | ES_AUTOHSCROLL, 0, 0,
                             10, 10, hwnd_, reinterpret_cast<HMENU>(static_cast<INT_PTR>(kCmdAddress)), inst, nullptr);
  SetWindowSubclass(address_, AddressProc, 2, reinterpret_cast<DWORD_PTR>(this));
  security_ = CreateWindowExW(0, L"STATIC", L"", WS_CHILD | WS_VISIBLE | SS_CENTERIMAGE | SS_CENTER, 0, 0, 10, 10,
                              hwnd_, reinterpret_cast<HMENU>(static_cast<INT_PTR>(kCmdSecurity)), inst, nullptr);
  font_ = CreateUiFont(dpi_);
  SendMessageW(tabs_, WM_SETFONT, reinterpret_cast<WPARAM>(font_), TRUE);
  EnumChildWindows(hwnd_, [](HWND c, LPARAM f) -> BOOL {
    SendMessageW(c, WM_SETFONT, static_cast<WPARAM>(f), TRUE);
    return TRUE;
  }, reinterpret_cast<LPARAM>(font_));
}

RECT MainWindow::BrowserRect() const {
  RECT rc;
  GetClientRect(hwnd_, &rc);
  if (!fullscreen_) rc.top = Scale(32 + 40, dpi_);
  return rc;
}

void MainWindow::Layout() {
  if (!tabs_) return;
  RECT rc;
  GetClientRect(hwnd_, &rc);
  int w = rc.right;
  int tab_h = Scale(32, dpi_);
  int bar_h = Scale(40, dpi_);
  int pad = Scale(6, dpi_);
  int gap = Scale(4, dpi_);
  int btn_h = bar_h - 2 * pad + Scale(2, dpi_);
  int y = tab_h + (bar_h - btn_h) / 2;
  auto mirror = [&](int x, int cw) { return rtl_ ? w - x - cw : x; };
  auto place = [&](HWND h, int x, int cw, int ch, int cy) { MoveWindow(h, mirror(x, cw), cy, cw, ch, TRUE); };

  ShowWindow(tabs_, fullscreen_ ? SW_HIDE : SW_SHOW);
  const int ids[] = {kCmdBack, kCmdForward, kCmdReload, kCmdHome, kCmdNewTab, kCmdBookmark, kCmdMenu, kCmdAddress,
                     kCmdSecurity};
  for (int id : ids) ShowWindow(GetDlgItem(hwnd_, id), fullscreen_ ? SW_HIDE : SW_SHOW);

  if (!fullscreen_) {
    place(tabs_, 0, w, tab_h, 0);
    int x = pad;
    const int left_ids[] = {kCmdBack, kCmdForward, kCmdReload, kCmdHome, kCmdNewTab};
    const int left_w[] = {64, 74, 70, 60, 80};
    for (int i = 0; i < 5; ++i) {
      int bw = Scale(left_w[i], dpi_);
      place(GetDlgItem(hwnd_, left_ids[i]), x, bw, btn_h, y);
      x += bw + gap;
    }
    int menu_w = Scale(64, dpi_);
    int bookmark_w = Scale(118, dpi_);
    int security_w = Scale(86, dpi_);
    int right = w - pad;
    right -= menu_w;
    place(GetDlgItem(hwnd_, kCmdMenu), right, menu_w, btn_h, y);
    right -= gap + bookmark_w;
    place(GetDlgItem(hwnd_, kCmdBookmark), right, bookmark_w, btn_h, y);
    int addr_w = std::max(Scale(80, dpi_), right - gap - x - security_w - gap);
    place(security_, x, security_w, btn_h, y);
    x += security_w + gap;
    place(address_, x, addr_w, btn_h, y);
    UpdateTabSizes();
  }
  RECT br = BrowserRect();
  for (const Tab& t : tabs_list_) {
    HWND h = t.browser ? t.browser->GetHost()->GetWindowHandle() : nullptr;
    if (h) SetWindowPos(h, nullptr, br.left, br.top, br.right - br.left, br.bottom - br.top, SWP_NOZORDER);
  }
}

void MainWindow::UpdateTabSizes() {
  if (tabs_list_.empty()) return;
  RECT rc;
  GetClientRect(tabs_, &rc);
  int count = static_cast<int>(tabs_list_.size());
  int width = std::min(Scale(220, dpi_), std::max(Scale(90, dpi_), (static_cast<int>(rc.right) - Scale(8, dpi_)) / count));
  SendMessageW(tabs_, TCM_SETITEMSIZE, 0, MAKELPARAM(width, Scale(28, dpi_)));
}

MainWindow::Tab* MainWindow::ActiveTab() {
  if (active_ < 0 || active_ >= static_cast<int>(tabs_list_.size())) return nullptr;
  return &tabs_list_[static_cast<size_t>(active_)];
}

int MainWindow::IndexOf(CefRefPtr<CefBrowser> browser) const {
  for (size_t i = 0; i < tabs_list_.size(); ++i) {
    if (tabs_list_[i].browser_id == browser->GetIdentifier()) return static_cast<int>(i);
  }
  return -1;
}

void MainWindow::AddTab(const std::string& url, bool foreground) {
  if (closing_) return;
  std::string target = url.empty() ? Profile::Get().HomeUrl() : url;
  RECT br = BrowserRect();
  CefWindowInfo info;
  info.SetAsChild(hwnd_, CefRect(br.left, br.top, br.right - br.left, br.bottom - br.top));
  info.runtime_style = CEF_RUNTIME_STYLE_ALLOY;
  CefBrowserSettings settings;
  CefRefPtr<CefBrowser> browser = CefBrowserHost::CreateBrowserSync(info, client_, target, settings, nullptr, nullptr);
  if (!browser) return;
  Tab tab;
  tab.browser_id = browser->GetIdentifier();
  tab.browser = browser;
  tab.url = target;
  owned_ids_.insert(tab.browser_id);
  tabs_list_.push_back(tab);
  int index = static_cast<int>(tabs_list_.size()) - 1;
  TCITEMW item{};
  item.mask = TCIF_TEXT;
  item.pszText = const_cast<wchar_t*>(Tr(Str::Untitled));
  TabCtrl_InsertItem(tabs_, index, &item);
  UpdateTabSizes();
  if (foreground || active_ < 0) {
    SwitchTo(index);
  } else {
    HWND h = browser->GetHost()->GetWindowHandle();
    if (h) ShowWindow(h, SW_HIDE);
    browser->GetHost()->WasHidden(true);
  }
}

void MainWindow::SwitchTo(int index) {
  if (index < 0 || index >= static_cast<int>(tabs_list_.size())) return;
  if (active_ >= 0 && active_ < static_cast<int>(tabs_list_.size()) && active_ != index) {
    Tab& old = tabs_list_[static_cast<size_t>(active_)];
    HWND h = old.browser->GetHost()->GetWindowHandle();
    if (h) ShowWindow(h, SW_HIDE);
    old.browser->GetHost()->WasHidden(true);
  }
  active_ = index;
  Tab& t = tabs_list_[static_cast<size_t>(index)];
  RECT br = BrowserRect();
  HWND h = t.browser->GetHost()->GetWindowHandle();
  if (h) {
    SetWindowPos(h, nullptr, br.left, br.top, br.right - br.left, br.bottom - br.top, SWP_NOZORDER | SWP_SHOWWINDOW);
  }
  t.browser->GetHost()->WasHidden(false);
  t.browser->GetHost()->SetFocus(true);
  TabCtrl_SetCurSel(tabs_, index);
  UpdateChrome();
}

void MainWindow::CloseTabAt(int index) {
  if (index < 0 || index >= static_cast<int>(tabs_list_.size())) return;
  CefRefPtr<CefBrowser> browser = tabs_list_[static_cast<size_t>(index)].browser;
  bool was_active = index == active_;
  tabs_list_.erase(tabs_list_.begin() + index);
  TabCtrl_DeleteItem(tabs_, index);
  if (tabs_list_.empty()) {
    active_ = -1;
    browser->GetHost()->CloseBrowser(true);
    CloseWindowRequest();
    return;
  }
  if (index < active_) --active_;
  if (was_active) {
    active_ = -1;
    SwitchTo(std::min(index, static_cast<int>(tabs_list_.size()) - 1));
  }
  browser->GetHost()->CloseBrowser(true);
  UpdateTabSizes();
}

void MainWindow::CloseWindowRequest() { PostMessageW(hwnd_, WM_CLOSE, 0, 0); }

void MainWindow::FinishCloseIfReady() {
  if (closing_ && hwnd_ && !destroying_ && owned_ids_.empty() && client_->PopupCount() == 0) DestroyWindow(hwnd_);
}

void MainWindow::OnBrowserClosed(CefRefPtr<CefBrowser> browser) {
  LogLine("browser closed id=" + std::to_string(browser->GetIdentifier()) + " remaining=" +
          std::to_string(owned_ids_.size() - 1) + " popups=" + std::to_string(client_->PopupCount()));
  owned_ids_.erase(browser->GetIdentifier());
  FinishCloseIfReady();
  MaybeDelete();
}

void MainWindow::UpdateTabLabel(int index) {
  if (index < 0 || index >= static_cast<int>(tabs_list_.size())) return;
  const Tab& t = tabs_list_[static_cast<size_t>(index)];
  std::string label = t.title.empty() ? HostOf(t.url) : t.title;
  std::wstring text = label.empty() ? std::wstring(Tr(Str::Untitled)) : Widen(TruncateTitle(label));
  if (t.loading) text = L"... " + text;
  TCITEMW item{};
  item.mask = TCIF_TEXT;
  item.pszText = text.data();
  TabCtrl_SetItem(tabs_, index, &item);
}

std::wstring MainWindow::AddressText() const {
  int n = GetWindowTextLengthW(address_);
  std::wstring s(static_cast<size_t>(n) + 1, L'\0');
  GetWindowTextW(address_, s.data(), n + 1);
  s.resize(static_cast<size_t>(n));
  return s;
}

void MainWindow::UpdateChrome() {
  Tab* t = ActiveTab();
  if (!t) return;
  EnableWindow(GetDlgItem(hwnd_, kCmdBack), t->can_back);
  EnableWindow(GetDlgItem(hwnd_, kCmdForward), t->can_forward);
  SetWindowTextW(GetDlgItem(hwnd_, kCmdReload), t->loading ? Tr(Str::Stop) : Tr(Str::Reload));
  if (GetFocus() != address_) {
    std::string shown = t->url.rfind("data:", 0) == 0 ? "" : t->url;
    SetWindowTextW(address_, Widen(shown).c_str());
  }
  std::string scheme = SchemeOf(t->url);
  const wchar_t* label = Tr(Str::Local);
  if (scheme == "https") {
    bool secure = true;
    CefRefPtr<CefNavigationEntry> entry = t->browser->GetHost()->GetVisibleNavigationEntry();
    if (entry && entry->GetSSLStatus()) secure = entry->GetSSLStatus()->IsSecureConnection();
    label = secure ? Tr(Str::Secure) : Tr(Str::NotSecure);
  } else if (scheme == "http") {
    label = Tr(Str::NotSecure);
  }
  SetWindowTextW(security_, label);
  SetWindowTextW(GetDlgItem(hwnd_, kCmdBookmark),
                 Profile::Get().bookmarks.Contains(t->url) ? Tr(Str::RemoveBookmark) : Tr(Str::AddBookmark));
  std::string title = t->title.empty() ? HostOf(t->url) : t->title;
  std::wstring window_title = (title.empty() ? std::wstring() : Widen(title) + L" - ") + Tr(Str::AppName);
  SetWindowTextW(hwnd_, window_title.c_str());
}

void MainWindow::OnTitle(CefRefPtr<CefBrowser> browser, const std::string& title) {
  int i = IndexOf(browser);
  if (i < 0) return;
  tabs_list_[static_cast<size_t>(i)].title = title;
  UpdateTabLabel(i);
  if (i == active_) UpdateChrome();
}

void MainWindow::OnAddress(CefRefPtr<CefBrowser> browser, const std::string& url) {
  int i = IndexOf(browser);
  if (i < 0) return;
  Tab& t = tabs_list_[static_cast<size_t>(i)];
  std::string origin = OriginOf(url);
  if (origin != t.origin) {
    Profile::Get().permissions.ClearScope(static_cast<uint64_t>(t.browser_id));
    t.origin = origin;
  }
  t.url = url;
  if (i == active_) UpdateChrome();
}

void MainWindow::OnLoadingState(CefRefPtr<CefBrowser> browser, bool loading, bool can_back, bool can_forward) {
  int i = IndexOf(browser);
  if (i < 0) return;
  Tab& t = tabs_list_[static_cast<size_t>(i)];
  t.loading = loading;
  t.can_back = can_back;
  t.can_forward = can_forward;
  UpdateTabLabel(i);
  if (i == active_) UpdateChrome();
}

void MainWindow::OnLoadStarted(CefRefPtr<CefBrowser> browser) {
  int i = IndexOf(browser);
  if (i < 0) return;
  ApplyUserAgent(browser, browser->GetMainFrame()->GetURL().ToString());
}

void MainWindow::OnLoadFinished(CefRefPtr<CefBrowser> browser) {
  int i = IndexOf(browser);
  if (i < 0) return;
  std::string url = browser->GetMainFrame()->GetURL().ToString();
  Tab& t = tabs_list_[static_cast<size_t>(i)];
  if (IsWebScheme(SchemeOf(url))) {
    Profile::Get().history.Add(url, t.title, NowSeconds());
    Profile::Get().SaveHistory();
    std::string css = Profile::Get().SiteCss(HostOf(url));
    if (!css.empty()) InjectSiteCss(browser, css);
  }
  if (i == active_) UpdateChrome();
}

void MainWindow::ApplyUserAgent(CefRefPtr<CefBrowser> browser, const std::string& url) {
  Profile& p = Profile::Get();
  UaMode mode = IsWebScheme(SchemeOf(url)) ? p.UaForHost(HostOf(url)) : UaMode::kDefault;
  std::string ua;
  bool mobile = false;
  switch (mode) {
    case UaMode::kDesktop:
      ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" +
           MajorVersion() + ".0.0.0 Safari/537.36";
      break;
    case UaMode::kMobile:
      ua = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" +
           MajorVersion() + ".0.0.0 Mobile Safari/537.36";
      mobile = true;
      break;
    case UaMode::kCustom: ua = p.CustomUa(); break;
    default: break;
  }
  CefRefPtr<CefBrowserHost> host = browser->GetHost();
  CefRefPtr<CefDictionaryValue> ua_params = CefDictionaryValue::Create();
  ua_params->SetString("userAgent", ua);
  host->ExecuteDevToolsMethod(0, "Emulation.setUserAgentOverride", ua_params);
  if (mobile) {
    CefRefPtr<CefDictionaryValue> metrics = CefDictionaryValue::Create();
    metrics->SetInt("width", 0);
    metrics->SetInt("height", 0);
    metrics->SetDouble("deviceScaleFactor", 0);
    metrics->SetBool("mobile", true);
    host->ExecuteDevToolsMethod(0, "Emulation.setDeviceMetricsOverride", metrics);
  } else {
    host->ExecuteDevToolsMethod(0, "Emulation.clearDeviceMetricsOverride", nullptr);
  }
}

void MainWindow::InjectSiteCss(CefRefPtr<CefBrowser> browser, const std::string& css) {
  std::string js =
      "(function(){var id='__materialex_site_css';var e=document.getElementById(id);"
      "if(!e){e=document.createElement('style');e.id=id;(document.head||document.documentElement).appendChild(e);}"
      "e.textContent=" +
      JsonQuote(css) + ";})();";
  browser->GetMainFrame()->ExecuteJavaScript(js, "", 0);
}

void MainWindow::SetSiteUa(int menu_id) {
  Tab* t = ActiveTab();
  if (!t) return;
  std::string host = HostOf(t->url);
  if (host.empty() || !IsWebScheme(SchemeOf(t->url))) return;
  Profile& p = Profile::Get();
  switch (menu_id) {
    case kCmdUaGlobal: p.SetSiteUa(host, true, UaMode::kDefault); break;
    case kCmdUaDefault: p.SetSiteUa(host, false, UaMode::kDefault); break;
    case kCmdUaDesktop: p.SetSiteUa(host, false, UaMode::kDesktop); break;
    case kCmdUaMobile: p.SetSiteUa(host, false, UaMode::kMobile); break;
    case kCmdUaCustom: p.SetSiteUa(host, false, UaMode::kCustom); break;
  }
  ApplyUserAgent(t->browser, t->url);
  t->browser->Reload();
}

void MainWindow::Zoom(double delta, bool reset) {
  Tab* t = ActiveTab();
  if (!t) return;
  CefRefPtr<CefBrowserHost> host = t->browser->GetHost();
  double level = reset ? 0.0 : std::clamp(host->GetZoomLevel() + delta, -4.0, 8.0);
  host->SetZoomLevel(level);
}

void MainWindow::ToggleBookmark() {
  Tab* t = ActiveTab();
  if (!t || !IsWebScheme(SchemeOf(t->url))) return;
  Profile::Get().bookmarks.Toggle(t->url, t->title);
  Profile::Get().SaveBookmarks();
  UpdateChrome();
}

void MainWindow::ShowDevTools(CefRefPtr<CefBrowser> browser, const CefPoint& inspect_at) {
  CefWindowInfo info;
  info.SetAsPopup(nullptr, "Developer tools");
  browser->GetHost()->ShowDevTools(info, nullptr, CefBrowserSettings(), inspect_at);
}

void MainWindow::OpenSiteEditor() {
  Tab* t = ActiveTab();
  if (!t || !IsWebScheme(SchemeOf(t->url))) return;
  std::string host = HostOf(t->url);
  CefRefPtr<CefBrowser> browser = t->browser;
  ShowSiteEditorPanel(
      hwnd_, host,
      [this, browser, host](const std::string& css) {
        if (browser->GetMainFrame() && HostOf(browser->GetMainFrame()->GetURL().ToString()) == host) {
          InjectSiteCss(browser, css);
        }
      },
      [this, browser]() { ShowDevTools(browser, CefPoint()); });
}

void MainWindow::SetFullscreen(bool on) {
  if (on == fullscreen_) return;
  if (on) {
    saved_placement_.length = sizeof(saved_placement_);
    GetWindowPlacement(hwnd_, &saved_placement_);
    saved_style_ = GetWindowLongPtrW(hwnd_, GWL_STYLE);
    MONITORINFO mi{sizeof(mi)};
    GetMonitorInfoW(MonitorFromWindow(hwnd_, MONITOR_DEFAULTTONEAREST), &mi);
    fullscreen_ = true;
    SetWindowLongPtrW(hwnd_, GWL_STYLE, (saved_style_ & ~WS_OVERLAPPEDWINDOW) | WS_POPUP);
    SetWindowPos(hwnd_, HWND_TOP, mi.rcMonitor.left, mi.rcMonitor.top, mi.rcMonitor.right - mi.rcMonitor.left,
                 mi.rcMonitor.bottom - mi.rcMonitor.top, SWP_FRAMECHANGED | SWP_NOOWNERZORDER);
  } else {
    fullscreen_ = false;
    SetWindowLongPtrW(hwnd_, GWL_STYLE, saved_style_);
    SetWindowPlacement(hwnd_, &saved_placement_);
    SetWindowPos(hwnd_, nullptr, 0, 0, 0, 0,
                 SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOOWNERZORDER | SWP_FRAMECHANGED);
  }
  Layout();
}

void MainWindow::OnFullscreen(CefRefPtr<CefBrowser>, bool fullscreen) { SetFullscreen(fullscreen); }

void MainWindow::Navigate(const std::wstring& text) {
  Tab* t = ActiveTab();
  if (!t) return;
  std::string url = NormalizeInput(Narrow(text), Profile::Get().SearchTemplate());
  if (url.empty()) return;
  t->browser->GetMainFrame()->LoadURL(url);
  t->browser->GetHost()->SetFocus(true);
}

void MainWindow::FocusAddress() {
  SetFocus(address_);
  SendMessageW(address_, EM_SETSEL, 0, -1);
}

bool MainWindow::HandleShortcut(const CefKeyEvent& e) {
  bool ctrl = e.modifiers & EVENTFLAG_CONTROL_DOWN;
  bool alt = e.modifiers & EVENTFLAG_ALT_DOWN;
  bool shift = e.modifiers & EVENTFLAG_SHIFT_DOWN;
  int key = e.windows_key_code;
  int cmd = 0;
  if (ctrl && !alt) {
    switch (key) {
      case 'T': cmd = kCmdNewTab; break;
      case 'W': cmd = kCmdCloseTab; break;
      case 'L': cmd = kCmdFocusAddress; break;
      case 'R': cmd = kCmdReload; break;
      case 'D': cmd = kCmdBookmark; break;
      case 'H': cmd = kCmdHistory; break;
      case 'J': cmd = kCmdDownloads; break;
      case 'N': cmd = kCmdNewWindow; break;
      case VK_TAB: cmd = shift ? kCmdPrevTab : kCmdNextTab; break;
      case VK_OEM_PLUS: cmd = kCmdZoomIn; break;
      case VK_OEM_MINUS: cmd = kCmdZoomOut; break;
      case '0': cmd = kCmdZoomReset; break;
    }
  } else if (alt && !ctrl) {
    if (key == VK_LEFT) cmd = kCmdBack;
    if (key == VK_RIGHT) cmd = kCmdForward;
  } else {
    if (key == VK_F5) cmd = kCmdReload;
    if (key == VK_F11) cmd = kCmdFullscreen;
    if (key == VK_F12) cmd = kCmdDevTools;
  }
  if (!cmd) return false;
  PostMessageW(hwnd_, kMsgCommand, static_cast<WPARAM>(cmd), 0);
  return true;
}

void MainWindow::OnCommand(int id) {
  Tab* t = ActiveTab();
  switch (id) {
    case kCmdBack:
      if (t) t->browser->GoBack();
      break;
    case kCmdForward:
      if (t) t->browser->GoForward();
      break;
    case kCmdReload:
      if (t) {
        if (t->loading) t->browser->StopLoad();
        else t->browser->Reload();
      }
      break;
    case kCmdHome: Navigate(Widen(Profile::Get().HomeUrl())); break;
    case kCmdNewTab: AddTab("", true); FocusAddress(); break;
    case kCmdBookmark: ToggleBookmark(); break;
    case kCmdMenu: ShowMainMenu(); break;
    case kCmdNewWindow: MainWindow::Create(""); break;
    case kCmdCloseTab: CloseTabAt(active_); break;
    case kCmdCloseOthers:
      for (int i = static_cast<int>(tabs_list_.size()) - 1; i >= 0; --i) {
        if (i != active_) CloseTabAt(i);
      }
      break;
    case kCmdBookmarks:
      ShowBookmarksPanel(hwnd_, [this](const std::string& u) { AddTab(u, true); });
      break;
    case kCmdHistory:
      ShowHistoryPanel(hwnd_, [this](const std::string& u) { AddTab(u, true); });
      break;
    case kCmdDownloads:
      ShowDownloadsPanel(hwnd_, [this](const std::string& u) {
        Tab* a = ActiveTab();
        if (a) a->browser->GetHost()->StartDownload(u);
      });
      break;
    case kCmdSiteEditor: OpenSiteEditor(); break;
    case kCmdSettings: ShowSettingsPanel(hwnd_); break;
    case kCmdDevTools:
      if (t) ShowDevTools(t->browser, CefPoint());
      break;
    case kCmdFullscreen: SetFullscreen(!fullscreen_); break;
    case kCmdExit: PostMessageW(hwnd_, WM_CLOSE, 0, 0); break;
    case kCmdFocusAddress: if (!fullscreen_) FocusAddress(); break;
    case kCmdNextTab:
      if (!tabs_list_.empty()) SwitchTo((active_ + 1) % static_cast<int>(tabs_list_.size()));
      break;
    case kCmdPrevTab:
      if (!tabs_list_.empty()) SwitchTo((active_ + static_cast<int>(tabs_list_.size()) - 1) % static_cast<int>(tabs_list_.size()));
      break;
    case kCmdZoomIn: Zoom(0.5, false); break;
    case kCmdZoomOut: Zoom(-0.5, false); break;
    case kCmdZoomReset: Zoom(0, true); break;
    case kCmdUaGlobal:
    case kCmdUaDefault:
    case kCmdUaDesktop:
    case kCmdUaMobile:
    case kCmdUaCustom: SetSiteUa(id); break;
  }
}

void MainWindow::ShowMainMenu() {
  Tab* t = ActiveTab();
  HMENU menu = CreatePopupMenu();
  HMENU ua = CreatePopupMenu();
  std::string host = t ? HostOf(t->url) : "";
  bool web = t && IsWebScheme(SchemeOf(t->url));
  bool has_site = web && Profile::Get().HasSiteUa(host);
  UaMode mode = web ? Profile::Get().UaForHost(host) : UaMode::kDefault;
  AppendMenuW(ua, MF_STRING | (has_site ? 0 : MF_CHECKED), kCmdUaGlobal, Tr(Str::UaUseGlobal));
  AppendMenuW(ua, MF_SEPARATOR, 0, nullptr);
  struct M {
    int id;
    Str label;
    UaMode mode;
  };
  const M modes[] = {{kCmdUaDefault, Str::UaDefault, UaMode::kDefault},
                     {kCmdUaDesktop, Str::UaDesktop, UaMode::kDesktop},
                     {kCmdUaMobile, Str::UaMobile, UaMode::kMobile},
                     {kCmdUaCustom, Str::UaCustom, UaMode::kCustom}};
  for (const M& m : modes) {
    AppendMenuW(ua, MF_STRING | (has_site && mode == m.mode ? MF_CHECKED : 0), m.id, Tr(m.label));
  }
  AppendMenuW(menu, MF_STRING, kCmdNewWindow, Tr(Str::NewWindow));
  AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
  AppendMenuW(menu, MF_STRING, kCmdBookmarks, Tr(Str::Bookmarks));
  AppendMenuW(menu, MF_STRING, kCmdHistory, Tr(Str::History));
  AppendMenuW(menu, MF_STRING, kCmdDownloads, Tr(Str::Downloads));
  AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
  AppendMenuW(menu, MF_STRING | (web ? 0 : MF_GRAYED), kCmdSiteEditor, Tr(Str::SiteEditor));
  AppendMenuW(menu, MF_POPUP | (web ? 0 : MF_GRAYED), reinterpret_cast<UINT_PTR>(ua), Tr(Str::UserAgent));
  AppendMenuW(menu, MF_STRING, kCmdDevTools, Tr(Str::DevTools));
  AppendMenuW(menu, MF_STRING | (fullscreen_ ? MF_CHECKED : 0), kCmdFullscreen, Tr(Str::Fullscreen));
  AppendMenuW(menu, MF_SEPARATOR, 0, nullptr);
  AppendMenuW(menu, MF_STRING, kCmdSettings, Tr(Str::Settings));
  AppendMenuW(menu, MF_STRING, kCmdExit, Tr(Str::Exit));
  RECT r;
  GetWindowRect(GetDlgItem(hwnd_, kCmdMenu), &r);
  UINT flags = TPM_RETURNCMD | TPM_RIGHTBUTTON | (rtl_ ? TPM_LEFTALIGN : TPM_RIGHTALIGN);
  int x = rtl_ ? r.left : r.right;
  int cmd = TrackPopupMenu(menu, flags, x, r.bottom, 0, hwnd_, nullptr);
  DestroyMenu(menu);
  if (cmd) OnCommand(cmd);
}

void MainWindow::ShowTabMenu(int index, POINT screen) {
  HMENU menu = CreatePopupMenu();
  AppendMenuW(menu, MF_STRING, kCmdNewTab, Tr(Str::NewTab));
  AppendMenuW(menu, MF_STRING, kCmdCloseTab, Tr(Str::CloseTab));
  AppendMenuW(menu, MF_STRING | (tabs_list_.size() > 1 ? 0 : MF_GRAYED), kCmdCloseOthers, Tr(Str::CloseOtherTabs));
  UINT flags = TPM_RETURNCMD | TPM_RIGHTBUTTON | (rtl_ ? TPM_RIGHTALIGN : TPM_LEFTALIGN);
  int cmd = TrackPopupMenu(menu, flags, screen.x, screen.y, 0, hwnd_, nullptr);
  DestroyMenu(menu);
  if (!cmd) return;
  if (index >= 0 && index != active_) SwitchTo(index);
  OnCommand(cmd);
}

void MainWindow::SavePlacement() {
  if (fullscreen_) return;
  WINDOWPLACEMENT wp{sizeof(wp)};
  if (!GetWindowPlacement(hwnd_, &wp)) return;
  Profile& p = Profile::Get();
  p.settings.Set("win.left", std::to_string(wp.rcNormalPosition.left));
  p.settings.Set("win.top", std::to_string(wp.rcNormalPosition.top));
  p.settings.Set("win.right", std::to_string(wp.rcNormalPosition.right));
  p.settings.Set("win.bottom", std::to_string(wp.rcNormalPosition.bottom));
  p.settings.Set("win.max", wp.showCmd == SW_SHOWMAXIMIZED ? "1" : "0");
  p.SaveSettings();
}

void MainWindow::RestorePlacement() {
  Profile& p = Profile::Get();
  saved_placement_.length = sizeof(saved_placement_);
  saved_placement_.showCmd = SW_SHOWNORMAL;
  if (p.settings.Get("win.right").empty()) return;
  RECT r{std::atoi(p.settings.Get("win.left").c_str()), std::atoi(p.settings.Get("win.top").c_str()),
         std::atoi(p.settings.Get("win.right").c_str()), std::atoi(p.settings.Get("win.bottom").c_str())};
  if (r.right - r.left < 300 || r.bottom - r.top < 200) return;
  HMONITOR mon = MonitorFromRect(&r, MONITOR_DEFAULTTONULL);
  if (!mon) return;
  WINDOWPLACEMENT wp{sizeof(wp)};
  wp.showCmd = p.settings.Get("win.max") == "1" ? SW_SHOWMAXIMIZED : SW_SHOWNORMAL;
  wp.rcNormalPosition = r;
  SetWindowPlacement(hwnd_, &wp);
  saved_placement_.showCmd = wp.showCmd;
}

LRESULT CALLBACK MainWindow::AddressProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp, UINT_PTR, DWORD_PTR ref) {
  MainWindow* self = reinterpret_cast<MainWindow*>(ref);
  switch (msg) {
    case WM_KEYDOWN:
      if (wp == VK_RETURN) {
        self->Navigate(self->AddressText());
        return 0;
      }
      if (wp == VK_ESCAPE) {
        self->UpdateChrome();
        SetWindowTextW(hwnd, Widen(self->ActiveTab() ? self->ActiveTab()->url : "").c_str());
        if (self->ActiveTab()) self->ActiveTab()->browser->GetHost()->SetFocus(true);
        return 0;
      }
      break;
    case WM_CHAR:
      if (wp == VK_RETURN || wp == VK_ESCAPE) return 0;
      break;
    case WM_NCDESTROY: RemoveWindowSubclass(hwnd, AddressProc, 2); break;
  }
  return DefSubclassProc(hwnd, msg, wp, lp);
}

LRESULT CALLBACK MainWindow::TabsProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp, UINT_PTR, DWORD_PTR ref) {
  MainWindow* self = reinterpret_cast<MainWindow*>(ref);
  switch (msg) {
    case WM_MBUTTONUP: {
      TCHITTESTINFO hit{{GET_X_LPARAM(lp), GET_Y_LPARAM(lp)}, 0};
      int i = TabCtrl_HitTest(hwnd, &hit);
      if (i >= 0) self->CloseTabAt(i);
      return 0;
    }
    case WM_RBUTTONUP: {
      POINT client{GET_X_LPARAM(lp), GET_Y_LPARAM(lp)};
      TCHITTESTINFO hit{client, 0};
      int i = TabCtrl_HitTest(hwnd, &hit);
      POINT screen = client;
      ClientToScreen(hwnd, &screen);
      self->ShowTabMenu(i, screen);
      return 0;
    }
    case WM_NCDESTROY: RemoveWindowSubclass(hwnd, TabsProc, 1); break;
  }
  return DefSubclassProc(hwnd, msg, wp, lp);
}

LRESULT CALLBACK MainWindow::WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
  MainWindow* self = reinterpret_cast<MainWindow*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
  if (msg == WM_NCCREATE) {
    auto* cs = reinterpret_cast<CREATESTRUCTW*>(lp);
    self = static_cast<MainWindow*>(cs->lpCreateParams);
    self->hwnd_ = hwnd;
    SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(self));
  }
  if (!self) return DefWindowProcW(hwnd, msg, wp, lp);
  return self->HandleMessage(msg, wp, lp);
}

LRESULT MainWindow::HandleMessage(UINT msg, WPARAM wp, LPARAM lp) {
  switch (msg) {
    case WM_CREATE:
      dpi_ = GetDpiForWindow(hwnd_);
      CreateControls();
      return 0;
    case WM_SIZE:
      Layout();
      return 0;
    case WM_DPICHANGED: {
      dpi_ = HIWORD(wp);
      auto* r = reinterpret_cast<RECT*>(lp);
      SetWindowPos(hwnd_, nullptr, r->left, r->top, r->right - r->left, r->bottom - r->top, SWP_NOZORDER | SWP_NOACTIVATE);
      HFONT nf = CreateUiFont(dpi_);
      EnumChildWindows(hwnd_, [](HWND c, LPARAM f) -> BOOL {
        SendMessageW(c, WM_SETFONT, static_cast<WPARAM>(f), TRUE);
        return TRUE;
      }, reinterpret_cast<LPARAM>(nf));
      if (font_) DeleteObject(font_);
      font_ = nf;
      Layout();
      return 0;
    }
    case WM_GETMINMAXINFO: {
      auto* mmi = reinterpret_cast<MINMAXINFO*>(lp);
      mmi->ptMinTrackSize.x = Scale(480, dpi_);
      mmi->ptMinTrackSize.y = Scale(320, dpi_);
      return 0;
    }
    case WM_SETFOCUS: {
      Tab* t = ActiveTab();
      if (t) t->browser->GetHost()->SetFocus(true);
      return 0;
    }
    case WM_ACTIVATE:
      if (LOWORD(wp) != WA_INACTIVE) {
        Tab* t = ActiveTab();
        if (t && GetFocus() != address_) t->browser->GetHost()->SetFocus(true);
      }
      return 0;
    case WM_COMMAND:
      if (LOWORD(wp) != kCmdAddress) OnCommand(LOWORD(wp));
      return 0;
    case kMsgCommand: OnCommand(static_cast<int>(wp)); return 0;
    case WM_NOTIFY: {
      auto* hdr = reinterpret_cast<NMHDR*>(lp);
      if (hdr->hwndFrom == tabs_ && hdr->code == TCN_SELCHANGE) SwitchTo(TabCtrl_GetCurSel(tabs_));
      return 0;
    }
    case WM_ERASEBKGND:
      return 1;
    case WM_PAINT: {
      PAINTSTRUCT ps;
      HDC dc = BeginPaint(hwnd_, &ps);
      RECT rc;
      GetClientRect(hwnd_, &rc);
      FillRect(dc, &rc, GetSysColorBrush(COLOR_BTNFACE));
      EndPaint(hwnd_, &ps);
      return 0;
    }
    case WM_CLOSE: {
      if (closing_) {
        LogLine("WM_CLOSE while closing, destroying");
        if (!destroying_) DestroyWindow(hwnd_);
        return 0;
      }
      LogLine("WM_CLOSE");
      closing_ = true;
      SavePlacement();
      Profile::Get().SaveHistory();
      std::vector<Tab> tabs;
      tabs.swap(tabs_list_);
      active_ = -1;
      LogLine("closing tabs=" + std::to_string(tabs.size()) + " owned=" + std::to_string(owned_ids_.size()) +
              " popups=" + std::to_string(client_->PopupCount()));
      for (Tab& t : tabs) {
        t.browser->GetHost()->CloseBrowser(true);
        LogLine("CloseBrowser returned");
      }
      client_->ClosePopups();
      FinishCloseIfReady();
      return 0;
    }
    case WM_DESTROY: {
      LogLine("WM_DESTROY");
      destroying_ = true;
      auto& w = Windows();
      w.erase(std::remove(w.begin(), w.end(), this), w.end());
      if (font_) {
        DeleteObject(font_);
        font_ = nullptr;
      }
      return 0;
    }
    case WM_NCDESTROY:
      SetWindowLongPtrW(hwnd_, GWLP_USERDATA, 0);
      hwnd_ = nullptr;
      destroyed_ = true;
      MaybeDelete();
      return 0;
  }
  return DefWindowProcW(hwnd_, msg, wp, lp);
}

}  // namespace mex

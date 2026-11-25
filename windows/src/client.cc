#include "client.h"

#include <algorithm>

#include "core/filename.h"
#include "core/url_utils.h"
#include "downloads.h"
#include "include/cef_parser.h"
#include "include/wrapper/cef_helpers.h"
#include "permissions.h"
#include "platform.h"
#include "profile.h"
#include "strings.h"

namespace mex {

namespace {

constexpr int kMenuOpenLinkInTab = MENU_ID_USER_FIRST + 1;
constexpr int kMenuInspect = MENU_ID_USER_FIRST + 2;
constexpr size_t kMaxPopupsPerWindow = 6;

std::wstring Format(const wchar_t* fmt, const std::wstring& a, const std::wstring& b = L"",
                    const std::wstring& c = L"") {
  std::wstring out(2048, L'\0');
  int n = swprintf(out.data(), out.size(), fmt, a.c_str(), b.c_str(), c.c_str());
  out.resize(n > 0 ? static_cast<size_t>(n) : 0);
  return out;
}

bool Ask(HWND parent, const std::wstring& text, const wchar_t* title) {
  return MessageBoxW(parent, text.c_str(), title, MB_YESNO | MB_ICONWARNING | MB_DEFBUTTON2 | MB_APPLMODAL) == IDYES;
}

}  // namespace

bool Client::AllowPopupNow() {
  int64_t now = NowSeconds();
  popup_times_.erase(std::remove_if(popup_times_.begin(), popup_times_.end(),
                                    [now](int64_t t) { return now - t > 10; }),
                     popup_times_.end());
  if (popup_times_.size() >= 3 || popups_.size() >= kMaxPopupsPerWindow) return false;
  popup_times_.push_back(now);
  return true;
}

bool Client::OnBeforePopup(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame>, int, const CefString& target_url,
                           const CefString&, CefLifeSpanHandler::WindowOpenDisposition target_disposition,
                           bool user_gesture, const CefPopupFeatures&, CefWindowInfo&, CefRefPtr<CefClient>&,
                           CefBrowserSettings&, CefRefPtr<CefDictionaryValue>&, bool* no_javascript_access) {
  CEF_REQUIRE_UI_THREAD();
  std::string url = target_url.ToString();
  std::string scheme = SchemeOf(url);
  if (!IsWebScheme(scheme) && url != "about:blank") return true;

  if (!user_gesture || !AllowPopupNow()) {
    MessageBeep(MB_ICONWARNING);
    return true;
  }
  bool as_tab = target_disposition == CEF_WOD_NEW_FOREGROUND_TAB || target_disposition == CEF_WOD_NEW_BACKGROUND_TAB;
  if (as_tab && Owned(browser)) {
    delegate_->OpenInNewTab(url, target_disposition == CEF_WOD_NEW_FOREGROUND_TAB);
    return true;
  }
  *no_javascript_access = false;
  return false;
}

bool Client::DoClose(CefRefPtr<CefBrowser> browser) {
  LogLine("DoClose id=" + std::to_string(browser->GetIdentifier()));
  return false;
}

void Client::OnAfterCreated(CefRefPtr<CefBrowser> browser) {
  CEF_REQUIRE_UI_THREAD();
  LogLine("AfterCreated id=" + std::to_string(browser->GetIdentifier()) + " popup=" +
          std::to_string(browser->IsPopup()));
  if (browser->IsPopup() && !Owned(browser)) popups_.push_back(browser);
}

void Client::ClosePopups() {
  std::vector<CefRefPtr<CefBrowser>> copy = popups_;
  for (const auto& b : copy) b->GetHost()->CloseBrowser(true);
}

void Client::OnBeforeClose(CefRefPtr<CefBrowser> browser) {
  CEF_REQUIRE_UI_THREAD();
  LogLine("BeforeClose id=" + std::to_string(browser->GetIdentifier()));
  delegate_->Permissions().ClearScope(static_cast<uint64_t>(browser->GetIdentifier()));
  popups_.erase(std::remove_if(popups_.begin(), popups_.end(),
                               [&](const CefRefPtr<CefBrowser>& b) { return b->IsSame(browser); }),
                popups_.end());
  delegate_->OnBrowserClosed(browser);
}

void Client::OnTitleChange(CefRefPtr<CefBrowser> browser, const CefString& title) {
  if (Owned(browser)) delegate_->OnTitle(browser, title.ToString());
}

void Client::OnAddressChange(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, const CefString& url) {
  if (frame->IsMain() && Owned(browser)) delegate_->OnAddress(browser, url.ToString());
}

void Client::OnFullscreenModeChange(CefRefPtr<CefBrowser> browser, bool fullscreen) {
  if (Owned(browser)) delegate_->OnFullscreen(browser, fullscreen);
}

void Client::OnLoadingStateChange(CefRefPtr<CefBrowser> browser, bool is_loading, bool can_go_back,
                                  bool can_go_forward) {
  if (Owned(browser)) delegate_->OnLoadingState(browser, is_loading, can_go_back, can_go_forward);
}

void Client::OnLoadStart(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, TransitionType) {
  if (frame->IsMain() && Owned(browser)) delegate_->OnLoadStarted(browser);
}

void Client::OnLoadEnd(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, int) {
  if (frame->IsMain() && Owned(browser)) delegate_->OnLoadFinished(browser);
}

void Client::OnLoadError(CefRefPtr<CefBrowser>, CefRefPtr<CefFrame> frame, ErrorCode error_code,
                         const CefString& error_text, const CefString& failed_url) {
  if (error_code == ERR_ABORTED || !frame->IsMain()) return;
  std::string html =
      "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"color-scheme\" content=\"light dark\">"
      "<title>" +
      Narrow(Tr(Str::ErrorTitle)) +
      "</title><style>body{font:16px Segoe UI,sans-serif;margin:12vh auto;max-width:36rem;padding:0 1.5rem}"
      "h1{font-size:1.5rem;font-weight:600}p{color:GrayText;word-break:break-all}</style></head><body><h1>" +
      Narrow(Tr(Str::ErrorTitle)) + "</h1><p>" + EscapeHtml(failed_url.ToString()) + "</p><p>" +
      EscapeHtml(Narrow(Format(Tr(Str::ErrorDetail), error_text.ToWString(), std::to_wstring(error_code)))) +
      "</p></body></html>";
  frame->LoadURL("data:text/html;charset=utf-8," + CefURIEncode(html, false).ToString());
}

bool Client::OnBeforeDownload(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDownloadItem> item,
                              const CefString& suggested_name, CefRefPtr<CefBeforeDownloadCallback> callback) {
  CEF_REQUIRE_UI_THREAD();
  DownloadManager& manager = DownloadManager::Get();
  std::string url = item->GetURL().ToString();
  std::string name = SanitizeFilename(suggested_name.ToString(), "download");
  std::string mime = item->GetMimeType().ToString();

  if (!manager.AllowNewDownload()) {
    manager.AddBlocked(url, name, Narrow(Tr(Str::DownloadBlockedMany)), delegate_->IsPrivate());
    return true;
  }
  bool dangerous = IsDangerousExtension(name);
  bool mismatch = HasMimeMismatch(name, mime);
  if (dangerous || mismatch) {
    std::wstring source = Widen(HostOf(url).empty() ? url : HostOf(url));
    std::wstring text = mismatch ? Format(Tr(Str::MimeMismatchBody), Widen(name), Widen(mime), source)
                                 : Format(Tr(Str::DangerBody), Widen(name), source);
    if (!Ask(delegate_->Handle(), text, Tr(Str::DangerTitle))) {
      manager.AddBlocked(url, name, "user declined", delegate_->IsPrivate());
      return true;
    }
  }
  std::wstring dir = DownloadsDir();
  std::string unique = UniqueFilename(name, [&](const std::string& candidate) {
    return FileExists(dir + L"\\" + Widen(candidate));
  });
  callback->Continue(dir + L"\\" + Widen(unique), false);
  return true;
}

void Client::OnDownloadUpdated(CefRefPtr<CefBrowser>, CefRefPtr<CefDownloadItem> item,
                               CefRefPtr<CefDownloadItemCallback> callback) {
  CEF_REQUIRE_UI_THREAD();
  DownloadManager::Get().Update(item, callback, delegate_->IsPrivate());
}

namespace {

std::vector<PermissionBit> PromptBits(uint32_t requested) {
  struct Map {
    uint32_t mask;
    const char* name;
  };
  static const Map map[] = {
      {CEF_PERMISSION_TYPE_CAMERA_STREAM, "camera"},
      {CEF_PERMISSION_TYPE_MIC_STREAM, "microphone"},
      {CEF_PERMISSION_TYPE_GEOLOCATION, "location"},
      {CEF_PERMISSION_TYPE_NOTIFICATIONS, "notifications"},
      {CEF_PERMISSION_TYPE_CLIPBOARD, "clipboard"},
      {CEF_PERMISSION_TYPE_MIDI_SYSEX, "midi"},
      {CEF_PERMISSION_TYPE_MULTIPLE_DOWNLOADS, "multiple-downloads"},
      {CEF_PERMISSION_TYPE_LOCAL_FONTS, "local-fonts"},
  };
  std::vector<PermissionBit> bits;
  for (const Map& m : map) {
    if (requested & m.mask) bits.push_back({m.mask, m.name});
  }
  return bits;
}

}  // namespace

bool Client::OnShowPermissionPrompt(CefRefPtr<CefBrowser> browser, uint64_t, const CefString& requesting_origin,
                                    uint32_t requested_permissions,
                                    CefRefPtr<CefPermissionPromptCallback> callback) {
  CEF_REQUIRE_UI_THREAD();
  std::vector<PermissionBit> bits = PromptBits(requested_permissions);
  uint32_t known = 0;
  for (const PermissionBit& b : bits) known |= b.mask;
  PermissionOutcome outcome = ResolvePermissions(delegate_->Handle(), OriginOf(requesting_origin.ToString()), bits,
                                                 static_cast<uint64_t>(browser->GetIdentifier()),
                                                 delegate_->Permissions(), delegate_->PermissionsChanged());
  bool all_allowed = known == requested_permissions && known != 0 && outcome.allowed_mask == known;
  if (all_allowed) {
    std::vector<std::string> names;
    for (const PermissionBit& b : bits) names.push_back(b.name);
    delegate_->RecordPermissionUse(browser->GetIdentifier(), OriginOf(requesting_origin.ToString()), names);
  }
  callback->Continue(all_allowed ? CEF_PERMISSION_RESULT_ACCEPT
                     : outcome.dismissed ? CEF_PERMISSION_RESULT_DISMISS
                                         : CEF_PERMISSION_RESULT_DENY);
  return true;
}

bool Client::OnRequestMediaAccessPermission(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame>,
                                            const CefString& requesting_url, uint32_t requested_permissions,
                                            CefRefPtr<CefMediaAccessCallback> callback) {
  CEF_REQUIRE_UI_THREAD();
  std::vector<PermissionBit> bits;
  if (requested_permissions & CEF_MEDIA_PERMISSION_DEVICE_AUDIO_CAPTURE) {
    bits.push_back({CEF_MEDIA_PERMISSION_DEVICE_AUDIO_CAPTURE, "microphone"});
  }
  if (requested_permissions & CEF_MEDIA_PERMISSION_DEVICE_VIDEO_CAPTURE) {
    bits.push_back({CEF_MEDIA_PERMISSION_DEVICE_VIDEO_CAPTURE, "camera"});
  }
  if (requested_permissions & (CEF_MEDIA_PERMISSION_DESKTOP_AUDIO_CAPTURE | CEF_MEDIA_PERMISSION_DESKTOP_VIDEO_CAPTURE)) {
    bits.push_back({CEF_MEDIA_PERMISSION_DESKTOP_AUDIO_CAPTURE | CEF_MEDIA_PERMISSION_DESKTOP_VIDEO_CAPTURE,
                    "screen-capture"});
  }
  PermissionOutcome outcome = ResolvePermissions(delegate_->Handle(), OriginOf(requesting_url.ToString()), bits,
                                                 static_cast<uint64_t>(browser->GetIdentifier()),
                                                 delegate_->Permissions(), delegate_->PermissionsChanged());
  uint32_t allowed = 0;
  for (const PermissionBit& b : bits) {
    if (outcome.allowed_mask & b.mask) allowed |= b.mask;
  }
  allowed &= requested_permissions;
  std::vector<std::string> granted;
  for (const PermissionBit& b : bits) {
    if (allowed & b.mask) granted.push_back(b.name);
  }
  if (!granted.empty()) {
    delegate_->RecordPermissionUse(browser->GetIdentifier(), OriginOf(requesting_url.ToString()), granted);
  }
  if (allowed == 0) {
    callback->Cancel();
  } else {
    callback->Continue(allowed);
  }
  return true;
}

void Client::OnBeforeContextMenu(CefRefPtr<CefBrowser>, CefRefPtr<CefFrame>, CefRefPtr<CefContextMenuParams> params,
                                 CefRefPtr<CefMenuModel> model) {
  if (!params->GetLinkUrl().empty()) {
    model->InsertItemAt(0, kMenuOpenLinkInTab, Tr(Str::OpenLinkInNewTab));
    model->InsertSeparatorAt(1);
  }
  model->AddSeparator();
  model->AddItem(kMenuInspect, Tr(Str::Inspect));
}

bool Client::OnContextMenuCommand(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame>,
                                  CefRefPtr<CefContextMenuParams> params, int command_id, EventFlags) {
  if (command_id == kMenuOpenLinkInTab) {
    std::string url = params->GetLinkUrl().ToString();
    if (IsWebScheme(SchemeOf(url))) delegate_->OpenInNewTab(url, true);
    return true;
  }
  if (command_id == kMenuInspect) {
    delegate_->ShowDevTools(browser, CefPoint(params->GetXCoord(), params->GetYCoord()));
    return true;
  }
  return false;
}

bool Client::OnPreKeyEvent(CefRefPtr<CefBrowser> browser, const CefKeyEvent& event, CefEventHandle,
                           bool* is_keyboard_shortcut) {
  if (event.type != KEYEVENT_RAWKEYDOWN || !Owned(browser)) return false;
  if (delegate_->HandleShortcut(event)) {
    *is_keyboard_shortcut = true;
    return true;
  }
  return false;
}

void Client::OnProtocolExecution(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame>, CefRefPtr<CefRequest> request,
                                 bool& allow_os_execution) {
  std::string url = request->GetURL().ToString();
  std::string scheme = SchemeOf(url);
  allow_os_execution = false;
  static const char* kBlocked[] = {"javascript", "file", "ms-msdt", "search-ms", "ms-officecmd"};
  for (const char* s : kBlocked) {
    if (scheme == s) return;
  }
  std::string origin = OriginOf(browser->GetMainFrame()->GetURL().ToString());
  std::wstring shown = Widen(url.size() > 200 ? url.substr(0, 200) + "..." : url);
  allow_os_execution = Ask(delegate_->Handle(), Format(Tr(Str::ExternalBody), Widen(origin), shown),
                           Tr(Str::ExternalTitle));
}

}  // namespace mex

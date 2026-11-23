#pragma once
#include <windows.h>

#include <cstdint>
#include <string>
#include <vector>

#include "core/permission_policy.h"
#include "include/cef_client.h"
#include "include/cef_life_span_handler.h"

namespace mex {

class ClientDelegate {
 public:
  virtual ~ClientDelegate() = default;
  virtual HWND Handle() const = 0;
  virtual bool OwnsBrowser(int id) const = 0;
  virtual void OnTitle(CefRefPtr<CefBrowser> browser, const std::string& title) = 0;
  virtual void OnAddress(CefRefPtr<CefBrowser> browser, const std::string& url) = 0;
  virtual void OnLoadingState(CefRefPtr<CefBrowser> browser, bool loading, bool can_back, bool can_forward) = 0;
  virtual void OnLoadStarted(CefRefPtr<CefBrowser> browser) = 0;
  virtual void OnLoadFinished(CefRefPtr<CefBrowser> browser) = 0;
  virtual void OnFullscreen(CefRefPtr<CefBrowser> browser, bool fullscreen) = 0;
  virtual void OnBrowserClosed(CefRefPtr<CefBrowser> browser) = 0;
  virtual void OpenInNewTab(const std::string& url, bool foreground) = 0;
  virtual bool HandleShortcut(const CefKeyEvent& event) = 0;
  virtual void ShowDevTools(CefRefPtr<CefBrowser> browser, const CefPoint& inspect_at) = 0;
  virtual bool IsPrivate() const = 0;
  virtual void RecordPermissionUse(int browser_id, const std::string& origin,
                                   const std::vector<std::string>& permissions) = 0;
  virtual PermissionPolicyStore& Permissions() = 0;
};

class Client : public CefClient,
               public CefLifeSpanHandler,
               public CefLoadHandler,
               public CefDisplayHandler,
               public CefDownloadHandler,
               public CefPermissionHandler,
               public CefContextMenuHandler,
               public CefKeyboardHandler,
               public CefRequestHandler,
               public CefResourceRequestHandler {
 public:
  explicit Client(ClientDelegate* delegate) : delegate_(delegate) {}
  void ClosePopups();
  size_t PopupCount() const { return popups_.size(); }

  CefRefPtr<CefLifeSpanHandler> GetLifeSpanHandler() override { return this; }
  CefRefPtr<CefLoadHandler> GetLoadHandler() override { return this; }
  CefRefPtr<CefDisplayHandler> GetDisplayHandler() override { return this; }
  CefRefPtr<CefDownloadHandler> GetDownloadHandler() override { return this; }
  CefRefPtr<CefPermissionHandler> GetPermissionHandler() override { return this; }
  CefRefPtr<CefContextMenuHandler> GetContextMenuHandler() override { return this; }
  CefRefPtr<CefKeyboardHandler> GetKeyboardHandler() override { return this; }
  CefRefPtr<CefRequestHandler> GetRequestHandler() override { return this; }

  bool OnBeforePopup(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, int popup_id,
                     const CefString& target_url, const CefString& target_frame_name,
                     CefLifeSpanHandler::WindowOpenDisposition target_disposition, bool user_gesture,
                     const CefPopupFeatures& popup_features, CefWindowInfo& window_info,
                     CefRefPtr<CefClient>& client, CefBrowserSettings& settings,
                     CefRefPtr<CefDictionaryValue>& extra_info, bool* no_javascript_access) override;
  void OnAfterCreated(CefRefPtr<CefBrowser> browser) override;
  bool DoClose(CefRefPtr<CefBrowser> browser) override;
  void OnBeforeClose(CefRefPtr<CefBrowser> browser) override;

  void OnTitleChange(CefRefPtr<CefBrowser> browser, const CefString& title) override;
  void OnAddressChange(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, const CefString& url) override;
  void OnFullscreenModeChange(CefRefPtr<CefBrowser> browser, bool fullscreen) override;

  void OnLoadingStateChange(CefRefPtr<CefBrowser> browser, bool is_loading, bool can_go_back,
                            bool can_go_forward) override;
  void OnLoadStart(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, TransitionType transition_type) override;
  void OnLoadEnd(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, int http_status_code) override;
  void OnLoadError(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, ErrorCode error_code,
                   const CefString& error_text, const CefString& failed_url) override;

  bool OnBeforeDownload(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDownloadItem> download_item,
                        const CefString& suggested_name, CefRefPtr<CefBeforeDownloadCallback> callback) override;
  void OnDownloadUpdated(CefRefPtr<CefBrowser> browser, CefRefPtr<CefDownloadItem> download_item,
                         CefRefPtr<CefDownloadItemCallback> callback) override;

  bool OnShowPermissionPrompt(CefRefPtr<CefBrowser> browser, uint64_t prompt_id, const CefString& requesting_origin,
                              uint32_t requested_permissions, CefRefPtr<CefPermissionPromptCallback> callback) override;
  bool OnRequestMediaAccessPermission(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                                      const CefString& requesting_url, uint32_t requested_permissions,
                                      CefRefPtr<CefMediaAccessCallback> callback) override;

  void OnBeforeContextMenu(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                           CefRefPtr<CefContextMenuParams> params, CefRefPtr<CefMenuModel> model) override;
  bool OnContextMenuCommand(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame,
                            CefRefPtr<CefContextMenuParams> params, int command_id,
                            EventFlags event_flags) override;

  bool OnPreKeyEvent(CefRefPtr<CefBrowser> browser, const CefKeyEvent& event, CefEventHandle os_event,
                     bool* is_keyboard_shortcut) override;

  CefRefPtr<CefResourceRequestHandler> GetResourceRequestHandler(CefRefPtr<CefBrowser> browser,
                                                                 CefRefPtr<CefFrame> frame,
                                                                 CefRefPtr<CefRequest> request, bool is_navigation,
                                                                 bool is_download, const CefString& request_initiator,
                                                                 bool& disable_default_handling) override {
    return this;
  }

  void OnProtocolExecution(CefRefPtr<CefBrowser> browser, CefRefPtr<CefFrame> frame, CefRefPtr<CefRequest> request,
                           bool& allow_os_execution) override;

 private:
  bool Owned(CefRefPtr<CefBrowser> browser) const { return delegate_->OwnsBrowser(browser->GetIdentifier()); }
  bool AllowPopupNow();

  ClientDelegate* delegate_;
  std::vector<int64_t> popup_times_;
  std::vector<CefRefPtr<CefBrowser>> popups_;

  IMPLEMENT_REFCOUNTING(Client);
  DISALLOW_COPY_AND_ASSIGN(Client);
};

}  // namespace mex

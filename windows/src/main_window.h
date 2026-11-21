#pragma once
#include <windows.h>

#include <set>
#include <string>
#include <vector>

#include "client.h"
#include "include/cef_browser.h"

namespace mex {

class MainWindow : public ClientDelegate {
 public:
  static MainWindow* Create(const std::string& url);
  static size_t Count();
  static void RegisterWindowClass(HINSTANCE instance);
  static MainWindow* Last();
  void AddTab(const std::string& url, bool foreground);
  void Activate();

  HWND Handle() const override { return hwnd_; }
  bool OwnsBrowser(int id) const override { return owned_ids_.count(id) > 0; }
  void OnTitle(CefRefPtr<CefBrowser> browser, const std::string& title) override;
  void OnAddress(CefRefPtr<CefBrowser> browser, const std::string& url) override;
  void OnLoadingState(CefRefPtr<CefBrowser> browser, bool loading, bool can_back, bool can_forward) override;
  void OnLoadStarted(CefRefPtr<CefBrowser> browser) override;
  void OnLoadFinished(CefRefPtr<CefBrowser> browser) override;
  void OnFullscreen(CefRefPtr<CefBrowser> browser, bool fullscreen) override;
  void OnBrowserClosed(CefRefPtr<CefBrowser> browser) override;
  void OpenInNewTab(const std::string& url, bool foreground) override { AddTab(url, foreground); }
  bool HandleShortcut(const CefKeyEvent& event) override;
  void ShowDevTools(CefRefPtr<CefBrowser> browser, const CefPoint& inspect_at) override;

 private:
  struct Tab {
    int browser_id = 0;
    CefRefPtr<CefBrowser> browser;
    std::string url;
    std::string title;
    std::string origin;
    bool loading = false;
    bool can_back = false;
    bool can_forward = false;
  };

  MainWindow();
  ~MainWindow();
  void MaybeDelete();
  static LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp);
  static LRESULT CALLBACK AddressProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp, UINT_PTR id, DWORD_PTR ref);
  static LRESULT CALLBACK TabsProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp, UINT_PTR id, DWORD_PTR ref);
  LRESULT HandleMessage(UINT msg, WPARAM wp, LPARAM lp);

  bool Init(const std::string& url);
  void CreateControls();
  void Layout();
  RECT BrowserRect() const;
  int ActiveIndex() const { return active_; }
  Tab* ActiveTab();
  int IndexOf(CefRefPtr<CefBrowser> browser) const;
  void SwitchTo(int index);
  void CloseTabAt(int index);
  void CloseWindowRequest();
  void FinishCloseIfReady();
  void UpdateTabLabel(int index);
  void UpdateChrome();
  void UpdateTabSizes();
  void OnCommand(int id);
  void ShowMainMenu();
  void ShowTabMenu(int index, POINT screen);
  void Navigate(const std::wstring& text);
  void FocusAddress();
  void SetFullscreen(bool on);
  void ApplyUserAgent(CefRefPtr<CefBrowser> browser, const std::string& url);
  void InjectSiteCss(CefRefPtr<CefBrowser> browser, const std::string& css);
  void SetSiteUa(int menu_id);
  void Zoom(double delta, bool reset);
  void ToggleBookmark();
  void OpenSiteEditor();
  std::wstring AddressText() const;
  void SavePlacement();
  void RestorePlacement();

  HWND hwnd_ = nullptr;
  HWND tabs_ = nullptr;
  HWND address_ = nullptr;
  HWND security_ = nullptr;
  HFONT font_ = nullptr;
  UINT dpi_ = 96;
  bool rtl_ = false;
  bool fullscreen_ = false;
  bool closing_ = false;
  bool destroyed_ = false;
  bool destroying_ = false;
  WINDOWPLACEMENT saved_placement_{};
  LONG_PTR saved_style_ = 0;
  std::vector<Tab> tabs_list_;
  int active_ = -1;
  std::set<int> owned_ids_;
  CefRefPtr<Client> client_;
  int pending_additions_ = 0;
};

}  // namespace mex

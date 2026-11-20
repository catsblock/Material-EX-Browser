#include "panels.h"

#include <commctrl.h>
#include <shellapi.h>

#include <algorithm>
#include <memory>
#include <vector>

#include "core/filename.h"
#include "core/url_utils.h"
#include "downloads.h"
#include "platform.h"
#include "profile.h"
#include "strings.h"

namespace mex {

namespace {

constexpr wchar_t kPanelClass[] = L"MaterialEXPanel";
constexpr int kIdList = 1000;
constexpr int kIdBase = 1100;

class Panel {
 public:
  virtual ~Panel() { if (font) DeleteObject(font); }
  virtual void OnCreate() = 0;
  virtual void OnSize(int w, int h) = 0;
  virtual void OnCommand(int id, int code) { (void)id; (void)code; }
  virtual void OnTimer() {}

  HWND hwnd = nullptr;
  HFONT font = nullptr;
  UINT dpi = 96;
  HWND* slot = nullptr;

 protected:
  int S(int px) const { return Scale(px, dpi); }
  HWND Child(const wchar_t* cls, const wchar_t* text, DWORD style, int id, DWORD ex = 0) {
    HWND h = CreateWindowExW(ex, cls, text, WS_CHILD | WS_VISIBLE | style, 0, 0, 10, 10, hwnd,
                             reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)), GetModuleHandleW(nullptr), nullptr);
    SendMessageW(h, WM_SETFONT, reinterpret_cast<WPARAM>(font), TRUE);
    return h;
  }
  HWND Button(const wchar_t* text, int id) { return Child(L"BUTTON", text, WS_TABSTOP | BS_PUSHBUTTON, id); }
  HWND Item(int id) const { return GetDlgItem(hwnd, id); }
  void Place(int id, int x, int y, int w, int h) const { MoveWindow(Item(id), x, y, w, h, TRUE); }
  int Selected() const {
    int sel = static_cast<int>(SendMessageW(Item(kIdList), LB_GETCURSEL, 0, 0));
    return sel == LB_ERR ? -1 : sel;
  }
};

LRESULT CALLBACK PanelProc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
  Panel* p = reinterpret_cast<Panel*>(GetWindowLongPtrW(hwnd, GWLP_USERDATA));
  switch (msg) {
    case WM_NCCREATE: {
      auto* cs = reinterpret_cast<CREATESTRUCTW*>(lp);
      SetWindowLongPtrW(hwnd, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(cs->lpCreateParams));
      return DefWindowProcW(hwnd, msg, wp, lp);
    }
    case WM_CREATE:
      p->hwnd = hwnd;
      p->dpi = GetDpiForWindow(hwnd);
      p->font = CreateUiFont(p->dpi);
      p->OnCreate();
      return 0;
    case WM_SIZE:
      if (p) p->OnSize(LOWORD(lp), HIWORD(lp));
      return 0;
    case WM_COMMAND:
      if (p) p->OnCommand(LOWORD(wp), HIWORD(wp));
      return 0;
    case WM_TIMER:
      if (p) p->OnTimer();
      return 0;
    case WM_DPICHANGED: {
      auto* r = reinterpret_cast<RECT*>(lp);
      SetWindowPos(hwnd, nullptr, r->left, r->top, r->right - r->left, r->bottom - r->top, SWP_NOZORDER | SWP_NOACTIVATE);
      if (p) {
        UINT old = p->dpi;
        p->dpi = HIWORD(wp);
        HFONT nf = CreateUiFont(p->dpi);
        EnumChildWindows(hwnd, [](HWND c, LPARAM f) -> BOOL {
          SendMessageW(c, WM_SETFONT, static_cast<WPARAM>(f), TRUE);
          return TRUE;
        }, reinterpret_cast<LPARAM>(nf));
        DeleteObject(p->font);
        p->font = nf;
        (void)old;
        RECT rc;
        GetClientRect(hwnd, &rc);
        p->OnSize(rc.right, rc.bottom);
      }
      return 0;
    }
    case WM_CLOSE:
      DestroyWindow(hwnd);
      return 0;
    case WM_NCDESTROY: {
      LRESULT res = DefWindowProcW(hwnd, msg, wp, lp);
      if (p) {
        if (p->slot) *p->slot = nullptr;
        delete p;
      }
      SetWindowLongPtrW(hwnd, GWLP_USERDATA, 0);
      return res;
    }
  }
  return DefWindowProcW(hwnd, msg, wp, lp);
}

void OpenPanel(HWND owner, const wchar_t* title, std::unique_ptr<Panel> panel, int w, int h, HWND* slot) {
  if (*slot && IsWindow(*slot)) {
    ShowWindow(*slot, SW_RESTORE);
    SetForegroundWindow(*slot);
    return;
  }
  static bool registered = false;
  if (!registered) {
    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = PanelProc;
    wc.hInstance = GetModuleHandleW(nullptr);
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_BTNFACE + 1);
    wc.lpszClassName = kPanelClass;
    RegisterClassExW(&wc);
    registered = true;
  }
  UINT dpi = owner ? GetDpiForWindow(owner) : 96;
  Panel* raw = panel.release();
  raw->slot = slot;
  DWORD ex = IsRtlUi() ? WS_EX_LAYOUTRTL : 0;
  HWND hwnd = CreateWindowExW(ex, kPanelClass, title, WS_OVERLAPPEDWINDOW | WS_VISIBLE, CW_USEDEFAULT, CW_USEDEFAULT,
                              Scale(w, dpi), Scale(h, dpi), owner, nullptr, GetModuleHandleW(nullptr), raw);
  if (!hwnd) {
    delete raw;
    return;
  }
  *slot = hwnd;
}

std::wstring Format1(const wchar_t* fmt, const std::wstring& a) {
  std::wstring out(1024, L'\0');
  int n = swprintf(out.data(), out.size(), fmt, a.c_str());
  out.resize(n > 0 ? static_cast<size_t>(n) : 0);
  return out;
}

void AddRow(HWND list, const std::wstring& text) {
  SendMessageW(list, LB_ADDSTRING, 0, reinterpret_cast<LPARAM>(text.c_str()));
}

class ListPanel : public Panel {
 public:
  void OnCreate() override {
    Child(L"LISTBOX", L"", WS_TABSTOP | WS_VSCROLL | WS_BORDER | LBS_NOTIFY | LBS_NOINTEGRALHEIGHT, kIdList);
    CreateButtons();
    Refresh();
  }

  void OnSize(int w, int h) override {
    int m = S(10);
    int bh = S(30);
    int per_row = std::max(1, (w - m) / S(130));
    int rows = (static_cast<int>(buttons_.size()) + per_row - 1) / per_row;
    int area = rows * (bh + S(6)) + m;
    Place(kIdList, m, m, w - 2 * m, h - area - m);
    int bw = (w - 2 * m - (per_row - 1) * S(6)) / per_row;
    for (size_t i = 0; i < buttons_.size(); ++i) {
      int r = static_cast<int>(i) / per_row;
      int c = static_cast<int>(i) % per_row;
      Place(buttons_[i], m + c * (bw + S(6)), h - area + r * (bh + S(6)), bw, bh);
    }
  }

  void OnCommand(int id, int code) override {
    if (id == kIdList && code == LBN_DBLCLK) {
      OnActivate(Selected());
      return;
    }
    if (id >= kIdBase) OnButton(id - kIdBase, Selected());
  }

 protected:
  virtual void CreateButtons() = 0;
  virtual void Refresh() = 0;
  virtual void OnActivate(int index) { (void)index; }
  virtual void OnButton(int index, int selected) = 0;

  void AddButton(const wchar_t* text) {
    int id = kIdBase + static_cast<int>(buttons_.size());
    Button(text, id);
    buttons_.push_back(id);
  }

  void Reload(const std::vector<std::wstring>& rows) {
    HWND list = Item(kIdList);
    int sel = Selected();
    int top = static_cast<int>(SendMessageW(list, LB_GETTOPINDEX, 0, 0));
    SendMessageW(list, WM_SETREDRAW, FALSE, 0);
    SendMessageW(list, LB_RESETCONTENT, 0, 0);
    for (const std::wstring& r : rows) AddRow(list, r);
    if (sel >= 0 && sel < static_cast<int>(rows.size())) SendMessageW(list, LB_SETCURSEL, sel, 0);
    SendMessageW(list, LB_SETTOPINDEX, top, 0);
    SendMessageW(list, WM_SETREDRAW, TRUE, 0);
    InvalidateRect(list, nullptr, TRUE);
  }

 private:
  std::vector<int> buttons_;
};

HWND g_history = nullptr;
HWND g_bookmarks = nullptr;
HWND g_downloads = nullptr;
HWND g_settings = nullptr;
HWND g_editor = nullptr;

class HistoryPanel : public ListPanel {
 public:
  explicit HistoryPanel(OpenUrlFn open) : open_(std::move(open)) {}

 protected:
  void CreateButtons() override {
    AddButton(Tr(Str::OpenInTab));
    AddButton(Tr(Str::ClearHistory));
  }
  void Refresh() override {
    std::vector<std::wstring> rows;
    const auto& entries = Profile::Get().history.Entries();
    order_.clear();
    for (size_t i = entries.size(); i-- > 0;) {
      order_.push_back(entries[i].url);
      rows.push_back((entries[i].title.empty() ? Widen(entries[i].url) : Widen(entries[i].title)) + L"  -  " +
                     Widen(entries[i].url));
    }
    Reload(rows);
  }
  void OnActivate(int index) override {
    if (index >= 0 && index < static_cast<int>(order_.size())) open_(order_[static_cast<size_t>(index)]);
  }
  void OnButton(int index, int selected) override {
    if (index == 0) {
      OnActivate(selected);
    } else {
      Profile::Get().history.Clear();
      Profile::Get().SaveHistory();
      Refresh();
    }
  }

 private:
  OpenUrlFn open_;
  std::vector<std::string> order_;
};

class BookmarksPanel : public ListPanel {
 public:
  explicit BookmarksPanel(OpenUrlFn open) : open_(std::move(open)) {}

 protected:
  void CreateButtons() override {
    AddButton(Tr(Str::OpenInTab));
    AddButton(Tr(Str::Remove));
  }
  void Refresh() override {
    std::vector<std::wstring> rows;
    for (const Bookmark& b : Profile::Get().bookmarks.Entries()) {
      rows.push_back((b.title.empty() ? Widen(b.url) : Widen(b.title)) + L"  -  " + Widen(b.url));
    }
    Reload(rows);
  }
  void OnActivate(int index) override {
    const auto& entries = Profile::Get().bookmarks.Entries();
    if (index >= 0 && index < static_cast<int>(entries.size())) open_(entries[static_cast<size_t>(index)].url);
  }
  void OnButton(int index, int selected) override {
    if (index == 0) {
      OnActivate(selected);
      return;
    }
    const auto& entries = Profile::Get().bookmarks.Entries();
    if (selected >= 0 && selected < static_cast<int>(entries.size())) {
      std::string url = entries[static_cast<size_t>(selected)].url;
      Profile::Get().bookmarks.Remove(url);
      Profile::Get().SaveBookmarks();
      Refresh();
    }
  }

 private:
  OpenUrlFn open_;
};

std::wstring FormatDuration(int64_t seconds) {
  wchar_t buf[32];
  if (seconds >= 3600) {
    swprintf(buf, 32, L"%lld:%02lld:%02lld", seconds / 3600, (seconds / 60) % 60, seconds % 60);
  } else {
    swprintf(buf, 32, L"%02lld:%02lld", seconds / 60, seconds % 60);
  }
  return buf;
}

class DownloadsPanel : public ListPanel {
 public:
  explicit DownloadsPanel(OpenUrlFn retry) : retry_(std::move(retry)) {}
  void OnCreate() override {
    ListPanel::OnCreate();
    SetTimer(hwnd, 1, 500, nullptr);
  }
  void OnTimer() override { Refresh(); }

 protected:
  void CreateButtons() override {
    AddButton(Tr(Str::Open));
    AddButton(Tr(Str::ShowInFolder));
    AddButton(Tr(Str::Pause));
    AddButton(Tr(Str::Resume));
    AddButton(Tr(Str::Cancel));
    AddButton(Tr(Str::Retry));
    AddButton(Tr(Str::Delete));
    AddButton(Tr(Str::Remove));
    AddButton(Tr(Str::ClearCompleted));
  }
  void Refresh() override {
    std::vector<std::wstring> rows;
    ids_.clear();
    const auto& records = DownloadManager::Get().Records();
    for (size_t i = records.size(); i-- > 0;) {
      const DownloadRecord& r = records[i];
      ids_.push_back(r.id);
      std::wstring line = Widen(r.filename) + L"  -  " + DownloadStateText(r);
      if (r.state == DownloadState::kDownloading || r.state == DownloadState::kPaused) {
        if (r.total > 0) {
          line += L"  " + std::to_wstring(std::min<int64_t>(100, r.received * 100 / r.total)) + L"%  (" +
                  FormatBytes(r.received) + L" / " + FormatBytes(r.total) + L")";
        } else {
          line += L"  " + FormatBytes(r.received);
        }
        if (r.state == DownloadState::kDownloading && r.speed > 0) {
          line += L"  " + FormatBytes(r.speed) + L"/s";
          if (r.total > r.received) {
            line += L"  " + FormatDuration((r.total - r.received) / r.speed) + L" " + Tr(Str::Remaining);
          }
        }
      } else if (r.total > 0) {
        line += L"  " + FormatBytes(r.total);
      }
      if (!r.note.empty()) line += L"  (" + Widen(r.note) + L")";
      std::string host = HostOf(r.url);
      if (!host.empty()) line += L"  -  " + Widen(host);
      rows.push_back(line);
    }
    Reload(rows);
  }
  void OnActivate(int index) override { OnButton(0, index); }
  void OnButton(int button, int selected) override {
    DownloadManager& m = DownloadManager::Get();
    if (button == 8) {
      m.ClearFinished();
      Refresh();
      return;
    }
    if (selected < 0 || selected >= static_cast<int>(ids_.size())) return;
    const DownloadRecord* r = m.Find(ids_[static_cast<size_t>(selected)]);
    if (!r) return;
    uint32_t id = r->id;
    std::wstring path = r->path;
    switch (button) {
      case 0:
        if (r->state == DownloadState::kComplete && FileExists(path)) {
          if (IsDangerousExtension(r->filename) &&
              MessageBoxW(hwnd, Format1(Tr(Str::OpenDangerBody), Widen(r->filename)).c_str(), Tr(Str::DangerTitle),
                          MB_YESNO | MB_ICONWARNING | MB_DEFBUTTON2) != IDYES) {
            return;
          }
          ShellExecuteW(hwnd, L"open", path.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
        }
        break;
      case 1:
        if (FileExists(path)) {
          std::wstring args = L"/select,\"" + path + L"\"";
          ShellExecuteW(hwnd, L"open", L"explorer.exe", args.c_str(), nullptr, SW_SHOWNORMAL);
        }
        break;
      case 2: m.Pause(id); break;
      case 3: m.Resume(id); break;
      case 4: m.Cancel(id); break;
      case 5:
        if (r->state != DownloadState::kDownloading && r->state != DownloadState::kPaused) retry_(r->url);
        break;
      case 6:
        if (r->state == DownloadState::kComplete && FileExists(path) &&
            MessageBoxW(hwnd, Format1(Tr(Str::DeleteConfirm), Widen(r->filename)).c_str(), Tr(Str::Delete),
                        MB_YESNO | MB_ICONWARNING | MB_DEFBUTTON2) == IDYES) {
          DeleteFileW(path.c_str());
          m.Remove(id);
        }
        break;
      case 7: m.Remove(id); break;
    }
    Refresh();
  }

 private:
  OpenUrlFn retry_;
  std::vector<uint32_t> ids_;
};

class SettingsPanel : public ListPanel {
 public:
  void OnCreate() override {
    Child(L"STATIC", Tr(Str::HomePage), 0, 2001);
    Child(L"EDIT", Widen(Profile::Get().HomeUrl()).c_str(), WS_TABSTOP | WS_BORDER | ES_AUTOHSCROLL, 2002);
    Child(L"STATIC", Tr(Str::SearchTemplate), 0, 2003);
    Child(L"EDIT", Widen(Profile::Get().SearchTemplate()).c_str(), WS_TABSTOP | WS_BORDER | ES_AUTOHSCROLL, 2004);
    Child(L"STATIC", Tr(Str::UaGlobal), 0, 2005);
    HWND combo = Child(L"COMBOBOX", L"", WS_TABSTOP | CBS_DROPDOWNLIST | WS_VSCROLL, 2006);
    const Str modes[] = {Str::UaDefault, Str::UaDesktop, Str::UaMobile, Str::UaCustom};
    for (Str m : modes) SendMessageW(combo, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(Tr(m)));
    SendMessageW(combo, CB_SETCURSEL, static_cast<int>(Profile::Get().GlobalUa()), 0);
    Child(L"STATIC", Tr(Str::CustomUa), 0, 2007);
    Child(L"EDIT", Widen(Profile::Get().CustomUa()).c_str(), WS_TABSTOP | WS_BORDER | ES_AUTOHSCROLL, 2008);
    Button(Tr(Str::Save), 2009);
    Child(L"STATIC", Tr(Str::Permissions), 0, 2010);
    ListPanel::OnCreate();
  }

  void OnSize(int w, int h) override {
    int m = S(10);
    int row = S(22);
    int edit = S(26);
    int y = m;
    auto field = [&](int label, int control, int ch) {
      Place(label, m, y, w - 2 * m, row);
      y += row;
      Place(control, m, y, w - 2 * m, ch);
      y += ch + S(6);
    };
    field(2001, 2002, edit);
    field(2003, 2004, edit);
    field(2005, 2006, S(200));
    y -= S(200) - edit;
    field(2007, 2008, edit);
    Place(2009, m, y, S(120), S(30));
    y += S(40);
    Place(2010, m, y, w - 2 * m, row);
    y += row;
    int bh = S(30);
    int bw = (w - 2 * m - 3 * S(6)) / 4;
    int by = h - bh - m;
    for (int i = 0; i < 4; ++i) Place(kIdBase + i, m + i * (bw + S(6)), by, bw, bh);
    Place(kIdList, m, y, w - 2 * m, std::max(S(60), by - y - S(8)));
  }

  void OnCommand(int id, int code) override {
    if (id == 2009) {
      SaveSettings();
      return;
    }
    ListPanel::OnCommand(id, code);
  }

 protected:
  void CreateButtons() override {
    AddButton(Tr(Str::SetAsk));
    AddButton(Tr(Str::SetAllow));
    AddButton(Tr(Str::SetBlock));
    AddButton(Tr(Str::ResetAllPermissions));
  }

  void Refresh() override {
    entries_ = Profile::Get().permissions.Entries();
    std::vector<std::wstring> rows;
    for (const PermissionEntry& e : entries_) {
      std::wstring state = e.policy == Policy::kAllow ? Tr(Str::PolicyAllow)
                           : e.policy == Policy::kDeny ? Tr(Str::PolicyDeny)
                           : e.auto_blocked            ? Tr(Str::BlockedAuto)
                                                       : Tr(Str::PolicyAsk);
      rows.push_back(Widen(e.origin) + L"  -  " + Widen(e.permission) + L"  -  " + state);
    }
    Reload(rows);
  }

  void OnButton(int index, int selected) override {
    Profile& p = Profile::Get();
    if (index == 3) {
      p.permissions.ResetAll();
    } else if (selected >= 0 && selected < static_cast<int>(entries_.size())) {
      const PermissionEntry& e = entries_[static_cast<size_t>(selected)];
      Policy policy = index == 0 ? Policy::kAsk : index == 1 ? Policy::kAllow : Policy::kDeny;
      p.permissions.SetPolicy(e.origin, e.permission, policy);
    } else {
      return;
    }
    p.SavePermissions();
    Refresh();
  }

 private:
  std::wstring Text(int id) {
    HWND h = Item(id);
    int n = GetWindowTextLengthW(h);
    std::wstring s(static_cast<size_t>(n) + 1, L'\0');
    GetWindowTextW(h, s.data(), n + 1);
    s.resize(static_cast<size_t>(n));
    return s;
  }

  void SaveSettings() {
    Profile& p = Profile::Get();
    std::string home = Trim(Narrow(Text(2002)));
    std::string search = Trim(Narrow(Text(2004)));
    if (!home.empty()) p.settings.Set("home", home == "about:blank" ? home : NormalizeInput(home, search));
    if (!search.empty() && search.find("%s") != std::string::npos && IsWebScheme(SchemeOf(search))) {
      p.settings.Set("search", search);
    }
    static const char* names[] = {"default", "desktop", "mobile", "custom"};
    int mode = static_cast<int>(SendMessageW(Item(2006), CB_GETCURSEL, 0, 0));
    if (mode >= 0 && mode < 4) p.settings.Set("ua", names[mode]);
    p.settings.Set("ua.custom", Trim(Narrow(Text(2008))));
    p.SaveSettings();
  }

  std::vector<PermissionEntry> entries_;
};

class SiteEditorPanel : public Panel {
 public:
  SiteEditorPanel(std::string host, std::function<void(const std::string&)> apply, std::function<void()> dev_tools)
      : host_(std::move(host)), apply_(std::move(apply)), dev_tools_(std::move(dev_tools)) {}

  void OnCreate() override {
    Child(L"STATIC", Format1(Tr(Str::SiteEditorHint), Widen(host_)).c_str(), 0, 3001);
    Child(L"EDIT", Widen(Profile::Get().SiteCss(host_)).c_str(),
          WS_TABSTOP | WS_BORDER | WS_VSCROLL | ES_MULTILINE | ES_AUTOVSCROLL | ES_WANTRETURN, 3002);
    Button(Tr(Str::Apply), 3003);
    Button(Tr(Str::Save), 3004);
    Button(Tr(Str::Reset), 3005);
    Button(Tr(Str::DevTools), 3006);
    SendMessageW(Item(3002), EM_SETLIMITTEXT, 200000, 0);
  }

  void OnSize(int w, int h) override {
    int m = S(10);
    int bh = S(30);
    Place(3001, m, m, w - 2 * m, S(40));
    int by = h - bh - m;
    Place(3002, m, m + S(46), w - 2 * m, by - m - S(46) - S(8));
    int bw = (w - 2 * m - 3 * S(6)) / 4;
    for (int i = 0; i < 4; ++i) Place(3003 + i, m + i * (bw + S(6)), by, bw, bh);
  }

  void OnCommand(int id, int) override {
    switch (id) {
      case 3003: apply_(Css()); break;
      case 3004: {
        Profile::Get().SetSiteCss(host_, Css());
        apply_(Css());
        break;
      }
      case 3005:
        SetWindowTextW(Item(3002), L"");
        Profile::Get().SetSiteCss(host_, "");
        apply_("");
        break;
      case 3006: dev_tools_(); break;
    }
  }

 private:
  std::string Css() {
    HWND h = Item(3002);
    int n = GetWindowTextLengthW(h);
    std::wstring s(static_cast<size_t>(n) + 1, L'\0');
    GetWindowTextW(h, s.data(), n + 1);
    s.resize(static_cast<size_t>(n));
    return Narrow(s);
  }

  std::string host_;
  std::function<void(const std::string&)> apply_;
  std::function<void()> dev_tools_;
};

}  // namespace

void ShowHistoryPanel(HWND owner, OpenUrlFn open) {
  OpenPanel(owner, Tr(Str::History), std::make_unique<HistoryPanel>(std::move(open)), 720, 520, &g_history);
}

void ShowBookmarksPanel(HWND owner, OpenUrlFn open) {
  OpenPanel(owner, Tr(Str::Bookmarks), std::make_unique<BookmarksPanel>(std::move(open)), 720, 520, &g_bookmarks);
}

void ShowDownloadsPanel(HWND owner, OpenUrlFn retry) {
  OpenPanel(owner, Tr(Str::Downloads), std::make_unique<DownloadsPanel>(std::move(retry)), 820, 560, &g_downloads);
}

void ShowSettingsPanel(HWND owner) {
  OpenPanel(owner, Tr(Str::Settings), std::make_unique<SettingsPanel>(), 640, 640, &g_settings);
}

void ShowSiteEditorPanel(HWND owner, const std::string& host, std::function<void(const std::string&)> apply,
                         std::function<void()> dev_tools) {
  OpenPanel(owner, Tr(Str::SiteEditor),
            std::make_unique<SiteEditorPanel>(host, std::move(apply), std::move(dev_tools)), 640, 520, &g_editor);
}

}  // namespace mex

#include "platform.h"

#include <shlobj.h>

#include <ctime>

namespace mex {

std::wstring Widen(const std::string& s) {
  if (s.empty()) return L"";
  int n = MultiByteToWideChar(CP_UTF8, 0, s.data(), static_cast<int>(s.size()), nullptr, 0);
  std::wstring out(static_cast<size_t>(n), L'\0');
  MultiByteToWideChar(CP_UTF8, 0, s.data(), static_cast<int>(s.size()), out.data(), n);
  return out;
}

std::string Narrow(const std::wstring& s) {
  if (s.empty()) return "";
  int n = WideCharToMultiByte(CP_UTF8, 0, s.data(), static_cast<int>(s.size()), nullptr, 0, nullptr, nullptr);
  std::string out(static_cast<size_t>(n), '\0');
  WideCharToMultiByte(CP_UTF8, 0, s.data(), static_cast<int>(s.size()), out.data(), n, nullptr, nullptr);
  return out;
}

static std::wstring KnownFolder(REFKNOWNFOLDERID id) {
  PWSTR raw = nullptr;
  std::wstring out;
  if (SUCCEEDED(SHGetKnownFolderPath(id, KF_FLAG_CREATE, nullptr, &raw)) && raw) out = raw;
  CoTaskMemFree(raw);
  return out;
}

std::wstring DataDir() {
  std::wstring dir = KnownFolder(FOLDERID_LocalAppData) + L"\\MaterialEX";
  CreateDirectoryW(dir.c_str(), nullptr);
  return dir;
}

std::wstring DownloadsDir() { return KnownFolder(FOLDERID_Downloads); }

bool ReadTextFile(const std::wstring& path, std::string* out) {
  HANDLE h = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING,
                         FILE_ATTRIBUTE_NORMAL, nullptr);
  if (h == INVALID_HANDLE_VALUE) return false;
  LARGE_INTEGER size{};
  GetFileSizeEx(h, &size);
  out->assign(static_cast<size_t>(size.QuadPart), '\0');
  DWORD read = 0;
  bool ok = size.QuadPart == 0 ||
            ReadFile(h, out->data(), static_cast<DWORD>(size.QuadPart), &read, nullptr);
  CloseHandle(h);
  out->resize(read);
  return ok;
}

bool WriteTextFile(const std::wstring& path, const std::string& text) {
  std::wstring tmp = path + L".tmp";
  HANDLE h = CreateFileW(tmp.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
  if (h == INVALID_HANDLE_VALUE) return false;
  DWORD written = 0;
  bool ok = text.empty() || WriteFile(h, text.data(), static_cast<DWORD>(text.size()), &written, nullptr);
  CloseHandle(h);
  if (!ok) {
    DeleteFileW(tmp.c_str());
    return false;
  }
  return MoveFileExW(tmp.c_str(), path.c_str(), MOVEFILE_REPLACE_EXISTING) != 0;
}

bool FileExists(const std::wstring& path) { return GetFileAttributesW(path.c_str()) != INVALID_FILE_ATTRIBUTES; }

int Scale(int px, UINT dpi) { return MulDiv(px, static_cast<int>(dpi), 96); }

bool IsRtlUi() {
  WORD primary = PRIMARYLANGID(GetUserDefaultUILanguage());
  return primary == LANG_ARABIC || primary == LANG_HEBREW || primary == LANG_PERSIAN || primary == LANG_URDU;
}

int64_t NowSeconds() { return static_cast<int64_t>(std::time(nullptr)); }

HFONT CreateUiFont(UINT dpi) {
  NONCLIENTMETRICSW ncm{};
  ncm.cbSize = sizeof(ncm);
  if (SystemParametersInfoForDpi(SPI_GETNONCLIENTMETRICS, sizeof(ncm), &ncm, 0, dpi)) {
    return CreateFontIndirectW(&ncm.lfMessageFont);
  }
  return static_cast<HFONT>(GetStockObject(DEFAULT_GUI_FONT));
}

std::wstring FormatBytes(int64_t bytes) {
  if (bytes < 0) return L"-";
  const wchar_t* units[] = {L"B", L"KB", L"MB", L"GB", L"TB"};
  double v = static_cast<double>(bytes);
  int u = 0;
  while (v >= 1024.0 && u < 4) {
    v /= 1024.0;
    ++u;
  }
  wchar_t buf[64];
  swprintf(buf, 64, u == 0 ? L"%.0f %s" : L"%.1f %s", v, units[u]);
  return buf;
}

void LogLine(const std::string& line) {
  std::wstring path = DataDir() + L"\\lifecycle.log";
  HANDLE h = CreateFileW(path.c_str(), FILE_APPEND_DATA, FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr, OPEN_ALWAYS,
                         FILE_ATTRIBUTE_NORMAL, nullptr);
  if (h == INVALID_HANDLE_VALUE) return;
  std::string text = std::to_string(GetTickCount64()) + " " + line + "\n";
  DWORD written = 0;
  WriteFile(h, text.data(), static_cast<DWORD>(text.size()), &written, nullptr);
  CloseHandle(h);
}

}  // namespace mex

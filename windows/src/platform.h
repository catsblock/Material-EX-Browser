#pragma once
#include <windows.h>

#include <cstdint>
#include <string>

namespace mex {

std::wstring Widen(const std::string& s);
std::string Narrow(const std::wstring& s);
std::wstring DataDir();
std::wstring DownloadsDir();
bool ReadTextFile(const std::wstring& path, std::string* out);
bool WriteTextFile(const std::wstring& path, const std::string& text);
bool FileExists(const std::wstring& path);
int Scale(int px, UINT dpi);
bool IsRtlUi();
int64_t NowSeconds();
HFONT CreateUiFont(UINT dpi);
std::wstring FormatBytes(int64_t bytes);
void LogLine(const std::string& line);

}  // namespace mex

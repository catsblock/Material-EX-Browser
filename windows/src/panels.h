#pragma once
#include <windows.h>

#include <functional>
#include <string>

namespace mex {

using OpenUrlFn = std::function<void(const std::string&)>;

void ShowHistoryPanel(HWND owner, OpenUrlFn open);
void ShowBookmarksPanel(HWND owner, OpenUrlFn open);
void ShowDownloadsPanel(HWND owner, OpenUrlFn retry);
void ShowSettingsPanel(HWND owner);
void ShowSiteEditorPanel(HWND owner, const std::string& host, std::function<void(const std::string&)> apply,
                         std::function<void()> dev_tools);

}  // namespace mex

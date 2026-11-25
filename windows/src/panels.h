#pragma once
#include <windows.h>

#include <functional>
#include <string>

namespace mex {

using OpenUrlFn = std::function<void(const std::string&)>;

struct ProfileData;
void ShowHistoryPanel(HWND owner, ProfileData& data, OpenUrlFn open);
void ShowBookmarksPanel(HWND owner, ProfileData& data, OpenUrlFn open);
void ShowDownloadsPanel(HWND owner, OpenUrlFn retry);
using RevocationFn = std::function<void(ProfileData* data, const std::string& origin, const std::string& permission)>;
void SetRevocationHandler(RevocationFn handler);
void ShowSettingsPanel(HWND owner, ProfileData& data);
void ShowSiteEditorPanel(HWND owner, const std::string& host, std::function<void(const std::string&)> apply,
                         std::function<void()> dev_tools);

}  // namespace mex

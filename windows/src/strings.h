#pragma once

namespace mex {

#define MEX_STRINGS(X)                                                                           \
  X(AppName, L"Material EX Browser")                                                             \
  X(Untitled, L"New tab")                                                                        \
  X(Back, L"Back")                                                                               \
  X(Forward, L"Forward")                                                                         \
  X(Reload, L"Reload")                                                                           \
  X(Stop, L"Stop")                                                                               \
  X(Home, L"Home")                                                                               \
  X(NewTab, L"New tab")                                                                          \
  X(NewWindow, L"New window")                                                                    \
  X(NewPrivateWindow, L"New private window")                                                     \
  X(PrivateSuffix, L"Private")                                                                   \
  X(ProfilesMenu, L"Profiles")                                                                   \
  X(ProfileNew, L"New profile")                                                                  \
  X(ProfileDelete, L"Delete this profile")                                                       \
  X(ProfileDeleteConfirm, L"Delete the profile \"%s\"? Its history, bookmarks, permissions, cookies and site data are removed the next time the app starts.") \
  X(ProfileDefaultName, L"Default profile")                                                      \
  X(ClearData, L"Clear browsing data")                                                           \
  X(ClearDataConfirm, L"Clear history, cookies, cache, the download list and saved site permissions? Downloaded files are kept.") \
  X(CloseTab, L"Close tab")                                                                      \
  X(CloseOtherTabs, L"Close other tabs")                                                         \
  X(Menu, L"Menu")                                                                               \
  X(AddBookmark, L"Add bookmark")                                                                \
  X(RemoveBookmark, L"Remove bookmark")                                                          \
  X(Bookmarks, L"Bookmarks")                                                                     \
  X(History, L"History")                                                                         \
  X(Downloads, L"Downloads")                                                                     \
  X(SiteEditor, L"Site editor")                                                                  \
  X(Settings, L"Settings")                                                                       \
  X(DevTools, L"Developer tools")                                                                \
  X(Fullscreen, L"Full screen")                                                                  \
  X(Exit, L"Exit")                                                                               \
  X(UserAgent, L"User agent for this site")                                                      \
  X(UaUseGlobal, L"Use global setting")                                                          \
  X(UaDefault, L"Default")                                                                       \
  X(UaDesktop, L"Desktop")                                                                       \
  X(UaMobile, L"Mobile")                                                                         \
  X(UaCustom, L"Custom")                                                                         \
  X(Secure, L"Secure")                                                                           \
  X(NotSecure, L"Not secure")                                                                    \
  X(Local, L"Local")                                                                             \
  X(OpenLinkInNewTab, L"Open link in new tab")                                                   \
  X(Inspect, L"Inspect element")                                                                 \
  X(PermissionTitle, L"Site permission")                                                         \
  X(PermissionBody, L"%s wants to use: %s.\n\nYes: allow until you leave this site or close the tab.\nNo: block.\nCancel: decide later.") \
  X(PermCamera, L"camera")                                                                       \
  X(PermMicrophone, L"microphone")                                                               \
  X(PermScreen, L"screen capture")                                                               \
  X(PermLocation, L"location")                                                                   \
  X(PermNotifications, L"notifications")                                                         \
  X(PermClipboard, L"clipboard")                                                                 \
  X(PermMidi, L"MIDI devices")                                                                   \
  X(PermMultipleDownloads, L"multiple downloads")                                                \
  X(PermLocalFonts, L"local fonts")                                                              \
  X(DangerTitle, L"Potentially dangerous file")                                                  \
  X(DangerBody, L"%s can run code on your computer. Only keep it if you trust the source.\n\nSource: %s\n\nKeep this file?") \
  X(MimeMismatchBody, L"%s does not match the type reported by the server (%s).\n\nSource: %s\n\nKeep this file?") \
  X(OpenDangerBody, L"%s can run code on your computer. Open it anyway?")                        \
  X(ExternalTitle, L"Open external application")                                                 \
  X(ExternalBody, L"%s wants to open an external application with:\n%s\n\nAllow?")               \
  X(ErrorTitle, L"This page can't be displayed")                                                 \
  X(ErrorDetail, L"Error: %s (%s)")                                                              \
  X(Close, L"Close")                                                                             \
  X(Open, L"Open")                                                                               \
  X(OpenInTab, L"Open in new tab")                                                               \
  X(ShowInFolder, L"Show in folder")                                                             \
  X(Pause, L"Pause")                                                                             \
  X(Resume, L"Resume")                                                                           \
  X(Cancel, L"Cancel")                                                                           \
  X(Retry, L"Retry")                                                                             \
  X(Delete, L"Delete file")                                                                      \
  X(Remove, L"Remove")                                                                           \
  X(ClearCompleted, L"Clear finished")                                                           \
  X(ClearHistory, L"Clear history")                                                              \
  X(DeleteConfirm, L"Delete %s from your computer?")                                             \
  X(StateDownloading, L"Downloading")                                                            \
  X(StatePaused, L"Paused")                                                                      \
  X(StateComplete, L"Completed")                                                                 \
  X(StateCancelled, L"Cancelled")                                                                \
  X(StateInterrupted, L"Interrupted")                                                            \
  X(StateBlocked, L"Blocked")                                                                    \
  X(Remaining, L"remaining")                                                                     \
  X(Save, L"Save")                                                                               \
  X(Apply, L"Apply")                                                                             \
  X(Reset, L"Reset")                                                                             \
  X(SiteEditorHint, L"CSS for %s. Apply previews it on the open page; Save keeps it for this site.") \
  X(HomePage, L"Home page")                                                                      \
  X(SearchTemplate, L"Search URL (%s is the query)")                                             \
  X(UaGlobal, L"User agent")                                                                     \
  X(CustomUa, L"Custom user agent")                                                              \
  X(Permissions, L"Site permissions")                                                            \
  X(PolicyAsk, L"Ask")                                                                           \
  X(PolicyAllow, L"Allow")                                                                       \
  X(PolicyDeny, L"Block")                                                                        \
  X(BlockedAuto, L"Blocked after 3 denials")                                                     \
  X(ResetAllPermissions, L"Reset all permissions")                                               \
  X(SetAsk, L"Ask")                                                                              \
  X(SetAllow, L"Allow")                                                                          \
  X(SetBlock, L"Block")                                                                          \
  X(DownloadBlockedMany, L"Download blocked: too many downloads were started at once.")

enum class Str {
#define X(name, text) name,
  MEX_STRINGS(X)
#undef X
};

const wchar_t* Tr(Str s);

}  // namespace mex

#include "profile.h"

#include <functional>

#include "platform.h"

namespace mex {

namespace {
std::wstring PathFor(const wchar_t* name) { return DataDir() + L"\\" + name; }

void Load(const wchar_t* name, const std::function<void(const std::string&)>& fn) {
  std::string text;
  if (ReadTextFile(PathFor(name), &text)) fn(text);
}
}  // namespace

Profile& Profile::Get() {
  static Profile p;
  return p;
}

void Profile::Load() {
  mex::Load(L"settings.tsv", [&](const std::string& t) { settings.Deserialize(t); });
  mex::Load(L"permissions.tsv", [&](const std::string& t) { permissions.Deserialize(t); });
  mex::Load(L"history.tsv", [&](const std::string& t) { history.Deserialize(t); });
  mex::Load(L"bookmarks.tsv", [&](const std::string& t) { bookmarks.Deserialize(t); });
}

void Profile::SaveSettings() { WriteTextFile(PathFor(L"settings.tsv"), settings.Serialize()); }
void Profile::SavePermissions() { WriteTextFile(PathFor(L"permissions.tsv"), permissions.Serialize()); }
void Profile::SaveHistory() { WriteTextFile(PathFor(L"history.tsv"), history.Serialize()); }
void Profile::SaveBookmarks() { WriteTextFile(PathFor(L"bookmarks.tsv"), bookmarks.Serialize()); }

std::string Profile::HomeUrl() const { return settings.Get("home", "about:blank"); }

std::string Profile::SearchTemplate() const {
  return settings.Get("search", "https://duckduckgo.com/?q=%s");
}

UaMode Profile::Parse(const std::string& s, UaMode fallback) {
  if (s == "default") return UaMode::kDefault;
  if (s == "desktop") return UaMode::kDesktop;
  if (s == "mobile") return UaMode::kMobile;
  if (s == "custom") return UaMode::kCustom;
  return fallback;
}

std::string Profile::Name(UaMode m) {
  switch (m) {
    case UaMode::kDesktop: return "desktop";
    case UaMode::kMobile: return "mobile";
    case UaMode::kCustom: return "custom";
    default: return "default";
  }
}

UaMode Profile::GlobalUa() const { return Parse(settings.Get("ua"), UaMode::kDefault); }

bool Profile::HasSiteUa(const std::string& host) const { return !settings.Get("ua.site." + host).empty(); }

UaMode Profile::UaForHost(const std::string& host) const {
  std::string site = settings.Get("ua.site." + host);
  return site.empty() ? GlobalUa() : Parse(site, GlobalUa());
}

void Profile::SetSiteUa(const std::string& host, bool use_global, UaMode mode) {
  if (use_global) {
    settings.Remove("ua.site." + host);
  } else {
    settings.Set("ua.site." + host, Name(mode));
  }
  SaveSettings();
}

std::string Profile::CustomUa() const { return settings.Get("ua.custom"); }

std::string Profile::SiteCss(const std::string& host) const { return settings.Get("css." + host); }

void Profile::SetSiteCss(const std::string& host, const std::string& css) {
  if (css.empty()) {
    settings.Remove("css." + host);
  } else {
    settings.Set("css." + host, css);
  }
  SaveSettings();
}

}  // namespace mex

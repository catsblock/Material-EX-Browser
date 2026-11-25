#include "profile.h"

#include <functional>

#include <filesystem>
#include <vector>

#include "platform.h"
#include "strings.h"

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

void ProfileData::Load() {
  auto read = [&](const wchar_t* name, const std::function<void(const std::string&)>& fn) {
    std::string text;
    if (ReadTextFile(dir + L"\\" + name, &text)) fn(text);
  };
  read(L"permissions.tsv", [&](const std::string& t) { permissions.Deserialize(t); });
  read(L"history.tsv", [&](const std::string& t) { history.Deserialize(t); });
  read(L"bookmarks.tsv", [&](const std::string& t) { bookmarks.Deserialize(t); });
}

void ProfileData::SaveHistory() { WriteTextFile(dir + L"\\history.tsv", history.Serialize()); }
void ProfileData::SaveBookmarks() { WriteTextFile(dir + L"\\bookmarks.tsv", bookmarks.Serialize()); }
void ProfileData::SavePermissions() { WriteTextFile(dir + L"\\permissions.tsv", permissions.Serialize()); }

void Profile::Load() {
  mex::Load(L"settings.tsv", [&](const std::string& t) { settings.Deserialize(t); });
  mex::Load(L"profiles.tsv", [&](const std::string& t) { registry.Deserialize(t); });
  std::vector<std::string> pending = registry.TakePendingDeletes();
  std::error_code ec;
  for (const std::string& id : pending) {
    std::filesystem::remove_all(DataDir() + L"\\profiles\\" + Widen(id), ec);
    std::filesystem::remove_all(CacheDir(id), ec);
  }
  if (!pending.empty()) SaveProfiles();
}

void Profile::SaveProfiles() { WriteTextFile(PathFor(L"profiles.tsv"), registry.Serialize()); }

ProfileData& Profile::Data(const std::string& id) {
  auto it = data_.find(id);
  if (it != data_.end()) return *it->second;
  auto data = std::make_unique<ProfileData>();
  data->id = id;
  if (id == ProfileRegistry::kDefaultId) {
    data->dir = DataDir();
  } else {
    data->dir = DataDir() + L"\\profiles";
    CreateDirectoryW(data->dir.c_str(), nullptr);
    data->dir += L"\\" + Widen(id);
    CreateDirectoryW(data->dir.c_str(), nullptr);
  }
  data->Load();
  ProfileData& ref = *data;
  data_[id] = std::move(data);
  return ref;
}

std::wstring Profile::CacheDir(const std::string& id) const {
  return DataDir() + L"\\Profile\\" + (id == ProfileRegistry::kDefaultId ? L"Default" : Widen(id));
}

std::wstring Profile::DisplayName(const std::string& id) const {
  const ProfileInfo* info = registry.Find(id);
  if (!info || id == ProfileRegistry::kDefaultId) return Tr(Str::ProfileDefaultName);
  return Widen(info->name);
}

void Profile::SaveSettings() { WriteTextFile(PathFor(L"settings.tsv"), settings.Serialize()); }

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

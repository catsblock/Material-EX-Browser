#pragma once
#include <functional>
#include <string>

#include "core/permission_policy.h"
#include "core/profile_registry.h"
#include "core/records.h"
#include <map>
#include <memory>

namespace mex {

enum class UaMode { kDefault, kDesktop, kMobile, kCustom };

struct ProfileData {
  std::string id;
  std::wstring dir;
  HistoryStore history;
  BookmarkStore bookmarks;
  PermissionPolicyStore permissions;

  void Load();
  void SaveHistory();
  void SaveBookmarks();
  void SavePermissions();
};

class Profile {
 public:
  static Profile& Get();
  void Load();
  void SaveSettings();
  void SaveProfiles();
  ProfileData& Data(const std::string& id);
  std::wstring DisplayName(const std::string& id) const;
  std::wstring CacheDir(const std::string& id) const;

  std::string HomeUrl() const;
  std::string SearchTemplate() const;
  UaMode GlobalUa() const;
  UaMode UaForHost(const std::string& host) const;
  bool HasSiteUa(const std::string& host) const;
  void SetSiteUa(const std::string& host, bool use_global, UaMode mode);
  std::string CustomUa() const;
  std::string SiteCss(const std::string& host) const;
  void SetSiteCss(const std::string& host, const std::string& css);

  KeyValueStore settings;
  ProfileRegistry registry;

 private:
  std::map<std::string, std::unique_ptr<ProfileData>> data_;
  static UaMode Parse(const std::string& s, UaMode fallback);
  static std::string Name(UaMode m);
};

}  // namespace mex

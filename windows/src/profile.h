#pragma once
#include <functional>
#include <string>

#include "core/permission_policy.h"
#include "core/records.h"

namespace mex {

enum class UaMode { kDefault, kDesktop, kMobile, kCustom };

class Profile {
 public:
  static Profile& Get();
  void Load();
  void SaveSettings();
  void SavePermissions();
  void SaveHistory();
  void SaveBookmarks();

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
  PermissionPolicyStore permissions;
  HistoryStore history;
  BookmarkStore bookmarks;

 private:
  static UaMode Parse(const std::string& s, UaMode fallback);
  static std::string Name(UaMode m);
};

}  // namespace mex

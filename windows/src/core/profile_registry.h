#pragma once
#include <string>
#include <vector>

namespace mex {

struct ProfileInfo {
  std::string id;
  std::string name;
};

class ProfileRegistry {
 public:
  static constexpr const char* kDefaultId = "default";
  static constexpr size_t kMaxProfiles = 12;
  static constexpr size_t kMaxNameLength = 40;

  ProfileRegistry();

  const std::vector<ProfileInfo>& Profiles() const { return profiles_; }
  const ProfileInfo* Find(const std::string& id) const;
  std::string NextDefaultName() const;
  const ProfileInfo* Create(const std::string& name);
  bool Delete(const std::string& id);
  std::vector<std::string> TakePendingDeletes();
  static std::string SanitizeName(const std::string& name);
  std::string Serialize() const;
  void Deserialize(const std::string& text);

 private:
  std::string NewId() const;

  std::vector<ProfileInfo> profiles_;
  std::vector<std::string> pending_deletes_;
  unsigned counter_ = 0;
};

}  // namespace mex

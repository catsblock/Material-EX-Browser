#pragma once
#include <cstdint>
#include <map>
#include <set>
#include <string>
#include <vector>

namespace mex {

enum class Policy { kAsk, kAllow, kDeny };
enum class Decision { kPrompt, kAllow, kDeny, kBlocked };
enum class PromptResult { kAllowOnce, kAllowAlways, kDeny, kDismiss };

struct PermissionEntry {
  std::string origin;
  std::string permission;
  Policy policy = Policy::kAsk;
  int consecutive_denials = 0;
  bool auto_blocked = false;
};

class PermissionPolicyStore {
 public:
  static constexpr int kDenialThreshold = 3;

  Decision Evaluate(const std::string& origin, const std::string& permission, uint64_t scope) const;
  void Record(const std::string& origin, const std::string& permission, uint64_t scope, PromptResult result);
  void SetPolicy(const std::string& origin, const std::string& permission, Policy policy);
  void ResetOrigin(const std::string& origin);
  void ResetAll();
  void ClearScope(uint64_t scope);
  bool HasTemporaryGrant(const std::string& origin, const std::string& permission, uint64_t scope) const;
  const PermissionEntry* Find(const std::string& origin, const std::string& permission) const;
  std::vector<PermissionEntry> Entries() const;
  std::string Serialize() const;
  void Deserialize(const std::string& text);

 private:
  using Key = std::pair<std::string, std::string>;
  using GrantKey = std::pair<uint64_t, Key>;
  std::map<Key, PermissionEntry> entries_;
  std::set<GrantKey> temporary_;
};

}  // namespace mex

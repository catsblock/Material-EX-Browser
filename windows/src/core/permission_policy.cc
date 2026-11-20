#include "core/permission_policy.h"

#include <algorithm>
#include <cstdlib>

#include "core/text_store.h"

namespace mex {

Decision PermissionPolicyStore::Evaluate(const std::string& origin, const std::string& permission,
                                         uint64_t scope) const {
  if (temporary_.count({scope, {origin, permission}})) return Decision::kAllow;
  const PermissionEntry* e = Find(origin, permission);
  if (!e) return Decision::kPrompt;
  if (e->policy == Policy::kAllow) return Decision::kAllow;
  if (e->policy == Policy::kDeny) return Decision::kDeny;
  if (e->auto_blocked) return Decision::kBlocked;
  return Decision::kPrompt;
}

void PermissionPolicyStore::Record(const std::string& origin, const std::string& permission, uint64_t scope,
                                   PromptResult result) {
  PermissionEntry& e = entries_[{origin, permission}];
  e.origin = origin;
  e.permission = permission;
  switch (result) {
    case PromptResult::kAllowOnce:
      e.consecutive_denials = 0;
      temporary_.insert({scope, {origin, permission}});
      break;
    case PromptResult::kAllowAlways:
      e.consecutive_denials = 0;
      e.auto_blocked = false;
      e.policy = Policy::kAllow;
      break;
    case PromptResult::kDeny:
      if (e.policy == Policy::kAsk) {
        ++e.consecutive_denials;
        if (e.consecutive_denials >= kDenialThreshold) e.auto_blocked = true;
      }
      break;
    case PromptResult::kDismiss:
      break;
  }
}

void PermissionPolicyStore::SetPolicy(const std::string& origin, const std::string& permission, Policy policy) {
  PermissionEntry& e = entries_[{origin, permission}];
  e.origin = origin;
  e.permission = permission;
  e.policy = policy;
  e.consecutive_denials = 0;
  e.auto_blocked = false;
  if (policy != Policy::kAllow) {
    for (auto it = temporary_.begin(); it != temporary_.end();) {
      if (it->second.first == origin && it->second.second == permission) {
        it = temporary_.erase(it);
      } else {
        ++it;
      }
    }
  }
}

void PermissionPolicyStore::ResetOrigin(const std::string& origin) {
  for (auto it = entries_.begin(); it != entries_.end();) {
    it = it->first.first == origin ? entries_.erase(it) : std::next(it);
  }
  for (auto it = temporary_.begin(); it != temporary_.end();) {
    it = it->second.first == origin ? temporary_.erase(it) : std::next(it);
  }
}

void PermissionPolicyStore::ResetAll() {
  entries_.clear();
  temporary_.clear();
}

void PermissionPolicyStore::ClearScope(uint64_t scope) {
  for (auto it = temporary_.begin(); it != temporary_.end();) {
    it = it->first == scope ? temporary_.erase(it) : std::next(it);
  }
}

bool PermissionPolicyStore::HasTemporaryGrant(const std::string& origin, const std::string& permission,
                                              uint64_t scope) const {
  return temporary_.count({scope, {origin, permission}}) > 0;
}

const PermissionEntry* PermissionPolicyStore::Find(const std::string& origin, const std::string& permission) const {
  auto it = entries_.find({origin, permission});
  return it == entries_.end() ? nullptr : &it->second;
}

std::vector<PermissionEntry> PermissionPolicyStore::Entries() const {
  std::vector<PermissionEntry> out;
  for (const auto& kv : entries_) out.push_back(kv.second);
  return out;
}

std::string PermissionPolicyStore::Serialize() const {
  std::string out;
  for (const auto& kv : entries_) {
    const PermissionEntry& e = kv.second;
    if (e.policy == Policy::kAsk && e.consecutive_denials == 0 && !e.auto_blocked) continue;
    out += JoinRow({e.origin, e.permission, std::to_string(static_cast<int>(e.policy)),
                    std::to_string(e.consecutive_denials), e.auto_blocked ? "1" : "0"});
    out.push_back('\n');
  }
  return out;
}

void PermissionPolicyStore::Deserialize(const std::string& text) {
  entries_.clear();
  for (const std::string& line : SplitLines(text)) {
    std::vector<std::string> f = SplitRow(line);
    if (f.size() < 5) continue;
    int policy = std::atoi(f[2].c_str());
    if (policy < 0 || policy > 2) continue;
    PermissionEntry e;
    e.origin = f[0];
    e.permission = f[1];
    e.policy = static_cast<Policy>(policy);
    e.consecutive_denials = std::max(0, std::atoi(f[3].c_str()));
    e.auto_blocked = f[4] == "1";
    entries_[{e.origin, e.permission}] = e;
  }
}

}  // namespace mex

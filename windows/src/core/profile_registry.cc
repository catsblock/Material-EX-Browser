#include "core/profile_registry.h"

#include <algorithm>
#include <cctype>
#include <cstdlib>

#include "core/text_store.h"

namespace mex {

ProfileRegistry::ProfileRegistry() { profiles_.push_back({kDefaultId, ""}); }

const ProfileInfo* ProfileRegistry::Find(const std::string& id) const {
  for (const ProfileInfo& p : profiles_) {
    if (p.id == id) return &p;
  }
  return nullptr;
}

std::string ProfileRegistry::SanitizeName(const std::string& name) {
  std::string out;
  for (unsigned char c : name) {
    if (c < 0x20 || c == 0x7f) continue;
    out.push_back(static_cast<char>(c));
  }
  size_t b = 0;
  while (b < out.size() && std::isspace(static_cast<unsigned char>(out[b]))) ++b;
  size_t e = out.size();
  while (e > b && std::isspace(static_cast<unsigned char>(out[e - 1]))) --e;
  out = out.substr(b, e - b);
  if (out.size() > kMaxNameLength) {
    size_t cut = kMaxNameLength;
    while (cut > 0 && (static_cast<unsigned char>(out[cut]) & 0xC0) == 0x80) --cut;
    out = out.substr(0, cut);
  }
  return out;
}

std::string ProfileRegistry::NextDefaultName() const {
  for (int i = 2; i < 1000; ++i) {
    std::string candidate = "Profile " + std::to_string(i);
    bool taken = std::any_of(profiles_.begin(), profiles_.end(),
                             [&](const ProfileInfo& p) { return p.name == candidate; });
    if (!taken) return candidate;
  }
  return "Profile";
}

std::string ProfileRegistry::NewId() const {
  for (unsigned n = counter_ + 1;; ++n) {
    std::string id = "p" + std::to_string(n);
    bool used = Find(id) != nullptr ||
                std::find(pending_deletes_.begin(), pending_deletes_.end(), id) != pending_deletes_.end();
    if (!used) return id;
  }
}

const ProfileInfo* ProfileRegistry::Create(const std::string& name) {
  if (profiles_.size() >= kMaxProfiles) return nullptr;
  std::string clean = SanitizeName(name);
  if (clean.empty()) clean = NextDefaultName();
  std::string id = NewId();
  counter_ = static_cast<unsigned>(std::atoi(id.c_str() + 1));
  profiles_.push_back({id, clean});
  return &profiles_.back();
}

bool ProfileRegistry::Delete(const std::string& id) {
  if (id == kDefaultId) return false;
  auto it = std::find_if(profiles_.begin(), profiles_.end(), [&](const ProfileInfo& p) { return p.id == id; });
  if (it == profiles_.end()) return false;
  profiles_.erase(it);
  pending_deletes_.push_back(id);
  return true;
}

std::vector<std::string> ProfileRegistry::TakePendingDeletes() {
  std::vector<std::string> out;
  out.swap(pending_deletes_);
  return out;
}

std::string ProfileRegistry::Serialize() const {
  std::string out;
  for (const ProfileInfo& p : profiles_) {
    if (p.id == kDefaultId) continue;
    out += JoinRow({"profile", p.id, p.name}) + "\n";
  }
  for (const std::string& id : pending_deletes_) out += JoinRow({"delete", id, ""}) + "\n";
  return out;
}

void ProfileRegistry::Deserialize(const std::string& text) {
  profiles_.clear();
  profiles_.push_back({kDefaultId, ""});
  pending_deletes_.clear();
  counter_ = 0;
  for (const std::string& line : SplitLines(text)) {
    std::vector<std::string> f = SplitRow(line);
    if (f.size() < 3) continue;
    bool valid_id = f[1].size() > 1 && f[1][0] == 'p' &&
                    std::all_of(f[1].begin() + 1, f[1].end(), [](unsigned char c) { return std::isdigit(c); });
    if (!valid_id) continue;
    counter_ = std::max(counter_, static_cast<unsigned>(std::atoi(f[1].c_str() + 1)));
    if (f[0] == "profile" && profiles_.size() < kMaxProfiles && !Find(f[1])) {
      std::string name = SanitizeName(f[2]);
      profiles_.push_back({f[1], name.empty() ? "Profile" : name});
    } else if (f[0] == "delete") {
      pending_deletes_.push_back(f[1]);
    }
  }
}

}  // namespace mex

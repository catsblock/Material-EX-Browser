#include "core/records.h"

#include <algorithm>
#include <cstdlib>

#include "core/text_store.h"

namespace mex {

void HistoryStore::Add(const std::string& url, const std::string& title, int64_t time) {
  if (!entries_.empty() && entries_.back().url == url) {
    entries_.back().title = title.empty() ? entries_.back().title : title;
    entries_.back().time = time;
    return;
  }
  entries_.push_back({url, title, time});
  if (entries_.size() > kMaxEntries) {
    entries_.erase(entries_.begin(), entries_.begin() + static_cast<long>(entries_.size() - kMaxEntries));
  }
}

void HistoryStore::Clear() { entries_.clear(); }

void HistoryStore::ClearSince(int64_t time) {
  entries_.erase(std::remove_if(entries_.begin(), entries_.end(),
                                [time](const HistoryEntry& e) { return e.time >= time; }),
                 entries_.end());
}

std::string HistoryStore::Serialize() const {
  std::string out;
  for (const HistoryEntry& e : entries_) out += JoinRow({e.url, e.title, std::to_string(e.time)}) + "\n";
  return out;
}

void HistoryStore::Deserialize(const std::string& text) {
  entries_.clear();
  for (const std::string& line : SplitLines(text)) {
    std::vector<std::string> f = SplitRow(line);
    if (f.size() < 3) continue;
    entries_.push_back({f[0], f[1], std::strtoll(f[2].c_str(), nullptr, 10)});
  }
}

bool BookmarkStore::Contains(const std::string& url) const {
  return std::any_of(entries_.begin(), entries_.end(), [&](const Bookmark& b) { return b.url == url; });
}

bool BookmarkStore::Toggle(const std::string& url, const std::string& title) {
  if (Contains(url)) {
    Remove(url);
    return false;
  }
  entries_.push_back({url, title});
  return true;
}

void BookmarkStore::Remove(const std::string& url) {
  entries_.erase(std::remove_if(entries_.begin(), entries_.end(), [&](const Bookmark& b) { return b.url == url; }),
                 entries_.end());
}

std::string BookmarkStore::Serialize() const {
  std::string out;
  for (const Bookmark& b : entries_) out += JoinRow({b.url, b.title}) + "\n";
  return out;
}

void BookmarkStore::Deserialize(const std::string& text) {
  entries_.clear();
  for (const std::string& line : SplitLines(text)) {
    std::vector<std::string> f = SplitRow(line);
    if (f.size() < 2) continue;
    entries_.push_back({f[0], f[1]});
  }
}

std::string KeyValueStore::Get(const std::string& key, const std::string& fallback) const {
  auto it = values_.find(key);
  return it == values_.end() ? fallback : it->second;
}

void KeyValueStore::Set(const std::string& key, const std::string& value) { values_[key] = value; }

void KeyValueStore::Remove(const std::string& key) { values_.erase(key); }

std::vector<std::pair<std::string, std::string>> KeyValueStore::WithPrefix(const std::string& prefix) const {
  std::vector<std::pair<std::string, std::string>> out;
  for (const auto& kv : values_) {
    if (kv.first.rfind(prefix, 0) == 0) out.push_back(kv);
  }
  return out;
}

std::string KeyValueStore::Serialize() const {
  std::string out;
  for (const auto& kv : values_) out += JoinRow({kv.first, kv.second}) + "\n";
  return out;
}

void KeyValueStore::Deserialize(const std::string& text) {
  values_.clear();
  for (const std::string& line : SplitLines(text)) {
    std::vector<std::string> f = SplitRow(line);
    if (f.size() < 2) continue;
    values_[f[0]] = f[1];
  }
}

}  // namespace mex

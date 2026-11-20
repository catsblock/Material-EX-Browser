#pragma once
#include <cstdint>
#include <map>
#include <string>
#include <vector>

namespace mex {

struct HistoryEntry {
  std::string url;
  std::string title;
  int64_t time = 0;
};

struct Bookmark {
  std::string url;
  std::string title;
};

class HistoryStore {
 public:
  static constexpr size_t kMaxEntries = 5000;
  void Add(const std::string& url, const std::string& title, int64_t time);
  void Clear();
  void ClearSince(int64_t time);
  const std::vector<HistoryEntry>& Entries() const { return entries_; }
  std::string Serialize() const;
  void Deserialize(const std::string& text);

 private:
  std::vector<HistoryEntry> entries_;
};

class BookmarkStore {
 public:
  bool Contains(const std::string& url) const;
  bool Toggle(const std::string& url, const std::string& title);
  void Remove(const std::string& url);
  const std::vector<Bookmark>& Entries() const { return entries_; }
  std::string Serialize() const;
  void Deserialize(const std::string& text);

 private:
  std::vector<Bookmark> entries_;
};

class KeyValueStore {
 public:
  std::string Get(const std::string& key, const std::string& fallback = "") const;
  void Set(const std::string& key, const std::string& value);
  void Remove(const std::string& key);
  std::vector<std::pair<std::string, std::string>> WithPrefix(const std::string& prefix) const;
  std::string Serialize() const;
  void Deserialize(const std::string& text);

 private:
  std::map<std::string, std::string> values_;
};

}  // namespace mex

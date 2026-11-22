#pragma once
#include <cstdint>
#include <string>
#include <vector>

#include "include/cef_download_handler.h"
#include "include/cef_download_item.h"

namespace mex {

enum class DownloadState { kDownloading, kPaused, kComplete, kCancelled, kInterrupted, kBlocked };

struct DownloadRecord {
  uint32_t id = 0;
  std::string url;
  std::string filename;
  std::wstring path;
  int64_t received = 0;
  int64_t total = -1;
  int64_t speed = 0;
  int64_t started = 0;
  int64_t finished = 0;
  DownloadState state = DownloadState::kDownloading;
  std::string note;
  bool is_private = false;
  CefRefPtr<CefDownloadItemCallback> callback;
};

class DownloadManager {
 public:
  static DownloadManager& Get();
  void Load();
  void Save() const;
  void Update(CefRefPtr<CefDownloadItem> item, CefRefPtr<CefDownloadItemCallback> callback, bool is_private);
  void RemoveFinishedPrivate();
  void AddBlocked(const std::string& url, const std::string& filename, const std::string& note,
                  bool is_private = false);
  bool AllowNewDownload();
  void Pause(uint32_t id);
  void Resume(uint32_t id);
  void Cancel(uint32_t id);
  void Remove(uint32_t id);
  void ClearFinished();
  void MarkAllInterrupted();
  const std::vector<DownloadRecord>& Records() const { return records_; }
  const DownloadRecord* Find(uint32_t id) const;

 private:
  DownloadRecord* FindMutable(uint32_t id);
  std::vector<DownloadRecord> records_;
  std::vector<int64_t> recent_starts_;
  uint32_t next_blocked_id_ = 0x80000000u;
};

std::wstring DownloadStateText(const DownloadRecord& r);

}  // namespace mex

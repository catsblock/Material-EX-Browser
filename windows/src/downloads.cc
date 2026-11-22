#include "downloads.h"

#include <algorithm>
#include <cstdlib>

#include "core/text_store.h"
#include "platform.h"
#include "strings.h"

namespace mex {

DownloadManager& DownloadManager::Get() {
  static DownloadManager m;
  return m;
}

void DownloadManager::Load() {
  std::string text;
  if (!ReadTextFile(DataDir() + L"\\downloads.tsv", &text)) return;
  for (const std::string& line : SplitLines(text)) {
    std::vector<std::string> f = SplitRow(line);
    if (f.size() < 8) continue;
    DownloadRecord r;
    r.id = next_blocked_id_++;
    r.url = f[0];
    r.filename = f[1];
    r.path = Widen(f[2]);
    r.received = std::strtoll(f[3].c_str(), nullptr, 10);
    r.total = std::strtoll(f[4].c_str(), nullptr, 10);
    r.started = std::strtoll(f[5].c_str(), nullptr, 10);
    r.finished = std::strtoll(f[6].c_str(), nullptr, 10);
    int state = std::atoi(f[7].c_str());
    if (state < 0 || state > 5) continue;
    r.state = static_cast<DownloadState>(state);
    if (f.size() > 8) r.note = f[8];
    records_.push_back(std::move(r));
  }
}

void DownloadManager::Save() const {
  std::string out;
  for (const DownloadRecord& r : records_) {
    if (r.is_private) continue;
    DownloadState state = r.state;
    if (state == DownloadState::kDownloading || state == DownloadState::kPaused) state = DownloadState::kInterrupted;
    out += JoinRow({r.url, r.filename, Narrow(r.path), std::to_string(r.received), std::to_string(r.total),
                    std::to_string(r.started), std::to_string(r.finished),
                    std::to_string(static_cast<int>(state)), r.note}) +
           "\n";
  }
  WriteTextFile(DataDir() + L"\\downloads.tsv", out);
}

DownloadRecord* DownloadManager::FindMutable(uint32_t id) {
  for (DownloadRecord& r : records_) {
    if (r.id == id) return &r;
  }
  return nullptr;
}

const DownloadRecord* DownloadManager::Find(uint32_t id) const {
  for (const DownloadRecord& r : records_) {
    if (r.id == id) return &r;
  }
  return nullptr;
}

void DownloadManager::Update(CefRefPtr<CefDownloadItem> item, CefRefPtr<CefDownloadItemCallback> callback,
                             bool is_private) {
  if (!item->IsValid()) return;
  DownloadRecord* r = FindMutable(item->GetId());
  if (!r) {
    records_.emplace_back();
    r = &records_.back();
    r->id = item->GetId();
    r->started = NowSeconds();
    r->is_private = is_private;
  }
  r->url = item->GetURL().ToString();
  std::wstring path = item->GetFullPath().ToWString();
  if (!path.empty()) {
    r->path = path;
    size_t slash = path.find_last_of(L"\\/");
    r->filename = Narrow(slash == std::wstring::npos ? path : path.substr(slash + 1));
  }
  r->received = item->GetReceivedBytes();
  r->total = item->GetTotalBytes();
  r->speed = item->GetCurrentSpeed();
  r->callback = callback;
  if (item->IsComplete()) {
    r->state = DownloadState::kComplete;
    r->finished = NowSeconds();
    r->callback = nullptr;
  } else if (item->IsCanceled()) {
    r->state = DownloadState::kCancelled;
    r->finished = NowSeconds();
    r->callback = nullptr;
  } else if (item->IsInterrupted()) {
    r->state = DownloadState::kInterrupted;
    r->finished = NowSeconds();
    r->callback = nullptr;
  } else if (r->state != DownloadState::kPaused || item->IsInProgress()) {
    if (r->state != DownloadState::kPaused) r->state = DownloadState::kDownloading;
  }
  if (r->state != DownloadState::kDownloading && r->state != DownloadState::kPaused) Save();
}

void DownloadManager::AddBlocked(const std::string& url, const std::string& filename, const std::string& note,
                                 bool is_private) {
  DownloadRecord r;
  r.id = next_blocked_id_++;
  r.url = url;
  r.filename = filename;
  r.state = DownloadState::kBlocked;
  r.started = r.finished = NowSeconds();
  r.note = note;
  r.is_private = is_private;
  records_.push_back(std::move(r));
  Save();
}

bool DownloadManager::AllowNewDownload() {
  int64_t now = NowSeconds();
  recent_starts_.erase(std::remove_if(recent_starts_.begin(), recent_starts_.end(),
                                      [now](int64_t t) { return now - t > 5; }),
                       recent_starts_.end());
  if (recent_starts_.size() >= 3) return false;
  recent_starts_.push_back(now);
  return true;
}

void DownloadManager::Pause(uint32_t id) {
  DownloadRecord* r = FindMutable(id);
  if (!r || !r->callback || r->state != DownloadState::kDownloading) return;
  r->callback->Pause();
  r->state = DownloadState::kPaused;
}

void DownloadManager::Resume(uint32_t id) {
  DownloadRecord* r = FindMutable(id);
  if (!r || !r->callback || r->state != DownloadState::kPaused) return;
  r->callback->Resume();
  r->state = DownloadState::kDownloading;
}

void DownloadManager::Cancel(uint32_t id) {
  DownloadRecord* r = FindMutable(id);
  if (!r || !r->callback) return;
  r->callback->Cancel();
}

void DownloadManager::Remove(uint32_t id) {
  records_.erase(std::remove_if(records_.begin(), records_.end(),
                                [id](const DownloadRecord& r) { return r.id == id && !r.callback; }),
                 records_.end());
  Save();
}

void DownloadManager::ClearFinished() {
  records_.erase(std::remove_if(records_.begin(), records_.end(), [](const DownloadRecord& r) { return !r.callback; }),
                 records_.end());
  Save();
}

void DownloadManager::RemoveFinishedPrivate() {
  records_.erase(std::remove_if(records_.begin(), records_.end(),
                                [](const DownloadRecord& r) { return r.is_private && !r.callback; }),
                 records_.end());
}

void DownloadManager::MarkAllInterrupted() {
  for (DownloadRecord& r : records_) {
    if (r.callback) {
      r.callback->Cancel();
    }
  }
}

std::wstring DownloadStateText(const DownloadRecord& r) {
  switch (r.state) {
    case DownloadState::kDownloading: return Tr(Str::StateDownloading);
    case DownloadState::kPaused: return Tr(Str::StatePaused);
    case DownloadState::kComplete: return Tr(Str::StateComplete);
    case DownloadState::kCancelled: return Tr(Str::StateCancelled);
    case DownloadState::kInterrupted: return Tr(Str::StateInterrupted);
    case DownloadState::kBlocked: return Tr(Str::StateBlocked);
  }
  return L"";
}

}  // namespace mex

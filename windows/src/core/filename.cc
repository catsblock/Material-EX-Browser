#include "core/filename.h"

#include <algorithm>
#include <array>

#include "core/url_utils.h"

namespace mex {

std::string ExtensionOf(const std::string& name) {
  size_t dot = name.rfind('.');
  if (dot == std::string::npos || dot == 0 || dot + 1 == name.size()) return "";
  return ToLower(name.substr(dot + 1));
}

static bool IsReservedDeviceName(const std::string& stem) {
  static const std::array<const char*, 4> plain = {"con", "prn", "aux", "nul"};
  std::string s = ToLower(stem);
  for (const char* p : plain) {
    if (s == p) return true;
  }
  return s.size() == 4 && (s.rfind("com", 0) == 0 || s.rfind("lpt", 0) == 0) && s[3] >= '1' && s[3] <= '9';
}

std::string SanitizeFilename(const std::string& name, const std::string& fallback) {
  std::string base = name;
  size_t slash = base.find_last_of("/\\");
  if (slash != std::string::npos) base = base.substr(slash + 1);
  std::string out;
  for (unsigned char c : base) {
    if (c < 0x20 || c == 0x7f) continue;
    if (std::string("<>:\"|?*").find(static_cast<char>(c)) != std::string::npos) {
      out.push_back('_');
    } else {
      out.push_back(static_cast<char>(c));
    }
  }
  while (!out.empty() && (out.back() == '.' || out.back() == ' ')) out.pop_back();
  size_t lead = 0;
  while (lead < out.size() && (out[lead] == ' ' || out[lead] == '.')) ++lead;
  out = out.substr(lead);
  if (out.empty()) out = fallback.empty() ? "download" : fallback;
  size_t dot = out.find('.');
  std::string stem = dot == std::string::npos ? out : out.substr(0, dot);
  if (IsReservedDeviceName(stem)) out = "_" + out;
  const size_t kMax = 120;
  if (out.size() > kMax) {
    std::string ext = ExtensionOf(out);
    size_t keep = ext.empty() ? kMax : kMax - ext.size() - 1;
    std::string stem_part = out.substr(0, keep);
    out = ext.empty() ? stem_part : stem_part + "." + ext;
  }
  return out;
}

bool IsDangerousExtension(const std::string& name) {
  static const std::array<const char*, 38> dangerous = {
      "exe", "msi", "msp", "bat", "cmd", "com", "scr", "ps1", "psm1", "vbs", "vbe", "js", "jse",
      "wsf", "wsh", "hta", "jar", "lnk", "reg", "dll", "msix", "appx", "pif", "cpl", "msc", "gadget",
      "application", "chm", "inf", "ins", "isp", "ws", "xll", "iso", "img", "vhd", "appinstaller", "url"};
  std::string ext = ExtensionOf(name);
  if (ext.empty()) return false;
  return std::find(dangerous.begin(), dangerous.end(), ext) != dangerous.end();
}

bool HasMimeMismatch(const std::string& name, const std::string& mime) {
  std::string m = ToLower(mime);
  bool media = m.rfind("image/", 0) == 0 || m.rfind("video/", 0) == 0 || m.rfind("audio/", 0) == 0 ||
               m == "text/plain" || m == "application/pdf";
  if (media && IsDangerousExtension(name)) return true;
  bool executable_mime = m == "application/x-msdownload" || m == "application/x-msdos-program" ||
                         m == "application/vnd.microsoft.portable-executable";
  return executable_mime && !IsDangerousExtension(name);
}

std::string UniqueFilename(const std::string& name, const std::function<bool(const std::string&)>& exists) {
  if (!exists(name)) return name;
  size_t dot = name.rfind('.');
  bool has_ext = dot != std::string::npos && dot != 0;
  std::string stem = has_ext ? name.substr(0, dot) : name;
  std::string ext = has_ext ? name.substr(dot) : "";
  for (int i = 1; i < 10000; ++i) {
    std::string candidate = stem + " (" + std::to_string(i) + ")" + ext;
    if (!exists(candidate)) return candidate;
  }
  return stem + " (copy)" + ext;
}

}  // namespace mex

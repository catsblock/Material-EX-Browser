#include "core/url_utils.h"

#include <algorithm>
#include <cctype>

namespace mex {

std::string Trim(const std::string& s) {
  size_t b = 0;
  size_t e = s.size();
  while (b < e && std::isspace(static_cast<unsigned char>(s[b]))) ++b;
  while (e > b && std::isspace(static_cast<unsigned char>(s[e - 1]))) --e;
  return s.substr(b, e - b);
}

std::string ToLower(std::string s) {
  std::transform(s.begin(), s.end(), s.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return s;
}

std::string PercentEncode(const std::string& s) {
  static const char* hex = "0123456789ABCDEF";
  std::string out;
  for (unsigned char c : s) {
    if (std::isalnum(c) || c == '-' || c == '_' || c == '.' || c == '~') {
      out.push_back(static_cast<char>(c));
    } else if (c == ' ') {
      out.push_back('+');
    } else {
      out.push_back('%');
      out.push_back(hex[c >> 4]);
      out.push_back(hex[c & 15]);
    }
  }
  return out;
}

std::string SchemeOf(const std::string& url) {
  size_t colon = url.find(':');
  if (colon == std::string::npos || colon == 0) return "";
  for (size_t i = 0; i < colon; ++i) {
    unsigned char c = static_cast<unsigned char>(url[i]);
    if (!(std::isalnum(c) || c == '+' || c == '-' || c == '.')) return "";
  }
  return ToLower(url.substr(0, colon));
}

static std::string AuthorityOf(const std::string& url) {
  size_t start = url.find("://");
  if (start == std::string::npos) return "";
  start += 3;
  size_t end = url.find_first_of("/?#", start);
  std::string authority = url.substr(start, end == std::string::npos ? std::string::npos : end - start);
  size_t at = authority.rfind('@');
  if (at != std::string::npos) authority = authority.substr(at + 1);
  return authority;
}

std::string HostOf(const std::string& url) {
  std::string authority = AuthorityOf(url);
  if (authority.empty()) return "";
  if (authority[0] == '[') {
    size_t close = authority.find(']');
    return ToLower(authority.substr(0, close == std::string::npos ? std::string::npos : close + 1));
  }
  size_t colon = authority.find(':');
  if (colon != std::string::npos) authority = authority.substr(0, colon);
  return ToLower(authority);
}

std::string OriginOf(const std::string& url) {
  std::string scheme = SchemeOf(url);
  std::string authority = AuthorityOf(url);
  if (scheme.empty() || authority.empty()) return "";
  return scheme + "://" + ToLower(authority);
}

bool IsWebScheme(const std::string& scheme) {
  return scheme == "http" || scheme == "https";
}

static bool LooksLikeHost(const std::string& s) {
  if (s.find(' ') != std::string::npos) return false;
  std::string host = s.substr(0, s.find_first_of("/?#"));
  size_t colon = host.rfind(':');
  if (colon != std::string::npos) {
    std::string port = host.substr(colon + 1);
    if (port.empty() || !std::all_of(port.begin(), port.end(), [](unsigned char c) { return std::isdigit(c); })) {
      return false;
    }
    host = host.substr(0, colon);
  }
  if (host.empty()) return false;
  if (ToLower(host) == "localhost") return true;
  if (host.find('.') == std::string::npos) return false;
  if (host.front() == '.' || host.back() == '.') return false;
  return std::all_of(host.begin(), host.end(), [](unsigned char c) {
    return std::isalnum(c) || c == '.' || c == '-' || c >= 0x80;
  });
}

std::string NormalizeInput(const std::string& input, const std::string& search_template) {
  std::string text = Trim(input);
  if (text.empty()) return "";
  std::string lower = ToLower(text);
  if (lower == "about:blank") return "about:blank";
  if (lower.rfind("http://", 0) == 0 || lower.rfind("https://", 0) == 0) {
    return HostOf(text).empty() ? "" : text;
  }
  if (lower.rfind("file:///", 0) == 0) return text;
  if (LooksLikeHost(text)) {
    bool local = ToLower(text.substr(0, text.find_first_of(":/?#"))) == "localhost";
    return (local ? "http://" : "https://") + text;
  }
  std::string tmpl = search_template.empty() ? "https://duckduckgo.com/?q=%s" : search_template;
  size_t pos = tmpl.find("%s");
  std::string encoded = PercentEncode(text);
  if (pos == std::string::npos) return tmpl + encoded;
  return tmpl.substr(0, pos) + encoded + tmpl.substr(pos + 2);
}

std::string EscapeHtml(const std::string& s) {
  std::string out;
  for (char c : s) {
    switch (c) {
      case '&': out += "&amp;"; break;
      case '<': out += "&lt;"; break;
      case '>': out += "&gt;"; break;
      case '"': out += "&quot;"; break;
      case '\'': out += "&#39;"; break;
      default: out.push_back(c);
    }
  }
  return out;
}

}  // namespace mex

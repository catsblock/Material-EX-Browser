#include "core/text_store.h"

namespace mex {

std::string EscapeField(const std::string& s) {
  std::string out;
  for (char c : s) {
    switch (c) {
      case '\\': out += "\\\\"; break;
      case '\t': out += "\\t"; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      default: out.push_back(c);
    }
  }
  return out;
}

std::string UnescapeField(const std::string& s) {
  std::string out;
  for (size_t i = 0; i < s.size(); ++i) {
    if (s[i] == '\\' && i + 1 < s.size()) {
      ++i;
      switch (s[i]) {
        case 't': out.push_back('\t'); break;
        case 'n': out.push_back('\n'); break;
        case 'r': out.push_back('\r'); break;
        default: out.push_back(s[i]);
      }
    } else {
      out.push_back(s[i]);
    }
  }
  return out;
}

std::string JoinRow(const std::vector<std::string>& fields) {
  std::string out;
  for (size_t i = 0; i < fields.size(); ++i) {
    if (i) out.push_back('\t');
    out += EscapeField(fields[i]);
  }
  return out;
}

std::vector<std::string> SplitRow(const std::string& line) {
  std::vector<std::string> out;
  size_t start = 0;
  while (true) {
    size_t tab = line.find('\t', start);
    if (tab == std::string::npos) {
      out.push_back(UnescapeField(line.substr(start)));
      break;
    }
    out.push_back(UnescapeField(line.substr(start, tab - start)));
    start = tab + 1;
  }
  return out;
}

std::vector<std::string> SplitLines(const std::string& text) {
  std::vector<std::string> lines;
  size_t start = 0;
  while (start <= text.size()) {
    size_t nl = text.find('\n', start);
    std::string line = text.substr(start, nl == std::string::npos ? std::string::npos : nl - start);
    if (!line.empty() && line.back() == '\r') line.pop_back();
    if (!line.empty()) lines.push_back(line);
    if (nl == std::string::npos) break;
    start = nl + 1;
  }
  return lines;
}

std::string JsonQuote(const std::string& s) {
  static const char* hex = "0123456789abcdef";
  std::string out = "\"";
  for (unsigned char c : s) {
    switch (c) {
      case '"': out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default:
        if (c < 0x20) {
          out += "\\u00";
          out.push_back(hex[c >> 4]);
          out.push_back(hex[c & 15]);
        } else {
          out.push_back(static_cast<char>(c));
        }
    }
  }
  out.push_back('"');
  return out;
}

}  // namespace mex

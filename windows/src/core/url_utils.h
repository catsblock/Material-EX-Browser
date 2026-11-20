#pragma once
#include <string>

namespace mex {

std::string Trim(const std::string& s);
std::string ToLower(std::string s);
std::string PercentEncode(const std::string& s);
std::string SchemeOf(const std::string& url);
std::string HostOf(const std::string& url);
std::string OriginOf(const std::string& url);
bool IsWebScheme(const std::string& scheme);
std::string NormalizeInput(const std::string& input, const std::string& search_template);
std::string EscapeHtml(const std::string& s);

}  // namespace mex

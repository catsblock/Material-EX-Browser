#pragma once
#include <string>
#include <vector>

namespace mex {

std::string EscapeField(const std::string& s);
std::string UnescapeField(const std::string& s);
std::string JoinRow(const std::vector<std::string>& fields);
std::vector<std::string> SplitRow(const std::string& line);
std::vector<std::string> SplitLines(const std::string& text);
std::string JsonQuote(const std::string& s);

}  // namespace mex

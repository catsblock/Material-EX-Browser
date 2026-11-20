#pragma once
#include <functional>
#include <string>

namespace mex {

std::string SanitizeFilename(const std::string& name, const std::string& fallback);
std::string ExtensionOf(const std::string& name);
bool IsDangerousExtension(const std::string& name);
bool HasMimeMismatch(const std::string& name, const std::string& mime);
std::string UniqueFilename(const std::string& name, const std::function<bool(const std::string&)>& exists);

}  // namespace mex

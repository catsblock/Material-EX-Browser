#include "strings.h"

#include <windows.h>

#include <cwchar>
#include <string>
#include <unordered_map>

#include "strings_i18n.h"

namespace mex {

namespace {

std::wstring CurrentLanguage() {
  wchar_t name[LOCALE_NAME_MAX_LENGTH] = {};
  if (!GetUserDefaultLocaleName(name, LOCALE_NAME_MAX_LENGTH)) return L"en";
  std::wstring locale = name;
  if (locale == L"pt-BR") return locale;
  size_t dash = locale.find(L'-');
  std::wstring lang = dash == std::wstring::npos ? locale : locale.substr(0, dash);
  return lang;
}

const std::unordered_map<int, const wchar_t*>& Overrides() {
  static const std::unordered_map<int, const wchar_t*> table = [] {
    std::unordered_map<int, const wchar_t*> t;
    std::wstring lang = CurrentLanguage();
    for (int i = 0; i < kI18nEntryCount; ++i) {
      if (lang == kI18nEntries[i].lang) t[static_cast<int>(kI18nEntries[i].key)] = kI18nEntries[i].text;
    }
    return t;
  }();
  return table;
}

}  // namespace

const wchar_t* Tr(Str s) {
  static const wchar_t* const table[] = {
#define X(name, text) text,
      MEX_STRINGS(X)
#undef X
  };
  const auto& overrides = Overrides();
  auto it = overrides.find(static_cast<int>(s));
  return it != overrides.end() ? it->second : table[static_cast<int>(s)];
}

}  // namespace mex

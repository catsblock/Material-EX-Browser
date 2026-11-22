#pragma once
#include "strings.h"

namespace mex {

struct I18nEntry {
  const wchar_t* lang;
  Str key;
  const wchar_t* text;
};

extern const I18nEntry kI18nEntries[];
extern const int kI18nEntryCount;

}  // namespace mex

#include "strings.h"

namespace mex {

const wchar_t* Tr(Str s) {
  static const wchar_t* const table[] = {
#define X(name, text) text,
      MEX_STRINGS(X)
#undef X
  };
  return table[static_cast<int>(s)];
}

}  // namespace mex

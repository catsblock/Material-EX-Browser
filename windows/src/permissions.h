#pragma once
#include <windows.h>

#include <cstdint>
#include <string>
#include <vector>

#include "core/permission_policy.h"
#include "strings.h"

namespace mex {

struct PermissionBit {
  uint32_t mask;
  std::string name;
};

struct PermissionOutcome {
  uint32_t allowed_mask = 0;
  bool dismissed = false;
};

const wchar_t* PermissionLabel(const std::string& name);
PermissionOutcome ResolvePermissions(HWND parent, const std::string& origin,
                                     const std::vector<PermissionBit>& bits, uint64_t scope);

}  // namespace mex

#include "permissions.h"

#include "platform.h"
#include "profile.h"

namespace mex {

const wchar_t* PermissionLabel(const std::string& name) {
  if (name == "camera") return Tr(Str::PermCamera);
  if (name == "microphone") return Tr(Str::PermMicrophone);
  if (name == "screen-capture") return Tr(Str::PermScreen);
  if (name == "location") return Tr(Str::PermLocation);
  if (name == "notifications") return Tr(Str::PermNotifications);
  if (name == "clipboard") return Tr(Str::PermClipboard);
  if (name == "midi") return Tr(Str::PermMidi);
  if (name == "multiple-downloads") return Tr(Str::PermMultipleDownloads);
  if (name == "local-fonts") return Tr(Str::PermLocalFonts);
  return L"?";
}

PermissionOutcome ResolvePermissions(HWND parent, const std::string& origin,
                                     const std::vector<PermissionBit>& bits, uint64_t scope) {
  Profile& profile = Profile::Get();
  PermissionOutcome outcome;
  std::vector<PermissionBit> to_prompt;
  for (const PermissionBit& bit : bits) {
    switch (profile.permissions.Evaluate(origin, bit.name, scope)) {
      case Decision::kAllow: outcome.allowed_mask |= bit.mask; break;
      case Decision::kPrompt: to_prompt.push_back(bit); break;
      case Decision::kDeny:
      case Decision::kBlocked: break;
    }
  }
  if (to_prompt.empty() || origin.empty()) return outcome;

  std::wstring names;
  for (size_t i = 0; i < to_prompt.size(); ++i) {
    if (i) names += L", ";
    names += PermissionLabel(to_prompt[i].name);
  }
  std::wstring woring = Widen(origin);
  std::wstring text(1024, L'\0');
  int n = swprintf(text.data(), text.size(), Tr(Str::PermissionBody), woring.c_str(), names.c_str());
  text.resize(n > 0 ? static_cast<size_t>(n) : 0);

  int answer = MessageBoxW(parent, text.c_str(), Tr(Str::PermissionTitle),
                           MB_YESNOCANCEL | MB_ICONQUESTION | MB_DEFBUTTON3 | MB_APPLMODAL);
  PromptResult result = answer == IDYES ? PromptResult::kAllowOnce
                        : answer == IDNO ? PromptResult::kDeny
                                         : PromptResult::kDismiss;
  for (const PermissionBit& bit : to_prompt) {
    profile.permissions.Record(origin, bit.name, scope, result);
    if (result == PromptResult::kAllowOnce) outcome.allowed_mask |= bit.mask;
  }
  outcome.dismissed = result == PromptResult::kDismiss;
  profile.SavePermissions();
  return outcome;
}

}  // namespace mex

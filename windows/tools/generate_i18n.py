import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from translations import KEYS, T, KEYS2, T2, KEYS3, T3, KEYS4, T4

def esc(s):
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")

out = ['#include "strings_i18n.h"', '', 'namespace mex {', '', 'const I18nEntry kI18nEntries[] = {']
for lang, vals in T.items():
    assert len(vals) == len(KEYS), lang
    for k, v in zip(KEYS, vals):
        out.append('    {L"%s", Str::%s, L"%s"},' % (lang, k, esc(v)))
    vals4 = T4[lang]
    assert len(vals4) == len(KEYS4), lang
    for k, v in zip(KEYS4, vals4):
        out.append('    {L"%s", Str::%s, L"%s"},' % (lang, k, esc(v)))
    vals3 = T3[lang]
    assert len(vals3) == len(KEYS3), lang
    for k, v in zip(KEYS3, vals3):
        out.append('    {L"%s", Str::%s, L"%s"},' % (lang, k, esc(v)))
    vals2 = T2[lang]
    assert len(vals2) == len(KEYS2), lang
    for k, v in zip(KEYS2, vals2):
        out.append('    {L"%s", Str::%s, L"%s"},' % (lang, k, esc(v)))
out += ['};', '', 'const int kI18nEntryCount = sizeof(kI18nEntries) / sizeof(kI18nEntries[0]);', '', '}  // namespace mex', '']
path = os.path.join(os.path.dirname(__file__), '..', 'src', 'strings_i18n.cc')
open(path, 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print(len(out))

#include <cstdio>
#include <cstdlib>
#include <set>
#include <string>

#include "core/filename.h"
#include "core/permission_policy.h"
#include "core/records.h"
#include "core/text_store.h"
#include "core/url_utils.h"

static int g_failures = 0;

#define CHECK(cond)                                                      \
  do {                                                                   \
    if (!(cond)) {                                                       \
      std::printf("FAIL %s:%d %s\n", __FILE__, __LINE__, #cond);         \
      ++g_failures;                                                      \
    }                                                                    \
  } while (0)

using namespace mex;

static void TestUrls() {
  const std::string t = "https://search.example/?q=%s";
  CHECK(NormalizeInput("example.com", t) == "https://example.com");
  CHECK(NormalizeInput("localhost:8080/x", t) == "http://localhost:8080/x");
  CHECK(NormalizeInput("hello world", t) == "https://search.example/?q=hello+world");
  CHECK(NormalizeInput("javascript:alert(1)", t) == "https://search.example/?q=javascript%3Aalert%281%29");
  CHECK(NormalizeInput("data:text/html,<b>", t).rfind("https://search.example/", 0) == 0);
  CHECK(NormalizeInput("  ", t).empty());
  CHECK(NormalizeInput("https://a.b/c", t) == "https://a.b/c");
  CHECK(HostOf("https://User:pw@Example.com:8443/p?q") == "example.com");
  CHECK(OriginOf("https://Example.com:8443/p") == "https://example.com:8443");
  CHECK(SchemeOf("mailto:a@b.c") == "mailto");
  CHECK(SchemeOf("no scheme") == "");
  CHECK(EscapeHtml("<a href=\"x\">&") == "&lt;a href=&quot;x&quot;&gt;&amp;");
}

static void TestFilenames() {
  CHECK(SanitizeFilename("../../evil.txt", "f") == "evil.txt");
  CHECK(SanitizeFilename("C:\\Windows\\a.dll", "f") == "a.dll");
  CHECK(SanitizeFilename("a<b>c.txt", "f") == "a_b_c.txt");
  CHECK(SanitizeFilename("con.txt", "f") == "_con.txt");
  CHECK(SanitizeFilename("...", "fallback") == "fallback");
  CHECK(SanitizeFilename("name. ", "f") == "name");
  CHECK(SanitizeFilename(std::string(300, 'a') + ".pdf", "f").size() == 120);
  CHECK(IsDangerousExtension("setup.EXE"));
  CHECK(IsDangerousExtension("photo.jpg.scr"));
  CHECK(!IsDangerousExtension("photo.jpg"));
  CHECK(HasMimeMismatch("movie.exe", "video/mp4"));
  CHECK(HasMimeMismatch("readme.txt", "application/x-msdownload"));
  CHECK(!HasMimeMismatch("movie.mp4", "video/mp4"));
  std::set<std::string> existing = {"a.txt", "a (1).txt"};
  auto exists = [&](const std::string& n) { return existing.count(n) > 0; };
  CHECK(UniqueFilename("a.txt", exists) == "a (2).txt");
  CHECK(UniqueFilename("b.txt", exists) == "b.txt");
}

static void TestPermissions() {
  PermissionPolicyStore s;
  const std::string a = "https://a.example";
  const std::string b = "https://b.example";
  CHECK(s.Evaluate(a, "camera", 1) == Decision::kPrompt);
  s.Record(a, "camera", 1, PromptResult::kDeny);
  s.Record(a, "camera", 1, PromptResult::kDismiss);
  s.Record(a, "camera", 1, PromptResult::kDismiss);
  s.Record(a, "camera", 1, PromptResult::kDeny);
  CHECK(s.Evaluate(a, "camera", 1) == Decision::kPrompt);
  s.Record(a, "camera", 1, PromptResult::kDeny);
  CHECK(s.Evaluate(a, "camera", 1) == Decision::kBlocked);
  CHECK(s.Evaluate(a, "microphone", 1) == Decision::kPrompt);
  CHECK(s.Evaluate(b, "camera", 1) == Decision::kPrompt);
  s.SetPolicy(a, "camera", Policy::kAsk);
  CHECK(s.Evaluate(a, "camera", 1) == Decision::kPrompt);

  s.Record(a, "location", 7, PromptResult::kAllowOnce);
  CHECK(s.Evaluate(a, "location", 7) == Decision::kAllow);
  CHECK(s.Evaluate(a, "location", 8) == Decision::kPrompt);
  CHECK(s.Evaluate(b, "location", 7) == Decision::kPrompt);
  CHECK(s.Find(a, "location")->policy == Policy::kAsk);
  CHECK(s.Serialize().find("location") == std::string::npos);
  s.ClearScope(7);
  CHECK(s.Evaluate(a, "location", 7) == Decision::kPrompt);

  s.Record(a, "notifications", 1, PromptResult::kAllowAlways);
  CHECK(s.Evaluate(a, "notifications", 99) == Decision::kAllow);
  s.Record(a, "notifications", 1, PromptResult::kDeny);
  s.Record(a, "notifications", 1, PromptResult::kDeny);
  s.Record(a, "notifications", 1, PromptResult::kDeny);
  CHECK(s.Evaluate(a, "notifications", 1) == Decision::kAllow);

  s.Record(b, "camera", 1, PromptResult::kDeny);
  s.Record(b, "camera", 1, PromptResult::kDeny);
  s.Record(b, "camera", 1, PromptResult::kAllowOnce);
  s.ClearScope(1);
  s.Record(b, "camera", 1, PromptResult::kDeny);
  CHECK(s.Evaluate(b, "camera", 1) == Decision::kPrompt);

  PermissionPolicyStore t;
  t.Record(a, "camera", 1, PromptResult::kDeny);
  t.Record(a, "camera", 1, PromptResult::kDeny);
  t.Record(a, "camera", 1, PromptResult::kDeny);
  t.SetPolicy(b, "midi", Policy::kDeny);
  PermissionPolicyStore u;
  u.Deserialize(t.Serialize());
  CHECK(u.Evaluate(a, "camera", 1) == Decision::kBlocked);
  CHECK(u.Evaluate(b, "midi", 1) == Decision::kDeny);
  u.ResetOrigin(a);
  CHECK(u.Evaluate(a, "camera", 1) == Decision::kPrompt);
}

static void TestRecords() {
  CHECK(JsonQuote("a\"b\\c\n</style>") == "\"a\\\"b\\\\c\\n</style>\"");
  CHECK(UnescapeField(EscapeField("a\tb\nc\\d")) == "a\tb\nc\\d");
  std::vector<std::string> row = SplitRow(JoinRow({"x\ty", "", "z"}));
  CHECK(row.size() == 3 && row[0] == "x\ty" && row[1].empty() && row[2] == "z");

  HistoryStore h;
  h.Add("https://a", "A", 10);
  h.Add("https://a", "A2", 11);
  h.Add("https://b", "B", 20);
  CHECK(h.Entries().size() == 2 && h.Entries()[0].title == "A2");
  HistoryStore h2;
  h2.Deserialize(h.Serialize());
  CHECK(h2.Entries().size() == 2 && h2.Entries()[1].time == 20);
  h2.ClearSince(15);
  CHECK(h2.Entries().size() == 1);

  BookmarkStore b;
  CHECK(b.Toggle("https://a", "A"));
  CHECK(b.Contains("https://a"));
  CHECK(!b.Toggle("https://a", "A"));
  CHECK(!b.Contains("https://a"));

  KeyValueStore kv;
  kv.Set("ua.site.example.com", "desktop");
  kv.Set("home", "https://h");
  KeyValueStore kv2;
  kv2.Deserialize(kv.Serialize());
  CHECK(kv2.Get("home") == "https://h");
  CHECK(kv2.WithPrefix("ua.site.").size() == 1);
  CHECK(kv2.Get("missing", "d") == "d");
}

int main() {
  TestUrls();
  TestFilenames();
  TestPermissions();
  TestRecords();
  if (g_failures) {
    std::printf("%d check(s) failed\n", g_failures);
    return EXIT_FAILURE;
  }
  std::printf("all core tests passed\n");
  return EXIT_SUCCESS;
}

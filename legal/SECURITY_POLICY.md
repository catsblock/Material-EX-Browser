MATERIAL EX BROWSER - SECURITY POLICY

Last updated: 2026-11-20

1. Scope
This policy describes the security measures that are implemented in the Android version of Material EX and how to report a vulnerability. No browser is completely secure, and this document is not a guarantee.

2. Browser engine
Web content is rendered by GeckoView, provided by Mozilla. Material EX relies on the engine for TLS validation, certificate error pages and process isolation. Material EX does not disable certificate checks.

3. Safe browsing
When the Safe Browsing setting is on, the engine warns before loading pages it recognises as phishing or malware. You can bypass a warning for the current page only through an explicit action.

4. Website permissions
- Camera, microphone, location, notifications and persistent storage are controlled by the browser, not by the website.
- Each website and permission has its own policy: ask every time, allow or deny.
- After three consecutive denials of the same permission by the same website, further requests from that website for that permission are blocked automatically. You can recover from this in the site information panel.
- "Allow this time" exists for camera and microphone only. It applies to the tab where it was granted, is revoked when you navigate to another website, close the tab or change the policy, expires after 15 minutes and is kept only in memory, so it does not survive a restart.
- Android system permissions still apply and can be denied independently.

5. Pop-ups
With pop-up blocking on, a page can open a new window only shortly after you touched it. The number of simultaneously open pop-up windows is limited.

6. Downloads
- A download that starts without a recent interaction on your side is blocked and listed as blocked.
- At most six downloads run at the same time.
- File names supplied by websites are sanitised. Files are never opened automatically.
- Before you open a file, Material EX checks for executable file types, deceptive names (double extensions, hidden direction characters), a file type that does not match what the website declared, and insecure sources, and asks for confirmation when the risk is not only an insecure source.

7. Full screen
A page can enter full screen only while it is the foreground tab. Back leaves full screen first.

8. Stored data
Settings, history, bookmarks, permission decisions and the download list are kept in the application's private storage. Material EX stores no passwords of its own and no access tokens.

9. Reporting a vulnerability
Please report it privately through GitHub: https://github.com/catsblock/Material-EX-Browser/security/advisories/new
If that is not available to you, open an issue at https://github.com/catsblock/Material-EX-Browser/issues without exploit details and ask for a private channel. Do not publish a vulnerability before the developers have had a reasonable time to fix it.

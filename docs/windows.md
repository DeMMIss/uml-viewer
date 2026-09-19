# Windows

The PowerShell launcher uses Java 21 or newer and bootstraps the pinned official
Clojure CLI into the repository's ignored `.tools` directory. It does not need
administrator access or change global environment variables.

From the repository root:

```powershell
powershell -File scripts/clj.ps1 -JavaHome C:\path\to\jdk-21 -M:kotlin:ir examples\project.policy.edn
powershell -File scripts/clj.ps1 -JavaHome C:\path\to\jdk-21 -M:kotlin:run --standalone generated.edn
```

`-JavaHome` takes precedence over `JAVA_HOME`, which takes precedence over
`java` on `PATH`. The selected Java must report version 21 or newer. All
remaining arguments are passed to Clojure unchanged, and the launcher returns
the Clojure process exit code.

On first use, the launcher downloads Clojure CLI `1.12.5.1664` from the
[official immutable release](https://github.com/clojure/brew-install/releases/tag/1.12.5.1664)
and checks it against the release's
[published SHA-256](https://github.com/clojure/brew-install/releases/download/1.12.5.1664/clojure-tools.zip.sha256).
Later runs reuse the local copy. The downloaded module identifies its license
as EPL-1.0. The launcher adjusts its file reads to UTF-8 so Windows PowerShell
5.1 can use paths containing non-ASCII characters.

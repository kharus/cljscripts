# cljscripts

Three independent converters that turn web content into EPUB:

- **lj_epub** — LiveJournal posts
- **substack_epub** — Substack articles (handles datawrapper iframes)
- **sysch_epub** — System School (aisystant.system-school.ru) courses

## Build & Test

    clojure -T:build test   # run tests
    clojure -T:build ci     # run tests + build uberjar

## Running

### lj_epub

    clojure -M:run-m <url>

### substack_epub

    clojure -M -m substack-epub.convert <url>

Example:

    clojure -M -m substack-epub.convert https://addyo.substack.com/p/software-factories-light-and-dark

### sysch_epub

    clojure -M -m sysch-epub.convert <course-slug>

`<course-slug>` is the path segment right after `/course/` in the course URL,
e.g. for `.../lk/#/course/modeling-1-r2/2026-06-30T0620/79933` it's `modeling-1-r2`.

**Auth:** sysch_epub needs a `fetch.json` in the project root:

    {"headers": {"cookie": "session-token=...; JSESSIONID=..."}}

Get this by logging into the site, opening DevTools → Network, and doing
"Copy as fetch" on any page load, then pasting just the `headers` object here.
The cookie expires periodically — if the converter reports "no active passing
found", re-capture it.

**Caching:** every fetched URL is cached forever under `cache/` — if a stale
response is causing wrong output, delete the specific file under `cache/` to
force a refetch (refreshing `fetch.json` alone won't invalidate it).

Output lands in `target/<course-slug>.epub`.

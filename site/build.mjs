// Builds the project site into site/dist. Each page is a template rendered once per language:
// the landing page at / and /ru/, the guide at /guide/ and /ru/guide/.
// Usage: node site/build.mjs
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const SRC = join(HERE, "src"), DIST = join(HERE, "dist");
const SITE = "https://denfry.github.io/Prompt_Critical/";
const LANGS = { en: "", ru: "ru/" };
// slug: path under the language root; css: stylesheets inlined into the page; content: per-language HTML body
const PAGES = [
  { template: "page.html", slug: "", css: ["site.css"] },
  { template: "guide.html", slug: "guide/", css: ["site.css", "guide.css"], content: "guide" },
];

const read = (...p) => readFileSync(join(...p), "utf8");
const escape = s => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const lookup = (obj, path) => path.split(".").reduce((o, k) => (o == null ? undefined : o[k]), obj);
const inlineJson = v => JSON.stringify(v).replace(/</g, "\\u003c");

rmSync(DIST, { recursive: true, force: true });
mkdirSync(DIST, { recursive: true });
cpSync(join(SRC, "assets"), join(DIST, "assets"), { recursive: true });
cpSync(join(HERE, "data"), join(DIST, "data"), { recursive: true });
for (const page of PAGES) for (const f of page.css) rmSync(join(DIST, "assets", f), { force: true }); // inlined into each page

for (const [lang, langPath] of Object.entries(LANGS)) {
  const t = JSON.parse(read(SRC, "i18n", lang + ".json"));
  const other = lang === "en" ? "ru" : "en";
  for (const page of PAGES) {
    const path = langPath + page.slug;
    const root = "../".repeat(path.split("/").filter(Boolean).length) || "./";
    const vars = {
      lang,
      root,
      langPath,
      site: SITE,
      url: SITE + path,
      slug: page.slug,
      otherLang: other,
      otherUrl: root + LANGS[other] + page.slug,
      jsonld: inlineJson({
        "@context": "https://schema.org",
        "@type": "SoftwareApplication",
        name: "Prompt Critical",
        description: t.meta.description,
        applicationCategory: "GameApplication",
        operatingSystem: "Minecraft Java Edition 1.21.1 with NeoForge",
        url: SITE + langPath,
        inLanguage: lang,
        author: { "@type": "Person", name: "denfry" },
        sameAs: ["https://github.com/denfry/Prompt_Critical", "https://www.youtube.com/@denfrydev"],
      }),
      i18nJs: inlineJson({ sim: t.sim, chart: t.chart, perfNote: t.tests.perfNote }),
    };
    vars.css = page.css.map(f => read(SRC, "assets", f)).join("\n").replaceAll("{{root}}", root);
    // content is spliced in before substitution, so it may use {{root}} and other keys too
    const source = read(SRC, page.template).replace("{{content}}", page.content ? read(SRC, page.content, lang + ".html") : "");
    const missing = [];
    const html = source.replace(/\{\{([\w.]+)\}\}/g, (_, key) => {
      if (key in vars) return vars[key];
      const v = lookup(t, key);
      if (v == null) { missing.push(key); return ""; }
      return key.endsWith("_html") ? v : escape(v);
    });
    if (missing.length) throw new Error(`${lang}/${page.slug}: missing strings ${missing.join(", ")}`);
    mkdirSync(join(DIST, path), { recursive: true });
    writeFileSync(join(DIST, path, "index.html"), html);
  }
}

const alternates = p => Object.entries(LANGS).map(([l, q]) => `<xhtml:link rel="alternate" hreflang="${l}" href="${SITE}${q}${p.slug}"/>`).join("");
writeFileSync(join(DIST, "robots.txt"), `User-agent: *\nAllow: /\nSitemap: ${SITE}sitemap.xml\n`);
writeFileSync(join(DIST, "sitemap.xml"), `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${Object.values(LANGS).flatMap(q => PAGES.map(p => `  <url><loc>${SITE}${q}${p.slug}</loc>${alternates(p)}</url>`)).join("\n")}
</urlset>
`);
writeFileSync(join(DIST, ".nojekyll"), "");
console.log("site/dist: " + Object.values(LANGS).flatMap(q => PAGES.map(p => "/" + q + p.slug)).join(", "));

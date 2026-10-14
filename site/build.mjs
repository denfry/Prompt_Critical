// Builds the project site into site/dist: one template, an EN page at / and an RU page at /ru/.
// Usage: node site/build.mjs
import { cpSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const SRC = join(HERE, "src"), DIST = join(HERE, "dist");
const SITE = "https://denfry.github.io/Prompt_Critical/";
const LANGS = { en: "", ru: "ru/" };

const template = readFileSync(join(SRC, "page.html"), "utf8");
const css = readFileSync(join(SRC, "assets", "site.css"), "utf8");
const escape = s => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const lookup = (obj, path) => path.split(".").reduce((o, k) => (o == null ? undefined : o[k]), obj);
const inlineJson = v => JSON.stringify(v).replace(/</g, "\\u003c");

rmSync(DIST, { recursive: true, force: true });
mkdirSync(DIST, { recursive: true });
cpSync(join(SRC, "assets"), join(DIST, "assets"), { recursive: true });
cpSync(join(HERE, "data"), join(DIST, "data"), { recursive: true });
rmSync(join(DIST, "assets", "site.css")); // inlined into each page

for (const [lang, path] of Object.entries(LANGS)) {
  const t = JSON.parse(readFileSync(join(SRC, "i18n", lang + ".json"), "utf8"));
  const other = lang === "en" ? "ru" : "en";
  const vars = {
    lang,
    root: path ? "../" : "./",
    langPath: path,
    site: SITE,
    url: SITE + path,
    otherLang: other,
    otherUrl: (path ? "../" : "./") + LANGS[other],
    jsonld: inlineJson({
      "@context": "https://schema.org",
      "@type": "SoftwareApplication",
      name: "Prompt Critical",
      description: t.meta.description,
      applicationCategory: "GameApplication",
      operatingSystem: "Minecraft Java Edition 1.21.1 with NeoForge",
      url: SITE + path,
      inLanguage: lang,
      author: { "@type": "Person", name: "denfry" },
      sameAs: ["https://github.com/denfry/Prompt_Critical", "https://www.youtube.com/@denfrydev"],
    }),
    i18nJs: inlineJson({ sim: t.sim, chart: t.chart, perfNote: t.tests.perfNote }),
  };
  vars.css = css.replaceAll("{{root}}", vars.root);
  const missing = [];
  const html = template.replace(/\{\{([\w.]+)\}\}/g, (_, key) => {
    if (key in vars) return vars[key];
    const v = lookup(t, key);
    if (v == null) { missing.push(key); return ""; }
    return key.endsWith("_html") ? v : escape(v);
  });
  if (missing.length) throw new Error(`${lang}: missing strings ${missing.join(", ")}`);
  mkdirSync(join(DIST, path), { recursive: true });
  writeFileSync(join(DIST, path, "index.html"), html);
}

writeFileSync(join(DIST, "robots.txt"), `User-agent: *\nAllow: /\nSitemap: ${SITE}sitemap.xml\n`);
writeFileSync(join(DIST, "sitemap.xml"), `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">
${Object.values(LANGS).map(p => `  <url><loc>${SITE}${p}</loc>${Object.entries(LANGS).map(([l, q]) => `<xhtml:link rel="alternate" hreflang="${l}" href="${SITE}${q}"/>`).join("")}</url>`).join("\n")}
</urlset>
`);
writeFileSync(join(DIST, ".nojekyll"), "");
console.log("site/dist: " + Object.keys(LANGS).join(", "));

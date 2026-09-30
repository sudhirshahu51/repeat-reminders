// Serves the Repeat Reminders static pages (privacy policy, support) bundled with this function.
import { readFileSync } from "node:fs";

const TYPES = { html: "text/html; charset=utf-8", css: "text/css; charset=utf-8" };
const PAGES = ["index.html", "privacy.html", "404.html", "style.css"];
const files = Object.fromEntries(PAGES.map((p) => [p, readFileSync(new URL(p, import.meta.url), "utf8")]));

const SECURITY_HEADERS = {
  "strict-transport-security": "max-age=63072000; includeSubDomains",
  "x-content-type-options": "nosniff",
  "x-frame-options": "DENY",
  "referrer-policy": "strict-origin-when-cross-origin",
  "content-security-policy": "default-src 'none'; style-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'",
};

export const handler = async (event) => {
  let path = (event.rawPath || "/").replace(/^\/+/, "");
  if (path === "") path = "index.html";
  if (path === "privacy" || path === "privacy-policy") path = "privacy.html";
  const found = Object.hasOwn(files, path);
  const name = found ? path : "404.html";
  return {
    statusCode: found ? 200 : 404,
    headers: {
      "content-type": TYPES[name.split(".").pop()],
      "cache-control": "public, max-age=300",
      ...SECURITY_HEADERS,
    },
    body: files[name],
  };
};

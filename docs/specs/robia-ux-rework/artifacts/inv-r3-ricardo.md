# Ricardo integration feasibility for Robia

**Research date:** 17 October 2026. **Scope:** read-only research; no Ricardo account action was performed.

## Bottom line

A “one tap, pre-filled Ricardo listing” is **not verified**. The lowest-risk design is a user-mediated handoff: Robia prepares the title/description, shares an image through Android’s `ACTION_SEND` if Ricardo accepts it, and opens Ricardo’s sell page. Clipboard plus a user paste is the baseline. Do not automate Ricardo’s UI or depend on undocumented query parameters.

## Verified findings

### 1) API / partner integration

Ricardo still publishes a legacy **Interface Archive** with technical documentation and a `SellService` that can create/modify/list articles.[3][4] The archive includes JSON/SOAP examples and category services.[6] The documented authentication is not self-serve OAuth: a partnership key/password is required; the seller authorizes through a Ricardo validation URL, then the partner obtains a token. Credentials are unique per application/language and must not be shared.[5][7] Ricardo’s connection guide says the use agreement must be signed before a partnership key is issued and development starts in a sandbox.[8]

A non-primary but current marketplace-integrator notice says Ricardo retired the public seller API on **1 September 2026**, with no successor, and directed sellers to the product feed, app, and web form.[23]

Ricardo’s legacy documentation remains publicly indexed, but probes of those endpoints returned HTTP 503 or timed out.[24][25][26] **Verdict:** treat the legacy API as unavailable/unsupported unless Ricardo confirms otherwise. Even if reactivated, it would require partner approval, credentials, token handling, server-side security, and more work than Robia’s goal. I found no evidence of a current self-serve API for a private individual.

### 2) Sell URL, app links, and prefill

Ricardo’s official help links the web listing form at `https://www.ricardo.ch/en/list/`.[1][6]

Ricardo’s `assetlinks.json` associates the domain with Android package `com.qxl.Client` (and beta package `com.qxl.Client.Beta`) using `delegate_permission/common.handle_all_urls`.[16]

The official Google Play listing identifies `com.qxl.Client` as Ricardo AG’s app and describes creating listings and uploading photos.[15]

**Prefill test:** direct HTTP requests to `/en/list/` and `/en/sell/` encountered Ricardo’s 403/Cloudflare barrier; the asset-links file itself fetched successfully. The rendered fetch of `/en/list/` showed a login/security-challenge page; the tested URL with `?title=Test%20Dress&description=Prepared%20text&price=25&category=Clothing` showed the same challenge and no reflected values. `/en/sell/?title=Test%20Dress` returned only an empty HTML shell. This verifies neither query-parameter prefill nor a dedicated `/sell/` route. The URL and domain association are verified; “opens directly into the sell form” and any query schema are **unknown**.[17][18]

### 3) Android share intent

Ricardo’s help confirms that its app listing form can take or upload photos, but I found no official manifest, developer documentation, or public evidence that the app registers an inbound `ACTION_SEND` filter for `image/*`, `text/plain`, or both.[1][15]

Android’s documented mechanism is an intent filter with `ACTION_SEND`, `CATEGORY_DEFAULT`, and matching MIME types; the receiving app decides what to do with `EXTRA_STREAM` and `EXTRA_TEXT`.[19][20]

Therefore, Ricardo appearing in Robia’s share sheet—and whether it lands in the sell flow or merely opens the home screen—is **unverified and must be tested on Manu’s device**.

### 4) Bulk, feeds, categories, fees

Ricardo provides a web-only multiple-listing tool with file upload. Ricardo’s product-news page describes the same upload workflow.[22]

The Help page says up to 100 listings.[1][13] An SMG H1 2026 release says up to 20, so the limit is version/UI-dependent.[21] This is useful for batches, not a one-garment Robia flow.

Ricardo also documents creating listings from tutti.ch or anibis.ch, but that is cross-posting from another marketplace account, not a Robia integration.[14]

The current product-feed route is for professional sellers: Ricardo manually onboards and evaluates requests, does not guarantee connection, requires a static public HTTPS JSON feed, and supports fixed-price offers only. Imported listings cannot be managed in the Ricardo website/app.[2] Feed fields include title, description, image URLs, price, condition, product type, category, and attributes; taxonomy mapping is Ricardo-controlled, with German/French/Italian terms recommended because English maps less reliably.[2] Robia’s Dresses/Trousers/Accessories can therefore be mapped, but should be confirmed in Ricardo’s form rather than hard-coded IDs.

Private selling requires an account, email and phone confirmation, identity verification, and Ricardo Buyer Protection+; Swiss/Liechtenstein domicile is the normal private-seller path.[12] Listing is normally free; a success fee applies on sale, generally 8–12% capped at CHF 290, and the published reference lists clothing examples such as dress, trousers, jacket, and accessories at 12%.[10][11]

### 5) Terms / automation

Ricardo’s current GTC prohibit scripts/software that disrupt the site, create excessive infrastructure load, or block/overwrite/modify generated content; the GTC also incorporate separate interface terms.[9] This makes browser automation, reverse-engineered private endpoints, and credential replay poor choices. A user-triggered share, clipboard copy, and normal URL open are materially safer because the user completes the official flow; they are not proof of Ricardo endorsement.

## Options

| Option | Robia permissions | User steps | Effort / risk | Verdict |
|---|---|---|---|---|
| Legacy/partner API | No dangerous Android permission, but network plus protected partner credentials/backend | Authorize Ricardo; Robia/server creates listing | High / API retirement, approval, compliance | Do not build unless Ricardo confirms a successor |
| Product feed / bulk import | No dangerous Android permission; needs public feed/server or web file upload | Prepare data, import, review/publish in Ricardo | Medium–high / professional onboarding, fixed-price and sync rules | Not suitable for occasional private garments |
| Official sell URL / App Link | None | Tap; log in; fill fields | Low / no verified prefill or route behavior | Good navigation fallback, not prefill |
| `ACTION_SEND` image + text | Reuse existing FileProvider URI; no storage permission; URI grant only | Tap Robia button; choose Ricardo if shown; edit and publish | Low / inbound support unverified | Test first; best UX if supported |
| Clipboard-assisted handoff | Clipboard permission none; avoid gallery copy if FileProvider sharing works | Robia copies prepared text, opens Ricardo; user uploads image and pastes | Low / user effort, low platform risk | Recommended baseline |

## Recommended path

Implement a **“Sell on Ricardo”** button with this bounded flow:

1. Generate a concise title/description from Robia’s garment fields; show an edit/preview screen.
2. Try `ACTION_SEND` with `type=image/*`, the existing FileProvider `EXTRA_STREAM`, `EXTRA_TEXT`, and `FLAG_GRANT_READ_URI_PERMISSION`.
3. If Ricardo is absent or ignores the payload, copy the prepared text to the clipboard and open `https://www.ricardo.ch/en/list/` with `ACTION_VIEW`. Tell the user to choose the photo, paste, select category/condition/price/delivery, and press Publish.
4. Do not save to the gallery unless device testing proves it is needed; direct FileProvider sharing minimizes permissions.

## Device tests Manu must perform

- Install the official app (`com.qxl.Client`), share one JPEG and one PNG from Gallery: does Ricardo appear, and where does it land?
- From Robia, test `ACTION_SEND` with image plus text: is the image imported, is text populated, and is a draft/sell form shown?
- Open `/en/list/` while logged in: app or browser? Does it open the sell form? Test the query URL only for observation; do not publish.
- Check whether Ricardo requires identity verification/Buyer Protection+ before the form can be saved or published.
- Confirm the current bulk limit and whether Ricardo accepts title/description language and Robia’s category mappings.

## Sources

[1] https://help.ricardo.ch/hc/en-ch/articles/115002488565-What-are-the-different-ways-to-create-listings
[2] https://help.ricardo.ch/hc/en-ch/articles/20263328525084-How-can-I-create-and-manage-listings-using-a-product-feed
[3] https://help.ricardo.ch/hc/fr/articles/115002970529-Technical-documentation
[4] https://help.ricardo.ch/hc/fr/articles/115002977185-Services
[5] https://help.ricardo.ch/hc/fr/articles/115002970729-How-to-start-quickly
[6] https://help.ricardo.ch/hc/fr/articles/115002433845-How-to-list-an-article
[7] https://help.ricardo.ch/hc/fr/articles/115002974805-Authentication
[8] https://help.ricardo.ch/hc/fr/articles/115002964325-Profils-d-utilisateur-de-l-interface-ricardo-ch
[9] https://help.ricardo.ch/hc/en-ch/articles/4417210291858-General-Terms-and-Conditions
[10] https://help.ricardo.ch/hc/en-ch/articles/115002854885-How-much-does-it-cost-to-sell-on-Ricardo
[11] https://help.ricardo.ch/hc/en-ch/articles/8238144523036-Detailed-overview-of-success-fees-by-product-type
[12] https://help.ricardo.ch/hc/en-ch/articles/115003832545-How-do-i-become-a-Ricardo-seller
[13] https://help.ricardo.ch/hc/en-ch/articles/4690220803474-List-multiple-items-at-once
[14] https://help.ricardo.ch/hc/en-ch/articles/15861708606748-Create-listings-from-tutti-ch-or-anibis-ch
[15] https://play.google.com/store/apps/details?id=com.qxl.Client&hl=en
[16] https://www.ricardo.ch/.well-known/assetlinks.json
[17] https://www.ricardo.ch/en/list
[18] https://www.ricardo.ch/en/list/?title=Test%20Dress&description=Prepared%20text&price=25&category=Clothing
[19] https://developer.android.com/training/basics/intents/filters
[20] https://developer.android.com/guide/components/intents-common
[21] https://swissmarketplace.group/media-release/smg-h1-2026
[22] https://communication.ricardo.ch/news/produkt-news/neueste-funktionen-ricardo
[23] https://www.magnalister.com/de/blog/meldungen/ricardo-schaltet-seine-verkaeufer-api-ab-das-musst-du-zum-1-september-2026-wissen
[24] https://ws.ricardo.ch/RicardoApi/documentation/index.aspx
[25] https://ws.ricardo.ch/RicardoApi/SellService.Json.svc
[26] https://ws.ricardo.ch/RicardoApi/SecurityService.Json.svc

## Resumen en español (5 líneas)

La API antigua de Ricardo parece retirada; no la usaría sin confirmación oficial.
El camino recomendado es compartir imagen/texto y dejar que el usuario publique en Ricardo.
Si `ACTION_SEND` no funciona, copiar el texto al portapapeles y abrir `/en/list/` requiere pocos permisos.
No se verificó ningún parámetro URL para rellenar título, descripción, precio o categoría.
Manu debe probar la app oficial en su dispositivo antes de cerrar la implementación.

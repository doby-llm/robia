# TASK-D01 — Schema v13 and snapshot contract

## Goal
Prepare the data layer that every later task uses: a table for the two vectors per garment (IMAGE, TEXT), two new tag fields (`description`, `suggestOccasions`), new optional Drive JSON fields, and reset of old "failed" sync states. No user-visible change.

## Read first
`data/local/Entities.kt`, `data/local/RobiaDatabase.kt` (pattern of `MIGRATION_11_12`), `data/local/Dao.kt`, `data/LocalRepositories.kt` (mappers at the end), `core/model/ClothingModels.kt` (`GarmentTag`), `core/model/DefaultTags.kt`, `core/model/SyncModels.kt`, `sync/GoogleDriveWardrobeRepository.kt` (`private object DriveSnapshotJson`, ~line 1127), `sync/LocalWardrobeSyncSnapshotRepository.kt` (`exportSnapshot`, `importSnapshot`). Spec TS§3.5, §3.9, §4.7; DEC-006, DEC-007.

## Steps
1. **Domain**: `GarmentTag` add at the end `val description: String? = null, val suggestOccasions: Boolean = true`. `TagSyncRecord` add the same two fields at the end. `WardrobeSnapshotMetadata` add `val writerDeviceId: String? = null`. `GarmentSyncRecord` add `val embeddings: List<EmbeddingSyncRecord> = emptyList()`. New `data class EmbeddingSyncRecord(val kind: String, val modelId: String, val recipeId: String, val dim: Int, val inputHash: String, val computedAtEpochMillis: Long, val b64: String)` in `SyncModels.kt`.
2. **Entity**: `GarmentTagEntity` add `val description: String? = null` and `@ColumnInfo(name = "suggest_occasions", defaultValue = "1") val suggestOccasions: Boolean = true` at the end. Update every `GarmentTag ⇄ GarmentTagEntity` and `GarmentTag ⇄ TagSyncRecord` mapper (grep `GarmentTagEntity(`, `TagSyncRecord(`, `GarmentTag(`). Convert positional constructor calls you touch to named arguments.
3. **DefaultTags**: `category-accessories` and `category-jewelry` get `suggestOccasions = false`. `isCustomOrModifiedDefault(tag)` also returns true when `description` or `suggestOccasions` differ from the default.
4. **New files** `data/local/EmbeddingEntities.kt` (`GarmentEmbeddingEntity` exactly as TS§3.5, `kind` constants `EmbeddingKind.IMAGE = "image"`, `TEXT = "text"`) and `data/local/EmbeddingDao.kt` (`@Dao interface EmbeddingDao` with the methods in TS§3.5; `garmentsNeedingImage` as `@Query` joining `clothing_items`: `is_archived = 0 AND photo_uri IS NOT NULL AND NOT EXISTS (SELECT 1 FROM garment_embeddings e WHERE e.garment_id = clothing_items.id AND e.kind = 'image' AND e.model_id = :modelId AND e.recipe_id = :recipeId) ORDER BY created_at_epoch_millis DESC LIMIT :limit` — check the real column names in `ClothingItemEntity` first).
5. **Database**: add the entity, `abstract fun embeddingDao(): EmbeddingDao`, `version = 13`, `MIGRATION_12_13` with the SQL of TS§3.5 plus the status reset of TS§4.7 for tables `clothing_items`, `tag_categories`, `garment_tags`, `main_colors`, `sync_tombstones` (only tables that have `sync_status`; `NeedsUserAction` rows are reset only if the table has a `photo_restore_guarded`-style column and it is false — read the entity; for tables without that column reset `NeedsUserAction` too). Register in `addMigrations`. The Room-generated schema must match the migration (column order, `NOT NULL`, defaults): CI's `13.json` is the proof.
6. **`suggestions/VectorMath.kt`** (pure Kotlin): `FloatArray.toLeBytes(): ByteArray` (ByteBuffer LITTLE_ENDIAN), `ByteArray.toFloatArrayLe()`, `FloatArray.toBase64()` / `floatArrayFromBase64(s)` (java.util.Base64), `dot(a,b)`, `FloatArray.normalized()`, `zScores(x)` (population std; std < 1e-6 → zeros).
7. **`suggestions/EmbeddingRecipe.kt`** with only: `MODEL_ID = "embeddinggemma-2-text-vision-440m@e301f74d"`, `RECIPE_ID = "eg2-r1"`, `DIMENSION = 768`. (E01/E04 append the rest.)
8. **`data/EmbeddingRepository.kt`**: class over `EmbeddingDao` with the API of tasks.md D01 step 5. `upsert` must never touch `clothing_items`.
9. **JSON**: make `DriveSnapshotJson` `internal` (tests need it). Encode: `description` only if non-null, `suggestOccasions` always, `writerDeviceId` only if non-null, `embeddings` array only if non-empty. Decode with `opt*`: missing `suggestOccasions` → `true` except the two ids above → `false`; missing others → defaults.
10. **Export/import** (`LocalWardrobeSyncSnapshotRepository`): export adds `embeddings` for each garment from `EmbeddingDao` rows matching `MODEL_ID`/`RECIPE_ID`; import inserts rows only when model+recipe match, `dim == 768` and the decoded byte length is 3072, with `pendingUpload = false`.
11. **Build**: `testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")` and `testImplementation("org.json:json:20240303")` (Android's org.json is a stub in JVM tests).
12. After CI green: download `room-schemas`, commit `13.json`.

## Tests (new)
- `data/local/Migration12To13Test.kt`: open an in-memory sqlite-jdbc DB, create v12 tables from `app/schemas/…/12.json` (`createSql` entries with `${TABLE_NAME}` replaced), insert sample rows (one `FailedRetryable` garment, the two default categories), run the same SQL strings as `MIGRATION_12_13` (expose them as `internal val MIGRATION_12_13_SQL: List<String>` and have the Migration execute that list), assert new table/columns/defaults/status reset.
- `sync/SnapshotJsonCompatTest.kt`: round-trip with all new fields; a JSON string without new fields (copy a minimal snapshot produced by the current encoder) decodes with defaults.
- `data/EmbeddingRepositoryTest.kt`: V-D03 — use a fake `EmbeddingDao` + fake wardrobe DAO and assert no garment write happens (simplest: repository has no reference to the wardrobe DAO; assert by construction + upsert test).
- `core/model/DefaultTagsTest.kt`: add V-D04 assertions.
- `suggestions/VectorMathTest.kt`: LE round-trip, base64 round-trip, z-scores.

## Done when
CI green; `13.json` committed; V-D01…V-D04.

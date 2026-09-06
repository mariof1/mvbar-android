# Shared MVBar artwork icons

`app/src/main/java/com/mvbar/android/ui/components/ArtworkIcons.kt` is generated from the companion server/web repository's `web/app/icons/artwork-icons.json`. Use `ArtworkIcons.Album` and `ArtworkIcons.Artist` as native Compose `ImageVector` placeholders. Keep existing artwork rendering and loading/error handling in `ArtworkImage`.

To change the designs, edit the JSON in the sibling `mvbar` checkout and run `node scripts/sync-artwork-icons.mjs` there. Run it with `--check` to verify the Android snapshot matches. Commit both repositories after the required builds pass. The generated copy is checked in so standalone Android builds have no dependency on the web checkout.

This initial rollout covers phone album/artist cards, details, and album collection sheets. TV, Wear and other icon categories can adopt the same geometry in later focused changes.

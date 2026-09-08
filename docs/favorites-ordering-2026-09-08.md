# Favorites ordering

Hold a favorite track or its grip and drag to change its position. Accessibility actions offer Move up and Move down. A successful move is saved to the server and reflected on other connected web/Android clients. A failed save restores server order. Existing order remains available offline; editing requires a connection.

The feature requires the matching mvbar server change adding `POST /api/favorites/reorder` and `favorite:reordered`. Requests contain numeric `trackId` and required nullable `beforeTrackId`; null moves to the end. Android fetches every favorites page, including for Android Auto browsing and voice favorites. Room migration 5 to 6 preserves server positions in the local cache, and adding a favorite no longer alphabetizes the existing custom order.

Verified: debug build, unit tests and lint; multi-page loading and explicit null request serialization; exact-position long-press drag on the emulator; immediate Android-to-web and web-to-Android updates; persistence after web refresh; Room migration and cached positions. Original favorite order was restored after live testing. No playback was started. Projected Android Auto drag interaction is outside this change; Auto browsing consumes the saved favorites order.

# Keep rules for R8, on release and the two benchmark builds. Most of what the app needs comes
# with its libraries; this file holds only what they cannot know.

# Type-safe navigation finds an enum argument of a route (TransactionForm.kind, AccountEdit.type,
# ...) by its class name and its constants by theirs, through reflection. Renamed, the app
# crashes on its first frame. Kept for every enum in domain.model, so a new route argument does
# not have to remember this file.
-keep enum io.github.anderescaray.cents.domain.model.** { *; }

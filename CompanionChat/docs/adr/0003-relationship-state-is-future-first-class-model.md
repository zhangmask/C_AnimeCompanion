# Relationship State Is a Future First-Class Model

Anime Companion's product direction includes relationship growth, familiarity, and long-term emotional continuity, but the current codebase does not model these as a first-class object. `ConversationSession`, `Memory`, and `UserPreference` should not be stretched to mean relationship progress; a future `RelationshipState` model should own that concept when the product is ready to define and test it.

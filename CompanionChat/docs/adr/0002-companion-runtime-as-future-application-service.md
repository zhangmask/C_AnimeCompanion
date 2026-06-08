# Companion Runtime as Future Application Service

The current `ChatViewModel` owns UI state and most companion-turn orchestration, including RoleCard and Skill prompt composition, memory retrieval, preference injection, context rebuilding, inference, persistence, and background learning. Future architecture should extract this orchestration into a `CompanionRuntime` application service so UI state stays separate from the domain flow of one AI companion turn, while avoiding a risky large refactor before the current product loop is stable.

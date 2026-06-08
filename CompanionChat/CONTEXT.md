# Anime Companion

Anime Companion is a local, privacy-first AI companion product. Its domain language centers on persistent companionship, character identity, user memory, and task abilities layered onto conversation.

## Language

**AI 伴侣**:
The core intelligent companion that maintains character identity, conversational continuity, memory, and expressive style across user interactions.
_Avoid_: 聊天助手, 通用助手, bot

**Skill**:
A task-oriented ability template temporarily layered onto the AI 伴侣, such as translation or writing behavior.
_Avoid_: 角色, 人设, 伴侣

**RoleCard**:
A companion identity definition that specifies who the AI 伴侣 is, including persona, speaking style, background, boundaries, opening message, visual style, and voice style.
_Avoid_: Skin, preset, Skill

**DiscoverRoleCard**:
A browsable candidate companion identity shown in discovery before it is imported into the local role library.
_Avoid_: RoleCard, active companion identity

**Memory**:
A remembered user fact, event, relationship, habit, or experience that may be short-term or long-term and has a source and lifecycle.
_Avoid_: Preference, chat history, summary

**Short-term Memory**:
A temporary Memory with an expiration time that can later be promoted.
_Avoid_: separate memory object, UserPreference

**Long-term Memory**:
A persistent Memory retained for ongoing companion continuity and eligible for always-on context injection.
_Avoid_: UserPreference, confirmed preference

**UserPreference**:
A stable preference signal about the user that becomes confirmed through repeated evidence and influences how the AI 伴侣 responds.
_Avoid_: Memory, one-off mention, setting

**ConversationSession**:
A continuous chat record containing messages, title, and timestamps.
_Avoid_: Relationship, companion state, memory

**Voice Interaction**:
An expression channel where the user and AI 伴侣 communicate through speech input and speech output.
_Avoid_: RoleCard, Skill, Relationship State

**Image Generation**:
A capability channel where the AI 伴侣 creates or attaches visual content during companionship.
_Avoid_: RoleCard, companion identity, Memory

**Relationship State**:
The future concept of how familiar or close the AI 伴侣 and user have become over time.
_Avoid_: ConversationSession, Message, RoleCard

**助手**:
A label for specific task-oriented Skills, not the product's core companion identity.
_Avoid_: using it as the umbrella term for the AI 伴侣

## Relationships

- An **AI 伴侣** has at most one active **RoleCard**
- An **AI 伴侣** may have one active **Skill**
- A **RoleCard** defines who the **AI 伴侣** is
- A **DiscoverRoleCard** becomes a **RoleCard** only after import
- A local **RoleCard** is the authoritative companion identity during conversation
- A **Skill** changes task behavior without replacing the **AI 伴侣**
- A **RoleCard** and a **Skill** may be active at the same time
- An **AI 伴侣** uses **Memory** to preserve continuity about the user
- **Short-term Memory** and **Long-term Memory** are lifecycle layers of **Memory**
- **Short-term Memory** may be promoted into **Long-term Memory**
- A **UserPreference** may be derived from repeated conversation evidence
- A **UserPreference** may produce a **Memory**, but it is not itself a **Memory**
- A **ConversationSession** records one continuous chat, but does not define **Relationship State**
- **Voice Interaction** and **Image Generation** enhance companionship without defining who the **AI 伴侣** is
- A **RoleCard** may provide voice style or image style, but it remains the companion identity definition
- **Relationship State** is not currently modeled as a first-class object
- **助手** is acceptable inside Skill names, but not as the domain name for the **AI 伴侣**

## Example dialogue

> **Dev:** "Is 翻译助手 a different companion?"
> **Domain expert:** "No. It is a **Skill** layered onto the same **AI 伴侣** for translation work."
>
> **Dev:** "Is a **RoleCard** just a skin?"
> **Domain expert:** "No. A **RoleCard** defines who the **AI 伴侣** is; a **Skill** only changes how it handles a task."
>
> **Dev:** "Can the chat use a **DiscoverRoleCard** directly?"
> **Domain expert:** "No. Discovery shows candidates; importing creates the local **RoleCard** used by conversation."
>
> **Dev:** "If the user says they like short answers once, is that a **UserPreference**?"
> **Domain expert:** "Not yet. It can become **Memory** immediately, but it becomes a confirmed **UserPreference** only after repeated evidence."
>
> **Dev:** "Should we create separate objects for short-term and long-term memories?"
> **Domain expert:** "No. They are lifecycle layers of **Memory**; promotion changes the layer, not the domain object."
>
> **Dev:** "Does starting a new **ConversationSession** reset the relationship?"
> **Domain expert:** "No. A **ConversationSession** is only the chat record; continuity comes from **RoleCard**, **Memory**, and **UserPreference**."
>
> **Dev:** "If a role has a cloned voice, is the voice the role?"
> **Domain expert:** "No. **Voice Interaction** is an expression channel; the **RoleCard** defines the companion identity."

## Flagged ambiguities

- "助手" was used to mean both the whole product and a task mode. Resolved: the product core is **AI 伴侣**; "助手" is only a label for task-oriented **Skills**.
- "角色卡" could mean a visual preset or a behavior preset. Resolved: **RoleCard** is the active companion identity definition, not a skin or Skill.
- `DiscoverRoleCard` and `RoleCard` looked interchangeable. Resolved: **DiscoverRoleCard** is a browsable candidate; **RoleCard** is the local authoritative identity.
- "偏好" and "记忆" were easy to merge. Resolved: **Memory** preserves remembered user continuity; **UserPreference** is a confirmed stable preference signal.
- "短期记忆" and "长期记忆" could look like separate object types. Resolved: **Short-term Memory** and **Long-term Memory** are lifecycle layers of **Memory**.
- "会话" could be mistaken for relationship progress. Resolved: **ConversationSession** is a chat record; **Relationship State** is a future domain concept not currently modeled.
- Voice and image features could be mistaken for identity. Resolved: **Voice Interaction** and **Image Generation** are channels; **RoleCard** defines identity.

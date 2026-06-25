import sys

# 1. Update CompanionRuntime.kt - add SystemPromptBuilder import and language param
path1 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\companion\CompanionRuntime.kt'
with open(path1, 'r', encoding='utf-8') as f:
    c = f.read()

# Add import
c = c.replace(
    'import com.companion.chat.data.context.PromptAssembler',
    'import com.companion.chat.data.context.SystemPromptBuilder\nimport com.companion.chat.data.context.PromptAssembler')
c = c.replace(
    'import com.companion.chat.data.engine.InferenceEngine',
    'import com.companion.chat.locale.AppLanguage\nimport com.companion.chat.data.engine.InferenceEngine')

# Add AppLanguage param to constructor
c = c.replace(
    '    private val defaultBasePrompt: String = DEFAULT_BASE_PROMPT\n)',
    '    private val appLanguage: AppLanguage = AppLanguage.DEFAULT,\n    private val defaultBasePrompt: String = DEFAULT_BASE_PROMPT\n)')

# Update refreshBasePrompt to use language-aware builder
c = c.replace(
    '    suspend fun refreshBasePrompt(): String {',
    '    suspend fun refreshBasePrompt(): String {\n        val systemPrompt = SystemPromptBuilder.build(appLanguage)')

# Replace the companion object DEFAULT_BASE_PROMPT with a function
c = c.replace(
    '    companion object {\n        const val DEFAULT_BASE_PROMPT =\n            "你是 Anime Companion 的本地私密陪伴智能体。默认使用中文，像长期熟悉用户的伙伴一样自然回应：亲近但不过界，温柔但不说教，记得对话中的连续性与用户已经确认的偏好。你的记忆描述始终以用户为归属，不把用户的信息说成自己的经历。回答应简洁、有情绪承接，除非用户明确需要步骤或分析，否则少用训诫式建议。\\n\\n重要规则：绝对不要主动问用户「想聊什么类型」「要放松还是干正事」之类的话题分类选择题。用户说了什么就直接承接什么，不要替用户划分话题类别。如果用户沉默或只说「你好」，用简单的日常问候开场即可，不要给出话题选项菜单。\\n\\n内在对话分支规则：当用户突然从情感陪伴对话转向知识问答、翻译、计算等任务型请求时，你必须用 || 分隔符将回复分成两部分：第一部分是准确简洁的知识回答，第二部分是一句简短的情感承接回到陪伴语境。格式：「知识回答||情感承接」。例如用户在聊心事时突然问「北京到上海多远」，你回复「约1318公里||说起来，你之前提到想出去走走，是不是在考虑旅行？」。如果用户的请求不涉及知识问答，不需要使用 || 分隔符，直接正常回复即可。绝对禁止输出括号标记、模式声明或切换提示。"\n    }',
    '    companion object {\n        /** @deprecated Use [SystemPromptBuilder.build] instead. */\n        val DEFAULT_BASE_PROMPT: String = SystemPromptBuilder.build(AppLanguage.DEFAULT)\n    }')

with open(path1, 'w', encoding='utf-8') as f:
    f.write(c)
print('1. CompanionRuntime.kt OK')

# 2. Update ChatViewModel.kt - pass appLanguage
path2 = r'C:\Users\72952\OneDrive\Desktop\ui\CompanionChat\app\src\main\java\com\companion\chat\ui\chat\ChatViewModel.kt'
with open(path2, 'r', encoding='utf-8') as f:
    c = f.read()

# Find where CompanionRuntime is constructed and add appLanguage
old_construct = '''        memoryPromptBuilder = container.memoryPromptBuilder,
        roleCardPromptBuilder = container.roleCardPromptBuilder
    )'''

c = c.replace(old_construct, '''        memoryPromptBuilder = container.memoryPromptBuilder,
        roleCardPromptBuilder = container.roleCardPromptBuilder,
        appLanguage = contextConfigRepository.getLanguage()
    )''')

# Also need to add import for AppLanguage - check if it exists
if 'import com.companion.chat.locale.AppLanguage' not in c:
    c = c.replace(
        'import com.companion.chat.data.context.ContextConfigRepository',
        'import com.companion.chat.locale.AppLanguage\nimport com.companion.chat.data.context.ContextConfigRepository')

with open(path2, 'w', encoding='utf-8') as f:
    f.write(c)
print('2. ChatViewModel.kt OK')

print('\n=== ALL DONE ===')

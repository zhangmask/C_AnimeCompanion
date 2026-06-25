# ==== VoiceDrivenChatPolicy.kt ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/ui/chat/VoiceDrivenChatPolicy.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('package com.companion.chat.ui.chat',
              'package com.companion.chat.ui.chat\n\nimport com.companion.chat.locale.AppLanguage\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey')

c = c.replace(
    '''    fun evaluateTranscript(
        transcript: String,
        isGenerating: Boolean,
        isEngineReady: Boolean
    ): VoiceTranscriptDecision {''',
    '''    fun evaluateTranscript(
        transcript: String,
        isGenerating: Boolean,
        isEngineReady: Boolean,
        lang: AppLanguage = AppLanguage.DEFAULT
    ): VoiceTranscriptDecision {'''
)

c = c.replace('VoiceTranscriptDecision.HoldForUser("未识别到文本")',
              'VoiceTranscriptDecision.HoldForUser(Strings.get(lang, StringsKey.voice_policy_no_text))')
c = c.replace('VoiceTranscriptDecision.HoldForUser("正在生成回复，请稍后再说")',
              'VoiceTranscriptDecision.HoldForUser(Strings.get(lang, StringsKey.voice_policy_generating))')
c = c.replace('VoiceTranscriptDecision.HoldForUser("模型未就绪，语音内容已保留在输入框")',
              'VoiceTranscriptDecision.HoldForUser(Strings.get(lang, StringsKey.voice_policy_not_ready))')

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("VoiceDrivenChatPolicy.kt done")

# ==== Update MainActivity.kt - screen label references ====
fp = 'CompanionChat/app/src/main/java/com/companion/chat/MainActivity.kt'
with open(fp, 'r', encoding='utf-8') as f:
    c = f.read()

# The AppNavigation Screen enum now uses labelKey instead of label.
# In MainActivity, screen.label is used at lines 169 and 172.
# Need to add Strings import and use Strings.txt(screen.labelKey)
c = c.replace(
    'import com.companion.chat.ui.theme.CompanionChatTheme',
    'import com.companion.chat.ui.theme.CompanionChatTheme\nimport com.companion.chat.locale.Strings\nimport com.companion.chat.locale.StringsKey'
)

c = c.replace(
    'contentDescription = screen.label',
    'contentDescription = Strings.txt(screen.labelKey)'
)
c = c.replace(
    'label = { Text(screen.label) }',
    'label = { Text(Strings.txt(screen.labelKey)) }'
)

with open(fp, 'w', encoding='utf-8') as f:
    f.write(c)
print("MainActivity.kt updated")

package com.companion.chat.data.profile

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfile(
    val nickname: String = "",
    val gender: String = "",
    val age: String = "",
    val bio: String = "",
    val interestTags: String = "",
    val avatarUri: String = "",
    val introduction: String = "",
    val importantInfo: String = ""
)

class UserProfileRepository(
    private val sharedPreferences: SharedPreferences
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    )

    private val _profileFlow = MutableStateFlow(getProfile())
    val profileFlow: StateFlow<UserProfile> = _profileFlow.asStateFlow()

    fun getProfile(): UserProfile {
        return UserProfile(
            nickname = sharedPreferences.getString(KEY_NICKNAME, "") ?: "",
            gender = sharedPreferences.getString(KEY_GENDER, "") ?: "",
            age = sharedPreferences.getString(KEY_AGE, "") ?: "",
            bio = sharedPreferences.getString(KEY_BIO, "") ?: "",
            interestTags = sharedPreferences.getString(KEY_INTEREST_TAGS, "") ?: "",
            avatarUri = sharedPreferences.getString(KEY_AVATAR_URI, "") ?: "",
            introduction = sharedPreferences.getString(KEY_INTRODUCTION, "") ?: "",
            importantInfo = sharedPreferences.getString(KEY_IMPORTANT_INFO, "") ?: ""
        )
    }

    fun updateProfile(profile: UserProfile) {
        sharedPreferences.edit()
            .putString(KEY_NICKNAME, profile.nickname)
            .putString(KEY_GENDER, profile.gender)
            .putString(KEY_AGE, profile.age)
            .putString(KEY_BIO, profile.bio)
            .putString(KEY_INTEREST_TAGS, profile.interestTags)
            .putString(KEY_AVATAR_URI, profile.avatarUri)
            .putString(KEY_INTRODUCTION, profile.introduction)
            .putString(KEY_IMPORTANT_INFO, profile.importantInfo)
            .apply()
        _profileFlow.value = profile
    }

    fun updateNickname(nickname: String) {
        sharedPreferences.edit().putString(KEY_NICKNAME, nickname).apply()
    }

    fun updateGender(gender: String) {
        sharedPreferences.edit().putString(KEY_GENDER, gender).apply()
    }

    fun updateAge(age: String) {
        sharedPreferences.edit().putString(KEY_AGE, age).apply()
    }

    fun updateBio(bio: String) {
        sharedPreferences.edit().putString(KEY_BIO, bio).apply()
    }

    fun updateInterestTags(tags: String) {
        sharedPreferences.edit().putString(KEY_INTEREST_TAGS, tags).apply()
    }

    companion object {
        private const val PREFS_NAME = "user_profile"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_GENDER = "gender"
        private const val KEY_AGE = "age"
        private const val KEY_BIO = "bio"
        private const val KEY_INTEREST_TAGS = "interest_tags"
        private const val KEY_AVATAR_URI = "avatar_uri"
        private const val KEY_INTRODUCTION = "introduction"
        private const val KEY_IMPORTANT_INFO = "important_info"
    }
}

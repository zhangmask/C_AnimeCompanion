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
    val interestTags: String = ""
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
            interestTags = sharedPreferences.getString(KEY_INTEREST_TAGS, "") ?: ""
        )
    }

    fun updateProfile(profile: UserProfile) {
        sharedPreferences.edit()
            .putString(KEY_NICKNAME, profile.nickname)
            .putString(KEY_GENDER, profile.gender)
            .putString(KEY_AGE, profile.age)
            .putString(KEY_BIO, profile.bio)
            .putString(KEY_INTEREST_TAGS, profile.interestTags)
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
    }
}

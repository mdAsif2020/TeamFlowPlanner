package com.teamflow.planner.supabase

import com.teamflow.planner.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperation
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.selectAsFlow
import io.github.jan.supabase.serializer.MoshiSerializer
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Supabase access layer for Java activities.
 *
 * - Uses Moshi serializer (no kotlinx-serialization plugin needed).
 * - Exposes callback-based methods for Java interop.
 */
object SupabaseService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            defaultSerializer = MoshiSerializer()
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }

    @JvmStatic
    fun isConfigured(): Boolean {
        return BuildConfig.SUPABASE_CONFIGURED &&
            BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    }

    @JvmStatic
    fun signIn(email: String, password: String, callback: SupabaseCallback<String>) {
        scope.launch {
            runCatching {
                client.auth.signInWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                    this.email = email
                    this.password = password
                }
                client.auth.currentUserOrNull()?.id ?: throw Exception("User ID not found")
            }.onSuccess { userId ->
                callback.onSuccess(userId)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    @JvmStatic
    fun resetPassword(email: String, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                client.auth.resetPasswordForEmail(email)
            }.onSuccess {
                callback.onSuccess(null)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    @JvmStatic
    fun signUp(email: String, password: String, metadata: Map<String, Any?>?, callback: SupabaseCallback<String>) {
        scope.launch {
            runCatching {
                client.auth.signUpWith(io.github.jan.supabase.auth.providers.builtin.Email) {
                    this.email = email
                    this.password = password
                    if (!metadata.isNullOrEmpty()) {
                        data = JsonObject(metadata.mapValues { (_, value) ->
                            JsonPrimitive(value.toString())
                        })
                    }
                }
                client.auth.currentUserOrNull()?.id ?: throw Exception("User registration failed")
            }.onSuccess { userId ->
                callback.onSuccess(userId)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    @JvmStatic
    fun uploadProfileImage(userId: String, imageBytes: ByteArray, callback: SupabaseCallback<String>) {
        scope.launch {
            runCatching {
                val bucket = client.storage["profiles"]
                val path = "$userId/profile.jpg"
                bucket.upload(path, imageBytes) {
                    upsert = true
                }
                bucket.publicUrl(path)
            }.onSuccess { url ->
                callback.onSuccess(url)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class Profile(
        val id: String? = null,
        val name: String? = null,
        val email: String? = null,
        val bio: String? = null,
        val phone: String? = null,
        val photo_url: String? = null
    )

    @JvmStatic
    fun updateProfile(profile: Profile, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                client.from("profiles").upsert(profile)
            }.onSuccess {
                callback.onSuccess(null)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    @JvmStatic
    fun fetchProfile(name: String, callback: SupabaseCallback<Profile>) {
        scope.launch {
            runCatching {
                client.from("profiles")
                    .select {
                        filter { eq("name", name) }
                    }
                    .decodeSingle<Profile>()
            }.onSuccess {
                callback.onSuccess(it)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    @JvmStatic
    fun fetchProfileById(id: String, callback: SupabaseCallback<Profile>) {
        scope.launch {
            runCatching {
                client.from("profiles")
                    .select {
                        filter { eq("id", id) }
                    }
                    .decodeSingle<Profile>()
            }.onSuccess {
                callback.onSuccess(it)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    @JvmStatic
    fun searchProfiles(query: String, callback: SupabaseCallback<List<Profile>>) {
        scope.launch {
            runCatching {
                client.from("profiles")
                    .select {
                        filter {
                            or {
                                ilike("name", "%$query%")
                                ilike("email", "%$query%")
                            }
                        }
                        limit(10)
                    }
                    .decodeList<Profile>()
            }.onSuccess {
                callback.onSuccess(it)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class Invitation(
        val id: Long? = null,
        val project_id: Long? = null,
        val project_name: String? = null,
        val inviter_email: String? = null,
        val invitee_email: String? = null,
        val status: String = "PENDING"
    )

    @JvmStatic
    fun sendInvitation(invitation: Invitation, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                client.from("invitations").insert(invitation)
            }.onSuccess {
                callback.onSuccess(null)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    data class MessageRow(
        val id: Long? = null,
        val project_id: Long? = null,
        val sender_name: String? = null,
        val sender_email: String? = null,
        val message: String? = null,
        val timestamp: Long? = null
    )

    @JvmStatic
    fun fetchMessages(projectId: Long, callback: SupabaseCallback<List<MessageRow>>) {
        scope.launch {
            runCatching {
                client.from("messages")
                    .select {
                        filter { eq("project_id", projectId) }
                        order("timestamp", Order.ASCENDING)
                    }
                    .decodeList<MessageRow>()
            }.onSuccess { rows ->
                callback.onSuccess(rows)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    @JvmStatic
    fun sendMessage(
        projectId: Long,
        senderName: String,
        senderEmail: String,
        text: String,
        callback: SupabaseCallback<Void>
    ) {
        val row = MessageRow(
            project_id = projectId,
            sender_name = senderName,
            sender_email = senderEmail,
            message = text,
            timestamp = System.currentTimeMillis()
        )
        scope.launch {
            runCatching {
                client.from("messages").insert(row)
            }.onSuccess {
                callback.onSuccess(null)
            }.onFailure {
                callback.onError(it)
            }
        }
    }

    /**
     * Realtime list of messages for a project. This uses PostgREST's `selectAsFlow`,
     * which emits initial data + subsequent realtime updates.
     *
     * Requires Realtime + Postgrest plugins.
     */
    @JvmStatic
    fun observeMessages(
        projectId: Long,
        callback: SupabaseCallback<List<MessageRow>>
    ) {
        scope.launch {
            try {
                @OptIn(SupabaseExperimental::class)
                client.from("messages")
                    .selectAsFlow<MessageRow, Long?>(
                        MessageRow::id,
                        filter = FilterOperation("project_id", FilterOperator.EQ, projectId)
                    )
                    .onEach { callback.onSuccess(it) }
                    .catch { callback.onError(it) }
                    .collect { }
            } catch (e: Exception) {
                callback.onError(e)
            }
        }
    }
}

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
import io.ktor.client.engine.okhttp.OkHttp
import android.util.Log
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
            httpEngine = OkHttp.create()
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
                scope.launch(Dispatchers.Main) { callback.onSuccess(userId) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun resetPassword(email: String, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                client.auth.resetPasswordForEmail(email)
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(null) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
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
                scope.launch(Dispatchers.Main) { callback.onSuccess(userId) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
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
                scope.launch(Dispatchers.Main) { callback.onSuccess(url) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class Profile(
        @com.squareup.moshi.Json(name = "id")
        val id: String? = null,
        @com.squareup.moshi.Json(name = "name")
        val name: String? = null,
        @com.squareup.moshi.Json(name = "username")
        val username: String? = null,
        @com.squareup.moshi.Json(name = "email")
        val email: String? = null,
        @com.squareup.moshi.Json(name = "bio")
        val bio: String? = null,
        @com.squareup.moshi.Json(name = "phone")
        val phone: String? = null,
        @com.squareup.moshi.Json(name = "photo_url")
        val photo_url: String? = null
    )

    @JvmStatic
    fun updateProfile(profile: Profile, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                // Using a Map ensures that null values (like photo_url) are explicitly sent 
                // to Supabase to clear the fields. 
                val updates = mutableMapOf<String, Any?>()
                updates["id"] = profile.id
                updates["name"] = profile.name
                updates["username"] = profile.username
                updates["email"] = profile.email
                updates["bio"] = profile.bio
                updates["phone"] = profile.phone
                updates["photo_url"] = profile.photo_url
                
                client.from("profiles").upsert(updates)
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(null) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun fetchProfile(name: String, callback: SupabaseCallback<Profile>) {
        scope.launch {
            runCatching {
                client.from("profiles")
                    .select {
                        filter { 
                            or {
                                eq("name", name)
                                eq("username", name)
                            }
                        }
                    }
                    .decodeSingleOrNull<Profile>()
            }.onSuccess {
                scope.launch(Dispatchers.Main) {
                    if (it != null) callback.onSuccess(it)
                    else callback.onError(Exception("Profile not found"))
                }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
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
                    .decodeSingleOrNull<Profile>()
            }.onSuccess {
                scope.launch(Dispatchers.Main) {
                    if (it != null) callback.onSuccess(it)
                    else callback.onError(Exception("Profile not found"))
                }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun fetchProfileByEmail(email: String, callback: SupabaseCallback<Profile>) {
        scope.launch {
            runCatching {
                client.from("profiles")
                    .select {
                        filter { eq("email", email) }
                    }
                    .decodeSingleOrNull<Profile>()
            }.onSuccess {
                scope.launch(Dispatchers.Main) {
                    if (it != null) callback.onSuccess(it)
                    else callback.onError(Exception("Profile not found"))
                }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun fetchProfileByUsername(username: String, callback: SupabaseCallback<Profile>) {
        scope.launch {
            runCatching {
                client.from("profiles")
                    .select {
                        filter { eq("username", username) }
                    }
                    .decodeSingleOrNull<Profile>()
            }.onSuccess {
                scope.launch(Dispatchers.Main) {
                    if (it != null) callback.onSuccess(it)
                    else callback.onError(Exception("Profile not found"))
                }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
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
                                ilike("username", "%$query%")
                                ilike("email", "%$query%")
                            }
                        }
                        limit(10)
                    }
                    .decodeList<Profile>()
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(it) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class ProjectSync(
        @com.squareup.moshi.Json(name = "id")
        val id: Long? = null,
        @com.squareup.moshi.Json(name = "name")
        val name: String? = null,
        @com.squareup.moshi.Json(name = "description")
        val description: String? = null,
        @com.squareup.moshi.Json(name = "owner_email")
        val owner_email: String? = null,
        @com.squareup.moshi.Json(name = "is_completed")
        val is_completed: Boolean = false,
        @com.squareup.moshi.Json(name = "is_pinned")
        val is_pinned: Boolean = false,
        @com.squareup.moshi.Json(name = "created_at")
        val created_at: Long? = null,
        @com.squareup.moshi.Json(name = "last_modified")
        val last_modified: Long? = null
    )

    @JvmStatic
    fun upsertProject(project: ProjectSync, callback: SupabaseCallback<Long>) {
        scope.launch {
            runCatching {
                val session = client.auth.currentSessionOrNull()
                Log.d("SupabaseService", "Syncing project: ${project.name}, ID: ${project.id}, Session: ${session != null}")

                val table = client.from("projects")
                // Use upsert to handle both insert and update automatically
                val response = table.upsert(project) {
                    select()
                }.decodeSingle<ProjectSync>()
                
                response.id ?: throw Exception("Failed to get project ID from response")
            }.onSuccess {
                Log.d("SupabaseService", "Project sync success: $it")
                scope.launch(Dispatchers.Main) { callback.onSuccess(it) }
            }.onFailure {
                Log.e("SupabaseService", "Project sync failure", it)
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun fetchProjectById(id: Long, callback: SupabaseCallback<ProjectSync>) {
        scope.launch {
            runCatching {
                client.from("projects")
                    .select {
                        filter { eq("id", id) }
                    }
                    .decodeSingle<ProjectSync>()
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(it) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun deleteProject(id: Long, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                client.from("projects").delete {
                    filter { eq("id", id) }
                }
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(null) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class ProjectMemberSync(
        @com.squareup.moshi.Json(name = "id")
        val id: Long? = null,
        @com.squareup.moshi.Json(name = "project_id")
        val project_id: Long? = null,
        @com.squareup.moshi.Json(name = "user_email")
        val user_email: String? = null,
        @com.squareup.moshi.Json(name = "user_name")
        val user_name: String? = null,
        @com.squareup.moshi.Json(name = "user_username")
        val user_username: String? = null
    )

    @JvmStatic
    fun addProjectMember(member: ProjectMemberSync, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                client.from("project_members").insert(member)
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(null) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun fetchProjectMembers(projectId: Long, callback: SupabaseCallback<List<ProjectMemberSync>>) {
        scope.launch {
            runCatching {
                client.from("project_members")
                    .select {
                        filter { eq("project_id", projectId) }
                    }
                    .decodeList<ProjectMemberSync>()
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(it) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class Invitation(
        @com.squareup.moshi.Json(name = "id")
        val id: Long? = null,
        @com.squareup.moshi.Json(name = "project_id")
        val project_id: Long? = null,
        @com.squareup.moshi.Json(name = "project_name")
        val project_name: String? = null,
        @com.squareup.moshi.Json(name = "inviter_email")
        val inviter_email: String? = null,
        @com.squareup.moshi.Json(name = "inviter_username")
        val inviter_username: String? = null,
        @com.squareup.moshi.Json(name = "invitee_email")
        val invitee_email: String? = null,
        @com.squareup.moshi.Json(name = "status")
        val status: String = "PENDING"
    )

    @JvmStatic
    fun sendInvitation(invitation: Invitation, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                Log.d("SupabaseService", "Sending invitation to ${invitation.invitee_email}")
                client.from("invitations").insert(invitation)
            }.onSuccess {
                Log.d("SupabaseService", "Invitation sent successfully")
                scope.launch(Dispatchers.Main) { callback.onSuccess(null) }
            }.onFailure {
                Log.e("SupabaseService", "Failed to send invitation", it)
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun fetchInvitations(email: String, callback: SupabaseCallback<List<Invitation>>) {
        scope.launch {
            runCatching {
                client.from("invitations")
                    .select {
                        filter { eq("invitee_email", email) }
                        filter { eq("status", "PENDING") }
                    }
                    .decodeList<Invitation>()
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(it) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun updateInvitationStatus(id: Long, status: String, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                client.from("invitations").update(
                    mapOf("status" to status)
                ) {
                    filter { eq("id", id) }
                }
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(null) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun observeInvitations(email: String, callback: SupabaseCallback<List<Invitation>>) {
        scope.launch {
            try {
                @OptIn(SupabaseExperimental::class)
                client.from("invitations")
                    .selectAsFlow<Invitation, Long?>(
                        Invitation::id,
                        filter = FilterOperation("invitee_email", FilterOperator.EQ, email)
                    )
                    .onEach { items ->
                        scope.launch(Dispatchers.Main) {
                            callback.onSuccess(items.filter { inv -> inv.status == "PENDING" })
                        }
                    }
                    .catch { e ->
                        scope.launch(Dispatchers.Main) { callback.onError(e) }
                    }
                    .collect { }
            } catch (e: Exception) {
                scope.launch(Dispatchers.Main) { callback.onError(e) }
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class TaskSync(
        @com.squareup.moshi.Json(name = "id")
        val id: Long? = null,
        @com.squareup.moshi.Json(name = "project_id")
        val project_id: Long? = null,
        @com.squareup.moshi.Json(name = "title")
        val title: String? = null,
        @com.squareup.moshi.Json(name = "description")
        val description: String? = null,
        @com.squareup.moshi.Json(name = "notes")
        val notes: String? = null,
        @com.squareup.moshi.Json(name = "assignee")
        val assignee: String? = null,
        @com.squareup.moshi.Json(name = "status")
        val status: String? = null,
        @com.squareup.moshi.Json(name = "priority")
        val priority: String? = null,
        @com.squareup.moshi.Json(name = "deadline")
        val deadline: Long? = null,
        @com.squareup.moshi.Json(name = "last_modified")
        val last_modified: Long? = null
    )

    @JvmStatic
    fun upsertTask(task: TaskSync, callback: SupabaseCallback<Long>) {
        scope.launch {
            runCatching {
                val session = client.auth.currentSessionOrNull()
                Log.d("SupabaseService", "Syncing task: ${task.title}, ID: ${task.id}, Session: ${session != null}")

                val table = client.from("tasks")
                // Use upsert to handle both insert and update automatically
                val response = table.upsert(task) {
                    select()
                }.decodeSingle<TaskSync>()
                
                response.id ?: throw Exception("Failed to get task ID from response")
            }.onSuccess {
                Log.d("SupabaseService", "Task sync success: $it")
                scope.launch(Dispatchers.Main) { callback.onSuccess(it) }
            }.onFailure {
                Log.e("SupabaseService", "Task sync failure", it)
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun fetchTasks(projectId: Long, callback: SupabaseCallback<List<TaskSync>>) {
        scope.launch {
            runCatching {
                client.from("tasks")
                    .select {
                        filter { eq("project_id", projectId) }
                    }
                    .decodeList<TaskSync>()
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(it) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun deleteTask(taskId: Long, callback: SupabaseCallback<Void>) {
        scope.launch {
            runCatching {
                client.from("tasks").delete {
                    filter { eq("id", taskId) }
                }
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(null) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @JvmStatic
    fun observeTasks(projectId: Long, callback: SupabaseCallback<List<TaskSync>>) {
        scope.launch {
            try {
                @OptIn(SupabaseExperimental::class)
                client.from("tasks")
                    .selectAsFlow<TaskSync, Long?>(
                        TaskSync::id,
                        filter = FilterOperation("project_id", FilterOperator.EQ, projectId)
                    )
                    .onEach { items ->
                        scope.launch(Dispatchers.Main) { callback.onSuccess(items) }
                    }
                    .catch { e ->
                        scope.launch(Dispatchers.Main) { callback.onError(e) }
                    }
                    .collect { }
            } catch (e: Exception) {
                scope.launch(Dispatchers.Main) { callback.onError(e) }
            }
        }
    }

    @JvmStatic
    fun fetchMyProjects(email: String, callback: SupabaseCallback<List<ProjectSync>>) {
        scope.launch {
            runCatching {
                // Projects where I am the owner
                val owned = client.from("projects").select {
                    filter { eq("owner_email", email) }
                }.decodeList<ProjectSync>()

                // Projects where I am a member
                val memberOfIds = client.from("project_members").select {
                    filter { eq("user_email", email) }
                }.decodeList<ProjectMemberSync>().mapNotNull { it.project_id }

                val memberOf = if (memberOfIds.isNotEmpty()) {
                    client.from("projects").select {
                        filter { isIn("id", memberOfIds) }
                    }.decodeList<ProjectSync>()
                } else emptyList()

                (owned + memberOf).distinctBy { it.id }
            }.onSuccess {
                scope.launch(Dispatchers.Main) { callback.onSuccess(it) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class MessageRow(
        @com.squareup.moshi.Json(name = "id")
        val id: Long? = null,
        @com.squareup.moshi.Json(name = "project_id")
        val project_id: Long? = null,
        @com.squareup.moshi.Json(name = "sender_name")
        val sender_name: String? = null,
        @com.squareup.moshi.Json(name = "sender_email")
        val sender_email: String? = null,
        @com.squareup.moshi.Json(name = "message")
        val message: String? = null,
        @com.squareup.moshi.Json(name = "timestamp")
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
                scope.launch(Dispatchers.Main) { callback.onSuccess(rows) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
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
                scope.launch(Dispatchers.Main) { callback.onSuccess(null) }
            }.onFailure {
                scope.launch(Dispatchers.Main) { callback.onError(it) }
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
                    .onEach { items ->
                        scope.launch(Dispatchers.Main) { callback.onSuccess(items) }
                    }
                    .catch { e ->
                        scope.launch(Dispatchers.Main) { callback.onError(e) }
                    }
                    .collect { }
            } catch (e: Exception) {
                scope.launch(Dispatchers.Main) { callback.onError(e) }
            }
        }
    }
}

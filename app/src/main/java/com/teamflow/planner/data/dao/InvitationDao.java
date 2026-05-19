package com.teamflow.planner.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.teamflow.planner.data.entity.Invitation;

import java.util.List;

@Dao
public interface InvitationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(Invitation invitation);

    @Update
    void update(Invitation invitation);

    @Query("SELECT * FROM invitations WHERE status = 'PENDING'")
    LiveData<List<Invitation>> observePendingInvitations();

    @Query("SELECT * FROM invitations WHERE remoteProjectId = :remoteId AND inviteeEmail = :email LIMIT 1")
    Invitation getInvitation(long remoteId, String email);
}

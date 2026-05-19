package com.teamflow.planner.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.teamflow.planner.data.entity.TeamMember;

import java.util.List;

@Dao
public interface TeamMemberDao {

    @Insert
    long insert(TeamMember member);

    @Delete
    void delete(TeamMember member);

    @Query("SELECT * FROM team_members WHERE projectId = :projectId ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<TeamMember>> observeMembersForProject(long projectId);

    @Query("SELECT * FROM team_members WHERE projectId = :projectId ORDER BY name COLLATE NOCASE ASC")
    List<TeamMember> getMembersForProjectSync(long projectId);
}

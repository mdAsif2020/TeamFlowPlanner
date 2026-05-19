package com.teamflow.planner.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.teamflow.planner.data.ProjectListItem;
import com.teamflow.planner.data.entity.Project;

import java.util.List;

/**
 * CRUD and dashboard queries for {@link Project}.
 */
@Dao
public interface ProjectDao {

    @Insert
    long insert(Project project);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(Project project);

    @Update
    void update(Project project);

    @Delete
    void delete(Project project);

    @Query("SELECT * FROM projects")
    List<Project> getAllProjectsSync();

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    Project getProjectById(long id);

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    LiveData<Project> observeProject(long id);

    @androidx.room.Transaction
    @Query("SELECT p.*, "
            + "(SELECT COUNT(*) FROM tasks t WHERE t.projectId = p.id) AS taskCount, "
            + "(SELECT COUNT(*) FROM tasks t WHERE t.projectId = p.id AND t.status = 'COMPLETED') AS completedCount "
            + "FROM projects p ORDER BY p.createdAt DESC")
    LiveData<List<ProjectListItem>> observeProjectsWithStats();
}

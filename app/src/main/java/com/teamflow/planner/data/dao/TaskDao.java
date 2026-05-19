package com.teamflow.planner.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.teamflow.planner.data.AssigneeName;
import com.teamflow.planner.data.DailyFocusItem;
import com.teamflow.planner.data.TaskWithProject;
import com.teamflow.planner.data.entity.Task;

import java.util.List;

/**
 * CRUD and reporting queries for {@link Task}.
 */
@Dao
public interface TaskDao {

    @Insert
    long insert(Task task);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrReplace(Task task);

    @Update
    void update(Task task);

    @Delete
    void delete(Task task);

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    Task getTaskById(long id);

    @Query("SELECT * FROM tasks WHERE remoteId = :remoteId LIMIT 1")
    Task getTaskByRemoteIdSync(long remoteId);

    @Query("SELECT tasks.*, p.name AS projectName FROM tasks INNER JOIN projects p ON p.id = tasks.projectId "
            + "WHERE LOWER(tasks.assignee) = LOWER(:assignee) ORDER BY (tasks.deadline IS NULL), tasks.deadline ASC")
    LiveData<List<TaskWithProject>> observeTasksForAssignee(String assignee);

    /**
     * Names from task assignees plus project roster (team_members), for the member workload directory.
     */
    @Query("SELECT DISTINCT assignee FROM tasks WHERE assignee != '' "
            + "UNION SELECT DISTINCT name AS assignee FROM team_members WHERE name != '' "
            + "ORDER BY assignee COLLATE NOCASE ASC")
    LiveData<List<AssigneeName>> observePeopleForMemberDirectory();

    @Query("SELECT * FROM tasks WHERE projectId = :projectId")
    LiveData<List<Task>> observeTasksForProject(long projectId);

    @Query("SELECT * FROM tasks WHERE projectId = :projectId")
    List<Task> getTasksForProjectSync(long projectId);

    @Query("SELECT COUNT(*) FROM tasks")
    LiveData<Integer> observeTotalTaskCount();

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'COMPLETED'")
    LiveData<Integer> observeCompletedTaskCount();

    @Query("UPDATE tasks SET assignee = '' WHERE LOWER(assignee) = LOWER(:name)")
    void unassignPerson(String name);

    /**
     * Smart Daily Focus: up to three nearest non-completed tasks by deadline.
     */
    @Query("SELECT tasks.*, projects.name AS projectName FROM tasks "
            + "INNER JOIN projects ON projects.id = tasks.projectId "
            + "WHERE tasks.status != 'COMPLETED' "
            + "ORDER BY CASE tasks.priority "
            + "WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 2 END DESC, "
            + "(tasks.deadline IS NULL), tasks.deadline ASC "
            + "LIMIT 3")
    LiveData<List<DailyFocusItem>> observeDailyFocus();
}

package com.teamflow.planner.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.teamflow.planner.data.dao.InvitationDao;
import com.teamflow.planner.data.dao.ProjectDao;
import com.teamflow.planner.data.dao.TaskDao;
import com.teamflow.planner.data.dao.TeamMemberDao;
import com.teamflow.planner.data.dao.UserDao;
import com.teamflow.planner.data.entity.Invitation;
import com.teamflow.planner.data.entity.Project;
import com.teamflow.planner.data.entity.Task;
import com.teamflow.planner.data.entity.TeamMember;
import com.teamflow.planner.data.entity.User;

/**
 * Single Room database for offline storage.
 */
@Database(
        entities = {Project.class, Task.class, TeamMember.class, User.class, Invitation.class},
        version = 8,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `team_members` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`projectId` INTEGER NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_team_members_projectId` ON `team_members` (`projectId`)");
            db.execSQL("ALTER TABLE tasks ADD COLUMN description TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE tasks ADD COLUMN priority TEXT NOT NULL DEFAULT 'MEDIUM'");
            db.execSQL("ALTER TABLE tasks ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE projects ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE projects ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `email` TEXT NOT NULL, `password` TEXT NOT NULL, `lastLogin` INTEGER NOT NULL)");
        }
    };

    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `invitations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectOwnerEmail` TEXT, `projectName` TEXT, `remoteProjectId` INTEGER NOT NULL, `inviteeEmail` TEXT, `status` TEXT)");
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE projects ADD COLUMN ownerEmail TEXT");
        }
    };

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE users ADD COLUMN photoUrl TEXT");
        }
    };

    private static volatile AppDatabase INSTANCE;

    public abstract ProjectDao projectDao();

    public abstract TaskDao taskDao();

    public abstract TeamMemberDao teamMemberDao();

    public abstract UserDao userDao();

    public abstract InvitationDao invitationDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "teamflow_planner.db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}

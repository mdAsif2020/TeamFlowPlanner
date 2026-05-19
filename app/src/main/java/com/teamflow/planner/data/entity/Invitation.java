package com.teamflow.planner.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "invitations")
public class Invitation {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String projectOwnerEmail;
    public String projectName;
    public long remoteProjectId;
    public String inviteeEmail;
    public String status; // PENDING, ACCEPTED, REJECTED

    public Invitation(String projectOwnerEmail, String projectName, long remoteProjectId, String inviteeEmail) {
        this.projectOwnerEmail = projectOwnerEmail;
        this.projectName = projectName;
        this.remoteProjectId = remoteProjectId;
        this.inviteeEmail = inviteeEmail;
        this.status = "PENDING";
    }
}

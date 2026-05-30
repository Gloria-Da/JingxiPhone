package com.yoyo.jingxi.data.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "my_personas")
public class MyPersona {
    @PrimaryKey
    @NonNull
    public String name = "";

    public String persona;
    public boolean isMainPersona;
    public String avatarPath;
}

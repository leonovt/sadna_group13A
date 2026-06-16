package com.sadna.group13a.domain.Aggregates.User;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Guest extends User {

    @JsonCreator
    public Guest(@JsonProperty("id") String id, @JsonProperty("username") String username) {
        super(id, username, new GuestState());
    }

    public Guest(String username) {
        super(username);
    }
}

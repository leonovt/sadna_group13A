package com.sadna.group13a.domain.Aggregates.User;

public class Guest extends User {

    public Guest(String id, String username) {
        super(id, username, new GuestState());
    }

    public Guest(String username) {
        super(username);
    }
}

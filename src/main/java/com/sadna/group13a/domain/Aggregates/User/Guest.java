package com.sadna.group13a.domain.Aggregates.User;

import jakarta.persistence.*;

@Entity
@DiscriminatorValue("GUEST")
public class Guest extends User {

    protected Guest() {}

    public Guest(String id, String username) {
        super(id, username, new GuestState());
    }

    public Guest(String username) {
        super(username);
    }

    @PostLoad
    private void onLoad() {
        setTypeState(new GuestState());
    }
}

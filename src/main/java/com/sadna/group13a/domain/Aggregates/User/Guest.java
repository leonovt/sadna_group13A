package com.sadna.group13a.domain.Aggregates.User;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@DiscriminatorValue("GUEST")
public class Guest extends User {

    protected Guest() {}

    @JsonCreator
    public Guest(@JsonProperty("id") String id, @JsonProperty("username") String username) {
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

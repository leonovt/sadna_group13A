package com.sadna.group13a.application.Interfaces;

public interface IPasswordEncoder {

    /**
     * Encodes a raw password string securely.
     * Use this method when a user is registering or changing their password.
     *
     * @param rawPassword The plain text password provided by the user.
     * @return The securely hashed version of the password.
     */
    String encodePassword(String rawPassword);

    /**
     * Verifies if a provided raw password matches a previously encoded password.
     * Use this method during the login process.
     *
     * @param rawPassword The plain text password provided by the user trying to log in.
     * @param encodedPassword The hashed password retrieved from the repository/database.
     * @return true if the passwords match, false otherwise.
     */
    boolean matches(String rawPassword, String encodedPassword);

}
package com.sadna.group13a.presentation.views.member;

import com.sadna.group13a.application.DTO.UserDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("member/profile")
@PageTitle("Profile")
public class ProfileView extends VerticalLayout {

    private final ProfilePresenter presenter;

    private final Span roleLabel = new Span();
    private final TextField usernameField = new TextField("Username");
    private final Span infoMessage = new Span();
    private final Span errorMessage = new Span();

    public ProfileView(ProfilePresenter presenter) {
        this.presenter = presenter;
        initView();
        presenter.loadProfile(this);
    }

    private void initView() {
        setAlignItems(Alignment.CENTER);

        infoMessage.getStyle().set("color", "var(--lumo-success-color)");
        infoMessage.setVisible(false);
        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);

        Button saveButton = new Button("Save Changes", e -> {
            clearMessages();
            presenter.handleUpdateUsername(usernameField.getValue(), this);
        });

        add(new H2("My Profile"), roleLabel, usernameField, saveButton, infoMessage, errorMessage);
    }

    public void showProfile(UserDTO user) {
        usernameField.setValue(user.username() == null ? "" : user.username());
        roleLabel.setText("Role: " + user.role());
    }

    public void showInfo(String message) {
        infoMessage.setText(message);
        infoMessage.setVisible(true);
    }

    public void showError(String message) {
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }

    private void clearMessages() {
        infoMessage.setVisible(false);
        errorMessage.setVisible(false);
    }
}

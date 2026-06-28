package com.sadna.group13a.presentation.views.member;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.UserDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route("member/profile")
@PageTitle("Profile")
public class ProfileView extends VerticalLayout implements BeforeEnterObserver {

    private final ProfilePresenter presenter;

    private final Span roleLabel = new Span();
    private final TextField usernameField = new TextField("Username");
    private final DatePicker birthDatePicker = new DatePicker("Date of Birth");
    private final Span infoMessage = new Span();
    private final Span errorMessage = new Span();

    /** The username last loaded from the server, used to detect unsaved edits. */
    private String loadedUsername = "";

    public ProfileView(ProfilePresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    /**
     * Loads the profile on navigation rather than from the constructor: at
     * construction time the component is not yet attached and
     * {@code VaadinSession.getCurrent()} may be unavailable. {@code beforeEnter}
     * runs in the UI request thread with the session initialized.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        presenter.loadProfile(this);
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setAlignItems(Alignment.CENTER);

        infoMessage.getStyle().set("color", "var(--lumo-success-color)");
        infoMessage.setVisible(false);
        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);

        Button saveUsernameButton = new Button("Save Username", e -> {
            clearMessages();
            presenter.handleUpdateUsername(usernameField.getValue(), this);
        });

        Button saveBirthDateButton = new Button("Save Birth Date", e -> {
            clearMessages();
            presenter.handleUpdateBirthDate(birthDatePicker.getValue(), this);
        });

        Image easterEgg = new Image("images/supe.png", "🦸");
        easterEgg.setHeight("180px");
        easterEgg.getStyle().set("border-radius", "12px")
                            .set("box-shadow", "0 4px 12px rgba(0,0,0,0.2)")
                            .set("margin-top", "32px");

        add(new H2("My Profile"), roleLabel,
            usernameField, saveUsernameButton,
            birthDatePicker, saveBirthDateButton,
            infoMessage, errorMessage, easterEgg);
    }

    public void showProfile(UserDTO user) {
        loadedUsername = user.username() == null ? "" : user.username();
        usernameField.setValue(loadedUsername);
        roleLabel.setText("Role: " + user.role());
        birthDatePicker.setValue(user.dateOfBirth());
    }

    /** The username currently loaded in the form (before any unsaved edit). */
    public String getLoadedUsername() {
        return loadedUsername;
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

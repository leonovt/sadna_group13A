package com.sadna.group13a.presentation.views.auth;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

@Route("register")
@PageTitle("Register")
public class RegisterView extends VerticalLayout {

    private final RegisterPresenter presenter;
    private final TextField usernameField = new TextField("Username");
    private final PasswordField passwordField = new PasswordField("Password");
    private final PasswordField confirmPasswordField = new PasswordField("Confirm Password");
    private final Span errorMessage = new Span();

    public RegisterView(RegisterPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setSizeFull();

        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);

        Button registerButton = new Button("Register", e -> {
            errorMessage.setVisible(false);
            presenter.handleRegister(
                usernameField.getValue(),
                passwordField.getValue(),
                confirmPasswordField.getValue(),
                this
            );
        });

        RouterLink loginLink = new RouterLink("Already have an account? Login", LoginView.class);

        add(new H2("Register"), usernameField, passwordField, confirmPasswordField,
            errorMessage, registerButton, loginLink);
    }

    public void showError(String message) {
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }
}

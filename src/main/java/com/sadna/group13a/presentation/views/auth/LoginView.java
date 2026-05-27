package com.sadna.group13a.presentation.views.auth;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

@Route("login")
@PageTitle("Login")
public class LoginView extends VerticalLayout {

    private final LoginPresenter presenter;
    private final TextField usernameField = new TextField("Username");
    private final PasswordField passwordField = new PasswordField("Password");
    private final Span errorMessage = new Span();

    public LoginView(LoginPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    private void initView() {
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setSizeFull();

        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);

        Button loginButton = new Button("Login", e -> {
            errorMessage.setVisible(false);
            presenter.handleLogin(usernameField.getValue(), passwordField.getValue(), this);
        });

        Button guestButton = new Button("Continue as Guest", e ->
            presenter.handleGuestLogin(this)
        );

        RouterLink registerLink = new RouterLink(
            "Don't have an account? Register", RegisterView.class
        );

        Image easterEgg = new Image("images/king.png", "👑");
        easterEgg.setHeight("180px");
        easterEgg.getStyle().set("border-radius", "12px")
                            .set("box-shadow", "0 4px 12px rgba(0,0,0,0.2)")
                            .set("margin-top", "32px");

        add(
            new H2("Login"),
            usernameField,
            passwordField,
            errorMessage,
            loginButton,
            guestButton,
            registerLink,
            easterEgg
        );
    }

    public void showError(String message) {
        errorMessage.setText(message);
        errorMessage.setVisible(true);
    }
}

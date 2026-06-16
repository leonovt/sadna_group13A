package com.sadna.group13a.presentation.views.member;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.sadna.group13a.application.DTO.UserDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;

@Route("member")
@PageTitle("Dashboard")
public class MemberDashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final MemberDashboardPresenter presenter;

    private final H2 greeting = new H2("Member Dashboard");
    private final TextField companyNameField = new TextField("Company name");
    private final TextField companyDescriptionField = new TextField("Description");
    private final Span infoMessage = new Span();
    private final Span errorMessage = new Span();

    public MemberDashboardView(MemberDashboardPresenter presenter) {
        this.presenter = presenter;
        initView();
    }

    /**
     * Loads the dashboard on navigation rather than from the constructor: at
     * construction time the component is not yet attached and
     * {@code VaadinSession.getCurrent()} may be unavailable. {@code beforeEnter}
     * runs in the UI request thread with the session initialized.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        presenter.loadDashboard(this);
    }

    private void initView() {
        add(new Button("<- Home", e -> UI.getCurrent().navigate("")));
        setAlignItems(Alignment.CENTER);

        infoMessage.getStyle().set("color", "var(--lumo-success-color)");
        infoMessage.setVisible(false);
        errorMessage.getStyle().set("color", "var(--lumo-error-color)");
        errorMessage.setVisible(false);

        HorizontalLayout navigation = new HorizontalLayout(
            new RouterLink("My Orders", OrderHistoryView.class),
            new RouterLink("My Profile", ProfileView.class),
            new RouterLink("Raffles", RaffleView.class),
            new RouterLink("Complaints", ComplaintView.class),
            new RouterLink("Inquiries", InquiryView.class)
        );

        Button createCompanyButton = new Button("Create Company", e -> {
            clearMessages();
            presenter.handleCreateCompany(
                companyNameField.getValue(),
                companyDescriptionField.getValue(),
                this
            );
        });

        Button logoutButton = new Button("Log out", e -> presenter.handleLogout(this));

        Image easterEgg = new Image("images/prof.png", "🎓");
        easterEgg.setHeight("180px");
        easterEgg.getStyle().set("border-radius", "12px")
                            .set("box-shadow", "0 4px 12px rgba(0,0,0,0.2)")
                            .set("margin-top", "32px");

        add(
            greeting,
            navigation,
            new H3("Start a Production Company"),
            companyNameField,
            companyDescriptionField,
            createCompanyButton,
            infoMessage,
            errorMessage,
            logoutButton,
            easterEgg
        );
    }

    public void showProfile(UserDTO user) {
        greeting.setText("Welcome, " + user.username() + " (" + user.role() + ")");
    }

    public void clearCompanyForm() {
        companyNameField.clear();
        companyDescriptionField.clear();
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

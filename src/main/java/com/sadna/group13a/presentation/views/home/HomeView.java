package com.sadna.group13a.presentation.views.home;

import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.User.UserRole;
import com.sadna.group13a.presentation.views.admin.AdminDashboardView;
import com.sadna.group13a.presentation.views.auth.LoginView;
import com.sadna.group13a.presentation.views.cart.CartView;
import com.sadna.group13a.presentation.views.company.CompanyDashboardView;
import com.sadna.group13a.presentation.views.member.MemberDashboardView;
import com.sadna.group13a.presentation.views.member.OrderHistoryView;
import com.sadna.group13a.presentation.views.member.ProfileView;
import com.sadna.group13a.presentation.views.member.RaffleView;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Route("")
@PageTitle("Events")
public class HomeView extends VerticalLayout implements BeforeEnterObserver {

    private final HomePresenter presenter;
    private final Grid<EventDTO> eventGrid = new Grid<>(EventDTO.class, false);
    private final TextField searchField = new TextField();
    private String currentUserId;

    public HomeView(HomePresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token == null || !presenter.isTokenValid(token)) {
            VaadinSession.getCurrent().setAttribute("token", null);
            event.rerouteTo(LoginView.class);
            return;
        }
        currentUserId = presenter.getUserId(token);
        removeAll();
        initView(token);
    }

    private void initView(String token) {
        setSizeFull();
        setPadding(true);

        Result<UserDTO> profileResult = presenter.loadUserProfile(token);
        UserDTO profile = profileResult.isSuccess() ? profileResult.getOrThrow() : null;
        String displayName = (profile != null) ? profile.username() : "Guest";
        UserRole role = (profile != null) ? profile.role() : UserRole.GUEST;

        add(buildHeader(token, displayName, role));

        if (role == UserRole.MEMBER || role == UserRole.ADMIN) {
            List<CompanyDTO> companies = presenter.getMyCompanies(token);
            add(buildMyCompaniesSection(companies));
        }

        add(buildSearchBar());
        add(buildEventGrid());

        loadEvents(null);

        add(buildYahlieSection());
    }

    private HorizontalLayout buildHeader(String token, String displayName, UserRole role) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);

        H2 title = new H2("Event Marketplace");
        Span userLabel = new Span("Hello, " + displayName);

        header.add(title, userLabel);
        header.addAndExpand(new Span());

        if (role == UserRole.MEMBER || role == UserRole.ADMIN) {
            header.add(new RouterLink("My Dashboard", MemberDashboardView.class));
            header.add(new RouterLink("My Orders", OrderHistoryView.class));
            header.add(new RouterLink("My Profile", ProfileView.class));
            header.add(new RouterLink("My Raffles", RaffleView.class));
        }
        if (role == UserRole.ADMIN) {
            header.add(new RouterLink("Admin", AdminDashboardView.class));
        }
        header.add(new RouterLink("Cart", CartView.class));
        header.add(new Button("Logout", e -> presenter.handleLogout(token)));
        return header;
    }

    private HorizontalLayout buildMyCompaniesSection(List<CompanyDTO> companies) {
        HorizontalLayout row = new HorizontalLayout();
        row.setAlignItems(Alignment.CENTER);
        row.add(new H4("My Companies:"));

        if (companies.isEmpty()) {
            row.add(new Span("None — create one from My Dashboard."));
        } else {
            for (CompanyDTO company : companies) {
                RouterLink link = new RouterLink(
                    company.name(),
                    CompanyDashboardView.class,
                    new RouteParameters("companyId", company.id())
                );
                row.add(link);
            }
        }
        return row;
    }

    private HorizontalLayout buildSearchBar() {
        searchField.setPlaceholder("Search events...");
        searchField.setWidth("300px");
        Button searchButton = new Button("Search", e -> loadEvents(searchField.getValue().isBlank() ? null : searchField.getValue()));
        searchField.addValueChangeListener(e -> {
            if (e.getValue().isBlank()) loadEvents(null);
        });
        HorizontalLayout bar = new HorizontalLayout(searchField, searchButton);
        bar.setAlignItems(Alignment.BASELINE);
        return bar;
    }

    private Grid<EventDTO> buildEventGrid() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        eventGrid.addColumn(EventDTO::title).setHeader("Event").setSortable(true).setAutoWidth(true);
        eventGrid.addColumn(e -> e.eventDate() != null ? e.eventDate().format(fmt) : "")
                .setHeader("Date")
                .setComparator(Comparator.comparing(EventDTO::eventDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .setAutoWidth(true);
        eventGrid.addColumn(EventDTO::location).setHeader("Location").setAutoWidth(true);
        eventGrid.addColumn(EventDTO::category).setHeader("Category").setAutoWidth(true);
        eventGrid.addColumn(EventDTO::totalAvailableTickets).setHeader("Available").setAutoWidth(true);
        eventGrid.addItemClickListener(click -> UI.getCurrent().navigate("events/" + click.getItem().id()));
        eventGrid.setWidthFull();
        return eventGrid;
    }

    private void loadEvents(String query) {
        Result<List<EventDTO>> result = presenter.loadEvents(query);
        List<EventDTO> events = result.isSuccess() ? result.getOrThrow() : Collections.emptyList();
        eventGrid.setItems(events);
    }

    private VerticalLayout buildYahlieSection() {
        VerticalLayout section = new VerticalLayout();
        section.setWidthFull();
        section.setPadding(true);
        section.getStyle().set("border-top", "2px solid var(--lumo-contrast-10pct)")
                          .set("margin-top", "40px");
        section.setAlignItems(Alignment.CENTER);

        Image img = new Image("images/image.png", "👑");
        img.setHeight("200px");
        img.getStyle().set("border-radius", "12px")
                      .set("box-shadow", "0 4px 12px rgba(0,0,0,0.2)");
        section.add(img);
        return section;
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        String token = (String) VaadinSession.getCurrent().getAttribute("token");
        if (token != null && currentUserId != null) {
            presenter.registerForNotifications(currentUserId, attachEvent.getUI());
            attachEvent.getUI().getPage().executeJs(
                "if (!window.__notificationHandler) {" +
                "  window.__notificationHandler = function(e) {" +
                "    var n = document.createElement('div');" +
                "    n.style.cssText = 'position:fixed;top:20px;right:20px;background:#323232;" +
                "color:white;padding:12px 20px;border-radius:4px;z-index:9999;" +
                "box-shadow:0 2px 8px rgba(0,0,0,0.3);';" +
                "    n.textContent = e.detail;" +
                "    document.body.appendChild(n);" +
                "    setTimeout(function(){ n.remove(); }, 5000);" +
                "  };" +
                "  window.addEventListener('notification', window.__notificationHandler);" +
                "}"
            );
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (currentUserId != null) {
            presenter.unregisterFromNotifications(currentUserId);
        }
        getUI().ifPresent(ui -> ui.getPage().executeJs(
            "if (window.__notificationHandler) {" +
            "  window.removeEventListener('notification', window.__notificationHandler);" +
            "  window.__notificationHandler = null;" +
            "}"
        ));
    }
}
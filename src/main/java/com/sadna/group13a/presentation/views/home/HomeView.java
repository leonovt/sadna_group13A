package com.sadna.group13a.presentation.views.home;

import com.sadna.group13a.application.DTO.CompanyDTO;
import com.sadna.group13a.application.DTO.EventDTO;
import com.sadna.group13a.application.DTO.UserDTO;
import com.sadna.group13a.application.Result;
import com.sadna.group13a.domain.Aggregates.User.UserRole;
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
        setWidthFull();
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

        loadEvents(null, null, null, null);

        Image easterEgg = new Image("images/image.png", "👑");
        easterEgg.setHeight("200px");
        easterEgg.getStyle().set("border-radius", "12px")
                            .set("box-shadow", "0 4px 12px rgba(0,0,0,0.2)")
                            .set("margin-top", "32px")
                            .set("align-self", "center");
        add(easterEgg);
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
        if (presenter.isAdmin(token)) {
            Button adminBtn = new Button("Admin Panel", e -> UI.getCurrent().navigate("admin"));
            adminBtn.getStyle().set("background-color", "var(--lumo-error-color)")
                               .set("color", "white");
            header.add(adminBtn);
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

    private final TextField categoryField = new TextField();
    private final TextField locationField = new TextField();
    private final TextField artistField   = new TextField();

    private HorizontalLayout buildSearchBar() {
        searchField.setPlaceholder("Search by title...");
        searchField.setWidth("200px");
        categoryField.setPlaceholder("Category");
        categoryField.setWidth("130px");
        locationField.setPlaceholder("Location");
        locationField.setWidth("130px");
        artistField.setPlaceholder("Artist");
        artistField.setWidth("130px");

        searchField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        categoryField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        locationField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        artistField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);

        searchField.addValueChangeListener(e -> runSearch());
        categoryField.addValueChangeListener(e -> runSearch());
        locationField.addValueChangeListener(e -> runSearch());
        artistField.addValueChangeListener(e -> runSearch());

        HorizontalLayout bar = new HorizontalLayout(searchField, categoryField, locationField, artistField);
        bar.setAlignItems(Alignment.BASELINE);
        return bar;
    }

    private void runSearch() {
        loadEvents(
                searchField.getValue().isBlank() ? null : searchField.getValue(),
                categoryField.getValue(), locationField.getValue(), artistField.getValue());
    }

    private Grid<EventDTO> buildEventGrid() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        eventGrid.addColumn(EventDTO::title).setHeader("Event").setSortable(true).setAutoWidth(true);
        eventGrid.addColumn(e -> e.artist() != null ? e.artist() : "").setHeader("Artist").setAutoWidth(true);
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

    private void loadEvents(String query, String category, String location, String artist) {
        Result<List<EventDTO>> result = presenter.loadEvents(query, category, location, artist);
        List<EventDTO> events = result.isSuccess() ? result.getOrThrow() : Collections.emptyList();
        eventGrid.setItems(events);
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
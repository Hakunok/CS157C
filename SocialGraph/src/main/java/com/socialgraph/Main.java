package com.socialgraph;

import com.socialgraph.database.Neo4jConnection;
import com.socialgraph.model.User;
import com.socialgraph.service.*;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Main extends Application {

    private Neo4jConnection connection;
    private Neo4jAuthService auth;
    private SocialGraphService social;
    private UserSearchService searchService;
    private PopularUsersService popularService;

    private Stage stage;
    private User currentUser;
    private BorderPane layout;

    private final ExecutorService executor =
        Executors.newFixedThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });

    private final AtomicInteger requestGeneration =
        new AtomicInteger();

    private static final int PAGE_SIZE = 20;

    private record SearchPage(
        List<UserSearchService.UserSearchResult> users,
        long total
    ) {}

    private record PopularPage(
        List<PopularUsersService.PopularUser> users,
        long total
    ) {}

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("SocialGraph");
        stage.setMinWidth(900);
        stage.setMinHeight(650);
        connectToDatabase();
        stage.show();
    }

    private void connectToDatabase() {
        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }

            connection = new Neo4jConnection();

            auth = new Neo4jAuthService(connection.getDriver());
            social = new Neo4jSocialGraphService(connection.getDriver());
            searchService = new UserSearchService(connection.getDriver());
            popularService = new PopularUsersService(connection.getDriver());

            showWelcome();

        } catch (Exception ex) {
            showStartupError(ex.getMessage());
        }
    }

    @Override
    public void stop() {
        executor.shutdownNow();

        if (connection != null) {
            connection.close();
        }
    }

    private void applyStyles(Scene scene) {
        var css = getClass().getResource("/css/styles.css");

        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    private Label heading(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("page-title");
        return label;
    }

    private Label subtitle(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("muted-label");
        return label;
    }

    private TextField field(String prompt) {
        TextField input = new TextField();
        input.setPromptText(prompt);
        input.setPrefHeight(40);
        return input;
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(text);
        button.setPrefHeight(40);
        button.setMinWidth(120);

        button.getStyleClass().add(
            primary ? "primary-button" : "secondary-button"
        );

        return button;
    }

    private VBox page(String title) {
        VBox box = new VBox(18);
        box.setPadding(new Insets(35));
        box.getChildren().add(heading(title));
        return box;
    }

    private VBox card() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");
        return box;
    }

    private void setContent(VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        layout.setCenter(scroll);
    }

    private void authScreen(VBox content) {
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(420);

        StackPane root = new StackPane(content);
        root.setPadding(new Insets(30));

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);

        Scene scene = new Scene(scroll, 1000, 700);
        applyStyles(scene);
        stage.setScene(scene);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("SocialGraph");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showStartupError(String message) {
        VBox box = page("Database Connection Failed");
        box.getChildren().add(subtitle(message));

        Button retry = button("Retry Connection", true);
        retry.setOnAction(e -> connectToDatabase());

        box.getChildren().add(retry);
        authScreen(box);
    }

    private void showWelcome() {
        VBox box = page("SocialGraph");

        box.getChildren().add(subtitle(
            "Connect, discover, and explore your network."
        ));

        Button login = button("Log In", true);
        Button register = button("Create Account", false);

        login.setOnAction(e -> showLogin());
        register.setOnAction(e -> showRegister());

        box.getChildren().addAll(login, register);
        authScreen(box);
    }

    private void showLogin() {
        VBox box = page("Welcome Back");

        TextField username = field("Username");

        PasswordField password = new PasswordField();
        password.setPromptText("Password");

        Label error = subtitle("");
        error.getStyleClass().add("error-label");

        Button login = button("Log In", true);
        Button back = button("Back", false);

        login.setOnAction(e -> {
            try {
                currentUser = auth.login(
                    username.getText(),
                    password.getText()
                );

                showDashboard();

            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());

            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });

        password.setOnAction(e -> login.fire());
        back.setOnAction(e -> showWelcome());

        box.getChildren().addAll(
            username, password, error, login, back
        );

        authScreen(box);
    }

    private void showRegister() {
        VBox box = page("Create Account");

        TextField name = field("Full name");
        TextField username = field("Username");
        TextField email = field("Email");

        PasswordField password = new PasswordField();
        password.setPromptText("Password (minimum 8 characters)");

        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm password");

        TextArea bio = new TextArea();
        bio.setPromptText("Bio (optional)");
        bio.setPrefRowCount(2);

        Label error = subtitle("");
        error.getStyleClass().add("error-label");

        Button create = button("Create Account", true);
        Button back = button("Back", false);

        create.setOnAction(e -> {
            if (!password.getText().equals(confirm.getText())) {
                error.setText("Passwords do not match.");
                return;
            }

            try {
                currentUser = auth.register(
                    name.getText(),
                    username.getText(),
                    email.getText(),
                    password.getText(),
                    bio.getText()
                );

                showDashboard();

            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());

            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });

        back.setOnAction(e -> showWelcome());

        box.getChildren().addAll(
            name, username, email, password,
            confirm, bio, error, create, back
        );

        authScreen(box);
    }

    private Button nav(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPrefHeight(42);
        button.getStyleClass().add("sidebar-button");

        button.setOnAction(e -> {
            requestGeneration.incrementAndGet();

            try {
                action.run();
            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });

        return button;
    }

    private void showDashboard() {
        layout = new BorderPane();

        VBox sidebar = new VBox(9);
        sidebar.setPadding(new Insets(25, 16, 25, 16));
        sidebar.setPrefWidth(240);
        sidebar.getStyleClass().add("sidebar");

        Label logo = new Label("SocialGraph");
        logo.setStyle(
            "-fx-font-size: 24px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: white;"
        );

        Label account = new Label(
            "@" + currentUser.getUsername()
        );
        account.setStyle("-fx-text-fill: #94A3B8;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(
            logo, account, new Separator(),
            nav("Dashboard", this::showDashboardContent),
            nav("My Profile", this::showProfile),
            nav("Edit Profile", this::showEditProfile),
            nav("Discover Users", this::showDiscoverUsers),
            nav("Connections", this::showConnections),
            nav("Mutual Connections", this::showMutualConnections),
            nav("Recommendations", this::showRecommendations),
            nav("Popular Users", this::showPopularUsers),
            nav("Degrees of Separation", this::showDegrees),
            spacer,
            nav("Log Out", () -> {
                currentUser = null;
                showWelcome();
            })
        );

        layout.setLeft(sidebar);

        Scene scene = new Scene(layout, 1100, 720);
        applyStyles(scene);
        stage.setScene(scene);

        showDashboardContent();
    }

    private void showDashboardContent() {
        VBox content = page(
            "Welcome, " + currentUser.getName() + "!"
        );

        VBox info = card();

        info.getChildren().addAll(
            heading("Your Account"),
            new Label("@" + currentUser.getUsername()),
            new Label("Email: " + currentUser.getEmail()),
            subtitle("Connected to Neo4j.")
        );

        content.getChildren().add(info);
        setContent(content);
    }

    private void showProfile() {
        currentUser = auth.findUser(
            currentUser.getUsername()
        );

        VBox content = page("My Profile");
        VBox info = card();

        Label bio = new Label(
            "Bio: " + (
                currentUser.getBio().isBlank()
                    ? "No bio provided."
                    : currentUser.getBio()
            )
        );

        bio.setWrapText(true);

        info.getChildren().addAll(
            heading(currentUser.getName()),
            subtitle("@" + currentUser.getUsername()),
            new Label("Email: " + currentUser.getEmail()),
            bio
        );

        Button edit = button("Edit Profile", true);
        edit.setOnAction(e -> showEditProfile());

        info.getChildren().add(edit);
        content.getChildren().add(info);
        setContent(content);
    }

    private void showEditProfile() {
        VBox content = page("Edit Profile");
        VBox form = card();

        TextField name = field("Name");
        name.setText(currentUser.getName());

        TextField email = field("Email");
        email.setText(currentUser.getEmail());

        TextArea bio = new TextArea(currentUser.getBio());
        bio.setPromptText("Bio (optional)");
        bio.setPrefRowCount(3);

        Label error = subtitle("");
        error.getStyleClass().add("error-label");

        Button save = button("Save Changes", true);
        Button cancel = button("Cancel", false);

        save.setOnAction(e -> {
            try {
                currentUser = auth.updateProfile(
                    currentUser.getUsername(),
                    name.getText(),
                    email.getText(),
                    bio.getText()
                );

                showProfile();

            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());

            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });

        cancel.setOnAction(e -> showProfile());

        form.getChildren().addAll(
            subtitle(
                "Username: @" + currentUser.getUsername()
                + " (cannot be changed)"
            ),
            new Label("Full Name"), name,
            new Label("Email"), email,
            new Label("Bio"), bio,
            error, save, cancel
        );

        content.getChildren().add(form);
        setContent(content);
    }

    private HBox userRow(User user) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER);
        row.getStyleClass().add("card");

        VBox info = new VBox(4);

        Label name = new Label(user.getName());
        name.setStyle("-fx-font-weight: bold;");

        info.getChildren().addAll(
            name,
            subtitle("@" + user.getUsername())
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(info, spacer);
        return row;
    }

    // --------------------------------------------------
    // UC-10 AND UC-11: USER DISCOVERY
    // --------------------------------------------------

    private void showDiscoverUsers() {
        VBox content = page("Discover Users");

        content.getChildren().add(subtitle(
            "Search users and manage your connections."
        ));

        TextField search = field("Search name or username");
        Label count = subtitle("Loading users...");
        Label pageLabel = subtitle("Page 1");

        ProgressIndicator loading = new ProgressIndicator();
        loading.setPrefSize(24, 24);

        VBox results = new VBox(10);

        Button previous = button("Previous", false);
        Button next = button("Next", true);

        HBox pagination = new HBox(
            15, previous, pageLabel, next
        );

        pagination.setAlignment(Pos.CENTER_LEFT);

        final int[] pageIndex = {0};
        Runnable[] refresh = new Runnable[1];

        refresh[0] = () -> {
            int requestId = requestGeneration.incrementAndGet();

            String query = search.getText();
            int requestedPage = pageIndex[0];
            String currentUsername = currentUser.getUsername();

            loading.setVisible(true);
            previous.setDisable(true);
            next.setDisable(true);

            Task<SearchPage> task = new Task<>() {
                @Override
                protected SearchPage call() {
                    var users = searchService.searchWithFollowStatus(
                        currentUsername,
                        query,
                        requestedPage,
                        PAGE_SIZE
                    );

                    long total = searchService.countUsers(query);

                    return new SearchPage(users, total);
                }
            };

            task.setOnSucceeded(e -> {
                if (requestId != requestGeneration.get()
                        || content.getScene() == null
                        || currentUser == null) {
                    return;
                }

                loading.setVisible(false);
                SearchPage data = task.getValue();

                long totalPages = Math.max(
                    1,
                    (data.total() + PAGE_SIZE - 1) / PAGE_SIZE
                );

                if (requestedPage >= totalPages) {
                    pageIndex[0] = (int) (totalPages - 1);
                    refresh[0].run();
                    return;
                }

                count.setText(data.total() + " users found");

                pageLabel.setText(
                    "Page " + (requestedPage + 1)
                    + " of " + totalPages
                );

                previous.setDisable(requestedPage == 0);
                next.setDisable(requestedPage + 1 >= totalPages);

                results.getChildren().clear();

                for (var item : data.users()) {
                    User user = item.user();
                    HBox row = userRow(user);

                    if (user.getUsername().equalsIgnoreCase(
                            currentUser.getUsername())) {

                        row.getChildren().add(subtitle("You"));

                    } else {
                        Button action = button(
                            item.following() ? "Unfollow" : "Follow",
                            !item.following()
                        );

                        action.setOnAction(event -> {
                            action.setDisable(true);

                            Task<Void> followTask = new Task<>() {
                                @Override
                                protected Void call() {
                                    if (item.following()) {
                                        social.unfollow(
                                            currentUsername,
                                            user.getUsername()
                                        );
                                    } else {
                                        social.follow(
                                            currentUsername,
                                            user.getUsername()
                                        );
                                    }
                                    return null;
                                }
                            };

                            followTask.setOnSucceeded(done -> {
                                if (content.getScene() != null) {
                                    refresh[0].run();
                                }
                            });

                            followTask.setOnFailed(done -> {
                                action.setDisable(false);

                                showError(
                                    followTask.getException().getMessage()
                                );
                            });

                            executor.submit(followTask);
                        });

                        row.getChildren().add(action);
                    }

                    results.getChildren().add(row);
                }

                if (data.users().isEmpty()) {
                    results.getChildren().add(
                        subtitle("No users found.")
                    );
                }
            });

            task.setOnFailed(e -> {
                if (requestId != requestGeneration.get()
                        || content.getScene() == null) {
                    return;
                }

                loading.setVisible(false);
                count.setText("Unable to load users.");

                showError(task.getException().getMessage());
            });

            executor.submit(task);
        };

        search.textProperty().addListener(
            (obs, old, value) -> {
                pageIndex[0] = 0;
                refresh[0].run();
            }
        );

        previous.setOnAction(e -> {
            if (pageIndex[0] > 0) {
                pageIndex[0]--;
                refresh[0].run();
            }
        });

        next.setOnAction(e -> {
            pageIndex[0]++;
            refresh[0].run();
        });

        HBox status = new HBox(12, count, loading);
        status.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(
            search, status, results, pagination
        );

        setContent(content);
        refresh[0].run();
    }

    // --------------------------------------------------
    // UC-7: FOLLOWERS AND FOLLOWING
    // --------------------------------------------------

    private void showConnections() {
        VBox content = page("Connections");
        content.getChildren().add(heading("Following"));

        List<User> following = social.getFollowing(
            currentUser.getUsername()
        );

        if (following.isEmpty()) {
            content.getChildren().add(
                subtitle("Not following anyone yet.")
            );
        }

        for (User user : following) {
            HBox row = userRow(user);
            Button unfollow = button("Unfollow", false);

            unfollow.setOnAction(e -> {
                try {
                    social.unfollow(
                        currentUser.getUsername(),
                        user.getUsername()
                    );

                    showConnections();

                } catch (Exception ex) {
                    showError(ex.getMessage());
                }
            });

            row.getChildren().add(unfollow);
            content.getChildren().add(row);
        }

        content.getChildren().add(new Separator());
        content.getChildren().add(heading("Followers"));

        List<User> followers = social.getFollowers(
            currentUser.getUsername()
        );

        if (followers.isEmpty()) {
            content.getChildren().add(
                subtitle("No followers yet.")
            );
        }

        for (User user : followers) {
            content.getChildren().add(userRow(user));
        }

        setContent(content);
    }

    // --------------------------------------------------
    // UC-8: MUTUAL CONNECTIONS
    // --------------------------------------------------

    private void showMutualConnections() {
        VBox content = page("Mutual Connections");

        content.getChildren().add(subtitle(
            "Find users followed by both selected accounts."
        ));

        TextField first = field("First username");
        TextField second = field("Second username");

        first.setText(currentUser.getUsername());

        Button find = button("Find Mutual Connections", true);
        VBox results = new VBox(10);

        find.setOnAction(e -> {
            results.getChildren().clear();

            try {
                List<User> shared = social.getMutualConnections(
                    first.getText(),
                    second.getText()
                );

                results.getChildren().add(
                    heading("Mutual Connections: " + shared.size())
                );

                for (User user : shared) {
                    results.getChildren().add(userRow(user));
                }

                if (shared.isEmpty()) {
                    results.getChildren().add(
                        subtitle("No mutual connections found.")
                    );
                }

            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });

        content.getChildren().addAll(
            first, second, find, results
        );

        setContent(content);
    }

    // --------------------------------------------------
    // UC-9: FRIEND RECOMMENDATIONS
    // --------------------------------------------------

    private void showRecommendations() {
        VBox content = page("Friend Recommendations");

        content.getChildren().add(subtitle(
            "Suggested accounts based on exactly two follow hops."
        ));

        List<User> recommendations = social.getRecommendations(
            currentUser.getUsername()
        );

        content.getChildren().add(
            heading("Recommendations: " + recommendations.size())
        );

        if (recommendations.isEmpty()) {
            content.getChildren().add(
                subtitle("No recommendations available yet.")
            );
        }

        for (User user : recommendations) {
            HBox row = userRow(user);
            Button follow = button("Follow", true);

            follow.setOnAction(e -> {
                try {
                    social.follow(
                        currentUser.getUsername(),
                        user.getUsername()
                    );

                    showRecommendations();

                } catch (Exception ex) {
                    showError(ex.getMessage());
                }
            });

            row.getChildren().add(follow);
            content.getChildren().add(row);
        }

        setContent(content);
    }

    // --------------------------------------------------
    // UC-12: POPULAR USERS + UC-5 FOLLOW/UNFOLLOW
    // --------------------------------------------------

    private void showPopularUsers() {
        VBox content = page("Popular Users");

        content.getChildren().add(subtitle(
            "Users ranked by incoming follower count."
        ));

        Label count = subtitle("Loading ranking...");
        Label pageLabel = subtitle("Page 1");

        ProgressIndicator loading = new ProgressIndicator();
        loading.setPrefSize(24, 24);

        VBox results = new VBox(10);

        Button previous = button("Previous", false);
        Button next = button("Next", true);

        HBox pagination = new HBox(
            15, previous, pageLabel, next
        );

        pagination.setAlignment(Pos.CENTER_LEFT);

        final int[] pageIndex = {0};
        Runnable[] refresh = new Runnable[1];

        refresh[0] = () -> {
            int requestId = requestGeneration.incrementAndGet();
            int requestedPage = pageIndex[0];

            loading.setVisible(true);
            previous.setDisable(true);
            next.setDisable(true);

            Task<PopularPage> task = new Task<>() {
                @Override
                protected PopularPage call() {
                    var users = popularService.getPopularUsers(
                        requestedPage,
                        PAGE_SIZE
                    );

                    long total = popularService.countUsers();

                    return new PopularPage(users, total);
                }
            };

            task.setOnSucceeded(e -> {
                if (requestId != requestGeneration.get()
                        || content.getScene() == null
                        || currentUser == null) {
                    return;
                }

                loading.setVisible(false);

                PopularPage data = task.getValue();

                long totalPages = Math.max(
                    1,
                    (data.total() + PAGE_SIZE - 1) / PAGE_SIZE
                );

                if (requestedPage >= totalPages) {
                    pageIndex[0] = (int) (totalPages - 1);
                    refresh[0].run();
                    return;
                }

                count.setText(data.total() + " users ranked");

                pageLabel.setText(
                    "Page " + (requestedPage + 1)
                    + " of " + totalPages
                );

                previous.setDisable(requestedPage == 0);
                next.setDisable(requestedPage + 1 >= totalPages);

                results.getChildren().clear();

                for (int i = 0; i < data.users().size(); i++) {
                    var item = data.users().get(i);
                    User user = item.user();

                    HBox row = userRow(user);

                    Label followerCount = subtitle(
                        item.followers() + " followers"
                    );

                    row.getChildren().add(followerCount);

                    if (user.getUsername().equalsIgnoreCase(
                            currentUser.getUsername())) {

                        row.getChildren().add(subtitle("You"));

                    } else {
                        Button followButton = button("Follow", true);
                        followButton.setDisable(true);
                        row.getChildren().add(followButton);

                        String sourceUsername =
                            currentUser.getUsername();

                        String targetUsername =
                            user.getUsername();

                        Task<Boolean> statusTask = new Task<>() {
                            @Override
                            protected Boolean call() {
                                return social.isFollowing(
                                    sourceUsername,
                                    targetUsername
                                );
                            }
                        };

                        statusTask.setOnSucceeded(done -> {
                            if (content.getScene() == null
                                    || currentUser == null
                                    || !currentUser.getUsername()
                                        .equalsIgnoreCase(
                                            sourceUsername)) {
                                return;
                            }

                            final boolean[] following = {
                                statusTask.getValue()
                            };

                            updateFollowButton(
                                followButton,
                                following[0]
                            );

                            followButton.setDisable(false);

                            followButton.setOnAction(event -> {
                                followButton.setDisable(true);

                                boolean wasFollowing = following[0];

                                Task<Void> followTask = new Task<>() {
                                    @Override
                                    protected Void call() {
                                        if (wasFollowing) {
                                            social.unfollow(
                                                sourceUsername,
                                                targetUsername
                                            );
                                        } else {
                                            social.follow(
                                                sourceUsername,
                                                targetUsername
                                            );
                                        }
                                        return null;
                                    }
                                };

                                followTask.setOnSucceeded(success -> {
                                    if (content.getScene() == null
                                            || currentUser == null
                                            || !currentUser.getUsername()
                                                .equalsIgnoreCase(
                                                    sourceUsername)) {
                                        return;
                                    }

                                    following[0] = !wasFollowing;

                                    updateFollowButton(
                                        followButton,
                                        following[0]
                                    );

                                    followButton.setDisable(false);
                                });

                                followTask.setOnFailed(failure -> {
                                    if (content.getScene() != null) {
                                        followButton.setDisable(false);

                                        showError(
                                            followTask.getException()
                                                .getMessage()
                                        );
                                    }
                                });

                                executor.submit(followTask);
                            });
                        });

                        statusTask.setOnFailed(done -> {
                            if (content.getScene() != null) {
                                followButton.setText("Unavailable");
                                followButton.setDisable(true);
                            }
                        });

                        executor.submit(statusTask);
                    }

                    VBox entry = new VBox(5);

                    entry.getChildren().addAll(
                        subtitle("Rank #" +
                            (requestedPage * PAGE_SIZE + i + 1)),
                        row
                    );

                    results.getChildren().add(entry);
                }

                if (data.users().isEmpty()) {
                    results.getChildren().add(
                        subtitle("No users found.")
                    );
                }
            });

            task.setOnFailed(e -> {
                if (requestId != requestGeneration.get()
                        || content.getScene() == null) {
                    return;
                }

                loading.setVisible(false);
                count.setText("Unable to load ranking.");

                showError(task.getException().getMessage());
            });

            executor.submit(task);
        };

        previous.setOnAction(e -> {
            if (pageIndex[0] > 0) {
                pageIndex[0]--;
                refresh[0].run();
            }
        });

        next.setOnAction(e -> {
            pageIndex[0]++;
            refresh[0].run();
        });

        HBox status = new HBox(12, count, loading);
        status.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(
            status, results, pagination
        );

        setContent(content);
        refresh[0].run();
    }

    private void updateFollowButton(
            Button button,
            boolean following) {

        button.setText(
            following ? "Unfollow" : "Follow"
        );

        button.getStyleClass().removeAll(
            "primary-button",
            "secondary-button"
        );

        button.getStyleClass().add(
            following ? "secondary-button" : "primary-button"
        );
    }

    // --------------------------------------------------
    // UC-13: DEGREES OF SEPARATION
    // --------------------------------------------------

    private void showDegrees() {
        VBox content = page("Degrees of Separation");

        content.getChildren().add(subtitle(
            "Find the shortest directed follow path (up to 4 hops)."
        ));

        TextField from = field("Starting username");
        TextField to = field("Destination username");

        from.setText(currentUser.getUsername());

        Button find = button("Find Shortest Path", true);
        VBox results = new VBox(12);

        find.setOnAction(e -> {
            results.getChildren().clear();

            try {
                List<User> path = social.findShortestPath(
                    from.getText(),
                    to.getText()
                );

                if (path.isEmpty()) {
                    results.getChildren().add(
                        subtitle(
                            "No connection found within 4 hops."
                        )
                    );
                    return;
                }

                int hops = path.size() - 1;

                results.getChildren().add(
                    heading(
                        "Degrees of separation: " + hops
                    )
                );

                StringBuilder display = new StringBuilder();

                for (int i = 0; i < path.size(); i++) {
                    if (i > 0) {
                        display.append(" -> ");
                    }

                    display.append("@")
                        .append(path.get(i).getUsername());
                }

                Label pathLabel = new Label(
                    display.toString()
                );

                pathLabel.setWrapText(true);

                results.getChildren().add(pathLabel);

            } catch (Exception ex) {
                showError(ex.getMessage());
            }
        });

        content.getChildren().addAll(
            from, to, find, results
        );

        setContent(content);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

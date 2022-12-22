package it.tdlight.example;

import it.tdlight.client.*;
import it.tdlight.client.AuthenticationData;
import it.tdlight.client.CommandHandler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.TDLibSettings;
import it.tdlight.common.Init;
import it.tdlight.common.utils.CantLoadLibrary;
import it.tdlight.jni.TdApi;
import org.apache.commons.codec.binary.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static it.tdlight.example.Controller.Logging;
import static it.tdlight.example.Controller.promptString;

public class ExampleNew {

    private static volatile boolean needQuit = false;
    private static volatile boolean canQuit = false;

    /**
     * Admin user id, used by the stop command example
     */
    private static final TdApi.MessageSender ADMIN_ID = new TdApi.MessageSenderUser(667900586);

    private static SimpleTelegramClient client;

    public static void main(String[] args) throws InterruptedException {

        try {
            login("");
        } catch (CantLoadLibrary e) {
            e.printStackTrace();
        }

        while (!needQuit) {
            getCommand();
        }
        while (!canQuit) {
            Thread.sleep(1);
        }
    }

    private static void login(String indexAccount) throws InterruptedException, CantLoadLibrary {
        // Initialize TDLight native libraries
        Init.start();
        // Obtain the API token
        var apiToken = APIToken.example();

        // Configure the client
        var settings = TDLibSettings.create(apiToken);

        // Configure the session directory
        var sessionPath = Paths.get("example-tdlight-session" + indexAccount);
        settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
        settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

        // Create a client
        client = new SimpleTelegramClient(settings);

        // Configure the authentication info
        var authenticationData = AuthenticationData.consoleLogin();

        // Add an example update handler that prints when the bot is started
        client.addUpdateHandler(TdApi.UpdateAuthorizationState.class, ExampleNew::onUpdateAuthorizationState);

        // Add an example update handler that prints every received message
        client.addUpdateHandler(TdApi.UpdateNewMessage.class, ExampleNew::onUpdateNewMessage);

        // Add an example command handler that stops the bot
        client.addCommandHandler("stop", new StopCommandHandler());

        // Start the client
        client.start(authenticationData);
//        client.waitForExit();
    }

    private static List<TdApi.Chat> ids = new ArrayList<>();
    private static List<Long> members = new ArrayList<>();

    private static void getCommand() {
        String command = promptString("Cmd Input:\n");
        String[] commands = command.split(" ", 2);
        long idGroup;
        try {
            switch (commands[0]) {
                case "!getchat":
                    canQuit = true;
//                    needQuit = true;
                    Controller.getChat(client, o -> {
                        for (long id : o) {
                            Controller.Logging("ID: " + id + "  Name: ");
//                            new Controller.ThreadGetChatInfo(client, id, chat -> {
//                                ids.add(chat);
//                                Controller.Logging("ID: " + id + "  Name: " + chat.title);
//                            }).start();
                        }
                    });

                    break;
                case "!getmember":
                    try {
                        idGroup = Long.parseLong(commands[1]);
                    } catch (NumberFormatException numberFormatException) {
                        break;
                    }
                    Controller.getChatHistory(client, ids.stream().filter(chat -> chat.id == idGroup).findFirst().get(), chat -> {
                        members = chat;
                        Controller.Logging("Member is " + chat.size());
                    });
                    break;
                case "getsuper":
//                    try {
//                        idGroup = Long.parseLong(commands[1]);
//                    } catch (NumberFormatException numberFormatException) {
//                        break;
//                    }
                    idGroup = -1001315055119L;
                    Controller.SupergroupFullInfo(client, idGroup, chat -> {
                        Logging(chat.memberCount + " ----");
                    });
                    break;
                case "!add":
                    Iterator<Long> iterator = Controller.listMembers.keySet().iterator();

                    while (iterator.hasNext()) {
                        Controller.addChatMembers(client, -614183139, iterator.next(), result -> {
                        });
                    }
                    break;
                case "!login":
                    canQuit = true;
//                    needQuit = true;
                    if (client != null)
                        client.sendClose();
                    new Thread(() -> {
                        try {
                            login(commands.length > 1 ? commands[1] : "");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (CantLoadLibrary e) {
                            e.printStackTrace();
                        }
                    }).start();
                    break;

            }
        } catch (ArrayIndexOutOfBoundsException e) {
        }

    }

    /**
     * Print new messages received via updateNewMessage
     */
    private static void onUpdateNewMessage(TdApi.UpdateNewMessage update) {
        // Get the message content
        var messageContent = update.message.content;

        // Get the message text
        String text;
        if (messageContent instanceof TdApi.MessageText messageText) {
            // Get the text of the text message
            text = messageText.text.text;
        } else {
            // We handle only text messages, the other messages will be printed as their type
            text = String.format("(%s)", messageContent.getClass().getSimpleName());
        }

        // Get the chat title
        client.send(new TdApi.GetChat(update.message.chatId), chatIdResult -> {
            // Get the chat response
            var chat = chatIdResult.get();
            // Get the chat name
            var chatName = chat.title;

            byte[] bytes = StringUtils.getBytesUtf8(chatName);
            String utf8ChatName = StringUtils.newStringUtf8(bytes);

            byte[] bytestext = StringUtils.getBytesUtf8(text);
            String utf8Text = StringUtils.newStringUtf8(bytestext);
        });
    }

    /**
     * Close the bot if the /stop command is sent by the administrator
     */
    private static class StopCommandHandler implements CommandHandler {

        @Override
        public void onCommand(TdApi.Chat chat, TdApi.MessageSender commandSender, String arguments) {
            // Check if the sender is the admin
            if (isAdmin(commandSender)) {
                // Stop the client
                System.out.println("Received stop command. closing...");
                client.sendClose();
            }
        }
    }

    /**
     * Print the bot status
     */
    private static void onUpdateAuthorizationState(TdApi.UpdateAuthorizationState update) {
        var authorizationState = update.authorizationState;
        if (authorizationState instanceof TdApi.AuthorizationStateReady) {
            canQuit = false;
            needQuit = false;
            Logging("Login success");
//            getCommand();
        } else if (authorizationState instanceof TdApi.AuthorizationStateClosing) {
            System.out.println("Closing...");
        } else if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
            System.out.println("Closed");
        } else if (authorizationState instanceof TdApi.AuthorizationStateLoggingOut) {
            System.out.println("Logging out...");
        }
    }

    /**
     * Check if the command sender is admin
     */
    private static boolean isAdmin(TdApi.MessageSender sender) {
        return sender.equals(ADMIN_ID);
    }
}

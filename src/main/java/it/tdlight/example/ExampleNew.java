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
import java.util.List;

import static it.tdlight.example.Controller.Logging;
import static it.tdlight.example.Controller.promptString;

public class ExampleNew {

    private static volatile boolean needQuit = false;
    private static volatile boolean canQuit = false;
    private static Logger logger = LoggerFactory.getLogger("Tdlib");

    /**
     * Admin user id, used by the stop command example
     */
    private static final TdApi.MessageSender ADMIN_ID = new TdApi.MessageSenderUser(667900586);

    private static SimpleTelegramClient client;

    public static void main(String[] args) throws InterruptedException {
        while (!needQuit) {
            getCommand();
        }
        while (!canQuit) {
            logger.error("Exiting...");
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
                    Controller.getChat(client, o -> {
                        logger.info("----------- LIST CHAT -----------");
                        for (long id : o) {
                            Controller.getChatInfo(client, id, o1 -> {
                                ids.add(o1);
                                Controller.Logging("ID: " + id + "  Name: " + o1.title);
                            });
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
                case "!getsuper":
                    try {
                        idGroup = Long.parseLong(commands[1]);
                    } catch (NumberFormatException numberFormatException) {
                        break;
                    }
                    Controller.SupergroupFullInfo(client, idGroup, chat -> {
                        Logging(chat.memberCount + " ----");
                    });
                    break;
                case "!add":
                    try {
                        idGroup = Long.parseLong(commands[1]);
                    } catch (NumberFormatException numberFormatException) {
                        break;
                    }
                    for (long l : members) {
                        Controller.addChatMembers(client, idGroup, l, result -> {
                        });
                    }
                    break;
                case "!login":
                    canQuit = true;
                    needQuit = true;
                    if (client != null)
                        client.sendClose();
                    login(commands.length > 1 ? commands[1] : "");
                    break;

            }
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.error("Not enough arguments");
        } catch (InterruptedException | CantLoadLibrary e) {
            e.printStackTrace();
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

            String format = String.format("Received new message from chat %s: %s%n", utf8ChatName, utf8Text);
            logger.info(format);
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
            logger.info("Login success");
            getCommand();
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

package it.tdlight.example;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.common.Log;
import it.tdlight.jni.TdApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Controller {

    public static String promptString(String prompt) {
        System.out.print(prompt);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String str = "";
        try {
            str = reader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return str;
    }

    public static void getChat(SimpleTelegramClient client, Consumer<long[]> consumer) {
        TdApi.GetChats getChats = new TdApi.GetChats(null, 20);
        client.send(getChats, result -> consumer.accept(result.get().chatIds));
    }

    public static void getChatInfo(SimpleTelegramClient client, long id, Consumer<TdApi.Chat> consumer) {
        TdApi.GetChat getChat = new TdApi.GetChat(id);
        client.send(getChat, result1 -> consumer.accept(result1.get()));
    }

    public static void addChatMembers(SimpleTelegramClient client, long id, long members, Consumer<TdApi.Ok> consumer) {
        TdApi.AddChatMember addChat = new TdApi.AddChatMember(id, members, 100);
        client.send(addChat, result1 -> consumer.accept(result1.get()));
    }

    private static long fromMessageID = 0;
    private static List<Long> users = new ArrayList<>();
    private static TdApi.Message cacheMess = null;
    private static boolean isFirstRequest = true;

    public static void getChatHistory(SimpleTelegramClient client, TdApi.Chat id, Consumer<List<Long>> consumer) {
        if (isFirstRequest) {
            fromMessageID = id.lastMessage.id;
            isFirstRequest = false;
        }
        TdApi.GetChatHistory getChat = new TdApi.GetChatHistory(id.id, fromMessageID, 0, 100, false);
        client.send(getChat, result1 -> {
            if (result1.get().messages.length == 0 || (cacheMess != null && cacheMess.id == result1.get().messages[result1.get().messages.length - 1].id)) {
                clearDataUser(client, consumer);
                return;
            }
            cacheMess = result1.get().messages[result1.get().messages.length - 1];
            for (int i = 0; i < result1.get().messages.length; i++) {
                if (result1.get().messages[i].senderId instanceof TdApi.MessageSenderUser) {
                    TdApi.MessageSenderUser messageSenderUser = (TdApi.MessageSenderUser) result1.get().messages[i].senderId;
                    if (!users.contains(messageSenderUser.userId)) {
                        users.add(messageSenderUser.userId);
                    }
                    fromMessageID = result1.get().messages[i].id;
                } else if (result1.get().messages.length == 1) {
                    clearDataUser(client, consumer);
                    return;
                }
            }
            Logging(users.size() + " members");
            getChatHistory(client, id, consumer);
        });
//        TdApi.GetChatMember getChatMember = new TdApi.GetChatMember(id.id)
//        TdApi.GetChat
    }

    private static void clearDataUser(SimpleTelegramClient client, Consumer consumer) {
        System.out.println("Total member: " + users.size());
        for (Long aLong : users) {
            TdApi.GetUser getUser = new TdApi.GetUser(aLong);
            client.send(getUser, result -> {
                System.out.println(result.get().firstName + result.get().lastName);
            });
        }
        consumer.accept(users);
        users = new ArrayList<>();
        fromMessageID = 0;
        isFirstRequest = true;
    }

    public static void SupergroupFullInfo(SimpleTelegramClient client, long id, Consumer<TdApi.SupergroupFullInfo> consumer) {
        TdApi.GetSupergroupFullInfo supergroupFullInfo = new TdApi.GetSupergroupFullInfo(id);
        client.send(supergroupFullInfo, result -> {
            TdApi.Error error = result.getError();
            if (error != null) {
                Logging(error.message);
            } else
                consumer.accept(result.get());

        });
    }

    public static void Logging(String text) {
        System.out.println(text);
    }

}

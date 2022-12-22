package it.tdlight.example;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.common.Log;
import it.tdlight.jni.TdApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class Controller {

    static BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    public static String promptString(String prompt) {
        System.out.print(prompt);
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

    public static class ThreadGetChatInfo extends Thread {

        SimpleTelegramClient client;
        long id;
        Consumer<TdApi.Chat> consumer;

        public ThreadGetChatInfo(SimpleTelegramClient client, long id, Consumer<TdApi.Chat> consumer) {
            this.client = client;
            this.id = id;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            getChatInfo(client, id, consumer);
        }
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

    //    private static List<TdApi.ChatMember> listMember = new ArrayList<>();
    public static ConcurrentHashMap<Long,Integer> listMembers = new ConcurrentHashMap();

    private static int limit = 100;
    private static int offSet = 0;
    private static int totalCountGroup = 0;
    static boolean skipLoadMember = false;


    public static void SupergroupFullInfo(SimpleTelegramClient client, long id, Consumer<TdApi.SupergroupFullInfo> consumer) {
        TdApi.GetChat getChat = new TdApi.GetChat(id);
        client.send(getChat, result -> {
            if (result.get().type instanceof TdApi.ChatTypeSupergroup) {
                TdApi.ChatTypeSupergroup chatTypeSupergroup = (TdApi.ChatTypeSupergroup) result.get().type;
                TdApi.GetSupergroupFullInfo supergroupFullInfo = new TdApi.GetSupergroupFullInfo(chatTypeSupergroup.supergroupId);
                client.send(supergroupFullInfo, result1 -> {
                    totalCountGroup = result1.get().memberCount;
                    if (result1.isError()) {
                        Logging(result1.getError().message);
                    } else {
                        GetMemberGroup(client, chatTypeSupergroup.supergroupId);
                    }
                });
            }
        });
    }

    private static void GetMemberGroup(SimpleTelegramClient client, long id) {
        TdApi.GetSupergroupMembers getSupergroupMembers = new TdApi.GetSupergroupMembers(id,
                new TdApi.SupergroupMembersFilterRecent(), offSet, limit);
        client.send(getSupergroupMembers, result2 -> {
            if (result2.isError()) {
                Logging(result2.getError().message);
            } else {
                if (result2.get().members.length <= 0) {
                    skipLoadMember = true;
                    return;
                }
                for (TdApi.ChatMember chatMember : result2.get().members) {
                    if (chatMember.memberId instanceof TdApi.MessageSenderUser) {
                        TdApi.MessageSenderUser member = (TdApi.MessageSenderUser) chatMember.memberId;
                        listMembers.put(member.userId, 1);
                    }
                }
                Logging("offSet " + offSet + " ____  natruou " + listMembers.size());
                offSet += 100;
                GetMemberGroup(client, id);
            }
        });
    }

    public static void Logging(String text) {
        System.out.println(text);
    }

}

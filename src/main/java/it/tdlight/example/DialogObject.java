package it.tdlight.example;

public class DialogObject {

    public static long makeFolderDialogId(int folderId) {
        return 0x2000000000000000L | (long) folderId;
    }

    public static boolean isFolderDialogId(long dialogId) {
        return (dialogId & 0x2000000000000000L) != 0 && (dialogId & 0x8000000000000000L) == 0;
    }

//    public static long getPeerDialogId(TLRPC.Peer peer) {
//        if (peer == null) {
//            return 0;
//        }
//        if (peer.user_id != 0) {
//            return peer.user_id;
//        } else if (peer.chat_id != 0) {
//            return -peer.chat_id;
//        } else {
//            return -peer.channel_id;
//        }
//    }

//    public static long getPeerDialogId(TLRPC.InputPeer peer) {
//        if (peer == null) {
//            return 0;
//        }
//        if (peer.user_id != 0) {
//            return peer.user_id;
//        } else if (peer.chat_id != 0) {
//            return -peer.chat_id;
//        } else {
//            return -peer.channel_id;
//        }
//    }


    public static boolean isChatDialog(long dialogId) {
        return !isEncryptedDialog(dialogId) && !isFolderDialogId(dialogId) && dialogId < 0;
    }

    public static boolean isUserDialog(long dialogId) {
        return !isEncryptedDialog(dialogId) && !isFolderDialogId(dialogId) && dialogId > 0;
    }

    public static boolean isEncryptedDialog(long dialogId) {
        return (dialogId & 0x4000000000000000L) != 0 && (dialogId & 0x8000000000000000L) == 0;
    }

    public static long makeEncryptedDialogId(long chatId) {
        return 0x4000000000000000L | (chatId & 0x00000000ffffffffL);
    }

    public static int getEncryptedChatId(long dialogId) {
        return (int) (dialogId & 0x00000000ffffffffL);
    }

    public static int getFolderId(long dialogId) {
        return (int) dialogId;
    }
}

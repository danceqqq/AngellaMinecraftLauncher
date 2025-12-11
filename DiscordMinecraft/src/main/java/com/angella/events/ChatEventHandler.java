package com.angella.events;

import com.angella.llm.AngellaChatService;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;

public class ChatEventHandler {

    public static void register() {
        // Listen to regular chat messages
        ServerMessageEvents.CHAT_MESSAGE.register(ChatEventHandler::onChatMessage);
    }

    private static void onChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        String raw = message.getContent().getString();
        AngellaChatService service = AngellaChatService.getInstance();
        if (service != null) {
            service.onChatMessage(sender, raw);
        }
    }
}

